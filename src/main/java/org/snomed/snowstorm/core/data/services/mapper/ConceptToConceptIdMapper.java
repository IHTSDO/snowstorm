package org.snomed.snowstorm.core.data.services.mapper;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.SearchResultMapper;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.aggregation.impl.AggregatedPageImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.lang.Long.parseLong;

public class ConceptToConceptIdMapper implements SearchResultMapper {

	private final Collection<Long> conceptIds;

	public ConceptToConceptIdMapper(Collection<Long> conceptIds) {
		this.conceptIds = conceptIds;
	}

	@Override
	public <T> AggregatedPage<T> mapResults(SearchResponse response, Class<T> clazz, Pageable pageable) {
		List<T> results = new ArrayList<>();
		Concept c = new Concept();
		SearchHit lastHit = null;
		for (SearchHit hit : response.getHits().getHits()) {
			conceptIds.add(parseLong(hit.getFields().get(Concept.Fields.CONCEPT_ID).getValue()));
			results.add((T)c);
			lastHit = hit;
		}
		return new AggregatedPageImpl<>(results, pageable, response.getHits().getTotalHits(), response.getAggregations(), response.getScrollId(), lastHit != null ? lastHit.getSortValues() : null);
	}
}
