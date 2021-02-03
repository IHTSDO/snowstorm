package org.snomed.snowstorm.core.data.services.postcoordination;

import org.snomed.languages.scg.domain.model.Attribute;
import org.snomed.languages.scg.domain.model.AttributeGroup;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableAttributeGroup;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_DIALECTS;

@Service
public class ExpressionTransformationService {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ExpressionParser expressionParser;

	@Value("${postcoordination.transform.self-grouped.attributes}")
	private Set<Long> selfGroupedAttributes;

	public ComparableExpression transform(ComparableExpression closeToUserForm, ExpressionContext context) throws ServiceException {
		// Dereference input with clone of object to avoid any modification affecting input.
		closeToUserForm = expressionParser.parseExpression(closeToUserForm.toString());

		List<Long> expressionFocusConceptIds = toLongList(closeToUserForm.getFocusConcepts());
		if (expressionFocusConceptIds.isEmpty()) {
			throw new TransformationException("No focus concepts given.");
		}

		List<Concept> focusConcepts = conceptService.doFind(expressionFocusConceptIds, DEFAULT_LANGUAGE_DIALECTS, context.getBranchCriteria(),
				PageRequest.of(0, expressionFocusConceptIds.size()), true, false, context.getBranch()).getContent();
		Map<Long, Concept> focusConceptMap = focusConcepts.stream().collect(Collectors.toMap(Concept::getConceptIdAsLong, Function.identity()));

		ComparableExpression newExpression = new ComparableExpression();
		for (Long expressionFocusConceptId : expressionFocusConceptIds) {
			Concept focusConcept = focusConceptMap.get(expressionFocusConceptId);
			if (focusConcept == null) {
				throw new TransformationException(String.format("Focus concept %s not found.", expressionFocusConceptId));
			}
			if (!focusConcept.isActive()) {
				throw new TransformationException(String.format("Focus concept %s is not active.", expressionFocusConceptId));
			}

			newExpression.addFocusConcept(expressionFocusConceptId.toString());
		}

		// Ungrouped attributes from input expression are either:
		// - self grouped if they are of a specific type
		// - or merged with existing role groups of the focus concepts
		Set<Attribute> looseAttributes = new HashSet<>();
		if (closeToUserForm.getAttributes() != null) {
			for (Attribute attribute : closeToUserForm.getAttributes()) {
				if (selfGroupedAttributes.contains(Long.parseLong(attribute.getAttributeId()))) {
					// Attribute must be self grouped due to its type
					AttributeGroup attributeGroup = new AttributeGroup();
					attributeGroup.setAttributes(Collections.singletonList(attribute));
					newExpression.addAttributeGroup(attributeGroup);
				} else {
					// Merge attribute with other groups from focus concepts
					looseAttributes.add(attribute);
				}
			}
		}
		if (!looseAttributes.isEmpty()) {
			// Copy all focus concept attribute groups into the expression and include the loose attributes in each.
			for (Concept focusConcept : focusConcepts) {
				Map<Integer, Set<Relationship>> relationshipGroups = new HashMap<>();
				for (Relationship relationship : focusConcept.getRelationships()) {
					relationshipGroups.computeIfAbsent(relationship.getGroupId(), (id) -> new HashSet<>()).add(relationship);
				}
				for (Integer groupId : relationshipGroups.keySet()) {
					if (groupId > 0) {
						ComparableAttributeGroup attributeGroup = new ComparableAttributeGroup();
						for (Relationship relationship : relationshipGroups.get(groupId)) {
							attributeGroup.addAttribute(relationship.getTypeId(), relationship.getDestinationId());
						}
						for (Attribute looseAttribute : looseAttributes) {
							attributeGroup.addAttribute(looseAttribute);
						}
						newExpression.addAttributeGroup(attributeGroup);
					}
				}
				if (relationshipGroups.size() == 1 && relationshipGroups.containsKey(0)) {
					// No grouped attributes, loose attributes must for their own group
					ComparableAttributeGroup attributeGroup = new ComparableAttributeGroup();
					for (Attribute looseAttribute : looseAttributes) {
						attributeGroup.addAttribute(looseAttribute);
					}
					newExpression.addAttributeGroup(attributeGroup);
				}
			}
		}

		// Attribute groups from input expression are kept as is without merging with existing groups from focus concepts.
		if (closeToUserForm.getAttributeGroups() != null) {
			for (AttributeGroup attributeGroup : closeToUserForm.getAttributeGroups()) {
				newExpression.addAttributeGroup(attributeGroup);
			}
		}

		return newExpression;
	}

	private List<Long> toLongList(List<String> focusConcepts) {
		return focusConcepts == null ? Collections.emptyList() : focusConcepts.stream().map(Long::parseLong).collect(Collectors.toList());
	}

}
