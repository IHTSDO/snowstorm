package org.snomed.snowstorm.fhir.services;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import io.kaicode.elasticvc.api.VersionControlHelper;

import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.domain.FHIRConcept;
import org.snomed.snowstorm.fhir.domain.FHIRGraphNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool;
import static io.kaicode.elasticvc.helper.QueryHelper.termQuery;

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
	private ElasticsearchOperations elasticsearchOperations;

	/**
	 * Returns true if codeA is an ancestor of codeB
	 */
	public boolean subsumes(String codeA, String codeB, FHIRCodeSystemVersion codeSystemVersion) {
		GraphCriteria graphCriteria = getGraphCriteria(codeSystemVersion, PageRequest.of(0, 1));
		graphCriteria.criteria()
				.must(termQuery(graphCriteria.getCodeField(), codeB))
				.must(termQuery(ANCESTORS, codeA));

		return elasticsearchOperations.search(graphCriteria.getQuery(), graphCriteria.nodeClass()).hasSearchHits();
	}

	public List<String> findChildren(String code, FHIRCodeSystemVersion codeSystemVersion, PageRequest page) {
		GraphCriteria graphCriteria = getGraphCriteria(codeSystemVersion, page);
		graphCriteria.criteria()
				.must(termQuery(PARENTS, code));

		return elasticsearchOperations.search(graphCriteria.getQuery(), graphCriteria.nodeClass())
				.get().map(hit -> hit.getContent().getCode()).collect(Collectors.toList());
	}

	private GraphCriteria getGraphCriteria(FHIRCodeSystemVersion codeSystemVersion, PageRequest page) {
		if (codeSystemVersion.isSnomed()) {
			BoolQuery.Builder criteria = bool().must(snomedVersionControlHelper.getBranchCriteria(codeSystemVersion.getSnomedBranch()).getEntityBranchCriteria(QueryConcept.class));
			criteria.must(termQuery(QueryConcept.Fields.STATED, false));
			return new GraphCriteria(QueryConcept.class, criteria, page);
		} else {
			BoolQuery.Builder criteria = bool();
			criteria.must(termQuery("codeSystemVersion", codeSystemVersion.getId()));
			return new GraphCriteria(FHIRConcept.class, criteria, page);
		}
	}

	private record GraphCriteria(Class<? extends FHIRGraphNode> nodeClass, BoolQuery.Builder criteria, PageRequest page) {


		public String getCodeField() {
				try {
					return nodeClass.getDeclaredConstructor().newInstance().getCodeField();
				} catch (ReflectiveOperationException e) {
					throw new RuntimeException(e);
				}
			}


		public NativeQuery getQuery() {
				NativeQueryBuilder searchQueryBuilder = new NativeQueryBuilder();
				searchQueryBuilder.withQuery(criteria().build()._toQuery());
				searchQueryBuilder.withPageable(page);
				return searchQueryBuilder.build();
			}
		}

}
