package org.snomed.snowstorm.core.data.services.classification;

import io.kaicode.elasticvc.api.BranchCriteria;
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
import org.snomed.snowstorm.core.data.domain.classification.Classification;
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
		final Boolean classificationStatus = getClassificationStatus(commit.getBranch());

		// If classified (or status not set)
		if ((classificationStatus == null || classificationStatus) && !classificationSavedForThisCommit(commit) && anyClassifiableChange(commit)) {
			setClassificationStatus(commit.getBranch(), false);
		}
	}

	private boolean classificationSavedForThisCommit(Commit commit) {
		System.out.println();
		System.out.println("all");
		final Iterable<Classification> all = classificationRepository.findAll();
		all.forEach(System.out::println);
		System.out.println();
		System.out.println("Commit timepoint " + commit.getTimepoint().getTime());
		final Classification oneByPathAndStatusAndSaveDate = classificationRepository.findOneByPathAndStatusAndSaveDate(commit.getBranch().getPath(), ClassificationStatus.SAVED, commit.getTimepoint().getTime());
		return oneByPathAndStatusAndSaveDate
				!= null;
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
