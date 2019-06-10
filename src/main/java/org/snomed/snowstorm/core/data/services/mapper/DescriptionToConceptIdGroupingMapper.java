package org.snomed.snowstorm.core.data.services.mapper;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.document.DocumentField;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.Description;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.SearchResultMapper;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.aggregation.impl.AggregatedPageImpl;

import java.util.*;

import static java.lang.Long.parseLong;

/**
 * Collects description conceptIds and description ids but only for the first description matched against a concept.
 * Additional descriptions for the same concept are ignored.
 * NOTE: Page total hit numbers will not be accurate.
 */
public class DescriptionToConceptIdGroupingMapper implements SearchResultMapper {

	private final Set<Long> conceptIds;
	private final Collection<Long> descriptionsGroupedByConcept;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public DescriptionToConceptIdGroupingMapper(Set<Long> conceptIds, Collection<Long> descriptionsGroupedByConcept) {
		this.conceptIds = conceptIds;
		this.descriptionsGroupedByConcept = descriptionsGroupedByConcept;
	}

	@Override
	public <T> AggregatedPage<T> mapResults(SearchResponse response, Class<T> clazz, Pageable pageable) {
		List<T> results = new ArrayList<>();
		Description d = new Description();
		SearchHit lastHit = null;
		for (SearchHit hit : response.getHits().getHits()) {
			Map<String, DocumentField> fields = hit.getFields();
			if (conceptIds.add(parseLong(fields.get(Description.Fields.CONCEPT_ID).getValue()))) {
				DocumentField storedDescriptionId = fields.get(Description.Fields.DESCRIPTION_ID);
				if (storedDescriptionId == null) {
					logger.warn("Description search with group-by-concept requires that the conceptId is a stored field in Elasticsearch. " +
							"Please update or recreate the Elasticsearch indexes and reindex the data to use this feature.");
					break;
				}
				descriptionsGroupedByConcept.add(parseLong(storedDescriptionId.getValue()));
				results.add((T) d);
				lastHit = hit;
			}
		}
		return new AggregatedPageImpl<>(results, pageable, response.getHits().getTotalHits(), response.getAggregations(), response.getScrollId(), lastHit != null ? lastHit.getSortValues() : null);
	}
}
