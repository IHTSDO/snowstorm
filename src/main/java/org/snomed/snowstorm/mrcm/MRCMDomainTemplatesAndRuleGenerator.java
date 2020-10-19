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

import static org.snomed.snowstorm.core.util.PredicateUtil.not;

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

	private static final Logger logger = LoggerFactory.getLogger(MRCMDomainTemplatesAndRuleGenerator.class);

	private static final Pattern REFINEMENT_PART_ONE_PATTERN = Pattern.compile("^\\[\\[(.*)\\]\\]\\s?([0-9]{6,9}).*");

	private static final Pattern REFINEMENT_PART_TWO_PATTERN = Pattern.compile("^\\[\\[\\+id\\((.*)\\)\\]\\]");

	public List<Domain> generateDomainTemplates(Map<String, Domain> domainsByDomainIdMap, Map<String, List<AttributeDomain>> domainToAttributesMap,
	                                            Map<String, List<AttributeRange>> attributeToRangesMap, Map<String, String> conceptToFsnMap) {

		List<Domain> updatedDomains = new ArrayList<>();
		logger.debug("Checking and updating templates for {} domains.", domainsByDomainIdMap.keySet().size());
		for (Map.Entry<String, Domain> entry : domainsByDomainIdMap.entrySet()) {
			Domain domain = entry.getValue();
			List<String> parentDomainIds = findParentDomains(domain, domainsByDomainIdMap);
			String preCoordinated = generateDomainTemplate(domain, domainToAttributesMap, attributeToRangesMap, conceptToFsnMap, parentDomainIds, ContentType.PRECOORDINATED);
			boolean isChanged = false;
			if (!preCoordinated.equals(domain.getDomainTemplateForPrecoordination())) {
				domain.setDomainTemplateForPrecoordination(preCoordinated);
				isChanged = true;
			}
			String postCoordinated = generateDomainTemplate(domain, domainToAttributesMap, attributeToRangesMap, conceptToFsnMap, parentDomainIds, ContentType.POSTCOORDINATED);
			if (!postCoordinated.equals(domain.getDomainTemplateForPostcoordination())) {
				domain.setDomainTemplateForPostcoordination(postCoordinated);
				isChanged = true;
			}
			if (isChanged) {
				updatedDomains.add(domain);
			}
		}
		return updatedDomains;
	}

	public List<AttributeRange> generateAttributeRules(Map<String, Domain> domainMapByDomainId, Map<String, List<AttributeDomain>> attributeToDomainsMap,
	                                                   Map<String, List<AttributeRange>> attributeToRangesMap,
	                                                   Map<String, String> conceptToFsnMap, List<Long> dataAttributes) {

		List<AttributeRange> updatedRanges = new ArrayList<>();
		for (Map.Entry<String, List<AttributeDomain>> entry : attributeToDomainsMap.entrySet()) {
			if (!attributeToRangesMap.containsKey(entry.getKey())) {
				logger.info("No attribute ranges defined in any domains for attribute {}.", entry.getKey());
				continue;
			}
			for (AttributeRange range : attributeToRangesMap.get(entry.getKey())) {
				boolean isRangeConstraintChanged = false;
				// only sort and validate ECL for non data attributes
				boolean isDataAttribute = dataAttributes.contains(Long.valueOf(range.getReferencedComponentId()));
				if (!isDataAttribute) {
					String sortedConstraint = sortExpressionConstraintByConceptId(range.getRangeConstraint(), range.getId());
					if (!range.getRangeConstraint().equals(sortedConstraint)) {
						isRangeConstraintChanged = true;
						range.setRangeConstraint(sortedConstraint);
					}
				}

				// attribute domains are mandatory and matched content type
				List<AttributeDomain> attributeDomains = entry.getValue()
						.stream()
						.filter(d -> (RuleStrength.MANDATORY == d.getRuleStrength())
										&& (ContentType.ALL == d.getContentType() || range.getContentType() == d.getContentType()))
						.collect(Collectors.toList());

				// group attribute domain by attribute cardinality and attribute in group cardinality
				List<AttributeDomain> grouped = attributeDomains.stream().filter(AttributeDomain::isGrouped).collect(Collectors.toList());
				Map<String, List<AttributeDomain>> groupedByCardinality = new HashMap<>();
				for (AttributeDomain attributeDomain : grouped) {
					groupedByCardinality.computeIfAbsent(attributeDomain.getAttributeCardinality().getValue()
							+ "_" + attributeDomain.getAttributeInGroupCardinality().getValue(), domains -> new ArrayList<>()).add(attributeDomain);
				}
				// Generate attribute rule un-grouped first then grouped
				StringBuilder ruleBuilder = new StringBuilder();
				// unGrouped
				List<AttributeDomain> unGrouped = new ArrayList<>(attributeDomains);
				unGrouped.removeAll(grouped);
				unGrouped.sort(ATTRIBUTE_DOMAIN_COMPARATOR_BY_DOMAIN_ID);
				ruleBuilder.append(constructAttributeRule(unGrouped, domainMapByDomainId, range, conceptToFsnMap, isDataAttribute));

				// grouped
				Map<String, String> attributeRuleMappedByDomain = new HashMap<>();
				for (Map.Entry<String, List<AttributeDomain>> groupedEntry : groupedByCardinality.entrySet()) {
					List<AttributeDomain> sortedAttributeDomains = groupedEntry.getValue();
					sortedAttributeDomains.sort(ATTRIBUTE_DOMAIN_COMPARATOR_BY_DOMAIN_ID);
					// generate rule and mapped by domain concept id
					List<String> domains = sortedAttributeDomains.stream().map(AttributeDomain::getDomainId).sorted().collect(Collectors.toList());
					attributeRuleMappedByDomain.put(domains.get(0), constructAttributeRule(sortedAttributeDomains, domainMapByDomainId, range, conceptToFsnMap, isDataAttribute));
				}
				if (!unGrouped.isEmpty() && !grouped.isEmpty()) {
					ruleBuilder.insert(0, "(");
					ruleBuilder.append(") OR (");
				}
				if (!attributeRuleMappedByDomain.isEmpty()) {
					List<String> domains = new ArrayList<>(attributeRuleMappedByDomain.keySet());
					Collections.sort(domains);
					int counter = 0;
					for (String domain : domains) {
						if (counter++ > 0) {
							ruleBuilder.append(" OR ");
						}
						if (domains.size() > 1) {
							ruleBuilder.append("(");
						}
						ruleBuilder.append(attributeRuleMappedByDomain.get(domain));
						if (domains.size() > 1) {
							ruleBuilder.append(")");
						}
					}
				}
				if (!unGrouped.isEmpty() && !grouped.isEmpty()) {
					ruleBuilder.append(")");
				}
				if (!ruleBuilder.toString().equals(range.getAttributeRule()) || isRangeConstraintChanged) {
					logger.debug("before = {} ", range.getAttributeRule());
					logger.debug("after =  {} ", ruleBuilder);
					AttributeRange updated = new AttributeRange(range);
					eclValidation(ruleBuilder.toString(), range);
					updated.setAttributeRule(ruleBuilder.toString());
					updatedRanges.add(updated);
				}
			}
		}
		return updatedRanges;
	}

	private void eclValidation(String attributeRule, AttributeRange range) {
		try {
			eclQueryBuilder.createQuery(attributeRule);
		} catch(ECLException e) {
			logger.info("Attribute range member id {} ", range.getId());
			String errorMsg = String.format("Generated attribute rule for attribute %s is not valid ECL: %s", range.getReferencedComponentId(), attributeRule);
			logger.error(errorMsg);
			throw new IllegalStateException(errorMsg, e);
		}
	}

	private String constructAttributeRule(List<AttributeDomain> attributeDomains, Map<String, Domain> domainMapByDomainId,
										AttributeRange range, Map<String, String> conceptToFsnMap, boolean isDataAttribute) {
		int counter = 0;
		StringBuilder ruleBuilder = new StringBuilder();
		if (attributeDomains == null || attributeDomains.isEmpty()) {
			return ruleBuilder.toString();
		}
		for (AttributeDomain attributeDomain : attributeDomains) {
			if (!domainMapByDomainId.containsKey(attributeDomain.getDomainId())) {
				logger.warn("No domain defined for domainId referenced {}", attributeDomain.getDomainId());
				continue;
			}
			Constraint domainConstraint = domainMapByDomainId.get(attributeDomain.getDomainId()).getDomainConstraint();
			if (domainConstraint == null) {
				logger.warn("No domain constraint defined for domain {}", attributeDomain.getDomainId());
				continue;
			}
			if (counter++ > 0) {
				ruleBuilder.append(" OR ");
			}
			ruleBuilder.append(domainConstraint.getExpression());
		}
		if (counter > 1) {
			ruleBuilder.insert(0, "(");
			ruleBuilder.append(")");
		}
		if (ruleBuilder.toString().contains(":")) {
			ruleBuilder.append(", ");
		} else {
			ruleBuilder.append(": ");
		}
		// attribute group and attribute cardinality
		AttributeDomain attributeDomain = attributeDomains.get(0);
		if (attributeDomain.isGrouped()) {
			ruleBuilder.append("[");
			ruleBuilder.append(attributeDomain.getAttributeCardinality().getValue());
			ruleBuilder.append("] {");
			ruleBuilder.append(" [");
			ruleBuilder.append(attributeDomain.getAttributeInGroupCardinality().getValue());
			ruleBuilder.append("]");
		} else {
			ruleBuilder.append("[");
			ruleBuilder.append(attributeDomain.getAttributeCardinality().getValue());
			ruleBuilder.append("]");
		}

		// constraint rule
		if (isDataAttribute) {
			ruleBuilder.append(constructValueConstraintRule(range, attributeDomain, conceptToFsnMap.get(range.getReferencedComponentId())));
		} else {
			ruleBuilder.append(" ");
			ruleBuilder.append(range.getReferencedComponentId());
			ruleBuilder.append(" |");
			ruleBuilder.append(conceptToFsnMap.get(range.getReferencedComponentId()));
			ruleBuilder.append("| = ");
			if (range.getRangeConstraint().contains("OR")) {
				ruleBuilder.append("(");
				ruleBuilder.append(range.getRangeConstraint());
				ruleBuilder.append(")");
			} else {
				ruleBuilder.append(range.getRangeConstraint());
			}
		}
		if (attributeDomain.isGrouped()) {
			ruleBuilder.append(" }");
		}
		return ruleBuilder.toString();
	}

	private String constructValueConstraintRule(AttributeRange range, AttributeDomain attributeDomain, String attributeFsn) {
		// https://confluence.ihtsdotools.org/display/mag/MRCM+for+Concrete+Domains
		ConcreteValueRangeConstraint valueConstraint = new ConcreteValueRangeConstraint(range.getRangeConstraint());
		if (valueConstraint.isNumber() && valueConstraint.isRangeConstraint()) {
			return constructNumericRangeRule(range, attributeDomain, attributeFsn, valueConstraint);
		} else {
			return constructValueRule(range, attributeDomain, attributeFsn, valueConstraint);
		}
	}

	private String constructValueRule(AttributeRange range, AttributeDomain attributeDomain, String attributeFsn, ConcreteValueRangeConstraint valueConstraint) {
		StringBuilder ruleBuilder = new StringBuilder();
		String[]  values = valueConstraint.getConstraint().split(ConcreteValueRangeConstraint.OR_OPERATOR);
		if (values.length > 1) {
			// add or
			for (int i = 0; i < values.length; i++) {
				if (i > 0) {
					ruleBuilder.append(" OR ");
					if (attributeDomain.isGrouped()) {
						ruleBuilder.append("[");
						ruleBuilder.append(attributeDomain.getAttributeInGroupCardinality().getValue());
						ruleBuilder.append("]");
					} else {
						ruleBuilder.append("[");
						ruleBuilder.append(attributeDomain.getAttributeCardinality().getValue());
						ruleBuilder.append("]");
					}
				}
				ruleBuilder.append(" ");
				ruleBuilder.append(range.getReferencedComponentId());
				ruleBuilder.append(" |");
				ruleBuilder.append(attributeFsn);
				ruleBuilder.append("| = ");
				ruleBuilder.append(values[i]);
			}
		} else {
			values = valueConstraint.getConstraint().split(ConcreteValueRangeConstraint.AND_OPERATOR);
			if (values.length > 1) {
				// add and
				for (int i = 0; i < values.length; i++) {
					if (i > 0) {
						ruleBuilder.append(" AND ");
						if (attributeDomain.isGrouped()) {
							ruleBuilder.append("[");
							ruleBuilder.append(attributeDomain.getAttributeInGroupCardinality().getValue());
							ruleBuilder.append("]");
						} else {
							ruleBuilder.append("[");
							ruleBuilder.append(attributeDomain.getAttributeCardinality().getValue());
							ruleBuilder.append("]");
						}
					}
					ruleBuilder.append(" ");
					ruleBuilder.append(range.getReferencedComponentId());
					ruleBuilder.append(" |");
					ruleBuilder.append(attributeFsn);
					ruleBuilder.append("| = ");
					ruleBuilder.append(values[i]);
				}
			} else {
				// single value
				ruleBuilder.append(" ");
				ruleBuilder.append(range.getReferencedComponentId());
				ruleBuilder.append(" |");
				ruleBuilder.append(attributeFsn);
				ruleBuilder.append("| = ");
				ruleBuilder.append(valueConstraint.getConstraint());
			}
		}
		return ruleBuilder.toString();
	}

	private String constructNumericRangeRule(AttributeRange range, AttributeDomain attributeDomain, String attributeFsn, ConcreteValueRangeConstraint valueConstraint) {
		StringBuilder ruleBuilder = new StringBuilder();
		if (valueConstraint.haveBothMinimumAndMaximum()) {
			ruleBuilder.append(" ");
			ruleBuilder.append(range.getReferencedComponentId());
			ruleBuilder.append(" |");
			ruleBuilder.append(attributeFsn);
			ruleBuilder.append("| ");
			ruleBuilder.append(constructNumericRangeRule(valueConstraint.getMinimumValue(), ">=" ));
			ruleBuilder.append("," );
			if (attributeDomain.isGrouped()) {
				ruleBuilder.append(" [");
				ruleBuilder.append(attributeDomain.getAttributeInGroupCardinality().getValue());
				ruleBuilder.append("]");
			} else {
				ruleBuilder.append("[");
				ruleBuilder.append(attributeDomain.getAttributeCardinality().getValue());
				ruleBuilder.append("]");
			}
			ruleBuilder.append(" ");
			ruleBuilder.append(range.getReferencedComponentId());
			ruleBuilder.append(" |");
			ruleBuilder.append(attributeFsn);
			ruleBuilder.append("| ");
			ruleBuilder.append(constructNumericRangeRule(valueConstraint.getMaximumValue(), "<="));
		} else {
			ruleBuilder.append(" ");
			ruleBuilder.append(range.getReferencedComponentId());
			ruleBuilder.append(" |");
			ruleBuilder.append(attributeFsn);
			ruleBuilder.append("| ");
			if (valueConstraint.getMinimumValue() != null && !valueConstraint.getMinimumValue().isEmpty()) {
				ruleBuilder.append(constructNumericRangeRule(valueConstraint.getMinimumValue(), ">="));
			} else if (valueConstraint.getMaximumValue() != null && !valueConstraint.getMaximumValue().isEmpty()) {
				ruleBuilder.append(constructNumericRangeRule(valueConstraint.getMaximumValue(), "<="));
			} else {
				// value list
				ruleBuilder.append("= ");
				ruleBuilder.append(valueConstraint.getConstraint());
			}
		}
		return ruleBuilder.toString();
	}

	private String constructNumericRangeRule(String numericRange, String sign) {
		if (numericRange == null || numericRange.isEmpty()) {
			return null;
		}
		if (numericRange.startsWith("#")) {
			return sign + " " + numericRange;
		} else {
			int index = numericRange.indexOf("#");
			return numericRange.substring(0, index) + " " + numericRange.substring(index);
		}
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

		// Currently there is no way in the MRCM for a sub domain to state whether to inherit the attributes from its parent domain.
		// The following logic requires future improvements
		List<ProximalPrimitiveRefinement> refinements = processProximalPrimitiveRefinement(domain.getProximalPrimitiveRefinement());
		List<String> domainIdsToInclude = new ArrayList<>();
		if (parentDomainIds != null && !parentDomainIds.isEmpty() && !excludeParentDomainAttributes(domain, domainToAttributesMap, refinements)) {
			domainIdsToInclude.addAll(parentDomainIds);
		}
		domainIdsToInclude.add(domain.getReferencedComponentId());
		// Filter for mandatory and all content type or given type
		List<AttributeDomain> attributeDomains = new ArrayList<>();
		for (String domainId : domainIdsToInclude) {
			if (domainToAttributesMap.containsKey(domainId)) {
				attributeDomains.addAll(domainToAttributesMap.get(domainId).stream()
						.filter(d -> (RuleStrength.MANDATORY == d.getRuleStrength()) && (ContentType.ALL == d.getContentType() || type == d.getContentType()))
						.collect(Collectors.toList()));
			}
		}

		List<AttributeDomain> grouped = attributeDomains.stream().filter(AttributeDomain::isGrouped).sorted(ATTRIBUTE_DOMAIN_COMPARATOR_BY_ATTRIBUTE_ID).collect(Collectors.toList());

		List<AttributeDomain> unGrouped = attributeDomains.stream().filter(not(AttributeDomain::isGrouped)).sorted(ATTRIBUTE_DOMAIN_COMPARATOR_BY_ATTRIBUTE_ID).collect(Collectors.toList());
		// un-grouped first if present
		constructAttributeRoleGroup(unGrouped, templateBuilder, attributeToRangesMap, conceptToFsnMap, type, refinements);
		if (!grouped.isEmpty() && !unGrouped.isEmpty()) {
			templateBuilder.append(", ");
		}
		// grouped
		// use the proximal primitive refinement to construct the first role group if present
		constructAttributeRoleGroup(grouped, templateBuilder, attributeToRangesMap, conceptToFsnMap, type, refinements);

		// additional optional role group
		if (hasMeaningfulChangesInRefinement(refinements, attributeToRangesMap, type)) {
			templateBuilder.append(", ");
			constructAttributeRoleGroup(grouped, templateBuilder, attributeToRangesMap, conceptToFsnMap, type, null);
		}
		return templateBuilder.toString();
	}

	private boolean excludeParentDomainAttributes(Domain domain, Map<String, List<AttributeDomain>> domainToAttributesMap,
												  List<ProximalPrimitiveRefinement> refinements) {
		if (domainToAttributesMap.containsKey(domain.getReferencedComponentId())) {
			List<AttributeDomain> attributeDomains = domainToAttributesMap.get(domain.getReferencedComponentId());
			for (AttributeDomain attributeDomain : attributeDomains) {
				if (attributeDomain.getAttributeCardinality() == null) {
					continue;
				}
				if (1 == attributeDomain.getAttributeCardinality().getMin() && refinements.isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean hasMeaningfulChangesInRefinement(List<ProximalPrimitiveRefinement> refinements,
													 Map<String, List<AttributeRange>> attributeToRangesMap, ContentType type) {
		// Current logic for this is not clear due to existing modeling requires further refinements.
		if (refinements == null || refinements.isEmpty()) {
			return false;
		}
		for (ProximalPrimitiveRefinement refinement : refinements) {
			// here just to check whether the role group has changed.
			if (refinement.getGroupCardinality() != null && refinement.getGroupCardinality().startsWith("1")) {
				return true;
			}
			List<AttributeRange> ranges = attributeToRangesMap.get(refinement.getAttributeId());
			for (AttributeRange range : ranges) {
				if (RuleStrength.MANDATORY == range.getRuleStrength() &&
						(ContentType.ALL == range.getContentType() ||  type == range.getContentType()) &&
						!range.getRangeConstraint().equals(refinement.getRangeConstraint())) {
					return true;
				}
			}
		}
		return false;
	}

	private List<ProximalPrimitiveRefinement> processProximalPrimitiveRefinement(String proximalPrimitiveRefinement) {

		List<ProximalPrimitiveRefinement> result = new ArrayList<>();
		//[[1..*]] 260686004 |Method| = [[+id(<< 129265001 |Evaluation - action|)]]
		if (proximalPrimitiveRefinement != null && !proximalPrimitiveRefinement.trim().isEmpty()) {
			logger.debug("processing proximalPrimitiveRefinement {}", proximalPrimitiveRefinement);
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
			Cardinality groupCardinality = null;
			if (refinements != null) {
				for (ProximalPrimitiveRefinement refinement : refinements) {
					attributeIdToRefinementMap.put(refinement.getAttributeId(), refinement);
					try {
						Cardinality cardinality = new Cardinality(refinement.getGroupCardinality());
						if (groupCardinality == null) {
							groupCardinality = cardinality;
						}
						if (cardinality.getMin() > groupCardinality.getMin()) {
							groupCardinality = cardinality;
						}
					} catch (ServiceException e) {
						throw new IllegalArgumentException("Wrong cardinality format found for " + refinement.getGroupCardinality(), e);
					}
				}
			}
			templateBuilder.append("[[");
			if (groupCardinality != null) {
				templateBuilder.append(groupCardinality.getValue());
			} else {
				// use the default 0..* role group for now when there is no mandatory attribute until we have implemented self grouping
				String roleGroup = "0..*";
				for (AttributeDomain attributeDomain : attributeDomains) {
					if (1 == attributeDomain.getAttributeCardinality().getMin()) {
						roleGroup = attributeDomain.getAttributeCardinality().getValue();
						break;
					}
				}
				templateBuilder.append(roleGroup);
			}
			templateBuilder.append("]] { ");
		}

		int counter = 0;
		for (AttributeDomain attributeDomain : attributeDomains) {
			if (counter > 0) {
				templateBuilder.append(", ");
			}
			List<AttributeRange> ranges = attributeToRangesMap.get(attributeDomain.getReferencedComponentId());
			if (ranges == null) {
				logger.warn("No attribute ranges defined for attribute {} in domain {} ", attributeDomain.getReferencedComponentId(), attributeDomain.getDomainId());
				continue;
			}
			AttributeRange attributeRange = null;
			for (AttributeRange range : ranges) {
				if (RuleStrength.MANDATORY == range.getRuleStrength() &&
						(ContentType.ALL == range.getContentType() || type == range.getContentType())) {
					attributeRange = range;
					break;
				}
			}
			if (attributeRange == null || attributeRange.getRangeConstraint() == null || attributeRange.getRangeConstraint().trim().isEmpty()) {
				logger.warn("No attribute range constraint found for attribute {} with content type {}",
						attributeDomain.getReferencedComponentId(), type.getName());
				continue;
			}
			counter++;
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
			templateBuilder.append(attributeDomain.getReferencedComponentId());
			templateBuilder.append(" |");
			templateBuilder.append(conceptToFsnMap.get(attributeDomain.getReferencedComponentId()));
			templateBuilder.append("|");
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

	/**
	 *  This is a simplified version to sort range constraint(type of CompoundExpressionConstraint for now only).
	 *  Tt doesn't cover RefinedExpressionConstraint yet.
	 * @param rangeConstraint Attribute range constraint
	 * @param memberId The refset member uuid
	 * @return A sorted expression
	 */
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
				conJunctions.sort(EXPRESSION_CONSTRAINT_COMPARATOR_BY_CONCEPT_ID);
				for (int i = 0; i < conJunctions.size(); i++) {
					if (i > 0) {
						expressionBuilder.append( " AND ");
					}
					expressionBuilder.append(constructExpression(conJunctions.get(i)));
				}
			}
			if (compound.getDisjunctionExpressionConstraints() != null) {
				List<SubExpressionConstraint> disJunctions = compound.getDisjunctionExpressionConstraints();
				disJunctions.sort(EXPRESSION_CONSTRAINT_COMPARATOR_BY_CONCEPT_ID);
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
