package org.snomed.snowstorm.core.data.services.classification;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.CommitListener;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.DomainEntity;
import io.kaicode.elasticvc.domain.Metadata;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.domain.classification.ClassificationStatus;
import org.snomed.snowstorm.core.data.repositories.ClassificationRepository;
import org.snomed.snowstorm.core.data.services.BranchMetadataHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Service
public class BranchClassificationStatusService implements CommitListener {

	private static final String CLASSIFIED_METADATA_KEY = "classified";

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchRestTemplate elasticsearchRestTemplate;

	@Autowired
	private ClassificationRepository classificationRepository;

	@Autowired
	private BranchService branchService;

	public static Boolean getClassificationStatus(Branch branch) {
		final String classificationStatus = branch.getMetadata().getMapOrCreate(BranchMetadataHelper.INTERNAL_METADATA_KEY).get(CLASSIFIED_METADATA_KEY);
		return classificationStatus == null ? null : Boolean.parseBoolean(classificationStatus);
	}

	public static void setClassificationStatus(Branch branch, boolean classified) {
		setClassificationStatus(branch.getMetadata(), classified);
	}

	public static void setClassificationStatus(Metadata metadata, boolean classified) {
		metadata.getMapOrCreate(BranchMetadataHelper.INTERNAL_METADATA_KEY).put(CLASSIFIED_METADATA_KEY, String.valueOf(classified));
	}

	public static void clearClassificationStatus(Metadata metadata) {
		metadata.getMapOrCreate(BranchMetadataHelper.INTERNAL_METADATA_KEY).remove(CLASSIFIED_METADATA_KEY);
	}

	@Override
	public void preCommitCompletion(Commit commit) throws IllegalStateException {
		final Commit.CommitType commitType = commit.getCommitType();

		if (commitType == Commit.CommitType.CONTENT) {
			// Mark branch as not classified if a classifiable change is being made and it's not a classification save.
			// If classified (or status not set)
			if (falseStatusNotSet(commit) && !classificationSavedForThisCommit(commit) && anyClassifiableChange(commit)) {
				setClassificationStatus(commit.getBranch(), false);
			}

		} else if (commitType == Commit.CommitType.REBASE) {
			// Mark branch as not classified if:
			// - the parent branch is not classified
			// - or this branch contain classifiable changes
			if (falseStatusNotSet(commit) && (trueStatusNotSet(commit.getSourceBranchPath()) || anyClassifiableChangesUnpromoted(commit.getBranch()))) {
				setClassificationStatus(commit.getBranch(), false);
			}

		} else if (commitType == Commit.CommitType.PROMOTION) {
			// Promote classification status
			final Boolean classificationStatus = getClassificationStatus(branchService.findBranchOrThrow(commit.getSourceBranchPath()));
			setClassificationStatus(commit.getBranch(), classificationStatus != null && classificationStatus);
		}
	}

	private boolean falseStatusNotSet(Commit commit) {
		return !Boolean.FALSE.equals(getClassificationStatus(commit.getBranch()));
	}

	private boolean trueStatusNotSet(String branchPath) {
		return !Boolean.TRUE.equals(getClassificationStatus(branchService.findBranchOrThrow(branchPath)));
	}

	private boolean classificationSavedForThisCommit(Commit commit) {
		return classificationRepository.findOneByPathAndStatusAndSaveDate(commit.getBranch().getPath(), ClassificationStatus.SAVED, commit.getTimepoint().getTime()) != null;
	}

	private boolean anyClassifiableChangesUnpromoted(Branch branch) {
		final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaUnpromotedChangesAndDeletions(branch);
		return anyChangeOfType(branchCriteria, Concept.class, null) ||
				anyChangeOfType(branchCriteria, Relationship.class, null) ||
				anyChangeOfType(branchCriteria, ReferenceSetMember.class, boolQuery().must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.OWL_AXIOM_REFERENCE_SET)));
	}

	private boolean anyClassifiableChange(Commit commit) {
		final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaChangesAndDeletionsWithinOpenCommitOnly(commit);
		return anyChangeOfType(branchCriteria, Concept.class, null) ||
				anyChangeOfType(branchCriteria, Relationship.class, null) ||
				anyChangeOfType(branchCriteria, ReferenceSetMember.class, boolQuery().must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.OWL_AXIOM_REFERENCE_SET)));
	}

	private boolean anyChangeOfType(BranchCriteria branchCriteria, Class<? extends DomainEntity<?>> entityClass, BoolQueryBuilder additionalCriteria) {
		final BoolQueryBuilder queryBuilder = branchCriteria.getEntityBranchCriteria(entityClass);
		if (additionalCriteria != null) {
			queryBuilder.must(additionalCriteria);
		}
		return elasticsearchRestTemplate.count(new NativeSearchQueryBuilder().withQuery(queryBuilder).build(), entityClass) > 0;
	}

}
