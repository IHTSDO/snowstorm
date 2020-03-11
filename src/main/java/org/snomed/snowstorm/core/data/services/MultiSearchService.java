package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.elasticsearch.common.Strings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.pojo.ConceptCriteria;
import org.snomed.snowstorm.core.data.services.pojo.DescriptionCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.*;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Service
/*
 * Service specifically for searching across multiple code systems or branches.
 */
public class MultiSearchService {

	@Autowired
	private DescriptionService descriptionService;

	@Autowired
	private ConceptService	conceptService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchTemplate elasticsearchTemplate;

	public Page<Description> findDescriptions(DescriptionCriteria criteria, PageRequest pageRequest) {
		final BoolQueryBuilder descriptionQuery = boolQuery()
				.must(getBranchesQuery());

		descriptionService.addTermClauses(criteria.getTerm(), criteria.getSearchLanguageCodes(), criteria.getType(), descriptionQuery, criteria.getSearchMode());

		Boolean active = criteria.getActive();
		if (active != null) {
			descriptionQuery.must(termQuery(Description.Fields.ACTIVE, active));
		}

		String module = criteria.getModule();
		if (!Strings.isNullOrEmpty(module)) {
			descriptionQuery.must(termQuery(Description.Fields.MODULE_ID, module));
		}

		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(descriptionQuery)
				.withPageable(pageRequest);
		if (criteria.getConceptActive() != null) {
			Set<Long> conceptsToFetch = getMatchedConcepts(criteria.getConceptActive(), branchesQuery, descriptionQuery);
			queryBuilder.withFilter(boolQuery().must(termsQuery(Description.Fields.CONCEPT_ID, conceptsToFetch)));
		}
		NativeSearchQuery query = queryBuilder.build();
		DescriptionService.addTermSort(query);
		return elasticsearchTemplate.queryForPage(query, Description.class);
	}

	private Set<Long> getMatchedConcepts(Boolean conceptActiveFlag, BoolQueryBuilder branchesQuery, BoolQueryBuilder descriptionQuery) {
		// return description and concept ids
		Set<Long> conceptIdsMatched = new LongOpenHashSet();
		try (final CloseableIterator<Description> descriptions = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(descriptionQuery)
				.withFields(Description.Fields.CONCEPT_ID)
				.withPageable(ConceptService.LARGE_PAGE).build(), Description.class)) {
			while (descriptions.hasNext()) {
				conceptIdsMatched.add(new Long(descriptions.next().getConceptId()));
			}
		}
		// filter description ids based on concept query results using active flag
		Set<Long> result = new LongOpenHashSet();
		if (!conceptIdsMatched.isEmpty()) {
			try (final CloseableIterator<Concept> concepts = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(branchesQuery)
							.must(termsQuery(Concept.Fields.CONCEPT_ID, conceptIdsMatched))
					)
					.withFilter(boolQuery().must(termQuery(Concept.Fields.ACTIVE, conceptActiveFlag)))
					.withFields(Concept.Fields.CONCEPT_ID)
					.withPageable(ConceptService.LARGE_PAGE).build(), Concept.class)) {
				while (concepts.hasNext()) {
					result.add(new Long(concepts.next().getConceptId()));
				}
			}
		}
		return result;
	}

	private QueryBuilder getBranchesQuery() {
		Set<String> branchPaths = getAllPublishedVersionBranchPaths();

		BoolQueryBuilder branchesQuery = boolQuery();
		if (branchPaths.isEmpty()) {
			branchesQuery.must(termQuery("path", "this-will-match-nothing"));
		}
		for (String branchPath : branchPaths) {
			BoolQueryBuilder branchQuery = boolQuery();
			if (!PathUtil.getParentPath(branchPath).equals(Branch.MAIN)) {
				// Prevent content on MAIN being found in every other code system
				branchQuery.mustNot(termQuery("path", Branch.MAIN));
			}
			branchQuery.must(versionControlHelper.getBranchCriteria(branchPath).getEntityBranchCriteria(Description.class));
			branchesQuery.should(branchQuery);
		}
		return branchesQuery;
	}

	public Set<String> getAllPublishedVersionBranchPaths() {
		Set<String> branchPaths = new HashSet<>();
		for (CodeSystem codeSystem : codeSystemService.findAll()) {
			CodeSystemVersion latestVisibleVersion = codeSystemService.findLatestVisibleVersion(codeSystem.getShortName());
			if (latestVisibleVersion != null) {
				branchPaths.add(latestVisibleVersion.getBranchPath());
			}
		}
		return branchPaths;
	}

	public Page<ConceptMini> findConcepts(ConceptCriteria criteria, PageRequest pageRequest) {

		final BoolQueryBuilder conceptQuery = boolQuery().must(getBranchesQuery());

		conceptService.addClauses(criteria.getConceptIds(), criteria.getActive(), conceptQuery);

		NativeSearchQuery query = new NativeSearchQueryBuilder()
				.withQuery(conceptQuery)
				.withPageable(pageRequest)
				.build();
		return elasticsearchTemplate.queryForPage(query, ConceptMini.class);
	}
}
