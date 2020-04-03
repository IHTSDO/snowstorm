package org.snomed.snowstorm.core.data.services;

import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.expression.*;
import org.snomed.snowstorm.core.data.services.pojo.ResultMapPage;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;

@Service
public class ExpressionService {
	
	@Autowired
	private ConceptService conceptService;
	
	@Autowired
	private QueryService queryService;

	public Expression getConceptAuthoringForm(String conceptId, List<LanguageDialect> languageDialects, String branchPath) {
		//First add the existing attributes
		Expression expression = new Expression();
		Concept concept = conceptService.find(conceptId, languageDialects, branchPath);
		concept.getRelationships().stream()
				.filter(relationship -> relationship.isActive() && !Concepts.STATED_RELATIONSHIP.equals(relationship.getCharacteristicTypeId()))
				.forEach(relationship -> addAttributeToExpression(relationship, expression));

		//Now work out the nearest primitive parents
		Collection<ConceptMini> ancestors = getAncestors(conceptId, languageDialects, branchPath);
		final Collection<ConceptMini> proxPrimParents = getProximalPrimitiveParents(ancestors, languageDialects, branchPath);
		expression.addConcepts(proxPrimParents);
		return expression;
	}
	
	public Expression getExpression(Concept concept, boolean statedView) {
		//First add the existing attributes
		Expression expression = new Expression();
		String charType = statedView ? Concepts.STATED_RELATIONSHIP : Concepts.INFERRED_RELATIONSHIP;
		concept.getRelationships().stream()
				.filter(relationship -> relationship.isActive()) 
				.filter(relationship -> charType.equals(relationship.getCharacteristicTypeId()))
				.forEach(relationship -> addRelationshipToExpression(relationship, expression));
		return expression;
	}
	
	public Expression getConceptNormalForm(String conceptId, List<LanguageDialect> languageDialects, String branchPath, boolean statedView) {
		Concept concept = conceptService.find(conceptId, languageDialects, branchPath);
		return getExpression(concept, statedView);
	}
	
	private Collection<ConceptMini> getAncestors(String conceptId, List<LanguageDialect> languageDialects, String branchPath) {
		Set<Long> ancestorIds = queryService.findAncestorIds(conceptId, branchPath, false);
		ResultMapPage<String, ConceptMini> pages = conceptService.findConceptMinis(branchPath, ancestorIds, languageDialects);
		return pages.getResultsMap().values();
	}

	private void addAttributeToExpression(Relationship rel, Expression expression) {
		final String attributeTypeId = rel.getTypeId();
		//Only collect non parent relationships
		if (!Concepts.ISA.equals(attributeTypeId)) {
			ExpressionAttribute attribute = new ExpressionAttribute(rel.type(), rel.target());
			//Are we adding this attribute grouped or ungrouped?
			if (rel.isGrouped()) {
				ExpressionGroup group = expression.getGroup(rel.getGroupId());
				group.addAttribute(attribute);
			} else {
				expression.addAttribute(attribute);
			}
		}
	}
	
	private void addRelationshipToExpression(Relationship rel, Expression expression) {
		if (Concepts.ISA.equals(rel.getTypeId())) {
			expression.addConcept(new ConceptMicro(rel.getTarget()));
		} else {
			addAttributeToExpression(rel, expression);
		}
	}
	
	public Collection<ConceptMini> getProximalPrimitiveParents(Collection<ConceptMini> ancestors, List<LanguageDialect> languageDialects, String branchPath) {
		Set<String> proxPrimIds = getProximalPrimitiveParentIds(ancestors, branchPath);
		ResultMapPage<String, ConceptMini> pages = conceptService.findConceptMinis(branchPath, proxPrimIds, languageDialects);
		return pages.getResultsMap().values();
	}
	
	private Set<String> getProximalPrimitiveParentIds(Collection<ConceptMini> ancestors, String branchPath) {
		final Set<String> proximalPrimitiveParentIds = new HashSet<>();
		for (ConceptMini ancestor : ancestors) {
			if (ancestor.isPrimitive() && !isGciAxiomPresent(ancestor.getId(), branchPath)) {
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
						} else if (doAdd && isSuperTypeOf(Long.parseLong(primitiveAncestorId), id, branchPath)) {
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
	 */
	public boolean isSuperTypeOf(Long superType, String subType, String branchPath) {
		return queryService.findAncestorIds(subType, branchPath, false).contains(superType);
	}

	/**
	 * Returns <code>true</code> if the given subType is a subType of the given superType according to this tree, otherwise returns <code>false</code>
	 */
	public boolean isSubTypeOf(String subType, Long superType, String branchPath) {
		return queryService.findAncestorIds(subType, branchPath, false).contains(superType);
	}

	private boolean isGciAxiomPresent(String conceptId, String branchPath) {
		Concept concept = conceptService.find(conceptId, branchPath);
		return !CollectionUtils.isEmpty(concept.getGciAxioms());
	}
}
