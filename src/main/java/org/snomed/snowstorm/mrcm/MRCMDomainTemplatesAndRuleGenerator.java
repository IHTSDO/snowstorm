package org.snomed.snowstorm.mrcm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.langauges.ecl.ECLException;
import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.langauges.ecl.domain.expressionconstraint.CompoundExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.mrcm.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class MRCMDomainTemplatesAndRuleGenerator {
	@Autowired
	private ECLQueryBuilder eclQueryBuilder;

	static final Comparator<AttributeDomain> ATTRIBUTE_DOMAIN_COMPARATOR_BY_DOMAIN_ID = Comparator
			.comparing(AttributeDomain::getDomainId, Comparator.nullsFirst(String::compareTo));

	static final Comparator<AttributeDomain> ATTRIBUTE_DOMAIN_COMPARATOR_BY_ATTRIBUTE_ID = Comparator
			.comparing(AttributeDomain::getReferencedComponentId, Comparator.nullsFirst(String::compareTo));

	static final Comparator<SubExpressionConstraint> EXPRESSION_CONSTRAINT_COMPARATOR_BY_CONCEPT_ID = Comparator
			.comparing(SubExpressionConstraint::getConceptId, Comparator.nullsFirst(String::compareTo));

	private static final Logger logger = LoggerFactory.getLogger(MRCMUpdateService.class);

	private static final Pattern REFINEMENT_PART_ONE_PATTERN = Pattern.compile("^\\[\\[(.*)\\]\\]\\s?([0-9]{6,9}).*");

	private static final Pattern REFINEMENT_PART_TWO_PATTERN = Pattern.compile("^\\[\\[\\+id\\((.*)\\)\\]\\]");

	public List<Domain> generateDomainTemplates(Map<String, Domain> domainsByDomainIdMap, Map<String, List<AttributeDomain>> domainToAttributesMap,
										 Map<String, List<AttributeRange>> attributeToRangesMap, Map<String, String> conceptToFsnMap) {

		List<Domain> updatedDomains = new ArrayList<>();
		logger.debug("Checking and updating templates for {} domains.", domainsByDomainIdMap.keySet().size());
		for (String domainId : domainsByDomainIdMap.keySet()) {
			Domain domain = new Domain(domainsByDomainIdMap.get(domainId));
			List<String> parentDomainIds = findParentDomains(domain, domainsByDomainIdMap);
			String precoordinated = generateDomainTemplate(domain, domainToAttributesMap, attributeToRangesMap, conceptToFsnMap, parentDomainIds, ContentType.PRECOORDINATED);
			boolean isChanged = false;
			if (!precoordinated.equals(domain.getDomainTemplateForPrecoordination())) {
				domain.setDomainTemplateForPrecoordination(precoordinated);
				isChanged = true;
			}
			String postcoordinated = generateDomainTemplate(domain, domainToAttributesMap, attributeToRangesMap, conceptToFsnMap, parentDomainIds, ContentType.POSTCOORDINATED);
			if (!postcoordinated.equals(domain.getDomainTemplateForPostcoordination())) {
				domain.setDomainTemplateForPostcoordination(postcoordinated);
				isChanged = true;
			}
			if (isChanged) {
				updatedDomains.add(domain);
			}
		}
		return updatedDomains;
	}

	public List<AttributeRange> generateAttributeRule(Map<String, Domain> domainMapByDomainId, Map<String, List<AttributeDomain>> attributeToDomainsMap,
											   Map<String, List<AttributeRange>> attributeToRangesMap,
											   Map<String, String> conceptToFsnMap) {
		List<AttributeRange> updatedRanges = new ArrayList<>();
		// generate attribute rule

		for (String attributeId : attributeToDomainsMap.keySet()) {
			// domain
			List<AttributeDomain> sorted = attributeToDomainsMap.get(attributeId);
			Collections.sort(sorted, ATTRIBUTE_DOMAIN_COMPARATOR_BY_DOMAIN_ID);
			if (!attributeToRangesMap.containsKey(attributeId)) {
				logger.info("No attribute ranges defined for attribute {}.", attributeId);
				continue;
			}
			for (AttributeRange range : attributeToRangesMap.get(attributeId)) {
				String sortedConstraint = sortExpressionConstraintByConceptId(range.getRangeConstraint(), range.getId());
				boolean isRangeConstraintChanged = false;
				if (!range.getRangeConstraint().equals(sortedConstraint)) {
					isRangeConstraintChanged = true;
					range.setRangeConstraint(sortedConstraint);
				}
				int counter = 0;
				StringBuilder ruleBuilder = new StringBuilder();
				for (AttributeDomain attributeDomain : sorted) {
					if (RuleStrength.MANDATORY != attributeDomain.getRuleStrength()) {
						continue;
					}
					if (ContentType.ALL != attributeDomain.getContentType() && range.getContentType() != attributeDomain.getContentType()) {
						continue;
					}
					if (counter++ > 0) {
						ruleBuilder.insert(0, "(");
						ruleBuilder.append(")");
						ruleBuilder.append(" OR (");
					}
					String domainConstraint = domainMapByDomainId.get(attributeDomain.getDomainId()).getDomainConstraint().getExpression();
					ruleBuilder.append(domainConstraint);
					if (domainConstraint.contains(":")) {
						ruleBuilder.append(",");
					} else {
						ruleBuilder.append(":");
					}
					// attribute group and attribute cardinality
					if (attributeDomain.isGrouped()) {
						ruleBuilder.append(" [" + attributeDomain.getAttributeCardinality().getValue() + "]" + " {");
						ruleBuilder.append(" [" + attributeDomain.getAttributeInGroupCardinality().getValue() + "]");
					} else {
						ruleBuilder.append(" [" + attributeDomain.getAttributeCardinality().getValue() + "]");
					}

					ruleBuilder.append(" " + attributeId + " |" + conceptToFsnMap.get(attributeId) + "|" + " = ");
					// range constraint
					if (range.getRangeConstraint().contains("OR")) {
						ruleBuilder.append("(" + range.getRangeConstraint() + ")");
					} else {
						ruleBuilder.append(range.getRangeConstraint());
					}
					if (attributeDomain.isGrouped()) {
						ruleBuilder.append(" }");
					}
					if (counter > 1) {
						ruleBuilder.append(")");
					}
				}
				if (!ruleBuilder.toString().equals(range.getAttributeRule()) || isRangeConstraintChanged) {
					logger.debug("before = " + range.getAttributeRule());
					logger.debug("after = " + ruleBuilder.toString());
					AttributeRange updated = new AttributeRange(range);
					updated.setAttributeRule(ruleBuilder.toString());
					updatedRanges.add(updated);
				}
			}
		}
		return updatedRanges;
	}

	public String generateDomainTemplate(Domain domain, Map<String, List<AttributeDomain>> domainToAttributesMap,
										  Map<String, List<AttributeRange>> attributeToRangesMap,
										  Map<String, String> conceptToFsnMap,
										  List<String> parentDomainIds, ContentType type) {

		StringBuilder templateBuilder = new StringBuilder();
		// proximal primitive domain constraint
		if (domain.getProximalPrimitiveConstraint() != null) {
			if ( ContentType.PRECOORDINATED == type) {
				templateBuilder.append("[[+id(");
			} else {
				templateBuilder.append("[[+scg(");
			}
			templateBuilder.append(domain.getProximalPrimitiveConstraint().getExpression());
			templateBuilder.append(")]]: ");
		}

		// Filter for mandatory and all content type or given type
		List<String> domainIdsToInclude = new ArrayList<>(parentDomainIds);
		domainIdsToInclude.add(domain.getReferencedComponentId());
		List<AttributeDomain> attributeDomains = new ArrayList<>();
		for (String domainId : domainIdsToInclude) {
			if (domainToAttributesMap.containsKey(domainId)) {
				attributeDomains.addAll(domainToAttributesMap.get(domainId).stream()
						.filter(d -> (RuleStrength.MANDATORY == d.getRuleStrength()) && (ContentType.ALL == d.getContentType() || type == d.getContentType()))
						.collect(Collectors.toList()));
			}
		}
		List<AttributeDomain> grouped = attributeDomains.stream().filter(AttributeDomain::isGrouped).collect(Collectors.toList());
		Collections.sort(grouped, ATTRIBUTE_DOMAIN_COMPARATOR_BY_ATTRIBUTE_ID);

		List<AttributeDomain> unGrouped = new ArrayList<>(attributeDomains);
		unGrouped.removeAll(grouped);
		Collections.sort(unGrouped, ATTRIBUTE_DOMAIN_COMPARATOR_BY_ATTRIBUTE_ID);

		List<ProximalPrimitiveRefinement> refinements = processProximalPrimitiveRefinement(domain.getProximalPrimitiveRefinement());

		// un-grouped first if present
		constructAttributeRoleGroup(unGrouped, templateBuilder, attributeToRangesMap, conceptToFsnMap, type, refinements);
		if (!grouped.isEmpty() && !unGrouped.isEmpty()) {
			templateBuilder.append(", ");
		}
		// grouped
		constructAttributeRoleGroup(grouped, templateBuilder, attributeToRangesMap, conceptToFsnMap, type, refinements);

		// additional parent domain when there is range change in the sub domain
		if (hasAttributeRangeChangedInSubDomain(refinements, attributeToRangesMap, type)) {
			templateBuilder.append(", ");
			constructAttributeRoleGroup(grouped, templateBuilder, attributeToRangesMap, conceptToFsnMap, type, null);
		}
		return templateBuilder.toString();
	}

	private boolean hasAttributeRangeChangedInSubDomain(List<ProximalPrimitiveRefinement> refinements, Map<String, List<AttributeRange>> attributeToRangesMap, ContentType type) {
		boolean hasChange = false;
		for (ProximalPrimitiveRefinement refinement : refinements) {
			List<AttributeRange> ranges = attributeToRangesMap.get(refinement.getAttributeId());
			for (AttributeRange range : ranges) {
				if (RuleStrength.MANDATORY == range.getRuleStrength() &&
						(ContentType.ALL == range.getContentType() ||  type == range.getContentType())) {
					if (!range.getRangeConstraint().equals(refinement.getRangeConstraint())) {
						hasChange = true;
					}
					break;
				}
			}
		}
		return hasChange;
	}

	private List<ProximalPrimitiveRefinement> processProximalPrimitiveRefinement(String proximalPrimitiveRefinement) {

		List<ProximalPrimitiveRefinement> result = new ArrayList<>();
		//[[1..*]] 260686004 |Method| = [[+id(<< 129265001 |Evaluation - action|)]]
		if (proximalPrimitiveRefinement != null && !proximalPrimitiveRefinement.trim().isEmpty()) {
			logger.info("processing proximalPrimitiveRefinement {}", proximalPrimitiveRefinement);
			String[] splits = proximalPrimitiveRefinement.split(",", -1);
			for (String refinement : splits) {
				String[] parts = refinement.split("=", -1);
				if (parts.length != 2) {
					throw new IllegalArgumentException("Invalid format found for proximal primitive refinement" + refinement);
				}
				ProximalPrimitiveRefinement attributeRefinement = new ProximalPrimitiveRefinement();
				Matcher partOneMatcher = REFINEMENT_PART_ONE_PATTERN.matcher(parts[0].trim());
				if (partOneMatcher.matches()) {
					attributeRefinement.setGroupCardinality(partOneMatcher.group(1));
					attributeRefinement.setAttributeId(partOneMatcher.group(2));
				} else {
					throw new IllegalArgumentException("Invalid format found for proximal primitive refinement as pattern not matched " + parts[0]);
				}
				String partTwo = parts[1].trim();
				Matcher partTwoMatcher = REFINEMENT_PART_TWO_PATTERN.matcher(partTwo);
				if (partTwoMatcher.matches()) {
					attributeRefinement.setRangeConstraint(partTwoMatcher.group(1));
				} else {
					throw new IllegalArgumentException("Invalid format found for proximal primitive refinement as pattern not matched " + parts[1]);
				}
				result.add(attributeRefinement);
			}
		}
		return result;
	}

	private void constructAttributeRoleGroup(List<AttributeDomain> attributeDomains, StringBuilder templateBuilder,
											 Map<String, List<AttributeRange>> attributeToRangesMap,
											 Map<String, String> conceptToFsnMap,
											 ContentType type,
											 List<ProximalPrimitiveRefinement> refinements) {
		if (attributeDomains.isEmpty()) {
			return;
		}
		boolean isGrouped = attributeDomains.get(0).isGrouped();
		Map<String, ProximalPrimitiveRefinement> attributeIdToRefinementMap = new HashMap<>();
		if (isGrouped) {
			Cardinality groupCardinality = attributeDomains.get(0).getAttributeCardinality();
			if (refinements != null) {
				for (ProximalPrimitiveRefinement refinement : refinements) {
					attributeIdToRefinementMap.put(refinement.getAttributeId(), refinement);
					try {
						Cardinality cardinality = new Cardinality(refinement.getGroupCardinality());
						if (groupCardinality == null) {
							groupCardinality = cardinality;
						} else {
							if (cardinality.getMin() > groupCardinality.getMin()) {
								groupCardinality = cardinality;
							}
						}
					} catch (ServiceException e) {
						throw new IllegalArgumentException("Wrong cardinality format found for " + refinement.getGroupCardinality(), e);
					}
				}
			}
			templateBuilder.append("[[");
			templateBuilder.append(groupCardinality.getValue());
			templateBuilder.append("]] { ");
		}

		int counter = 0;
		for (AttributeDomain attributeDomain : attributeDomains) {
			if (counter++ > 0) {
				templateBuilder.append(", ");
			}
			List<AttributeRange> ranges = attributeToRangesMap.get(attributeDomain.getReferencedComponentId());
			if (ranges == null) {
				logger.warn("No attribute ranges defined for attribute {} in domain ", attributeDomain.getReferencedComponentId(), attributeDomain.getDomainId());
				continue;
			}
			AttributeRange attributeRange = null;
			for (AttributeRange range : ranges) {
				if (RuleStrength.MANDATORY == range.getRuleStrength() &&
						(ContentType.ALL == range.getContentType() ||  type == range.getContentType())) {
					attributeRange = range;
					break;
				}
			}
			if (attributeRange == null) {
				logger.warn("No attribute range found for attribute {} with content type or {}", attributeDomain.getReferencedComponentId(), type.getName(), ContentType.ALL.name());
				continue;
			}
			ProximalPrimitiveRefinement refinement = null;
			templateBuilder.append("[[");
			if (isGrouped) {
				if (attributeIdToRefinementMap.containsKey(attributeDomain.getReferencedComponentId())) {
					refinement = attributeIdToRefinementMap.get(attributeDomain.getReferencedComponentId());
					try {
						Cardinality refined = new Cardinality(refinement.getGroupCardinality());
						if (refined.getMax() == null) {
							Cardinality updated = new Cardinality(refined.getMin(), attributeDomain.getAttributeInGroupCardinality().getMax());
							templateBuilder.append(updated.getValue());
						} else {
							templateBuilder.append(refined.getValue());
						}
					} catch (ServiceException e) {
						throw new IllegalArgumentException("Wrong format of cardinality found " + refinement, e);
					}
				} else {
					templateBuilder.append(attributeDomain.getAttributeInGroupCardinality().getValue());
				}
			} else {
				templateBuilder.append(attributeDomain.getAttributeCardinality().getValue());
			}
			templateBuilder.append("]] ");
			templateBuilder.append(attributeDomain.getReferencedComponentId() + " |" + conceptToFsnMap.get(attributeDomain.getReferencedComponentId()) + "|");
			if (ContentType.PRECOORDINATED == type) {
				templateBuilder.append(" = [[+id(");
			} else {
				templateBuilder.append(" = [[+scg(");
			}

			if (refinement != null) {
				templateBuilder.append(refinement.getRangeConstraint());
			} else {
				templateBuilder.append(attributeRange.getRangeConstraint());
			}
			templateBuilder.append(")]]");
		}
		if (isGrouped) {
			templateBuilder.append(" }");
		}
	}

	public String sortExpressionConstraintByConceptId(String rangeConstraint, String memberId) {
		if (rangeConstraint == null || rangeConstraint.trim().isEmpty()) {
			return rangeConstraint;
		}
		ExpressionConstraint constraint;
		try {
			constraint = eclQueryBuilder.createQuery(rangeConstraint);
		} catch(ECLException e) {
			logger.error("Invalid range constraint {} found in member {}.", rangeConstraint, memberId);
			return rangeConstraint;
		}
		if (constraint == null) return rangeConstraint;

		if (constraint instanceof CompoundExpressionConstraint) {
			StringBuilder expressionBuilder = new StringBuilder();
			CompoundExpressionConstraint compound = (CompoundExpressionConstraint) constraint;
			if (compound.getConjunctionExpressionConstraints() != null) {
				List<SubExpressionConstraint> conJunctions = compound.getConjunctionExpressionConstraints();
				Collections.sort(conJunctions, EXPRESSION_CONSTRAINT_COMPARATOR_BY_CONCEPT_ID);
				for (int i = 0; i < conJunctions.size(); i++) {
					if (i > 0) {
						expressionBuilder.append( " AND ");
					}
					expressionBuilder.append(constructExpression(conJunctions.get(i)));
				}
			}
			if (compound.getDisjunctionExpressionConstraints() != null) {
				List<SubExpressionConstraint> disJunctions = compound.getDisjunctionExpressionConstraints();
				Collections.sort(disJunctions, EXPRESSION_CONSTRAINT_COMPARATOR_BY_CONCEPT_ID);
				for (int i = 0; i < disJunctions.size(); i++) {
					if (i > 0) {
						expressionBuilder.append( " OR ");
					}
					expressionBuilder.append(constructExpression(disJunctions.get(i)));
				}
			}

			if (compound.getExclusionExpressionConstraint() != null) {
				expressionBuilder.append(" MINUS ");
				expressionBuilder.append(constructExpression(compound.getExclusionExpressionConstraint()));
			}
			return expressionBuilder.toString();
		}
		return rangeConstraint;
	}

	private String constructExpression(SubExpressionConstraint constraint) {
		StringBuilder expressionBuilder = new StringBuilder();
		if (constraint.getOperator() != null) {
			expressionBuilder.append(constraint.getOperator().getText());
			expressionBuilder.append(" ");
		}
		expressionBuilder.append(constraint.getConceptId());
		expressionBuilder.append(" ");
		expressionBuilder.append("|");
		expressionBuilder.append(constraint.getTerm());
		expressionBuilder.append("|");
		return expressionBuilder.toString();
	}

	private List<String> findParentDomains(Domain domain, Map<String, Domain> domainsByDomainIdMap) {
		List<String> result = new ArrayList<>();
		Domain current = domain;
		while (current != null && current.getParentDomain() != null && !current.getParentDomain().isEmpty()) {
			String parentDomain = current.getParentDomain();
			parentDomain = parentDomain.substring(0, parentDomain.indexOf("|")).trim();
			Domain parent = domainsByDomainIdMap.get(parentDomain);
			if (parent == null) {
				throw new IllegalStateException("No domain object found for for " + parentDomain);
			}
			result.add(parent.getReferencedComponentId());
			current = parent;
		}
		return result;
	}

	private static class ProximalPrimitiveRefinement {
		private String attributeId;
		private String groupCardinality;
		private String rangeConstraint;


		public String getAttributeId() {
			return attributeId;
		}

		public void setAttributeId(String attributeId) {
			this.attributeId = attributeId;
		}

		public void setGroupCardinality(String groupCardinality) {
			this.groupCardinality = groupCardinality;
		}

		public void setRangeConstraint(String rangeConstraint) {
			this.rangeConstraint = rangeConstraint;
		}

		public String getGroupCardinality() {
			return groupCardinality;
		}

		public String getRangeConstraint() {
			return rangeConstraint;
		}

		@Override
		public String toString() {
			return "ProximalPrimitiveRefinement{" +
					"attributeId='" + attributeId + '\'' +
					", groupCardinality='" + groupCardinality + '\'' +
					", rangeConstraint='" + rangeConstraint + '\'' +
					'}';
		}
	}
}
