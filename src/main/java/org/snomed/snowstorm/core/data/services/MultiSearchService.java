package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.*;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.pojo.ConceptCriteria;
import org.snomed.snowstorm.core.data.services.pojo.DescriptionCriteria;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregations;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregationsFactory;
import org.snomed.snowstorm.ecl.ECLQueryService;
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

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.snomed.snowstorm.config.Config.AGGREGATION_SEARCH_SIZE;

@Service
/*
 * Service specifically for searching across multiple code systems or branches.
 */
public class MultiSearchService implements CommitListener {

	@Autowired
	private DescriptionService descriptionService;

	@Autowired
	private ConceptService	conceptService;

	@Autowired
	private ECLQueryService eclQueryService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchRestTemplate elasticsearchTemplate;
	
	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final Map<String, String> publishedBranches = new HashMap<>();

	private MultiBranchCriteria cachedBranchCriteria = null;
	private LocalDate cacheDate = null;

	public Page<Description> findDescriptions(DescriptionCriteria criteria, String ecl, PageRequest pageRequest) {

		if (ecl != null) {
			// ECL -> conceptIds
			MultiBranchCriteria branchesQuery = getBranchesQuery();
			Page<Long> page = eclQueryService.selectConceptIds(ecl, branchesQuery, "MAIN", false, LARGE_PAGE);
			criteria.conceptIds(page.getContent());
		}

		SearchHits<Description> searchHits = findDescriptionsHelper(criteria, pageRequest);
		return new PageImpl<>(searchHits.get().map(SearchHit::getContent).collect(Collectors.toList()), pageRequest, searchHits.getTotalHits());
	}
	
	public PageWithBucketAggregations<Description> findDescriptionsReferenceSets(DescriptionCriteria criteria, PageRequest pageRequest) {

		// all search results are required to determine total refset bucket membership
		SearchHits<Description> allSearchHits = findDescriptionsHelper(criteria, null);
		// paged results are required for the list of descriptions returned
		SearchHits<Description> searchHits = findDescriptionsHelper(criteria, pageRequest);
		
		List<Aggregation> allAggregations = new ArrayList<>();
		Set<Long> conceptIds = new HashSet<>();
		for (SearchHit<Description> desc : allSearchHits) {
			conceptIds.add(Long.parseLong(desc.getContent().getConceptId()));
		}
		// Fetch concept refset membership aggregation
		SearchHits<ReferenceSetMember> membershipResults = elasticsearchTemplate.search(new NativeSearchQueryBuilder()
						.withQuery(boolQuery()
								//.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
								.must(termsQuery(ReferenceSetMember.Fields.ACTIVE, true))
								.filter(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, conceptIds))
						)
						.withPageable(PageRequest.of(0, 1))
						.addAggregation(AggregationBuilders.terms("membership").field(ReferenceSetMember.Fields.REFSET_ID).size(AGGREGATION_SEARCH_SIZE))
						.build(), ReferenceSetMember.class);
		allAggregations.add(membershipResults.getAggregations().get("membership"));

