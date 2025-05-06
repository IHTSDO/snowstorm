package org.snomed.snowstorm.validation;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.ihtsdo.drools.domain.Constants;
import org.ihtsdo.drools.domain.OntologyAxiom;
import org.ihtsdo.drools.domain.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.AxiomConversionService;
import org.snomed.snowstorm.core.data.services.pojo.SAxiomRepresentation;
import org.snomed.snowstorm.validation.domain.DroolsConcept;
import org.snomed.snowstorm.validation.domain.DroolsOntologyAxiom;
import org.snomed.snowstorm.validation.domain.DroolsRelationship;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;

import java.util.*;
import java.util.stream.Collectors;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool;
import static io.kaicode.elasticvc.helper.QueryHelper.termQuery;

public class ConceptDroolsValidationService implements org.ihtsdo.drools.service.ConceptService {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final VersionControlHelper versionControlHelper;
	private final AxiomConversionService axiomConversionService;
	private final BranchCriteria branchCriteria;
	private final ElasticsearchOperations elasticsearchOperations;
	private final DisposableQueryService queryService;
	private final Set<String> inferredTopLevelHierarchies;
	private final Map<String, Boolean> conceptActiveStates = Collections.synchronizedMap(new HashMap<>());
	private final Map<String, DroolsConcept> concepts = Collections.synchronizedMap(new HashMap<>());

