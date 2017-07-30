package org.ihtsdo.elasticsnomed.ecl.domain;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.domain.QueryConcept;
import org.ihtsdo.elasticsnomed.core.data.services.QueryService;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;

import java.util.Collection;
import java.util.List;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static org.elasticsearch.index.query.QueryBuilders.*;

public class SubExpressionConstraint implements ConceptSelector {

	private final Operator operator;
	private final boolean memberOf;

	private String conceptId;
	private boolean wildcard;

	private ExpressionConstraint expressionConstraint;

	public SubExpressionConstraint(Operator operator, boolean memberOf) {
		this.operator = operator;
		this.memberOf = memberOf;
	}

	@Override
	public Collection<Long> select(QueryBuilder branchCriteria, QueryService queryService, String path, boolean stated) {

		if (wildcard) return null;

//		TODO: Deal with sub expression
//		Collection<Long> conceptIds;
//		if (expressionConstraint != null) {
//			conceptIds = expressionConstraint.select(branchCriteria, queryService, path);
//		}

		BoolQueryBuilder query = boolQuery()
				.must(branchCriteria)
				.must(termQuery(QueryConcept.STATED_FIELD, stated));

		if (conceptId != null) {
			if (operator != null) {
				switch (operator) {
					case childof:
						query.must(termQuery(QueryConcept.PARENTS_FIELD, conceptId));
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
						return queryService.retrieveParents(branchCriteria, path, stated, conceptId);
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

		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(query)
				.withPageable(LARGE_PAGE)
				.build();

		List<Long> ids = new LongArrayList();
		try (CloseableIterator<QueryConcept> stream = queryService.stream(searchQuery)) {
			stream.forEachRemaining(c -> ids.add(c.getConceptId()));
		}
		return ids;
	}

	public void wildcard() {
		this.wildcard = true;
	}

	public void setConceptId(String conceptId) {
		this.conceptId = conceptId;
	}

	public String getConceptId() {
		return conceptId;
	}

	public void setExpressionConstraint(ExpressionConstraint expressionConstraint) {
		this.expressionConstraint = expressionConstraint;
	}

}
