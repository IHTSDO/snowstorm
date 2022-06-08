package org.snomed.snowstorm.fhir.services;

import io.kaicode.elasticvc.api.VersionControlHelper;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.domain.FHIRConcept;
import org.snomed.snowstorm.fhir.domain.FHIRGraphNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Service
/*
 * Generic service for graph/hierarchy queries on SNOMED CT or any other FHIR Code System.
 */
public class FHIRGraphService {

	private static final String PARENTS = "parents";
	private static final String ANCESTORS = "ancestors";

	@Autowired
	private VersionControlHelper snomedVersionControlHelper;

	@Autowired
	private ElasticsearchRestTemplate elasticsearchTemplate;

	/**
	 * Returns true if codeA is an ancestor of codeB
	 */
	public boolean subsumes(String codeA, String codeB, FHIRCodeSystemVersion codeSystemVersion) {
		GraphCriteria graphCriteria = getGraphCriteria(codeSystemVersion, PageRequest.of(0, 1));
		graphCriteria.getCriteria()
				.must(termQuery(graphCriteria.getCodeField(), codeB))
				.must(termQuery(ANCESTORS, codeA));

		return elasticsearchTemplate.search(graphCriteria.getQuery(), graphCriteria.getNodeClass()).hasSearchHits();
	}

	public List<String> findChildren(String code, FHIRCodeSystemVersion codeSystemVersion, PageRequest page) {
		GraphCriteria graphCriteria = getGraphCriteria(codeSystemVersion, page);
		graphCriteria.getCriteria()
				.must(termQuery(PARENTS, code));

		return elasticsearchTemplate.search(graphCriteria.getQuery(), graphCriteria.getNodeClass())
				.get().map(hit -> hit.getContent().getCode()).collect(Collectors.toList());
	}

	private GraphCriteria getGraphCriteria(FHIRCodeSystemVersion codeSystemVersion, PageRequest page) {
		if (codeSystemVersion.isSnomed()) {
			BoolQueryBuilder criteria = snomedVersionControlHelper.getBranchCriteria(codeSystemVersion.getSnomedBranch()).getEntityBranchCriteria(QueryConcept.class);
			criteria.must(termQuery(QueryConcept.Fields.STATED, false));
			return new GraphCriteria(QueryConcept.class, criteria, page);
		} else {
			BoolQueryBuilder criteria = boolQuery();
			criteria.must(termQuery("codeSystemVersion", codeSystemVersion.getIdAndVersion()));
			return new GraphCriteria(FHIRConcept.class, criteria, page);
		}
	}

	private static class GraphCriteria {

		private final Class<? extends FHIRGraphNode> nodeClass;
		private final BoolQueryBuilder criteria;
		private final PageRequest page;

		public GraphCriteria(Class<? extends FHIRGraphNode> nodeClass, BoolQueryBuilder criteria, PageRequest page) {
			this.nodeClass = nodeClass;
			this.criteria = criteria;
			this.page = page;
		}

		public Class<? extends FHIRGraphNode> getNodeClass() {
			return nodeClass;
		}

		public String getCodeField() {
			try {
				return nodeClass.getDeclaredConstructor().newInstance().getCodeField();
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		}

		public BoolQueryBuilder getCriteria() {
			return criteria;
		}

		public NativeSearchQuery getQuery() {
			NativeSearchQueryBuilder searchQueryBuilder = new NativeSearchQueryBuilder();
			searchQueryBuilder.withQuery(getCriteria());
			searchQueryBuilder.withPageable(page);
			return searchQueryBuilder.build();
		}
	}

}
