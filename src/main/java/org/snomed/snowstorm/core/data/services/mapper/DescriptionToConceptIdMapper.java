package org.snomed.snowstorm.core.data.services.mapper;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.snomed.snowstorm.core.data.domain.Description;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.SearchResultMapper;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.aggregation.impl.AggregatedPageImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.lang.Long.parseLong;

public class DescriptionToConceptIdMapper implements SearchResultMapper {

	private final Collection<Long> conceptIds;

	public DescriptionToConceptIdMapper(Collection<Long> conceptIds) {
		this.conceptIds = conceptIds;
	}

	@Override
	public <T> AggregatedPage<T> mapResults(SearchResponse response, Class<T> clazz, Pageable pageable) {
		List<T> results = new ArrayList<>();
		Description d = new Description();
		SearchHit lastHit = null;
		for (SearchHit hit : response.getHits().getHits()) {
			conceptIds.add(parseLong(hit.getFields().get(Description.Fields.CONCEPT_ID).getValue()));
			results.add((T) d);
			lastHit = hit;
		}
		return new AggregatedPageImpl<>(results, pageable, response.getHits().getTotalHits(), response.getAggregations(), response.getScrollId(), lastHit != null ? lastHit.getSortValues() : null);
	}
}