	ConceptDroolsValidationService(BranchCriteria branchCriteria, ElasticsearchOperations elasticsearchOperations, DisposableQueryService queryService, Set<String> inferredTopLevelHierarchies, VersionControlHelper versionControlHelper, AxiomConversionService axiomConversionService) {
		this.versionControlHelper = versionControlHelper;
		this.axiomConversionService = axiomConversionService;
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
	public boolean isConceptModellingChanged(org.ihtsdo.drools.domain.Concept concept) {
		BranchCriteria branchCriteriaAtBranchCreation = versionControlHelper.getBranchCriteriaAtBranchCreationTimepoint(this.branchCriteria.getBranchPath());
		if (hasChangedAdditionalAxioms(concept, branchCriteriaAtBranchCreation)) {
			return true;
		} else {
			return hasChangedInferredRelationships(concept, branchCriteriaAtBranchCreation);
		}
	}

	private boolean hasChangedInferredRelationships(org.ihtsdo.drools.domain.Concept concept, BranchCriteria branchCriteriaAtBranchCreation) {
		// Find inferred relationships
		NativeQuery query = new NativeQueryBuilder()
			.withQuery(bool(b -> b
					.must(branchCriteriaAtBranchCreation.getEntityBranchCriteria(org.snomed.snowstorm.core.data.domain.Relationship.class))
					.must(termQuery(org.snomed.snowstorm.core.data.domain.Relationship.Fields.SOURCE_ID, concept.getId()))
					.must(termQuery(org.snomed.snowstorm.core.data.domain.Relationship.Fields.CHARACTERISTIC_TYPE_ID, org.snomed.snowstorm.core.data.domain.Relationship.CharacteristicType.inferred.getConceptId()))
			))
			.withPageable(Config.PAGE_OF_ONE)
			.build();
		List<org.snomed.snowstorm.core.data.domain.Relationship> relationshipsAtBranchCreation = elasticsearchOperations.search(query, org.snomed.snowstorm.core.data.domain.Relationship.class).stream().map(SearchHit::getContent).toList();
		List<DroolsRelationship> droolsRelationships = (List<DroolsRelationship>) concept.getRelationships().stream().filter(r -> r.getAxiomId() == null).toList();

		if (droolsRelationships.size() != relationshipsAtBranchCreation.size()) return true;
		for (DroolsRelationship droolsRelationship : droolsRelationships) {
			boolean foundRelationship = false;
			for (org.snomed.snowstorm.core.data.domain.Relationship relationship : relationshipsAtBranchCreation) {
				if (droolsRelationship.getId().equals(relationship.getId())
					&& droolsRelationship.getTypeId().equals(relationship.getTypeId())
					&& ((droolsRelationship.getDestinationId() != null &&droolsRelationship.getDestinationId().equals(relationship.getDestinationId()))
					|| (droolsRelationship.getConcreteValue() != null && droolsRelationship.getConcreteValue().equals(relationship.getConcreteValue() != null ? relationship.getConcreteValue().getValue() : null)))
					&& droolsRelationship.isActive() == relationship.isActive()
					&& droolsRelationship.getRelationshipGroup() == relationship.getRelationshipGroup()) {
					foundRelationship = true;
				}
			}
			if (!foundRelationship) {
				return true;
			}
		}
		return false;
	}

	private boolean hasChangedAdditionalAxioms(org.ihtsdo.drools.domain.Concept concept, BranchCriteria branchCriteriaAtBranchCreation) {
		// Find axiom members
		List<Axiom> additionalAxiomsAtBranchCreation = new ArrayList<>();
		NativeQuery query = new NativeQueryBuilder()
				.withQuery(bool(bq -> bq
						.must(branchCriteriaAtBranchCreation.getEntityBranchCriteria(ReferenceSetMember.class))
						.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.OWL_AXIOM_REFERENCE_SET))
						.must(termQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, concept.getId()))))
				.withPageable(ComponentService.LARGE_PAGE)
				.build();
		try (final SearchHitsIterator <ReferenceSetMember> members = elasticsearchOperations.searchForStream(query, ReferenceSetMember.class)) {
			members.forEachRemaining(hit -> {
				ReferenceSetMember member = hit.getContent();
				try {
					SAxiomRepresentation axiomRepresentation = axiomConversionService.convertAxiomMemberToAxiomRepresentation(member);
					if (axiomRepresentation != null && (axiomRepresentation.getLeftHandSideNamedConcept() != null)) {
						// Regular Axiom
						Set<org.snomed.snowstorm.core.data.domain.Relationship> relationships = axiomRepresentation.getRightHandSideRelationships();
						additionalAxiomsAtBranchCreation.add(new Axiom(member, axiomRepresentation.isPrimitive() ? Concepts.PRIMITIVE : Concepts.FULLY_DEFINED, relationships));
					}
				} catch (ConversionException e) {
					logger.error("Failed to deserialize axiom {}", member.getId(), e);
				}
			});
		}
		// verify if there is any changes in additional Axiom
		List<DroolsOntologyAxiom> additionalAxioms = new ArrayList<>();
		for (OntologyAxiom ontologyAxiom : concept.getOntologyAxioms()) {
			DroolsOntologyAxiom droolsOntologyAxiom = (DroolsOntologyAxiom) ontologyAxiom;
			if (!droolsOntologyAxiom.isAxiomGCI()) {
				additionalAxioms.add(droolsOntologyAxiom);
			}
		}
		if (additionalAxiomsAtBranchCreation.size() != additionalAxioms.size()) return true;
		for (DroolsOntologyAxiom droolsOntologyAxiom : additionalAxioms) {
			if (isAdditionalAxiomChanged(concept, droolsOntologyAxiom, additionalAxiomsAtBranchCreation)) return true;
		}
		return false;
	}

	private boolean isAdditionalAxiomChanged(org.ihtsdo.drools.domain.Concept concept, DroolsOntologyAxiom droolsOntologyAxiom, List<Axiom> additionalAxiomsAtBranchCreation) {
		boolean foundAxiom = false;
		for (Axiom axiom : additionalAxiomsAtBranchCreation) {
			if (droolsOntologyAxiom.getId().equals(axiom.getAxiomId()) && droolsOntologyAxiom.isActive() == axiom.isActive()) {
				foundAxiom = true;
				List<DroolsRelationship> droolsRelationships = (List<DroolsRelationship>) concept.getRelationships().stream().filter(r -> droolsOntologyAxiom.getId().equals(r.getAxiomId())).toList();
				if (droolsRelationships.size() != axiom.getRelationships().size()) return true;
				for (DroolsRelationship droolsRelationship : droolsRelationships) {
					if (isRelationshipChanged(axiom, droolsRelationship)) return true;
				}
			}
		}
        return !foundAxiom;
    }

	private boolean isRelationshipChanged(Axiom axiom, DroolsRelationship droolsRelationship) {
		boolean foundRelationship = false;
		for (org.snomed.snowstorm.core.data.domain.Relationship relationship : axiom.getRelationships()) {
			if (droolsRelationship.getTypeId().equals(relationship.getTypeId())
				&& ((droolsRelationship.getDestinationId() != null && droolsRelationship.getDestinationId().equals(relationship.getDestinationId()))
					|| (droolsRelationship.getConcreteValue() != null && droolsRelationship.getConcreteValue().equals(relationship.getConcreteValue() != null ? relationship.getConcreteValue().getValue() : null)))
				&& droolsRelationship.isActive() == relationship.isActive()
				&& droolsRelationship.getRelationshipGroup() == relationship.getRelationshipGroup()) {
				foundRelationship = true;
			}
		}
        return !foundRelationship;
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
