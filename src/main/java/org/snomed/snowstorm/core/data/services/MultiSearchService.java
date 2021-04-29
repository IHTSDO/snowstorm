package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.services.pojo.ConceptCriteria;
import org.snomed.snowstorm.core.data.services.pojo.DescriptionCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.*;

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
	private ElasticsearchRestTemplate elasticsearchTemplate;
	
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	Map<String, String> publishedBranches = new HashMap<>();

	public Page<Description> findDescriptions(DescriptionCriteria criteria, PageRequest pageRequest) {
		final BoolQueryBuilder branchesQuery = getBranchesQuery();
		final BoolQueryBuilder descriptionQuery = boolQuery()
				.must(branchesQuery);

		descriptionService.addTermClauses(criteria.getTerm(), criteria.getSearchLanguageCodes(), criteria.getType(), descriptionQuery, criteria.getSearchMode());

		Boolean active = criteria.getActive();
		if (active != null) {
			descriptionQuery.must(termQuery(Description.Fields.ACTIVE, active));
		}

		Collection<String> modules = criteria.getModules();
		if (!CollectionUtils.isEmpty(modules)) {
			descriptionQuery.must(termsQuery(Description.Fields.MODULE_ID, modules));
		}

		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(descriptionQuery)
				.withPageable(pageRequest);
		if (criteria.getConceptActive() != null) {
			Set<Long> conceptsToFetch = getMatchedConcepts(criteria.getConceptActive(), branchesQuery, descriptionQuery);
			queryBuilder.withFilter(boolQuery().must(termsQuery(Description.Fields.CONCEPT_ID, conceptsToFetch)));
		}
		NativeSearchQuery query = queryBuilder.build();
		query.setTrackTotalHits(true);
		DescriptionService.addTermSort(query);
		SearchHits<Description> searchHits = elasticsearchTemplate.search(query, Description.class);
		return new PageImpl<>(searchHits.get().map(SearchHit::getContent).collect(Collectors.toList()), pageRequest, searchHits.getTotalHits());
	}

	private Set<Long> getMatchedConcepts(Boolean conceptActiveFlag, BoolQueryBuilder branchesQuery, BoolQueryBuilder descriptionQuery) {
		// return description and concept ids
		Set<Long> conceptIdsMatched = new LongOpenHashSet();
		try (final SearchHitsIterator<Description> descriptions = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
				.withQuery(descriptionQuery)
				.withFields(Description.Fields.CONCEPT_ID)
				.withPageable(ConceptService.LARGE_PAGE).build(), Description.class)) {
			while (descriptions.hasNext()) {
				conceptIdsMatched.add(Long.valueOf(descriptions.next().getContent().getConceptId()));
		}
		}
		// filter description ids based on concept query results using active flag
		Set<Long> result = new LongOpenHashSet();
		if (!conceptIdsMatched.isEmpty()) {
			try (final SearchHitsIterator<Concept> concepts = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(branchesQuery)
							.must(termsQuery(Concept.Fields.CONCEPT_ID, conceptIdsMatched))
					)
					.withFilter(boolQuery().must(termQuery(Concept.Fields.ACTIVE, conceptActiveFlag)))
					.withFields(Concept.Fields.CONCEPT_ID)
					.withPageable(ConceptService.LARGE_PAGE).build(), Concept.class)) {
				while (concepts.hasNext()) {
					result.add(Long.valueOf(concepts.next().getContent().getConceptId()));
				}
			}
		}
		return result;
	}

	private BoolQueryBuilder getBranchesQuery() {
		long startTime = System.currentTimeMillis();
		Set<String> branchPaths = getAllPublishedVersionBranchPaths();
		//long endTime = System.currentTimeMillis();
		//logger.info("Mutisearch finding published paths took " + (endTime - startTime) + "ms");
		
		BoolQueryBuilder branchesQuery = boolQuery();
		if (branchPaths.isEmpty()) {
			branchesQuery.must(termQuery("path", "this-will-match-nothing"));
		}
		for (String branchPath : branchPaths) {
			BoolQueryBuilder branchQuery = boolQuery();
			if (!Branch.MAIN.equals(PathUtil.getParentPath(branchPath))) {
				// Prevent content on MAIN being found in every other code system
				branchQuery.mustNot(termQuery("path", Branch.MAIN));
			}
			branchQuery.must(versionControlHelper.getBranchCriteria(branchPath).getEntityBranchCriteria(Description.class));
			branchesQuery.should(branchQuery);
		}
		long endTime = System.currentTimeMillis();
		logger.info("Mutisearch branches query took " + (endTime - startTime) + "ms");
		return branchesQuery;
	}

	public Set<String> getAllPublishedVersionBranchPaths() {
		List<CodeSystem> codeSystems = codeSystemService.findAll();
		Set<String> publishedVersionBranchPaths = new HashSet<>();
		synchronized(this) {
			publishedBranches.clear();
			for (CodeSystem cs : codeSystems) {
				//Cache the latest version paths so we can repopulate it on the concept
				if (cs.getLatestVersion() != null) {
					publishedBranches.put(cs.getBranchPath(), cs.getLatestVersion().getBranchPath());
					publishedVersionBranchPaths.add(cs.getLatestVersion().getBranchPath());
				}
			}
		}
		
		return publishedVersionBranchPaths;
	}
	
	public String getPublishedVersionOfBranch(String branch) {
		if (publishedBranches.isEmpty()) {
			getAllPublishedVersionBranchPaths();
		}
		//If we don't find a published version, return the branch we were given
		return publishedBranches.containsKey(branch) ? publishedBranches.get(branch) : branch;
	}
	
	public Set<CodeSystemVersion> getAllPublishedVersions() {
		Set<CodeSystemVersion> codeSystemVersions = new HashSet<>();
		for (CodeSystem codeSystem : codeSystemService.findAll()) {
			codeSystemVersions.addAll(codeSystemService.findAllVersions(codeSystem.getShortName(), true));
		}
		return codeSystemVersions;
	}

	public Page<Concept> findConcepts(ConceptCriteria criteria, PageRequest pageRequest) {
		final BoolQueryBuilder conceptQuery = boolQuery().must(getBranchesQuery());
		conceptService.addClauses(criteria.getConceptIds(), criteria.getActive(), conceptQuery);
		NativeSearchQuery query = new NativeSearchQueryBuilder()
				.withQuery(conceptQuery)
				.withPageable(pageRequest)
				.build();
		SearchHits<Concept> searchHits = elasticsearchTemplate.search(query, Concept.class);
		//Populate the published version path back in
		List<Concept> concepts = searchHits.get()
				.map(SearchHit::getContent)
				.map(c -> { c.setPath(getPublishedVersionOfBranch(c.getPath())) ; return c; })
				.collect(Collectors.toList());
		return new PageImpl<>(concepts, pageRequest, searchHits.getTotalHits());
	}
}
