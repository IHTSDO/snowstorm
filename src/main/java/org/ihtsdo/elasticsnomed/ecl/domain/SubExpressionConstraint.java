package org.ihtsdo.elasticsnomed.ecl.domain;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.domain.QueryConcept;
import org.ihtsdo.elasticsnomed.core.data.services.QueryService;

import java.util.Set;

import static org.elasticsearch.index.query.QueryBuilders.*;

public class SubExpressionConstraint implements ExpressionConstraint {

	private final Operator operator;

	private String conceptId;
	private boolean wildcard;

	public SubExpressionConstraint(Operator operator) {
		this.operator = operator;
	}

	@Override
	public void addCriteria(BoolQueryBuilder query, String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService) {
		if (conceptId != null) {
			if (operator != null) {
				switch (operator) {
					case childof:
						query.must(termQuery(QueryConcept.PARENTS_FIELD, conceptId));
						break;
					case descendantorselfof:
						// <<
						query.must(
								boolQuery()
										.should(termQuery(QueryConcept.ANCESTORS_FIELD, conceptId))
										.should(termQuery(QueryConcept.CONCEPT_ID_FIELD, conceptId))
						);
						break;
					case descendantof:
						// <
						query.must(termQuery(QueryConcept.ANCESTORS_FIELD, conceptId));
						break;
					case parentof:
						Set<Long> parents = queryService.retrieveParents(branchCriteria, path, stated, conceptId);
						query.must(termsQuery(QueryConcept.CONCEPT_ID_FIELD, parents));
						break;
					case ancestororselfof:
						query.must(
								boolQuery()
										.should(termsQuery(QueryConcept.CONCEPT_ID_FIELD, queryService.retrieveAncestors(branchCriteria, path, stated, conceptId)))
										.should(termQuery(QueryConcept.CONCEPT_ID_FIELD, conceptId))
						);
						break;
					case ancestorof:
						// > x
						query.must(termsQuery(QueryConcept.CONCEPT_ID_FIELD, queryService.retrieveAncestors(branchCriteria, path, stated, conceptId)));
						break;
				}
			} else {
				query.must(termQuery(QueryConcept.CONCEPT_ID_FIELD, conceptId));
			}
		}
	}

	@Override
	public Set<Long> select(String path, QueryBuilder branchCriteria, boolean stated, QueryService queryService) {
		if (wildcard) return null;
		BoolQueryBuilder query = ConceptSelectorHelper.getBranchAndStatedQuery(branchCriteria, stated);
		addCriteria(query, path, branchCriteria, stated, queryService);
		// TODO: Avoid this fetch in the case that we selecting a single known concept
		return ConceptSelectorHelper.fetch(query, queryService);
	}

	public void wildcard() {
		this.wildcard = true;
	}

	public boolean isWildcard() {
		return wildcard;
	}

	public void setConceptId(String conceptId) {
		this.conceptId = conceptId;
	}

	public String getConceptId() {
		return conceptId;
	}

}
