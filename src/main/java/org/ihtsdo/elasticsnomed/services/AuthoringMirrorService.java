package org.ihtsdo.elasticsnomed.services;

import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.elasticsnomed.domain.Concept;
import org.ihtsdo.elasticsnomed.services.authoringmirror.ConceptChange;
import org.ihtsdo.elasticsnomed.services.authoringmirror.TraceabilityActivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

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

	private static final Pattern BRANCH_MERGE_COMMIT_COMMENT_PATTERN = Pattern.compile("^(.*) performed merge of (MAIN[^ ]*) to (MAIN[^ ]*)$");

	private Logger logger = LoggerFactory.getLogger(getClass());

	public void receiveActivity(TraceabilityActivity activity) {
		String branchPath = activity.getBranchPath();
		Assert.hasLength(branchPath, "Branch path is required.");
		if (branchService.findLatest(branchPath) == null) {
			branchService.create(branchPath);
		}

		String commitComment = activity.getCommitComment();
		Matcher matcher = BRANCH_MERGE_COMMIT_COMMENT_PATTERN.matcher(commitComment);

		Map<String, ConceptChange> changes = activity.getChanges();
		if (changes != null && !changes.isEmpty()) {
			logger.info("Mirroring traceability content change.");
			List<Concept> concepts = changes.values().stream().map(ConceptChange::getConcept).collect(Collectors.toList());
			conceptService.update(concepts, branchPath);
		} else if (matcher.matches()) {
			logger.info("Mirroring traceability branch operation.");
			// Could be a branch rebase or promotion
			// We have to parse the commit comment to get the information. This is brittle.
			String sourceBranch = matcher.group(2);
			String targetBranch = matcher.group(3);
			Assert.isTrue(branchPath.equals(targetBranch));
			branchMergeService.mergeBranchSync(sourceBranch, targetBranch, null, true);
		} else {
			logger.warn("Could not mirror traceability event - unrecognised activity.", activity);
		}
	}
}
