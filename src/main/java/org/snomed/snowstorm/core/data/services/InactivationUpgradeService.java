package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import org.snomed.snowstorm.core.data.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Service
public class InactivationUpgradeService {

	@Autowired
	private BranchService branchService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchTemplate elasticsearchTemplate;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ConceptUpdateHelper conceptUpdateHelper;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	@Autowired
	private BranchMetadataHelper branchMetadataHelper;

	public void findAndUpdateInactivationIndicators(CodeSystem codeSystem) {
		if (codeSystem == null) {
			throw new IllegalArgumentException("CodeSystem must not be null");
		}
		String branchPath = codeSystem.getBranchPath();
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(codeSystem.getBranchPath());
		// find inactive concepts
		NativeSearchQuery inactiveConceptQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Concept.class))
						.must(termQuery(Concept.Fields.ACTIVE, "false")))
				.withFields(Concept.Fields.CONCEPT_ID)
				.build();
		List<Concept> conceptResults = elasticsearchTemplate.queryForList(inactiveConceptQuery, Concept.class);
		Set<String> inactiveConceptIds = conceptResults.stream().map(Concept :: getConceptId).collect(Collectors.toSet());

		// find descriptions with inactivation indicators
		NativeSearchQuery descriptionsWithIndicators = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						.must(termsQuery(ReferenceSetMember.Fields.CONCEPT_ID, inactiveConceptIds))
						.must(termsQuery(ReferenceSetMember.Fields.ACTIVE, "true")))
				.withFields(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID)
				.build();

		List<ReferenceSetMember> memberResults = elasticsearchTemplate.queryForList(descriptionsWithIndicators, ReferenceSetMember.class);

		Set<String> descriptionIds = memberResults.stream().map(ReferenceSetMember :: getReferencedComponentId).collect(Collectors.toSet());

				// find active descriptions without description inactivation indicators for inactive concepts
		NativeSearchQuery descriptionQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Description.class))
						.must(termQuery(Description.Fields.ACTIVE, "true"))
						.must(termsQuery(Description.Fields.CONCEPT_ID, inactiveConceptIds))
						.mustNot(termsQuery(Description.Fields.DESCRIPTION_ID, descriptionIds)))
				.build();
		List<Description> descriptions = elasticsearchTemplate.queryForList(descriptionQuery, Description.class);

		// delete descriptions if not published
		// add description inactivation indicators
		List<ReferenceSetMember> toSave = new ArrayList<>();
		List<Description> toDelete = new ArrayList<>();
		for (Description description : descriptions) {
			if (description.isReleased()) {
				ReferenceSetMember inactivation = new ReferenceSetMember(description.getModuleId(), Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET, description.getDescriptionId());
				inactivation.setAdditionalField(ReferenceSetMember.Fields.getAdditionalFieldTextTypeMapping("valueId"), Concepts.CONCEPT_NON_CURRENT);
				inactivation.setConceptId(description.getConceptId());
				inactivation.setCreating(true);
				inactivation.markChanged();
				toSave.add(inactivation);
			} else {
				description.markDeleted();
				toDelete.add(description);
			}
		}
		try (Commit commit = branchService.openCommit(branchPath, branchMetadataHelper.getBranchLockMetadata("Concept non-current description inactivation"))) {
			if (!toDelete.isEmpty()) {
				conceptUpdateHelper.doSaveBatchDescriptions(descriptions, commit);
			}
			if (!toSave.isEmpty()) {
				conceptUpdateHelper.doSaveBatchComponents(toSave, ReferenceSetMember.class, commit);
			}
			commit.markSuccessful();
		}
	}
}
