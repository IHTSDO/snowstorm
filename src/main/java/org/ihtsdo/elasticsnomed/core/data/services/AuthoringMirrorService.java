package org.ihtsdo.elasticsnomed.core.data.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.elasticsnomed.core.data.domain.Concept;
import org.ihtsdo.elasticsnomed.core.data.services.authoringmirror.ConceptChange;
import org.ihtsdo.elasticsnomed.core.data.services.authoringmirror.TraceabilityActivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AuthoringMirrorService {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMergeService branchMergeService;

	@Autowired
	private ObjectMapper objectMapper;

	private static final Pattern BRANCH_MERGE_COMMIT_COMMENT_PATTERN = Pattern.compile("^(.*) performed merge of (MAIN[^ ]*) to (MAIN[^ ]*)$");
	private static final Pattern QUOTES_NOT_ESCAPED_PATTERN = Pattern.compile("([^\\\\,:{]\"[^,:}])");

	private Logger logger = LoggerFactory.getLogger(getClass());

	public void receiveActivity(TraceabilityActivity activity) {
		String branchPath = activity.getBranchPath();
		String commitComment = activity.getCommitComment();
		Matcher matcher = BRANCH_MERGE_COMMIT_COMMENT_PATTERN.matcher(commitComment);

		Map<String, ConceptChange> changes = activity.getChanges();
		if (changes != null && !changes.isEmpty()) {
			Assert.hasLength(branchPath, "Branch path is required.");
			if (branchService.findLatest(branchPath) == null) {
				branchService.recursiveCreate(branchPath);
			}

			logger.info("Mirroring traceability content change '{}'", commitComment);
			changes.values().forEach(conceptChange -> {
				conceptChange.getChanges().forEach(componentChange -> {
					if ("Concept".equals(componentChange.getComponentType()) && "DELETE".equals(componentChange.getType())) {
						String componentId = componentChange.getComponentId();
						if (conceptService.exists(componentId, branchPath)) {
							conceptService.deleteConceptAndComponents(componentId, branchPath, true);
						}
					}
				});
			});
			List<Concept> conceptsToCreateUpdate = changes.values().stream().filter(componentChange -> componentChange.getConcept() != null).map(ConceptChange::getConcept).collect(Collectors.toList());
			if (!conceptsToCreateUpdate.isEmpty()) {
				conceptService.createUpdate(conceptsToCreateUpdate, branchPath);
			}
		} else if (matcher.matches()) {
			logger.info("Mirroring traceability branch operation.");
			// Could be a branch rebase or promotion
			// We have to parse the commit comment to get the information. This is brittle.
			String sourceBranch = matcher.group(2);
			String targetBranch = matcher.group(3);
			if (branchService.findLatest(sourceBranch) == null) {
				logger.warn("Source branch does not exist for merge operation {} -> {}, skipping.", sourceBranch, targetBranch);
				return;
			} else if (branchService.findLatest(targetBranch) == null) {
				logger.warn("Target branch does not exist for merge operation {} -> {}, skipping.", sourceBranch, targetBranch);
				return;
			}
			branchMergeService.mergeBranchSync(sourceBranch, targetBranch, null, true);
		} else {
			logger.warn("Could not mirror traceability event - unrecognised activity {} '{}'.", activity.getCommitTimestamp(), activity.getCommitComment());
		}
	}

	public void receiveActivityFile(InputStream inputStream) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
			String line;
			long lineNum = 0;
			try {
				while ((line = reader.readLine()) != null) {
					lineNum++;
					int i = line.indexOf("{");
					if (i >= 0) {
						if (line.contains("Classified ontology.")) {
							logger.info("Skipping classification commit.");
							continue;
						} else if (line.contains("Auto merging branches ")) {
							logger.info("Skipping 'Auto merging' commit.");
							continue;
						} else if (line.length() > 10000 && !(line.endsWith("} ") || line.endsWith("}"))) {
							logger.error("Can not process line {} because it's not well formed. Starting {}", lineNum, line.substring(0, 250));
							continue;
						}
						line = line.replace("\"empty\":false", "");
						line = fixQuotesNotEscaped(line);
						TraceabilityActivity traceabilityActivity = objectMapper.readValue(line.substring(i), TraceabilityActivity.class);
						receiveActivity(traceabilityActivity);
					}
				}
			} catch (IOException e) {
				throw new IOException("Failed to read line " + lineNum + " of traceability log file.", e);
			}
		}
	}

	String fixQuotesNotEscaped(String comment) {
		Matcher matcher = QUOTES_NOT_ESCAPED_PATTERN.matcher(comment);
		boolean anyFound = false;
		while (matcher.find()) {
			anyFound = true;
			comment = matcher.replaceFirst(matcher.group().replace(matcher.group(1), matcher.group(1).replace("\"", "|")));
			matcher = QUOTES_NOT_ESCAPED_PATTERN.matcher(comment);
		}
		if (anyFound) {
			comment = comment.replace("|", "\\\"");
		}
		return comment;
	}

	// Local testing method
	public void replayDirectoryOfFiles(String path) throws IOException {
		File dir = new File(path);
		for (File file : dir.listFiles()) {
			System.out.println("Replay file " + file.getName());
			receiveActivityFile(new FileInputStream(file));
		}
	}
}
