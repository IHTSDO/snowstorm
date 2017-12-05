package org.snomed.snowstorm.core.data.services;

import com.github.vanroy.springdata.jest.internal.SearchScrollResult;
import com.github.vanroy.springdata.jest.mapper.DefaultJestResultsMapper;
import com.google.gson.JsonObject;
import org.elasticsearch.common.collect.MapBuilder;
import org.snomed.snowstorm.core.data.domain.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.elasticsearch.core.EntityMapper;
import org.springframework.data.elasticsearch.core.mapping.ElasticsearchPersistentEntity;
import org.springframework.data.elasticsearch.core.mapping.ElasticsearchPersistentProperty;
import org.springframework.data.mapping.context.MappingContext;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.lang.Long.parseLong;

/**
 * This class can produce results from Elasticsearch much faster than the default mapper but only returns the conceptId.
 * To use this feature set the fields param in your Elasticsearch query to conceptId (or sourceId for relationships).
 */
public class FastJestResultsMapper extends DefaultJestResultsMapper {

	private Map<Class, Function<SearchScrollResult.Hit<JsonObject, Void>, Object>> mapFunctions = MapBuilder.newMapBuilder(new HashMap<Class, Function<SearchScrollResult.Hit<JsonObject, Void>, Object>>())
			.put(Concept.class, hit -> new Concept(hit.fields.get(Concept.Fields.CONCEPT_ID).get(0)))
			.put(Description.class, hit -> new Description().setConceptId(hit.fields.get(Description.Fields.CONCEPT_ID).get(0)))
			.put(Relationship.class, hit -> new Relationship().setSourceId(hit.fields.get(Relationship.Fields.SOURCE_ID).get(0)))
			.put(ReferenceSetMember.class, hit -> new ReferenceSetMember().setConceptId(hit.fields.get(ReferenceSetMember.Fields.CONCEPT_ID).get(0)))
			.put(QueryConcept.class, hit -> new QueryConcept().setConceptId(parseLong(hit.fields.get(QueryConcept.Fields.CONCEPT_ID).get(0))))
			.map();

	public FastJestResultsMapper(MappingContext<? extends ElasticsearchPersistentEntity<?>, ElasticsearchPersistentProperty> mappingContext, EntityMapper entityMapper) {
		super(mappingContext, entityMapper);
	}

	@Override
	public <T> Page<T> mapResults(SearchScrollResult response, Class<T> clazz) {
		if (!mapFunctions.containsKey(clazz) || !response.getJsonString().contains("\"fields\":{\"")) {
			return super.mapResults(response, clazz);
		}

		LinkedList<T> results = new LinkedList<>();
		List<SearchScrollResult.Hit<JsonObject, Void>> hits = response.getHits(JsonObject.class);
		if (!hits.isEmpty()) {
			// Let's create the entity just using the fields available
			for (SearchScrollResult.Hit<JsonObject, Void> hit : hits) {
				if (hit != null) {
					results.add((T) mapFunctions.get(clazz).apply(hit));
				}
			}
		}
		return new PageImpl<>(results, null, response.getTotal());
	}

}
