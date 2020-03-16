package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
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
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

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

		NativeSearchQuery query = new NativeSearchQueryBuilder()
				.withQuery(descriptionQuery)
				.withPageable(pageRequest)
				.build();
		DescriptionService.addTermSort(query);

		return elasticsearchTemplate.queryForPage(query, Description.class);
	}

	private QueryBuilder getBranchesQuery() {
		Set<String> branchPaths = getAllPublishedVersionBranchPaths();

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

	public Page<Concept> findConcepts(ConceptCriteria criteria, PageRequest pageRequest) {
		
		final BoolQueryBuilder conceptQuery = boolQuery().must(getBranchesQuery());

		conceptService.addClauses(criteria.getConceptIds(), criteria.getActive(), conceptQuery);
		
		NativeSearchQuery query = new NativeSearchQueryBuilder()
				.withQuery(conceptQuery)
				.withPageable(pageRequest)
				.build();
		return elasticsearchTemplate.queryForPage(query, Concept.class);
	}
}
