package org.snomed.snowstorm.core.data.services;

import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.domain.expression.Expression;
import org.snomed.snowstorm.core.data.domain.expression.ExpressionAttribute;
import org.snomed.snowstorm.core.data.domain.expression.ExpressionGroup;
import org.snomed.snowstorm.core.data.services.pojo.ResultMapPage;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ExpressionService {
	
	@Autowired
	private ConceptService conceptService;
	
	@Autowired
	private QueryService queryService;

	public Expression getConceptAuthoringForm(String conceptId, String branchPath) {
		//First add the existing attributes
		Expression expression = new Expression();
		Concept concept = conceptService.find(conceptId, branchPath);
		concept.getRelationships().stream()
				.filter(relationship -> relationship.isActive() && !Concepts.STATED_RELATIONSHIP.equals(relationship.getCharacteristicTypeId()))
				.forEach(relationship -> addAttributeToExpression(relationship, expression));

		//Now work out the nearest primitive parents
		Collection<ConceptMini> ancestors = getAncestors(conceptId, branchPath);
		final Collection<ConceptMini> proxPrimParents = getProximalPrimitiveParents(ancestors, branchPath);
		expression.addConcepts(proxPrimParents);
		return expression;
	}
	
	private Collection<ConceptMini> getAncestors(String conceptId, String branchPath) {
		Set<Long> ancestorIds = queryService.retrieveAncestors(conceptId, branchPath, false);
		ResultMapPage<String, ConceptMini> pages = conceptService.findConceptMinis(branchPath, ancestorIds);
		return pages.getResultsMap().values();
	}

	private void addAttributeToExpression(Relationship rel, Expression expression) {
		final String attributeTypeId = rel.getTypeId();
		//Only collect non parent relationships
		if (!Concepts.ISA.equals(attributeTypeId)) {
			ExpressionGroup group = expression.getGroup(rel.getGroupId());
			ExpressionAttribute attribute = new ExpressionAttribute(rel.type(), rel.target());
			group.addAttribute(attribute);
		}
	}
	
	public Collection<ConceptMini> getProximalPrimitiveParents(Collection<ConceptMini> ancestors, String branchPath) {
		Set<String> proxPrimIds = getProximalPrimitiveParentIds(ancestors, branchPath);
		ResultMapPage<String, ConceptMini> pages = conceptService.findConceptMinis(branchPath, proxPrimIds);
		return pages.getResultsMap().values();
	}
	
	private Set<String> getProximalPrimitiveParentIds(Collection<ConceptMini> ancestors, String branchPath) {
		final Set<String> proximalPrimitiveParentIds = new HashSet<>();
		for (ConceptMini ancestor : ancestors) {
			if (ancestor.getDefinitionStatus().equals(Concepts.PRIMITIVE)) {
				final String primitiveAncestorId = ancestor.getId();
				if (proximalPrimitiveParentIds.isEmpty()) {
					proximalPrimitiveParentIds.add(primitiveAncestorId);
				} else {
					boolean doAdd = true;
					for (String id : new HashSet<>(proximalPrimitiveParentIds)) {
						// if the current candidate is a subtype of any already visited nodes, then replace those nodes
						if (isSubTypeOf(primitiveAncestorId, Long.parseLong(id), branchPath)) {
							proximalPrimitiveParentIds.remove(id);
							proximalPrimitiveParentIds.add(primitiveAncestorId);
							doAdd = false;
						} else if (doAdd && isSuperTypeOf(primitiveAncestorId, Long.parseLong(id), branchPath)) {
							// do NOT add the node if it is a super type of any currently selected primitives
							doAdd = false;
						}
					}
					if (doAdd) {
						proximalPrimitiveParentIds.add(primitiveAncestorId);
					}
				}
			}
		}
		return proximalPrimitiveParentIds;
	}
	
	/**
	 * Returns <code>true</code> if the given superType is a superType of the given subType according to this tree, otherwise returns
	 * <code>false</code>.
	 * @param superType
	 * @param subType
	 */
	public boolean isSuperTypeOf(String superType, Long subType, String branchPath) {
		return queryService.retrieveDescendants(superType, branchPath, false).contains(subType);
	}

	/**
	 * Returns <code>true</code> if the given subType is a subType of the given superType according to this tree, otherwise returns <code>false</code>
	 * @param subType
	 * @param superType
	 */
	public boolean isSubTypeOf(String subType, Long superType, String branchPath) {
		return queryService.retrieveAncestors(subType, branchPath, false).contains(superType);
	}

}
