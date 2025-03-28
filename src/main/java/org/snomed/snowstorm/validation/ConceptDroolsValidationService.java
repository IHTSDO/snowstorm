package org.snomed.snowstorm.validation;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.ComponentService;
import org.ihtsdo.drools.domain.Constants;
import org.ihtsdo.drools.domain.Relationship;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.SnomedComponent;
import org.snomed.snowstorm.validation.domain.DroolsConcept;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool;
import static io.kaicode.elasticvc.helper.QueryHelper.termQuery;
import static io.kaicode.elasticvc.helper.QueryHelper.termsQuery;

public class ConceptDroolsValidationService implements org.ihtsdo.drools.service.ConceptService {

	private final BranchCriteria branchCriteria;
	private final ElasticsearchOperations elasticsearchOperations;
	private final DisposableQueryService queryService;
	private final Set<String> inferredTopLevelHierarchies;
	private final Map<String, Boolean> conceptActiveStates = Collections.synchronizedMap(new HashMap<>());
	private final Map<String, DroolsConcept> concepts = Collections.synchronizedMap(new HashMap<>());

	ConceptDroolsValidationService(BranchCriteria branchCriteria, ElasticsearchOperations elasticsearchOperations, DisposableQueryService queryService, Set<String> inferredTopLevelHierarchies) {
		this.branchCriteria = branchCriteria;
		this.elasticsearchOperations = elasticsearchOperations;
		this.queryService = queryService;
		this.inferredTopLevelHierarchies = inferredTopLevelHierarchies;
	}

	@Override
	public boolean isActive(String conceptId) {
		if (!conceptActiveStates.containsKey(conceptId)) {
			NativeQuery query = new NativeQueryBuilder()
					.withQuery(bool(b -> b
							.must(branchCriteria.getEntityBranchCriteria(Concept.class))
							.must(termQuery(Concept.Fields.CONCEPT_ID, conceptId))
							.must(termQuery(Concept.Fields.ACTIVE, true))))
					.withPageable(Config.PAGE_OF_ONE)
					.build();
			List<Concept> matches = elasticsearchOperations.search(query, Concept.class).stream().map(SearchHit::getContent).collect(Collectors.toList());
			conceptActiveStates.put(conceptId, !matches.isEmpty());
		}
		return conceptActiveStates.get(conceptId);
	}

	@Override
	public boolean isInactiveConceptSameAs(String inactiveConceptId, String conceptId) {
		final NativeQueryBuilder queryBuilder = new NativeQueryBuilder();
		queryBuilder.withQuery(bool(bq -> bq
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.REFSET_SAME_AS_ASSOCIATION))
						.must(termQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, inactiveConceptId))
						.must(termQuery(SnomedComponent.Fields.ACTIVE, true))))
				.withPageable(ComponentService.LARGE_PAGE);
		// Join Members
		List<ReferenceSetMember> associationTargetMembers = new ArrayList<>();
		try (final SearchHitsIterator <ReferenceSetMember> members = elasticsearchOperations.searchForStream(queryBuilder.build(), ReferenceSetMember.class)) {
			members.forEachRemaining(hit -> {
				ReferenceSetMember member = hit.getContent();
				associationTargetMembers.add(member);
			});
		}
		for (ReferenceSetMember member : associationTargetMembers) {
			if (member.getAdditionalField(ReferenceSetMember.AssociationFields.TARGET_COMP_ID).equals(conceptId)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public org.ihtsdo.drools.domain.Concept findById(String conceptId) {
		if (!concepts.containsKey(conceptId)) {
			NativeQuery query = new NativeQueryBuilder()
					.withQuery(bool(b -> b
							.must(branchCriteria.getEntityBranchCriteria(Concept.class))
							.must(termQuery(Concept.Fields.CONCEPT_ID, conceptId))))
					.withPageable(Config.PAGE_OF_ONE)
					.build();
			List<Concept> matches = elasticsearchOperations.search(query, Concept.class).stream().map(SearchHit::getContent).collect(Collectors.toList());
			concepts.put(conceptId, !matches.isEmpty() ? new DroolsConcept(matches.get(0)) : null);
		}
		return concepts.get(conceptId);
	}

    @Override
	public Set<String> getAllTopLevelHierarchies() {
		return inferredTopLevelHierarchies;
	}

	@Override
	public Set<String> findStatedAncestorsOfConcept(org.ihtsdo.drools.domain.Concept concept) {
		// This could be an unsaved concept, don't use the concept id, collect the stated parents - they will have an SCTID.

		Set<String> statedParents = getStatedParents(concept);
		if (statedParents.isEmpty()) {
			return Collections.emptySet();
		}

		StringBuilder ecl = new StringBuilder();
		Iterator<String> iterator = statedParents.iterator();
		for (int i = 0; i < statedParents.size(); i++) {
			if (i > 0) {
				ecl.append(" OR ");
			}
			// Using > rather than >> to hit ECL cache more often.
			ecl.append(">").append(iterator.next());
		}

		Set<String> conceptIds = getConceptIdsByEcl(true, ecl.toString());
		// Also include direct parents.
		conceptIds.addAll(statedParents);
		return conceptIds;
	}

	@Override
	public Set<String> findTopLevelHierarchiesOfConcept(org.ihtsdo.drools.domain.Concept concept) {
		Set<String> statedAncestorsOfConcept = findStatedAncestorsOfConcept(concept);
		return Sets.intersection(statedAncestorsOfConcept, inferredTopLevelHierarchies);
	}

	@Override
	public Set<String> findStatedAncestorsOfConcepts(List<String> statedParentIds) {
		if (statedParentIds.isEmpty()) {
			return Collections.emptySet();
		}
		StringBuilder eclBuilder = new StringBuilder();
		for (int i = 0; i < statedParentIds.size(); i++) {
			if (i > 0) {
				eclBuilder.append(" OR ");
			}
			eclBuilder.append(">").append(statedParentIds.get(i));
		}
		Set<String> ancestors = new HashSet<>(getConceptIdsByEcl(true, eclBuilder.toString()));
		ancestors.remove(Concepts.SNOMEDCT_ROOT);
		return ancestors;
	}

	@Override
	public Set<String> findLanguageReferenceSetByModule(String moduleId) {
		String ecl = "<" + Constants.LANGUAGE_TYPE_CONCEPT;
		return queryService.searchForIdStrings(queryService.createQueryBuilder(true).ecl(ecl).module(Long.valueOf(moduleId)));
	}

	private Set<String> getStatedParents(org.ihtsdo.drools.domain.Concept concept) {
		return concept.getRelationships().stream()
				.filter(r -> r.isActive() &&
						!r.isAxiomGCI() &&
						Concepts.ISA.equals(r.getTypeId()) &&
						(r.getAxiomId() != null || r.getCharacteristicTypeId().equals(Concepts.STATED_RELATIONSHIP))
				)
				.map(Relationship::getDestinationId)
				.collect(Collectors.toSet());
	}

	private Set<String> getConceptIdsByEcl(boolean stated, String ecl) {
		return queryService.searchForIdStrings(queryService.createQueryBuilder(stated).ecl(ecl));
	}
}
