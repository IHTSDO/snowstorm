package org.snomed.snowstorm.core.util;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException;

public class ElasticsearchErrorTestData {

	public static final String ROOT_CAUSE_REASON = "failed to create query: For input string: \"2024-09-01\"";

	public static final String CAUSED_BY_REASON = "maxClauseCount is set to 1024";

	private ElasticsearchErrorTestData() {
	}

	public static UncategorizedElasticsearchException searchPhaseFailure() {
		ErrorResponse response = ErrorResponse.of(r -> r
				.status(400)
				.error(e -> e
						.type("search_phase_execution_exception")
						.reason("all shards failed")
						.rootCause(rc -> rc.type("query_shard_exception").reason(ROOT_CAUSE_REASON))
						.rootCause(rc -> rc.type("query_shard_exception").reason(ROOT_CAUSE_REASON))));
		return wrap(new ElasticsearchException("es/search", response));
	}

	public static UncategorizedElasticsearchException failureWithoutRootCause() {
		ErrorResponse response = ErrorResponse.of(r -> r
				.status(500)
				.error(e -> e.type("exception").reason("something went wrong")));
		return wrap(new ElasticsearchException("es/search", response));
	}

	public static UncategorizedElasticsearchException failureWithCausedByChain() {
		ErrorResponse response = ErrorResponse.of(r -> r
				.status(400)
				.error(e -> e
						.type("illegal_argument_exception")
						.reason("failed to parse query")
						.causedBy(c1 -> c1
								.type("parse_exception")
								.reason("parse failed")
								.causedBy(c2 -> c2.type("too_many_clauses").reason(CAUSED_BY_REASON)))));
		return wrap(new ElasticsearchException("es/search", response));
	}

	private static UncategorizedElasticsearchException wrap(ElasticsearchException cause) {
		return new UncategorizedElasticsearchException(cause.getMessage(), cause.status(), null, cause);
	}
}
