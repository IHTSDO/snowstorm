package org.snomed.snowstorm.core.data.services.mapper;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.SearchResultMapper;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.aggregation.impl.AggregatedPageImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.lang.Long.parseLong;

public class RefsetMemberToReferenceComponentIdMapper implements SearchResultMapper {

	private final Collection<Long> referenceComponentId;

	public RefsetMemberToReferenceComponentIdMapper(Collection<Long> referenceComponentId) {
		this.referenceComponentId = referenceComponentId;
	}

	@Override
	public <T> AggregatedPage<T> mapResults(SearchResponse response, Class<T> clazz, Pageable pageable) {
		List<T> results = new ArrayList<>();
		ReferenceSetMember r = new ReferenceSetMember();
		SearchHit lastHit = null;
		for (SearchHit hit : response.getHits().getHits()) {
			referenceComponentId.add(parseLong(hit.getFields().get(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID).getValue()));
			results.add((T)r);
			lastHit = hit;
		}
		return new AggregatedPageImpl<>(results, pageable, response.getHits().getTotalHits(), response.getAggregations(), response.getScrollId(), lastHit != null ? lastHit.getSortValues() : null);
	}
}
