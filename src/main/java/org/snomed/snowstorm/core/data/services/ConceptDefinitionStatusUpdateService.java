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
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Service
public class ConceptDefinitionStatusUpdateService extends ComponentService implements CommitListener {

    public static final String EQUIVALENT_CLASSES = "EquivalentClasses";

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
            } catch (ServiceException | IOException e) {
                throw new IllegalStateException("Failed to update concept definition status due to " + e);
            }
        }
    }

    void performUpdate(Commit commit) throws ServiceException, IOException {
        Map<String, List<ReferenceSetMember>> changedAxioms = getAxiomsChangedOrDeleted(commit);
        if (!changedAxioms.isEmpty()) {
            Set<String> conceptIds = changedAxioms.keySet();
            Collection<Concept> committedConcepts = getConcepts(conceptIds, commit);
            Set<Concept> updated = new HashSet<>();
            Set<String> deletedComponents = commit.getEntityVersionsDeleted();
            committedConcepts.stream()
                    .forEach(concept -> update(concept, deletedComponents, changedAxioms.get(concept.getConceptId()), updated));
            saveChanges(updated, commit);
            logger.info("Total {} concepts updated with new definition status.", updated.size());
        }
    }

    private void update(Concept concept, Set<String> deletedComponents, List<ReferenceSetMember> referenceSetMembers, Set<Concept> updated) {
        if (referenceSetMembers != null && !referenceSetMembers.isEmpty()) {
            List<ReferenceSetMember> activeAxioms = new ArrayList<>();
            for (ReferenceSetMember axiom : referenceSetMembers) {
                if (deletedComponents.contains(axiom.getId()) || !axiom.isActive()) {
                    continue;
                }
                activeAxioms.add(axiom);
            }

            String definitionStatusId = getDefinitionStatus(activeAxioms);
            if (!concept.getDefinitionStatusId().equals(definitionStatusId)) {
                concept.setDefinitionStatusId(definitionStatusId);
                concept.markChanged();
                updated.add(concept);
            }
        }
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

    private String getDefinitionStatus(List<ReferenceSetMember> activeAxioms) {
        // Determine the definition status id
        for (ReferenceSetMember axiomRefset : activeAxioms) {

            String owlExpression = axiomRefset.getAdditionalField( ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION);
            if (owlExpression != null && owlExpression.startsWith(EQUIVALENT_CLASSES)) {
                return Concepts.FULLY_DEFINED;
            }
        }
        return Concepts.PRIMITIVE;
    }

    private Map<String, List<ReferenceSetMember>> getAxiomsChangedOrDeleted(Commit commit) {
        BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaChangesAndDeletionsWithinOpenCommitOnly(commit);
        Map<String, List<ReferenceSetMember>> result = new HashMap<>();
        try (final CloseableIterator<ReferenceSetMember> changedAxioms = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
                .withQuery(boolQuery()
                        .must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
                        .must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.OWL_AXIOM_REFERENCE_SET))
                )
                .withPageable(ConceptService.LARGE_PAGE).build(), ReferenceSetMember.class)) {

            while (changedAxioms.hasNext()) {
                ReferenceSetMember axiomRefset = changedAxioms.next();
                result.computeIfAbsent(axiomRefset.getConceptId(), newList -> new ArrayList<>()).add(axiomRefset);
            }
        }
        return result;
    }

    Collection<Concept> getConcepts(Set<String> conceptIds, Commit commit) {
        BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit);
        NativeSearchQuery query = new NativeSearchQueryBuilder()
                .withQuery(boolQuery()
                        .must(branchCriteria.getEntityBranchCriteria(Concept.class))
                )
                .withFilter(boolQuery()
                        .must(termsQuery(Concept.Fields.CONCEPT_ID,  conceptIds)))
                .withPageable(ConceptService.LARGE_PAGE).build();

        List<Concept> concepts = new ArrayList<>();

        try (final CloseableIterator<Concept> existingConcepts = elasticsearchTemplate.stream(query, Concept.class)) {
            existingConcepts.forEachRemaining(existing -> concepts.add(existing));
        }
        return concepts;
    }

    private void saveChanges(Set<Concept> conceptsToSave, Commit commit) throws IllegalStateException, IOException {
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
