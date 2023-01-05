package org.snomed.snowstorm.core.data.services.postcoordination;

import org.snomed.languages.scg.domain.model.AttributeValue;
import org.snomed.otf.owltoolkit.domain.AxiomRepresentation;
import org.snomed.otf.owltoolkit.domain.Relationship;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttribute;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttributeGroup;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

import static java.lang.Long.parseLong;

@Service
public class ExpressionAxiomConversionService {

	public Set<AxiomRepresentation> convertToAxioms(ComparableExpression classifiableForm) {
		Set<AxiomRepresentation> axioms = new HashSet<>();
		convertToAxioms(classifiableForm, axioms);
		return axioms;
	}

	private long convertToAxioms(ComparableExpression classifiableForm, Set<AxiomRepresentation> axioms) {
		Long sctid = classifiableForm.getExpressionId();

		AxiomRepresentation rep = new AxiomRepresentation();
		rep.setLeftHandSideNamedConcept(classifiableForm.getExpressionId());

		// Parents
		for (String focusConcept : classifiableForm.getFocusConcepts()) {
			rep.addRightHandSideRelationship(0, new Relationship(Concepts.IS_A_LONG, parseLong(focusConcept)));
		}

		int group = 0;

		// Ungrouped attributes
		if (classifiableForm.getComparableAttributes() != null) {
			for (ComparableAttribute attribute : classifiableForm.getComparableAttributes()) {
				rep.addRightHandSideRelationship(group++, new Relationship(parseLong(attribute.getAttributeId()), getAttributeValue(attribute, axioms)));
			}
		}

		// Grouped attributes
		if (classifiableForm.getComparableAttributeGroups() != null) {
			for (ComparableAttributeGroup attributeGroup : classifiableForm.getComparableAttributeGroups()) {
				group++;
				for (ComparableAttribute attribute : attributeGroup.getComparableAttributes()) {
					rep.addRightHandSideRelationship(group, new Relationship(group, parseLong(attribute.getAttributeId()), getAttributeValue(attribute, axioms)));
				}
			}
		}

		axioms.add(rep);
		return sctid;
	}

	private long getAttributeValue(ComparableAttribute attribute, Set<AxiomRepresentation> axioms) {
		final AttributeValue attributeValue = attribute.getAttributeValue();
		if (attributeValue.isNested()) {
			// Attribute value is nested expression.. convert to another axiom and assign an identifier to return
			return convertToAxioms((ComparableExpression) attributeValue.getNestedExpression(), axioms);
		} else {
			return parseLong(attribute.getAttributeValueId());
		}
	}
}
