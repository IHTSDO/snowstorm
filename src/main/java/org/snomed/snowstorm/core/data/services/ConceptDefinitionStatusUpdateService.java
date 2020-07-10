package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Iterables;
import io.kaicode.elasticvc.api.*;
import io.kaicode.elasticvc.domain.Commit;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.repositories.ConceptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.domain.Commit.CommitType.CONTENT;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.snomed.snowstorm.config.Config.BATCH_SAVE_SIZE;

@Service
public class ConceptDefinitionStatusUpdateService extends ComponentService implements CommitListener {

	private static final String DEFINED_CLASS_AXIOM_PREFIX = "EquivalentClasses(:";

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private  ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private ConceptRepository conceptRepository;

	@Autowired
	private BranchMetadataHelper branchMetadataHelper;

	private Logger logger = LoggerFactory.getLogger(ConceptDefinitionStatusUpdateService.class);

	@Override
	public void preCommitCompletion(Commit commit) throws IllegalStateException {
		if (commit.getCommitType() == CONTENT) {
			logger.debug("Start updating concept definition status on branch {}.", commit.getBranch().getPath());
			try {
				performUpdate(false, commit);
				logger.debug("End updating concept definition status on branch {}.", commit.getBranch().getPath());
			} catch (Exception e) {
				throw new IllegalStateException("Failed to update concept definition status." + e, e);
			}
		}
	}

	public void updateAllDefinitionStatuses(String path) throws ServiceException {
		logger.info("Updating all concept definition statuses on branch {}.", path);
		try (Commit commit = branchService.openCommit(path, branchMetadataHelper.getBranchLockMetadata("Updating all concept definition statuses."))) {
			performUpdate(true, commit);
			commit.markSuccessful();
		} catch (Exception e) {
			throw new ServiceException("Failed to update all concept definition statuses.", e);
		}
		logger.info("Completed updating all concept definition statuses on branch {}.", path);
	}

	private void performUpdate(boolean allConcepts, Commit commit) {
		Set<Long> conceptIdsToUpdate = allConcepts ? getAllConceptsWithActiveAxioms(commit) : getConceptsWithAxiomsChanged(commit);
		if (!conceptIdsToUpdate.isEmpty()) {
			logger.info("Checking {} concepts.", conceptIdsToUpdate.size());
			Set<Long> conceptsWithDefinedAxiomStatus = getConceptsWithDefinedAxiomStatus(allConcepts, conceptIdsToUpdate, commit);
			logger.info("{} concepts found with defined axioms.", conceptsWithDefinedAxiomStatus.size());
			Collection<Concept> conceptsToUpdate = findAndFixConceptsNeedingDefinitionUpdate(conceptIdsToUpdate, conceptsWithDefinedAxiomStatus, commit);
			logger.info("{} concepts need updating.", conceptsToUpdate.size());
			if (!conceptsToUpdate.isEmpty()) {
				saveChanges(conceptsToUpdate, commit);
			}
		}
	}

