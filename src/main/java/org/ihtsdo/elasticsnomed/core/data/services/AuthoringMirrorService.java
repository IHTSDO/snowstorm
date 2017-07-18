package org.ihtsdo.elasticsnomed.core.data.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.elasticsnomed.core.data.domain.Concept;
import org.ihtsdo.elasticsnomed.core.data.services.authoringmirror.ComponentChange;
import org.ihtsdo.elasticsnomed.core.data.services.authoringmirror.ConceptChange;
import org.ihtsdo.elasticsnomed.core.data.services.authoringmirror.TraceabilityActivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
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

			logger.info("Mirroring traceability content change.");
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
			logger.warn("Could not mirror traceability event - unrecognised activity.", activity);
		}
	}

	public void receiveActivityFile(MultipartFile traceabilityLogFile) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(traceabilityLogFile.getInputStream()))) {
			String line;
			long lineNum = 0;
			try {
				while ((line = reader.readLine()) != null) {
					lineNum++;
					int i = line.indexOf("{");
					if (i >= 0) {
						line = line.replace("\"empty\":false", "");
						TraceabilityActivity traceabilityActivity = objectMapper.readValue(line.substring(i), TraceabilityActivity.class);
						receiveActivity(traceabilityActivity);
					}
				}
			} catch (IOException e) {
				throw new IOException("Failed to read line " + lineNum + " of traceability log file.", e);
			}
		}
	}
}
