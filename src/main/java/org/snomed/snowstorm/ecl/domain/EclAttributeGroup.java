package org.snomed.snowstorm.ecl.domain;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.data.services.RuntimeServiceException;
import org.snomed.snowstorm.ecl.domain.query.ESBooleanQuery;
import org.snomed.snowstorm.ecl.domain.query.ESQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

public class EclAttributeGroup implements Refinement {

	private final EclAttributeSet attributeSet;

	public EclAttributeGroup(EclAttributeSet attributeSet) {
		this.attributeSet = attributeSet;
	}

	@Override
	public void addCriteria(BoolQueryBuilder query, String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService) {
		// The index can not support this kind of query directly so we need to do something special here:
		// 1. Grab concepts with matching attribute types and values (regardless of group)
		// 2. Filter the results by processing the attribute group map (which is stored as a plain string in the index)
		// 3. Add the matching concepts to a filter on the parent query

		BoolQueryBuilder attributesQueryForSingleGroup = new BoolQueryBuilder();
		attributeSet.addCriteria(attributesQueryForSingleGroup, path, branchCriteria, stated, queryService);

		List<Long> conceptsWithCorrectGrouping = new LongArrayList();
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(attributesQueryForSingleGroup)
				.withPageable(LARGE_PAGE)
				.build();

		String fromValue = attributesQueryForSingleGroup.toString();

		try {
			ESQuery esQuery = queryService.getObjectMapper().readValue(fromValue, ESQuery.class);
			queryService.stream(searchQuery).forEachRemaining(queryConcept -> {
				// Filter results manually to check all types and values are within the same group
				Map<Integer, Map<String, List<String>>> groupedAttributesMap = queryConcept.getGroupedAttributesMap();
				for (Integer groupNumber : groupedAttributesMap.keySet()) {
					if (groupNumber != 0) {
						if (doesAttributeGroupMatch(groupedAttributesMap.get(groupNumber), esQuery)) {
							conceptsWithCorrectGrouping.add(queryConcept.getConceptId());
						}
					}
				}
			});
		} catch (IOException e) {
			throw new RuntimeServiceException("Failed to parse Elasticsearch query", e);
		}

		// Use results to build filter
		query.filter(termsQuery(QueryConcept.CONCEPT_ID_FIELD, conceptsWithCorrectGrouping));
	}

	private boolean doesAttributeGroupMatch(Map<String, List<String>> attributeValuesMap, ESQuery esQuery) {
		ESBooleanQuery bool = esQuery.getBool();
		if (bool != null) {
			List<ESQuery> must = bool.getMust();
			if (must != null) {
				for (ESQuery query : must) {
					if (!doesAttributeGroupMatch(attributeValuesMap, query)) {
						return false;
					}
				}
			}
			List<ESQuery> shouldQueries = bool.getShould();
			if (shouldQueries != null) {
				boolean somethingTrue = false;
				for (ESQuery shouldQuery : shouldQueries) {
					if (doesAttributeGroupMatch(attributeValuesMap, shouldQuery)) {
						somethingTrue = true;
					}
				}
				if (!somethingTrue) {
					return false;
				}
			}
		} else {
			Map<String, String> term = esQuery.getTerm();
			Map<String, Set<String>> terms = esQuery.getTerms();
			if (term != null) {
				String key = term.keySet().iterator().next();
				String value = term.get(key);
				List<String> actualValues = attributeValuesMap.get(EclAttribute.attributeMapKeyToConceptId(key));
				if (actualValues == null || !actualValues.contains(value)) {
					return false;
				}
			} else if (terms != null) {
				String key = terms.keySet().iterator().next();
				Set<String> values = terms.get(key);
				List<String> actualValues = attributeValuesMap.get(EclAttribute.attributeMapKeyToConceptId(key));
				if (actualValues == null || !actualValues.containsAll(values)) {
					return false;
				}
			}
		}
		return true;
	}

}
