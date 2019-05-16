package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.snomed.otf.owltoolkit.conversion.AxiomRelationshipConversionService;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.otf.owltoolkit.domain.AxiomRepresentation;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.data.services.pojo.SAxiomRepresentation;
import org.snomed.snowstorm.ecl.ECLQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static java.lang.Long.parseLong;

@Service
public class AxiomConversionService {

	@Autowired
	private ReferenceSetMemberService memberService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ECLQueryService eclQueryService;

	private final AxiomRelationshipConversionService axiomRelationshipConversionService;
	private List<Long> objectAttributes;
	private List<Long> dataAttributes;

	public AxiomConversionService() {
		axiomRelationshipConversionService = new AxiomRelationshipConversionService(Collections.emptySet());
	}

	public SAxiomRepresentation convertAxiomMemberToAxiomRepresentation(ReferenceSetMember axiomMember) throws ConversionException {
		AxiomRepresentation axiomRepresentation = axiomRelationshipConversionService.convertAxiomToRelationships(
				parseLong(axiomMember.getReferencedComponentId()),
				axiomMember.getAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION));

		if (axiomRepresentation == null) {// Will be null if the axiom is an Ontology Axiom for example a property chain or transitive axiom rather than an Additional Axiom or GCI.
			return null;
		}

		// Convert to Snowstorm Axiom Representation
		SAxiomRepresentation sAxiom = new SAxiomRepresentation();
		sAxiom.setPrimitive(axiomRepresentation.isPrimitive());
		sAxiom.setLeftHandSideNamedConcept(axiomRepresentation.getLeftHandSideNamedConcept());
		sAxiom.setRightHandSideNamedConcept(axiomRepresentation.getRightHandSideNamedConcept());
		sAxiom.setLeftHandSideRelationships(mapToInternalRelationshipType(null, axiomRepresentation.getLeftHandSideRelationships()));
		sAxiom.setRightHandSideRelationships(mapToInternalRelationshipType(axiomRepresentation.getLeftHandSideNamedConcept(), axiomRepresentation.getRightHandSideRelationships()));
		return sAxiom;
	}

	public void populateAxiomMembers(Collection<Concept> concepts, String branchPath) {
		AxiomRelationshipConversionService conversionService = setupConversionService(branchPath);
		for (Concept concept : concepts) {
			for (Axiom axiom : concept.getClassAxioms()) {
				String owlExpression = conversionService.convertRelationshipsToAxiom(
						mapFromInternalRelationshipType(concept.getConceptId(), axiom.getDefinitionStatusId(), axiom.getRelationships(), true));
				axiom.setReferenceSetMember(createMember(concept, axiom, owlExpression));
			}
			for (Axiom gciAxiom : concept.getGciAxioms()) {
				String owlExpression = conversionService.convertRelationshipsToAxiom(
						mapFromInternalRelationshipType(concept.getConceptId(), gciAxiom.getDefinitionStatusId(), gciAxiom.getRelationships(), false));
				gciAxiom.setReferenceSetMember(createMember(concept, gciAxiom, owlExpression));
			}
		}
	}

	public Set<Long> getReferencedConcepts(String owlExpression) throws ConversionException {
		return axiomRelationshipConversionService.getIdsOfConceptsNamedInAxiom(owlExpression);
	}

	private ReferenceSetMember createMember(Concept concept, Axiom axiom, String owlExpression) {
		return new ReferenceSetMember(axiom.getAxiomId() != null ? axiom.getAxiomId() : UUID.randomUUID().toString(), null, axiom.isActive(), axiom.getModuleId(), Concepts.OWL_AXIOM_REFERENCE_SET, concept.getConceptId())
				.setAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION, owlExpression);
	}

	private AxiomRelationshipConversionService setupConversionService(String branchPath) {
		Page<ReferenceSetMember> mrcmAttributeDomainMembers = memberService.findMembers(branchPath, new MemberSearchRequest().active(true).referenceSet(Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN_INTERNATIONAL), LARGE_PAGE);
		Set<Long> neverGroupedAttributes = mrcmAttributeDomainMembers.getContent().stream()
				.filter(member -> "0".equals(member.getAdditionalField(ReferenceSetMember.MRCMAttributeDomainFields.GROUPED)))
				.map(ReferenceSetMember::getReferencedComponentId)
				.map(Long::parseLong)
				.collect(Collectors.toSet());
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		objectAttributes = eclQueryService.selectConceptIds("<<" + Concepts.CONCEPT_MODEL_OBJECT_ATTRIBUTE, branchCriteria, branchPath, true, LARGE_PAGE).getContent();
		dataAttributes = eclQueryService.selectConceptIds("<<" + Concepts.CONCEPT_MODEL_DATA_ATTRIBUTE, branchCriteria, branchPath, true, LARGE_PAGE).getContent();
		return new AxiomRelationshipConversionService(neverGroupedAttributes, objectAttributes, dataAttributes);
	}

	private Set<Relationship> mapToInternalRelationshipType(Long sourceId, Map<Integer, List<org.snomed.otf.owltoolkit.domain.Relationship>> relationships) {
		if (relationships == null) return null;

		return relationships.values().stream()
				.flatMap(Collection::stream)
				.map(r -> new Relationship(r.getTypeId() + "", r.getDestinationId() + "").setSourceId(sourceId != null ? sourceId.toString() : null).setGroupId(r.getGroup()))
				.collect(Collectors.toSet());
	}

	private AxiomRepresentation mapFromInternalRelationshipType(String conceptId, String definitionStatusId, Set<Relationship> relationships, boolean namedConceptOnLeft) {
		AxiomRepresentation axiomRepresentation = new AxiomRepresentation();
		HashMap<Integer, List<org.snomed.otf.owltoolkit.domain.Relationship>> map = new HashMap<>();
		for (Relationship relationship : relationships) {
			org.snomed.otf.owltoolkit.domain.Relationship rel = new org.snomed.otf.owltoolkit.domain.Relationship(
					relationship.getGroupId(), parseLong(relationship.getTypeId()), parseLong(relationship.getDestinationId()));
			map.computeIfAbsent(rel.getGroup(), g -> new ArrayList<>()).add(rel);
		}
		axiomRepresentation.setPrimitive(Concepts.PRIMITIVE.equals(definitionStatusId));
		if (namedConceptOnLeft) {
			axiomRepresentation.setLeftHandSideNamedConcept(parseLong(conceptId));
			axiomRepresentation.setRightHandSideRelationships(map);
		} else {
			axiomRepresentation.setRightHandSideNamedConcept(parseLong(conceptId));
			axiomRepresentation.setLeftHandSideRelationships(map);
		}
		return axiomRepresentation;
	}
}