		return PageWithBucketAggregationsFactory.createPage(searchHits, new Aggregations(allAggregations), pageRequest);
	}
	
	private SearchHits<Description> findDescriptionsHelper(DescriptionCriteria criteria, PageRequest pageRequest) {
		final BoolQueryBuilder branchesQuery = getBranchesQuery().getEntityBranchCriteria(Description.class);
		final BoolQueryBuilder descriptionQuery = boolQuery()
				.must(branchesQuery);

		descriptionService.addTermClauses(criteria.getTerm(), criteria.getSearchMode(), criteria.getSearchLanguageCodes(), criteria.getType(), descriptionQuery);

		Boolean active = criteria.getActive();
		if (active != null) {
			descriptionQuery.must(termQuery(Description.Fields.ACTIVE, active));
		}

		Collection<String> modules = criteria.getModules();
		if (!CollectionUtils.isEmpty(modules)) {
			descriptionQuery.must(termsQuery(Description.Fields.MODULE_ID, modules));
		}

		if (!CollectionUtils.isEmpty(criteria.getConceptIds())) {
			descriptionQuery.must(termsQuery(Description.Fields.CONCEPT_ID, criteria.getConceptIds()));
		}

		NativeSearchQueryBuilder queryBuilder;
		// if pageRequest is null, get all (needed for bucket membership
		if (pageRequest == null) {
		  queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(descriptionQuery);
		} else {
		  queryBuilder = new NativeSearchQueryBuilder()
						.withQuery(descriptionQuery)
						.withPageable(pageRequest);
		}
		if (criteria.getConceptActive() != null) {
			Set<Long> conceptsToFetch = getMatchedConcepts(criteria.getConceptActive(), branchesQuery, descriptionQuery);
			queryBuilder.withFilter(boolQuery().must(termsQuery(Description.Fields.CONCEPT_ID, conceptsToFetch)));
		}
		NativeSearchQuery query = queryBuilder.build();
		query.setTrackTotalHits(true);
		DescriptionService.addTermSort(query);

		return elasticsearchTemplate.search(query, Description.class);
	}


	private Set<Long> getMatchedConcepts(Boolean conceptActiveFlag, BoolQueryBuilder branchesQuery, BoolQueryBuilder descriptionQuery) {
		// return description and concept ids
		Set<Long> conceptIdsMatched = new LongOpenHashSet();
		try (final SearchHitsIterator<Description> descriptions = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
				.withQuery(descriptionQuery)
				.withFields(Description.Fields.CONCEPT_ID)
				.withPageable(LARGE_PAGE).build(), Description.class)) {
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
					.withPageable(LARGE_PAGE).build(), Concept.class)) {
				while (concepts.hasNext()) {
					result.add(Long.valueOf(concepts.next().getContent().getConceptId()));
				}
			}
		}
		return result;
	}

	private MultiBranchCriteria getBranchesQuery() {
		LocalDate today = LocalDate.now();
		if (cachedBranchCriteria == null || !cacheDate.equals(today)) {
			synchronized (this) {
				// Check again, may have been populated while waiting for the lock
				if (cachedBranchCriteria == null || !cacheDate.equals(today)) {
					long startTime = System.currentTimeMillis();
					Set<String> branchPaths = getAllPublishedVersionBranchPaths();

					List<BranchCriteria> branchCriteriaList = new ArrayList<>();
					for (String branchPath : branchPaths) {
						BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
						if (!Branch.MAIN.equals(PathUtil.getParentPath(branchPath))) {
							// Prevent content on MAIN being found in every other code system
							branchCriteria.excludeContentFromPath(Branch.MAIN);
						}
						branchCriteriaList.add(branchCriteria);
					}
					Date maxTimepoint = branchCriteriaList.stream().map(BranchCriteria::getTimepoint).max(Comparator.naturalOrder()).orElseGet(Date::new);
					MultiBranchCriteria multiBranchCriteria = new MultiBranchCriteria("all-released", maxTimepoint, branchCriteriaList);
					long endTime = System.currentTimeMillis();
					logger.info("Mutisearch branches query took " + (endTime - startTime) + "ms");
					cachedBranchCriteria = multiBranchCriteria;
					cacheDate = today;
				}
			}
		}
		return cachedBranchCriteria;
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
		return publishedBranches.getOrDefault(branch, branch);
	}
	
	public Set<CodeSystemVersion> getAllPublishedVersions() {
		Set<CodeSystemVersion> codeSystemVersions = new HashSet<>();
		for (CodeSystem codeSystem : codeSystemService.findAll()) {
			List<CodeSystemVersion> thisCodeSystemVersions = codeSystemService.findAllVersions(codeSystem.getShortName(), true, false);
			thisCodeSystemVersions.forEach(csv -> csv.setCodeSystem(codeSystem));
			codeSystemVersions.addAll(thisCodeSystemVersions);
		}
		return codeSystemVersions;
	}

	public Page<Concept> findConcepts(ConceptCriteria criteria, PageRequest pageRequest) {
		final BoolQueryBuilder conceptQuery = boolQuery().must(getBranchesQuery().getEntityBranchCriteria(Description.class));
		conceptService.addClauses(criteria.getConceptIds(), criteria.getActive(), conceptQuery);
		NativeSearchQuery query = new NativeSearchQueryBuilder()
				.withQuery(conceptQuery)
				.withPageable(pageRequest)
				.build();
		SearchHits<Concept> searchHits = elasticsearchTemplate.search(query, Concept.class);
		//Populate the published version path back in
		List<Concept> concepts = searchHits.get()
				.map(SearchHit::getContent)
				.peek(c -> c.setPath(getPublishedVersionOfBranch(c.getPath())))
				.collect(Collectors.toList());
		return new PageImpl<>(concepts, pageRequest, searchHits.getTotalHits());
	}

	@Override
	public void preCommitCompletion(Commit commit) throws IllegalStateException {
		if (cachedBranchCriteria != null) {
			if (BranchMetadataHelper.isCreatingCodeSystemVersion(commit)) {
				cachedBranchCriteria = null;
			} else {
				for (BranchCriteria branchCriterion : cachedBranchCriteria.getBranchCriteria()) {
					String branchPath = branchCriterion.getBranchPath();
					if (branchPath.equals(commit.getBranch().getPath())) {
						// Commit made on branch in cached criteria - clear criteria cache
						cachedBranchCriteria = null;
					}
				}
			}
		}
	}
}
