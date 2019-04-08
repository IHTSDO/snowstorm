package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Iterables;
import io.kaicode.elasticvc.api.*;
import io.kaicode.elasticvc.domain.Commit;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.elasticsearch.action.update.UpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.repositories.ConceptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.*;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.domain.Commit.CommitType.CONTENT;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
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

	private Logger logger = LoggerFactory.getLogger(ConceptDefinitionStatusUpdateService.class);

	@PostConstruct
	public void init() {
		branchService.addCommitListener(this);
	}

	@Override
	public void preCommitCompletion(Commit commit) throws IllegalStateException {
		if (commit.getCommitType() == CONTENT) {
			logger.debug("Start updating concept definition status on branch " + commit.getBranch().getPath());
			try {
				performUpdate(commit);
				logger.debug("End updating concept definition status on branch " + commit.getBranch().getPath());
			} catch (IOException e) {
				throw new IllegalStateException("Failed to update concept definition status due to " + e);
			}
		}
	}

	private void performUpdate(Commit commit) throws IOException {
		Set<Long> conceptIdsWithAxiomChange = getConceptsWithAxiomsChanged(commit);
		if (!conceptIdsWithAxiomChange.isEmpty()) {
			Set<Long> conceptsWithDefinedAxiomStatus = getConceptsWithDefinedAxiomStatus(conceptIdsWithAxiomChange, commit);
			Collection<Concept> conceptsToUpdate = findAndFixConceptsNeedingDefinitionUpdate(conceptIdsWithAxiomChange, conceptsWithDefinedAxiomStatus, commit);
			if (!conceptsToUpdate.isEmpty()) {
				saveChanges(conceptsToUpdate, commit);
			}
		}
	}

	private Set<Long> getConceptsWithDefinedAxiomStatus(Set<Long> conceptIdsWithAxiomChange, Commit commit) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit);
		Set<Long> result = new LongOpenHashSet();
		try (final CloseableIterator<ReferenceSetMember> changedAxioms = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.OWL_AXIOM_REFERENCE_SET))
						.must(termQuery(ReferenceSetMember.Fields.ACTIVE, true))
						.must(prefixQuery(ReferenceSetMember.Fields.getAdditionalFieldKeywordTypeMapping(
								ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION), DEFINED_CLASS_AXIOM_PREFIX))
						.must(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, conceptIdsWithAxiomChange))
				)
				.withFields(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID)
				.withPageable(ConceptService.LARGE_PAGE).build(), ReferenceSetMember.class)) {

			while (changedAxioms.hasNext()) {
				result.add(Long.parseLong(changedAxioms.next().getReferencedComponentId()));
			}
		}
		return result;
	}

	private Set<Long> getConceptsWithAxiomsChanged(Commit commit) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaChangesAndDeletionsWithinOpenCommitOnly(commit);
		Set<Long> result = new LongOpenHashSet();
		try (final CloseableIterator<ReferenceSetMember> axioms = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.OWL_AXIOM_REFERENCE_SET))
				)
				.withPageable(ConceptService.LARGE_PAGE)
				.withFields(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID)
				.build(), ReferenceSetMember.class)) {
			while (axioms.hasNext()) {
				result.add(Long.parseLong(axioms.next().getReferencedComponentId()));
			}
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
		try (final CloseableIterator<Concept> existingConcepts = elasticsearchTemplate.stream(fullyDefinedQuery, Concept.class)) {
			existingConcepts.forEachRemaining(existing -> result.add(
					// Correct the definition status
					setDefinitionStatus(existing, Concepts.FULLY_DEFINED)));
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
		try (final CloseableIterator<Concept> existingConcepts = elasticsearchTemplate.stream(primitiveQuery, Concept.class)) {
			existingConcepts.forEachRemaining(existing -> result.add(
					// Correct the definition status
					setDefinitionStatus(existing, Concepts.PRIMITIVE)));
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

	private void saveChanges(Collection<Concept> conceptsToSave, Commit commit) throws IllegalStateException, IOException {
		if (!conceptsToSave.isEmpty()) {
			// Save in batches
			for (List<Concept> concepts : Iterables.partition(conceptsToSave, BATCH_SAVE_SIZE)) {

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

	private void updateConceptDefinitionStatusViaUpdateQuery(Collection<Concept> concepts) throws IOException {
		List<UpdateQuery> updateQueries = new ArrayList<>();
		for (Concept concept : concepts) {
			UpdateRequest updateRequest = new UpdateRequest();
			updateRequest.doc(jsonBuilder()
					.startObject()
					.field(Concept.Fields.DEFINITION_STATUS_ID, concept.getDefinitionStatusId())
					.endObject());

			updateQueries.add(new UpdateQueryBuilder()
					.withClass(Concept.class)
					.withId(concept.getInternalId())
					.withUpdateRequest(updateRequest)
					.build());
		}
		if (!updateQueries.isEmpty()) {
			elasticsearchTemplate.bulkUpdate(updateQueries);
			elasticsearchTemplate.refresh(Concept.class);
		}
	}
}
