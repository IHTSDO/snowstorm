package org.snomed.snowstorm.core.data.services;

import co.elastic.clients.elasticsearch._types.aggregations.AggregationBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.kaicode.elasticvc.api.*;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.pojo.ConceptCriteria;
import org.snomed.snowstorm.core.data.services.pojo.MultiSearchDescriptionCriteria;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregations;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregationsFactory;
import org.snomed.snowstorm.ecl.ECLQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.*;
import static io.kaicode.elasticvc.helper.QueryHelper.*;

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
	private ElasticsearchOperations elasticsearchOperations;

	@Value("${search.refset.aggregation.size}")
	private int refsetAggregationSearchSize;
	
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	private final Map<String, String> publishedBranches = new HashMap<>();

	private MultiBranchCriteria cachedBranchCriteria = null;
	private LocalDate cacheDate = null;

	public Page<Description> findDescriptions(MultiSearchDescriptionCriteria criteria, PageRequest pageRequest) {

		MultiBranchCriteria cachedBranchesQuery = getBranchesQuery();
		
		MultiBranchCriteria branchesQuery = new MultiBranchCriteria("all-released-plus-requested",cachedBranchesQuery.getTimepoint(),new ArrayList<>(cachedBranchesQuery.getBranchCriteria()));
		if (criteria.getIncludeBranches() != null && criteria.getIncludeBranches().getBranches() != null) {
			// Check branches passed in by user
			// Only add if not already present in the branches returned by the query
			final List<BranchCriteria> branchesQueryBranchCriteria = branchesQuery.getBranchCriteria();
			for(String includedBranch : criteria.getIncludeBranches().getBranches()) {
				boolean branchAlreadyPresent = false;
				for(BranchCriteria branchesQueryBranchCriterion : branchesQueryBranchCriteria) {
					if(branchesQueryBranchCriterion.getBranchPath().equals(includedBranch)) {
						branchAlreadyPresent = true;
						break;
					}
				}
				if(!branchAlreadyPresent) {
					branchesQueryBranchCriteria.add(versionControlHelper.getBranchCriteria(includedBranch));
				}
			}
		}

		if (criteria.getEcl() != null) {
			Set<Long> conceptIds = new HashSet<>();
			for(BranchCriteria branchCriteria : branchesQuery.getBranchCriteria())
			{
				conceptIds.addAll(eclQueryService.selectConceptIds(criteria.getEcl(), branchCriteria, true, null, null).getContent());
			}
			criteria.conceptIds(conceptIds);
		}

		SearchHits<Description> searchHits = findDescriptionsHelper(criteria, pageRequest, branchesQuery);
		return new PageImpl<>(searchHits.get().map(SearchHit::getContent).collect(Collectors.toList()), pageRequest, searchHits.getTotalHits());
	}
	
	public PageWithBucketAggregations<Description> findDescriptionsReferenceSets(MultiSearchDescriptionCriteria criteria, PageRequest pageRequest) {

		// all search results are required to determine total refset bucket membership
		SearchHits<Description> allSearchHits = findDescriptionsHelper(criteria, null, getBranchesQuery());
		// paged results are required for the list of descriptions returned
		SearchHits<Description> searchHits = findDescriptionsHelper(criteria, pageRequest, getBranchesQuery());
		
		Set<Long> conceptIds = new HashSet<>();
		for (SearchHit<Description> desc : allSearchHits) {
			conceptIds.add(Long.parseLong(desc.getContent().getConceptId()));
		}
		// Fetch concept refset membership aggregation
		SearchHits<ReferenceSetMember> membershipResults = elasticsearchOperations.search(new NativeQueryBuilder()
						.withQuery(bool(b -> b
								.must(termQuery(ReferenceSetMember.Fields.ACTIVE, true))
								.filter(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, conceptIds)))
						)
						.withPageable(PageRequest.of(0, 1))
						.withAggregation("membership", AggregationBuilders.terms().field(ReferenceSetMember.Fields.REFSET_ID).size(refsetAggregationSearchSize).build()._toAggregation())
						.build(), ReferenceSetMember.class);
		return PageWithBucketAggregationsFactory.createPage(searchHits, membershipResults.getAggregations(), pageRequest);
	}
	
	private SearchHits<Description> findDescriptionsHelper(MultiSearchDescriptionCriteria criteria, PageRequest pageRequest, MultiBranchCriteria branchCriteria) {
		final Query branchesQuery = branchCriteria.getEntityBranchCriteria(Description.class);
		final BoolQuery.Builder descriptionQueryBuilder = bool().must(branchesQuery);

		descriptionService.addTermClauses(criteria.getTerm(), criteria.getSearchMode(), criteria.getSearchLanguageCodes(), criteria.getType(), descriptionQueryBuilder);

		Boolean active = criteria.getActive();
		if (active != null) {
			descriptionQueryBuilder.must(termQuery(Description.Fields.ACTIVE, active));
		}

		Collection<String> modules = criteria.getModules();
		if (!CollectionUtils.isEmpty(modules)) {
			descriptionQueryBuilder.must(termsQuery(Description.Fields.MODULE_ID, modules));
		}
		
		if (!CollectionUtils.isEmpty(criteria.getConceptIds())) {
			descriptionQueryBuilder.must(termsQuery(Description.Fields.CONCEPT_ID, criteria.getConceptIds()));
		}

		NativeQueryBuilder queryBuilder;
		// if pageRequest is null, get all (needed for bucket membership
		Query descriptionQuery = descriptionQueryBuilder.build()._toQuery();
		if (pageRequest == null) {
		  queryBuilder = new NativeQueryBuilder()
				.withQuery(descriptionQuery);
		} else {
		  queryBuilder = new NativeQueryBuilder()
						.withQuery(descriptionQuery)
						.withPageable(pageRequest);
		}
		if (criteria.getConceptActive() != null) {
			Set<Long> conceptsToFetch = getMatchedConcepts(criteria.getConceptActive(), branchesQuery, descriptionQuery);
			queryBuilder.withFilter(bool(b -> b.must(termsQuery(Description.Fields.CONCEPT_ID, conceptsToFetch))));
		}
		NativeQuery query = queryBuilder.build();
		query.setTrackTotalHits(true);
		DescriptionService.addTermSort(query);

		return elasticsearchOperations.search(query, Description.class);
	}


	private Set<Long> getMatchedConcepts(Boolean conceptActiveFlag, Query branchesQuery, Query descriptionQuery) {
		// return description and concept ids
		Set<Long> conceptIdsMatched = new LongOpenHashSet();
		try (final SearchHitsIterator<Description> descriptions = elasticsearchOperations.searchForStream(new NativeQueryBuilder()
				.withQuery(descriptionQuery)
				.withSourceFilter(new FetchSourceFilter(new String[]{Description.Fields.CONCEPT_ID}, null))
				.withPageable(LARGE_PAGE).build(), Description.class)) {
			while (descriptions.hasNext()) {
				conceptIdsMatched.add(Long.valueOf(descriptions.next().getContent().getConceptId()));
		}
		}
		// filter description ids based on concept query results using active flag
		Set<Long> result = new LongOpenHashSet();
		if (!conceptIdsMatched.isEmpty()) {
			try (final SearchHitsIterator<Concept> concepts = elasticsearchOperations.searchForStream(new NativeQueryBuilder()
					.withQuery(bool(b -> b
							.must(branchesQuery)
							.must(termsQuery(Concept.Fields.CONCEPT_ID, conceptIdsMatched)))
					)
					.withFilter(bool(b ->b.must(termQuery(Concept.Fields.ACTIVE, conceptActiveFlag))))
					.withSourceFilter(new FetchSourceFilter(new String[]{Concept.Fields.CONCEPT_ID}, null))
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

	public CodeSystemVersion getNearestPublishedVersion(String branchPath) {
		CodeSystem closestCodeSystem = codeSystemService.findClosestCodeSystemUsingAnyBranch(branchPath, true);
		CodeSystemVersion latestVisibleVersion = codeSystemService.findLatestVisibleVersion(closestCodeSystem.getShortName());
		if (latestVisibleVersion == null) {
			return null;
		}
		latestVisibleVersion.setCodeSystem(closestCodeSystem);
		return latestVisibleVersion;
	}

	public Page<Concept> findConcepts(ConceptCriteria criteria, PageRequest pageRequest) {
		final BoolQuery.Builder conceptQuery = bool().must(getBranchesQuery().getEntityBranchCriteria(Description.class));
		conceptService.addClauses(criteria.getConceptIds(), criteria.getActive(), conceptQuery);
		NativeQuery query = new NativeQueryBuilder()
				.withQuery(conceptQuery.build()._toQuery())
				.withPageable(pageRequest)
				.build();
		SearchHits<Concept> searchHits = elasticsearchOperations.search(query, Concept.class);
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
						break;
					}
				}
			}
		}
	}
}
