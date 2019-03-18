package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Iterables;
import io.kaicode.elasticvc.api.*;
import io.kaicode.elasticvc.domain.Commit;
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

@Service
public class ConceptDefinitionStatusUpdateService extends ComponentService implements CommitListener {

	public static final String DEFINED_CLASS_AXIOM_PREFIX = "EquivalentClasses(:";

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

	static final int BATCH_SAVE_SIZE = 10000;

	@PostConstruct
	public void init() {
		branchService.addCommitListener(this);
	}

	@Override
	public void preCommitCompletion(Commit commit) throws IllegalStateException {
		if (commit.getCommitType() == CONTENT) {
			logger.info(" Start updating concept definition status on branch " + commit.getBranch().getPath());
			try {
				performUpdate(commit);
				logger.info("End updating concept definition status on branch " + commit.getBranch().getPath());
			} catch ( IOException e) {
				throw new IllegalStateException("Failed to update concept definition status due to " + e);
			}
		}
	}

   private void performUpdate(Commit commit) throws IOException {
		Set<Long> conceptIds = getConceptsWithAxiomsChanged(commit);
		if (!conceptIds.isEmpty()) {
			Set<Long> definedConcepts = getConceptsWithDefinedStatus(commit, conceptIds);
			Collection<Concept> conceptsToUpdate = getConceptsToUpdate(definedConcepts, conceptIds, commit);
			saveChanges(conceptsToUpdate, commit);
			logger.info("Total {} concepts updated with new definition status.", conceptsToUpdate.size());
		}
	}


	private Concept update(Concept concept, String definitionStatus) {
		concept.setDefinitionStatusId(definitionStatus);
		concept.markChanged();
		return concept;
	}

	private void updateConceptDefinitionStatusViaTemplate(Collection<Concept> concepts) throws IOException {
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


	private Set<Long> getConceptsWithDefinedStatus(Commit commit, Set<Long> concepts) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit);
		Set<Long> result = new HashSet<>();
		try (final CloseableIterator<ReferenceSetMember> changedAxioms = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.OWL_AXIOM_REFERENCE_SET))
						.must(termQuery(ReferenceSetMember.Fields.ACTIVE, true))
						.must(prefixQuery(ReferenceSetMember.Fields.getAdditionalFieldKeywordTypeMapping(
								ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION), DEFINED_CLASS_AXIOM_PREFIX))
						.must(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, concepts))
						.mustNot(termsQuery(ReferenceSetMember.Fields.MEMBER_ID, commit.getEntityVersionsDeleted()))
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
		Set<Long> result = new HashSet<>();
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


	private Collection<Concept> getConceptsToUpdate(Set<Long> fullyDefined, Set<Long> conceptIds, Commit commit) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit);
		//search concepts need to be updated from primitive to fully defined
		NativeSearchQuery fullyDefinedQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Concept.class))
						.must(termsQuery(Concept.Fields.CONCEPT_ID, fullyDefined))
						.must(termQuery(Concept.Fields.DEFINITION_STATUS_ID, Concepts.PRIMITIVE))
						.mustNot(existsQuery("end"))
				)
				.withPageable(ConceptService.LARGE_PAGE).build();
		List<Concept> result = new ArrayList<>();
		try (final CloseableIterator<Concept> existingConcepts = elasticsearchTemplate.stream(fullyDefinedQuery, Concept.class)) {
			existingConcepts.forEachRemaining(existing -> result.add(update(existing, Concepts.FULLY_DEFINED)));
		}
		int counter = result.size();
		logger.info("Total {} concepts to be updated from primitive to fully defined", counter);
		Set<Long> primitiveConcepts = conceptIds.stream()
				.filter(c -> !fullyDefined.contains(c))
				.collect(Collectors.toSet());
		//search concepts need to be updated from fully defined to primitive
		NativeSearchQuery primitiveQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Concept.class))
						.must(termsQuery(Concept.Fields.CONCEPT_ID, primitiveConcepts))
						.must(termQuery(Concept.Fields.DEFINITION_STATUS_ID, Concepts.FULLY_DEFINED))
						.mustNot(existsQuery("end"))
				)
				.withPageable(ConceptService.LARGE_PAGE).build();
		try (final CloseableIterator<Concept> existingConcepts = elasticsearchTemplate.stream(primitiveQuery, Concept.class)) {
			existingConcepts.forEachRemaining(existing -> result.add(update(existing, Concepts.PRIMITIVE)));
		}
		logger.info("Total {} concepts to be updated from fully defined to primitive", (result.size()- counter));
		return result;
	}



	Collection<Concept> getConcepts(Set<String> conceptIds, Commit commit) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit);
		NativeSearchQuery query = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Concept.class))
						.must(termsQuery(Concept.Fields.CONCEPT_ID, conceptIds))
						.mustNot(existsQuery("end"))
				)
				.withPageable(ConceptService.LARGE_PAGE).build();
		List<Concept> concepts = new ArrayList<>();
		try (final CloseableIterator<Concept> existingConcepts = elasticsearchTemplate.stream(query, Concept.class)) {
			existingConcepts.forEachRemaining(existing -> concepts.add(existing));
		}
		if (concepts.size() > conceptIds.size()) {
			Map<String, List<Concept>> conceptMap = concepts.stream().collect(Collectors.groupingBy(Concept::getConceptId));
			List<String> duplicateIds = new ArrayList<>();
			for (String conceptId : conceptMap.keySet()) {
				List<Concept> duplicates = conceptMap.get(conceptId);
				if (duplicates.size() > 1) {
					logger.error("Found more than one concept {} on branch {}", duplicates.get(0), commit.getBranch().getPath());
					duplicates.forEach(c -> logger.info("id:{} path:{}, start:{}, end:{}", c.getInternalId(), c.getPath(), c.getStartDebugFormat(), c.getEndDebugFormat()));
					duplicateIds.add(conceptId);
				}
			}
			throw new IllegalStateException("More than one concept found for these id " + duplicateIds.toString() + " on branch " + commit.getBranch().getPath());
		}
		return concepts;
	}

	private void saveChanges(Collection<Concept> conceptsToSave, Commit commit) throws IllegalStateException, IOException {
		if (!conceptsToSave.isEmpty()) {
			// Save in batches
			for (List<Concept> concepts : Iterables.partition(conceptsToSave, BATCH_SAVE_SIZE)) {
				doSaveBatch(concepts, commit);
			}
		}
	}

	private void doSaveBatch(Collection<Concept> concepts, Commit commit) throws IOException {
		Set<Concept> editedConcepts = concepts.stream()
				.filter(concept -> concept.getStart().equals(commit.getTimepoint()))
				.collect(Collectors.toSet());
		updateConceptDefinitionStatusViaTemplate(editedConcepts);
		Set<Concept> toSave = concepts.stream()
				.filter(concept -> !editedConcepts.contains(concept))
				.collect(Collectors.toSet());
		doSaveBatchComponents(toSave, commit, Concept.Fields.CONCEPT_ID, conceptRepository);
	}
}
