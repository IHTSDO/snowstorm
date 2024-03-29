package org.snomed.snowstorm.core.data.services;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.ecl.ECLQueryService;
import org.snomed.snowstorm.rest.View;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.stereotype.Service;

import java.util.*;

import static io.kaicode.elasticvc.api.ComponentService.CLAUSE_LIMIT;
import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static java.lang.Long.parseLong;
import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.*;
import static io.kaicode.elasticvc.helper.QueryHelper.*;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_DIALECTS;

@Service
public class ContentReportService {

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private ECLQueryService eclQueryService;

	@Autowired
	private ConceptService conceptService;

	public List<InactivationTypeAndConceptIdList> findInactiveConceptsWithNoHistoricalAssociationByInactivationType(String branchPath, String conceptEffectiveTime) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);

		List<InactivationTypeAndConceptIdList> results = new ArrayList<>();

		// Gather ids of inactive concepts
		BoolQuery.Builder boolQueryBuilder = bool()
				.must(branchCriteria.getEntityBranchCriteria(Concept.class))
				.must(termQuery(Concept.Fields.ACTIVE, false));
		if (!Strings.isNullOrEmpty(conceptEffectiveTime)) {
			boolQueryBuilder.must(termQuery(Concept.Fields.EFFECTIVE_TIME, conceptEffectiveTime));
		}
		List<Long> conceptIds = new LongArrayList();
		try (SearchHitsIterator<Concept> conceptStream = elasticsearchOperations.searchForStream(new NativeQueryBuilder()
				.withQuery(boolQueryBuilder.build()._toQuery())
				.withSourceFilter(new FetchSourceFilter(new String[]{Concept.Fields.CONCEPT_ID}, null))
				.withPageable(LARGE_PAGE)
				.build(), Concept.class)) {
			conceptStream.forEachRemaining(hit -> conceptIds.add(hit.getContent().getConceptIdAsLong()));
		}
		if (conceptIds.isEmpty()) {
			return results;
		}

		// Gather ids of concepts with historical associations
		List<Long> conceptsWithAssociations = new LongArrayList();
		List<Long> allHistoricalAssociations = eclQueryService.selectConceptIds("<" + Concepts.REFSET_HISTORICAL_ASSOCIATION, branchCriteria, true, LARGE_PAGE).getContent();
		for (List<Long> batch : Iterables.partition(conceptIds, CLAUSE_LIMIT)) {
			try (SearchHitsIterator<ReferenceSetMember> memberStream = elasticsearchOperations.searchForStream(new NativeQueryBuilder()
					.withQuery(bool(b -> b
							.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
							.must(termQuery(ReferenceSetMember.Fields.ACTIVE, true))
							.must(termsQuery(ReferenceSetMember.Fields.REFSET_ID, allHistoricalAssociations))
							.filter(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, batch)))
					)
					.withSourceFilter(new FetchSourceFilter(new String[]{ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID}, null))
					.withPageable(LARGE_PAGE)
					.build(), ReferenceSetMember.class)) {
				memberStream.forEachRemaining(hit -> conceptsWithAssociations.add(parseLong(hit.getContent().getReferencedComponentId())));
			}
		}


		List<Long> conceptsWithoutAssociations = new LongArrayList(conceptIds);
		conceptsWithoutAssociations.removeAll(conceptsWithAssociations);
		if (conceptsWithoutAssociations.isEmpty()) {
			return results;
		}

		// Inactivation indicators
		Map<Long, List<Long>> conceptsByIndicator = new Long2ObjectOpenHashMap<>();
		for (List<Long> batch : Iterables.partition(conceptsWithoutAssociations, CLAUSE_LIMIT)) {
			try (SearchHitsIterator<ReferenceSetMember> memberStream = elasticsearchOperations.searchForStream(new NativeQueryBuilder()
					.withQuery(bool(b -> b
							.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
							.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.CONCEPT_INACTIVATION_INDICATOR_REFERENCE_SET))
							.must(termQuery(ReferenceSetMember.Fields.ACTIVE, true))
							.filter(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, batch)))
					)
					.withPageable(LARGE_PAGE)
					.build(), ReferenceSetMember.class)) {
				memberStream.forEachRemaining(hit ->
						conceptsByIndicator.computeIfAbsent(
								parseLong(hit.getContent().getAdditionalField("valueId")), key -> new LongArrayList())
								.add(parseLong(hit.getContent().getReferencedComponentId())));
			}
		}

		Map<String, ConceptMini> minis = conceptService.findConceptMinis(branchCriteria, conceptsByIndicator.keySet(), DEFAULT_LANGUAGE_DIALECTS).getResultsMap();

		for (Long indicator : conceptsByIndicator.keySet()) {
			results.add(new InactivationTypeAndConceptIdList(minis.get(indicator.toString()), new LongOpenHashSet(conceptsByIndicator.get(indicator))));
		}

		List<Long> conceptsWithNoIndicator = new LongArrayList(conceptsWithoutAssociations);
		for (List<Long> longs : conceptsByIndicator.values()) {
			conceptsWithNoIndicator.removeAll(longs);
		}
		if (!conceptsWithNoIndicator.isEmpty()) {
			results.add(new InactivationTypeAndConceptIdList(new ConceptMini("0", DEFAULT_LANGUAGE_DIALECTS), new LongOpenHashSet(conceptsWithNoIndicator)));
		}

		return results;
	}

	public record InactivationTypeAndConceptIdList(ConceptMini inactivationIndicator, Collection<Long> conceptIds) {

		@Override
		@JsonView(value = View.Component.class)
			public ConceptMini inactivationIndicator() {
				return inactivationIndicator;
			}

			@Override
			@JsonView(value = View.Component.class)
			public Collection<Long> conceptIds() {
				return conceptIds;
			}
		}
}