	private Set<Long> getAllConceptsWithActiveAxioms(Commit commit) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit);
		Set<Long> result = new LongOpenHashSet();
		BoolQueryBuilder must = boolQuery()
				.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
				.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.OWL_AXIOM_REFERENCE_SET))
				.must(termQuery(ReferenceSetMember.Fields.ACTIVE, true));
		try (final SearchHitsIterator<ReferenceSetMember> allActiveAxioms = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
				.withQuery(must)
				.withFields(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID)
				.withPageable(ConceptService.LARGE_PAGE).build(), ReferenceSetMember.class)) {

			allActiveAxioms.forEachRemaining(hit -> result.add(Long.parseLong(hit.getContent().getReferencedComponentId())));
		}
		return result;
	}

	private Set<Long> getConceptsWithDefinedAxiomStatus(boolean allConcepts, Set<Long> conceptIdsWithAxiomChange, Commit commit) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit);
		Set<Long> result = new LongOpenHashSet();
		BoolQueryBuilder must = boolQuery()
				.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
				.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.OWL_AXIOM_REFERENCE_SET))
				.must(termQuery(ReferenceSetMember.Fields.ACTIVE, true))
				.must(matchPhrasePrefixQuery(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION_FIELD_PATH, DEFINED_CLASS_AXIOM_PREFIX));
		if (!allConcepts) {
			must.must(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, conceptIdsWithAxiomChange));
		}
		try (final SearchHitsIterator<ReferenceSetMember> changedAxioms = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
				.withQuery(must)
				.withFields(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID)
				.withPageable(ConceptService.LARGE_PAGE).build(), ReferenceSetMember.class)) {

			changedAxioms.forEachRemaining(hit -> result.add(Long.parseLong(hit.getContent().getReferencedComponentId())));
			}
		return result;
	}

	private Set<Long> getConceptsWithAxiomsChanged(Commit commit) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaChangesAndDeletionsWithinOpenCommitOnly(commit);
		Set<Long> result = new LongOpenHashSet();
		try (final SearchHitsIterator<ReferenceSetMember> axioms = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.OWL_AXIOM_REFERENCE_SET))
				)
				.withPageable(ConceptService.LARGE_PAGE)
				.withFields(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID)
				.build(), ReferenceSetMember.class)) {
				axioms.forEachRemaining(hit -> result.add(Long.parseLong(hit.getContent().getReferencedComponentId())));
			}
		return result;
	}

	private Collection<Concept> findAndFixConceptsNeedingDefinitionUpdate(Set<Long> conceptIdsWithAxiomChange, Set<Long> conceptIdsWithDefinedAxiomStatus, Commit commit) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit);

		// Find primitive concepts which should be fully defined
		NativeSearchQuery fullyDefinedQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Concept.class))
						.must(termsQuery(Concept.Fields.CONCEPT_ID, conceptIdsWithDefinedAxiomStatus))
						.must(termQuery(Concept.Fields.DEFINITION_STATUS_ID, Concepts.PRIMITIVE))
						.mustNot(existsQuery("end"))
				)
				.withPageable(ConceptService.LARGE_PAGE).build();
		List<Concept> result = new ArrayList<>();
		try (final SearchHitsIterator<Concept> existingConcepts = elasticsearchTemplate.searchForStream(fullyDefinedQuery, Concept.class)) {
			existingConcepts.forEachRemaining(hit -> result.add(
					// Correct the definition status
					setDefinitionStatus(hit.getContent(), Concepts.FULLY_DEFINED)));
		}
		int toDefinedCount = result.size();
		if (toDefinedCount > 0) {
			logger.info("Updating {} concepts from primitive to fully defined due to axiom changes.", toDefinedCount);
		}
		Set<Long> primitiveConcepts = conceptIdsWithAxiomChange.stream()
				.filter(c -> !conceptIdsWithDefinedAxiomStatus.contains(c))
				.collect(Collectors.toSet());

		// Find fully defined concepts which should be primitive
		NativeSearchQuery primitiveQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Concept.class))
						.must(termsQuery(Concept.Fields.CONCEPT_ID, primitiveConcepts))
						.must(termQuery(Concept.Fields.DEFINITION_STATUS_ID, Concepts.FULLY_DEFINED))
						.mustNot(existsQuery("end"))
				)
				.withPageable(ConceptService.LARGE_PAGE).build();
		try (final SearchHitsIterator<Concept> existingConcepts = elasticsearchTemplate.searchForStream(primitiveQuery, Concept.class)) {
			existingConcepts.forEachRemaining(hit -> result.add(
					// Correct the definition status
					setDefinitionStatus(hit.getContent(), Concepts.PRIMITIVE)));
		}
		int toPrimitiveCount = result.size() - toDefinedCount;
		if (toPrimitiveCount > 0) {
			logger.info("Updating {} concepts from fully defined to primitive due to axiom changes.", toPrimitiveCount);
		}

		return result;
	}

	private Concept setDefinitionStatus(Concept concept, String definitionStatus) {
		concept.setDefinitionStatusId(definitionStatus);
		concept.markChanged();
		return concept;
	}

	private void saveChanges(Collection<Concept> conceptsToSave, Commit commit) {
		if (!conceptsToSave.isEmpty()) {
			// Save in batches
			for (List<Concept> concepts : Iterables.partition(conceptsToSave, BATCH_SAVE_SIZE)) {

				concepts.stream().forEach(Concept :: updateEffectiveTime);
				// Find concepts where new versions have already been created in the current commit.
				// Update these documents to avoid having two versions of the same concepts in the commit.
				Set<Concept> editedConcepts = concepts.stream()
						.filter(concept -> concept.getStart().equals(commit.getTimepoint()))
						.collect(Collectors.toSet());
				updateConceptDefinitionStatusViaUpdateQuery(editedConcepts);

				// Concepts which were not just saved/updated in the commit can go through the normal commit process.
				Set<Concept> toSave = concepts.stream()
						.filter(concept -> !editedConcepts.contains(concept))
						.collect(Collectors.toSet());
				doSaveBatchComponents(toSave, commit, Concept.Fields.CONCEPT_ID, conceptRepository);
			}
		}
	}

	private void updateConceptDefinitionStatusViaUpdateQuery(Collection<Concept> concepts) {
		List<UpdateQuery> updateQueries = new ArrayList<>();
		for (Concept concept : concepts) {
			String script = "ctx._source.definitionStatusId='" + concept.getDefinitionStatusId() + "'";
			updateQueries.add(UpdateQuery.builder(concept.getInternalId()).withScript(script).build());
		}
		if (!updateQueries.isEmpty()) {
			elasticsearchTemplate.bulkUpdate(updateQueries, elasticsearchTemplate.getIndexCoordinatesFor(Concept.class));
			elasticsearchTemplate.indexOps(Concept.class).refresh();
		}
	}
}
