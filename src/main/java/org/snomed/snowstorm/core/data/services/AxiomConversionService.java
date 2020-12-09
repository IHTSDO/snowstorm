package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.snomed.otf.owltoolkit.conversion.AxiomRelationshipConversionService;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.otf.owltoolkit.domain.AxiomRepresentation;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.data.services.pojo.SAxiomRepresentation;
import org.snomed.snowstorm.core.util.TimerUtil;
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

	public AxiomConversionService() {
		axiomRelationshipConversionService = new AxiomRelationshipConversionService(Collections.emptySet());
	}

	public SAxiomRepresentation convertAxiomMemberToAxiomRepresentation(ReferenceSetMember axiomMember) throws ConversionException {
		AxiomRepresentation axiomRepresentation = axiomRelationshipConversionService.convertAxiomToRelationships(
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
		try {
			AxiomRelationshipConversionService conversionService = setupConversionService(branchPath);
			for (Concept concept : concepts) {
				for (Axiom axiom : concept.getClassAxioms()) {
					String owlExpression = conversionService.convertRelationshipsToAxiom(mapFromInternalRelationshipType(concept.getConceptId(), axiom.getDefinitionStatusId(), axiom.getRelationships(), true));
					axiom.setReferenceSetMember(createMember(concept, axiom, owlExpression));
				}
				for (Axiom gciAxiom : concept.getGciAxioms()) {
					String owlExpression = conversionService.convertRelationshipsToAxiom(mapFromInternalRelationshipType(concept.getConceptId(), gciAxiom.getDefinitionStatusId(), gciAxiom.getRelationships(), false));
					gciAxiom.setReferenceSetMember(createMember(concept, gciAxiom, owlExpression));
				}
			}
		} catch (final ConversionException e) {
			throw new RuntimeException("Failed to convert Relationship(s) to Axiom(s).", e);
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
		TimerUtil timer = new TimerUtil("Axiom conversion service setup");
		Page<ReferenceSetMember> mrcmAttributeDomainMembers = memberService.findMembers(branchPath, new MemberSearchRequest().active(true).referenceSet(Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN_INTERNATIONAL), LARGE_PAGE);
		Set<Long> neverGroupedAttributes = mrcmAttributeDomainMembers.getContent().stream()
				.filter(member -> "0".equals(member.getAdditionalField(ReferenceSetMember.MRCMAttributeDomainFields.GROUPED)))
				.map(ReferenceSetMember::getReferencedComponentId)
				.map(Long::parseLong)
				.collect(Collectors.toSet());
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		List<Long> objectAttributes = eclQueryService.selectConceptIds("<<" + Concepts.CONCEPT_MODEL_OBJECT_ATTRIBUTE, branchCriteria, branchPath, true, LARGE_PAGE).getContent();
		List<Long> dataAttributes = eclQueryService.selectConceptIds("<<" + Concepts.CONCEPT_MODEL_DATA_ATTRIBUTE, branchCriteria, branchPath, true, LARGE_PAGE).getContent();
		timer.checkpoint(String.format("Gathering %s never grouped attributes, %s object attributes and %s data attributes.", neverGroupedAttributes.size(), objectAttributes.size(), dataAttributes.size()));
		AxiomRelationshipConversionService conversionService = new AxiomRelationshipConversionService(neverGroupedAttributes, objectAttributes, dataAttributes);
		timer.checkpoint("Creating AxiomRelationshipConversionService");
		timer.finish();
		return conversionService;
	}

	private Set<Relationship> mapToInternalRelationshipType(Long sourceId, Map<Integer, List<org.snomed.otf.owltoolkit.domain.Relationship>> relationships) {
		if (relationships == null) return null;

		return relationships
				.values()
				.stream()
				.flatMap(Collection::stream)
				.map(externalRelationship -> {
					final long externalRelationshipTypeId = externalRelationship.getTypeId();
					final long externalRelationshipDestinationId = externalRelationship.getDestinationId();
					final Relationship internalRelationship = new Relationship(externalRelationshipTypeId + "", externalRelationshipDestinationId + "");
					internalRelationship.setSourceId(sourceId != null ? sourceId.toString() : null);
					internalRelationship.setGroupId(externalRelationship.getGroup());
					internalRelationship.setInferred(false);
					internalRelationship.setConcreteValueFromExternal(externalRelationship.getValue());

					return internalRelationship;
				})
				.collect(Collectors.toSet());
	}

	private AxiomRepresentation mapFromInternalRelationshipType(String conceptId, String definitionStatusId, Set<Relationship> relationships, boolean namedConceptOnLeft) {
		HashMap<Integer, List<org.snomed.otf.owltoolkit.domain.Relationship>> map = new HashMap<>();
		for (Relationship relationship : relationships) {
			final int internalRelationshipGroupId = relationship.getGroupId();
			final String internalRelationshipTypeIdString = relationship.getTypeId();
			final long internalRelationshipTypeId = internalRelationshipTypeIdString == null ? 0 : parseLong(internalRelationshipTypeIdString);

			if (relationship.isConcrete()) {
				final String value = relationship.getValue();
				final org.snomed.otf.owltoolkit.domain.Relationship.ConcreteValue concreteValue = new org.snomed.otf.owltoolkit.domain.Relationship.ConcreteValue(value);
				final org.snomed.otf.owltoolkit.domain.Relationship externalConcreteRelationship = new org.snomed.otf.owltoolkit.domain.Relationship(
						internalRelationshipGroupId,
						internalRelationshipTypeId,
						concreteValue
				);

				map.computeIfAbsent(externalConcreteRelationship.getGroup(), g -> new ArrayList<>()).add(externalConcreteRelationship);
			} else {
				final String internalDestinationIdString = relationship.getDestinationId();
				final long internalDestinationId = internalDestinationIdString == null ? 0 : parseLong(internalDestinationIdString);
				final org.snomed.otf.owltoolkit.domain.Relationship externalRelationship = new org.snomed.otf.owltoolkit.domain.Relationship(
						internalRelationshipGroupId,
						internalRelationshipTypeId,
						internalDestinationId
				);

				map.computeIfAbsent(externalRelationship.getGroup(), g -> new ArrayList<>()).add(externalRelationship);
			}
		}

		AxiomRepresentation axiomRepresentation = new AxiomRepresentation();
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
