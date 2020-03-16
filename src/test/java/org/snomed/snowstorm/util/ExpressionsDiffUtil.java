package org.snomed.snowstorm.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.mrcm.model.AttributeRange;
import org.snomed.snowstorm.mrcm.model.Domain;

import java.util.*;

public class ExpressionsDiffUtil {

	private Logger logger = LoggerFactory.getLogger(ExpressionsDiffUtil.class);

	public void runPostcoordinationDiffReport(List<Domain> updatedDomains, Map<String,Domain> domainMapByDomainId) {
		if (updatedDomains == null || updatedDomains.isEmpty()) {
			return;
		}
		int sameCounter = 0;
		int sameWhenSortingIngored = 0;
		int diffCounter = 0;
		for (Domain domain : updatedDomains) {
			String domainId = domain.getReferencedComponentId();
			String published = domainMapByDomainId.get(domainId).getDomainTemplateForPostcoordination();
			String actual = domain.getDomainTemplateForPostcoordination();
			if (!published.equals(actual)) {
				logger.info("Analyzing postcoordination domain template for domain id " + domainId);
				if (hasDiff(published, actual, false)) {
					diffCounter++;
					logger.info("before = " + published);
					logger.info("after = " + actual);
				} else {
					logger.info("domain template is the same when cardinality and sorting is ignored " + domain.getReferencedComponentId());
					sameWhenSortingIngored++;
				}
			} else {
				sameCounter++;
				logger.info("domain template is the same for " + domainId);
			}
		}
		logger.info("Total templates updated = " + updatedDomains.size());
		logger.info("Total templates are the same without change = " + sameCounter);
		logger.info("Total templates are the same when cardinality and sorting is ignored = " + sameWhenSortingIngored);
		logger.info("Total templates found with diffs = " + diffCounter);
	}

	public void runAttributeRulesDiffReport(List<AttributeRange> attributeRanges, Map<String, List<AttributeRange>> attributeToRangesMap) {
		int sameCounter = 0;
		int sameWhenSortingIngored = 0;
		int diffCounter = 0;
		for (AttributeRange range : attributeRanges) {
			String attributeId = range.getReferencedComponentId();
			String publishedRule = null;
			for (AttributeRange published : attributeToRangesMap.get(attributeId)) {
				if (range.getId().equals(published.getId())) {
					publishedRule = published.getAttributeRule();
					break;
				}
			}
			String actual = range.getAttributeRule();
			if (!actual.equals(publishedRule)) {
				logger.info("Analyzing attribute rule for attribute " + attributeId + " with id = " + range.getId());
				if (hasDiff(publishedRule, actual, true)) {
					diffCounter++;
					logger.info("before = " + publishedRule);
					logger.info("after = " + actual);
				} else {
					logger.info("Attribute rules are the same when cardinality and sorting are ignored " + attributeId);
					sameWhenSortingIngored++;
				}
			} else {
				sameCounter++;
				logger.info("Attribute rule is the same for " + attributeId);
			}
		}
		logger.info("Total templates updated = " + attributeRanges.size());
		logger.info("Total templates are the same without change = " + sameCounter);
		logger.info("Total templates are the same when cardinality and sorting is ignored = " + sameWhenSortingIngored);
		logger.info("Total templates found with diffs = " + diffCounter);
	}

	public void runPrecoordinationDiffReport(List<Domain> updatedDomains, Map<String,Domain> domainMapByDomainId) {
		if (updatedDomains == null || updatedDomains.isEmpty()) {
			return;
		}
		int sameCounter = 0;
		int sameWhenSortingIngored = 0;
		int diffCounter = 0;
		for (Domain domain : updatedDomains) {
			String domainId = domain.getReferencedComponentId();
			String published = domainMapByDomainId.get(domainId).getDomainTemplateForPrecoordination();
			String actual = domain.getDomainTemplateForPrecoordination();
			if (!published.equals(actual)) {
				logger.info("Analyzing precoordinationdomain template for domain id " + domainId);
				if (hasDiff(published, actual, false)) {
					diffCounter++;
					logger.info("before = " + published);
					logger.info("after = " + actual);
				} else {
					logger.info("domain template is the same when cardinality and sorting is ignored " + domain.getReferencedComponentId());
					sameWhenSortingIngored++;
				}
			} else {
				sameCounter++;
				logger.info("domain template is the same for " + domainId);
			}
		}
		logger.info("Total templates updated = " + updatedDomains.size());
		logger.info("Total templates are the same without change = " + sameCounter);
		logger.info("Total templates are the same when cardinality and sorting is ignored = " + sameWhenSortingIngored);
		logger.info("Total templates found with diffs = " + diffCounter);
	}

	public boolean hasDiff(String published, String actual, boolean ignoreCardinality) {
		boolean hasDiff = false;
		List<String> publishedSorted = split(published, ignoreCardinality);
		List<String> actualSorted = split(actual, ignoreCardinality);

		logger.info("Published but missing in the new generated");
		for (String token : publishedSorted) {
			if (!actualSorted.contains(token)) {
				System.out.println(token);
				hasDiff = true;
			}
		}

		logger.info("In the new generated but missing from the published");
		for (String token : actualSorted) {
			if (!publishedSorted.contains(token)) {
				hasDiff = true;
			}
		}
		return hasDiff;
	}

	private List<String> split(String expression, boolean ignoreCardinality) {
		List<String> result = new ArrayList<>();
		for (String part : expression.split(",", -1)) {
			if (part.contains(":")) {
				result.addAll(Arrays.asList(part.split(":", -1)));
			} else {
				result.add(part.trim());
			}
		}
		if (ignoreCardinality) {
			List<String> updated = new ArrayList<>();
			for (String token : result) {
				if (token.contains("..")) {
					if (token.endsWith("}")) {
						token = token.replace("}", "");
					}
					updated.add(token.substring(token.lastIndexOf("..") + 5, token.length()).trim());
				}
			}
			result = updated;
		}
		Collections.sort(result);
		return result;
	}
}
