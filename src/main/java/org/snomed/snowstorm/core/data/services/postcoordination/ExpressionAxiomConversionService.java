package org.snomed.snowstorm.core.data.services.postcoordination;

import org.snomed.languages.scg.domain.model.AttributeValue;
import org.snomed.otf.owltoolkit.domain.AxiomRepresentation;
import org.snomed.otf.owltoolkit.domain.Relationship;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.services.identifier.LocalRandomIdentifierSource;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttribute;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttributeGroup;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

import static java.lang.Long.parseLong;

@Service
public class ExpressionAxiomConversionService {

	private final LocalRandomIdentifierSource identifierSource;

	public ExpressionAxiomConversionService(@Autowired ElasticsearchRestTemplate elasticsearchRestTemplate) {
		identifierSource = new LocalRandomIdentifierSource(elasticsearchRestTemplate);
	}

	public Set<AxiomRepresentation> assignExpressionIdsAndConvertToAxioms(ComparableExpression classifiableForm) {
		Set<AxiomRepresentation> axioms = new HashSet<>();
		assignExpressionIdsAndConvertToAxioms(classifiableForm, axioms);
		return axioms;
	}

	private long assignExpressionIdsAndConvertToAxioms(ComparableExpression classifiableForm, Set<AxiomRepresentation> axioms) {
		Long sctid = classifiableForm.getExpressionId();
		if (sctid == null) {
			sctid = identifierSource.reserveIds(0, "06", 1).get(0);
			classifiableForm.setExpressionId(sctid);
		}

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
			return assignExpressionIdsAndConvertToAxioms((ComparableExpression) attributeValue.getNestedExpression(), axioms);
		} else {
			return parseLong(attribute.getAttributeValueId());
		}
	}
}
