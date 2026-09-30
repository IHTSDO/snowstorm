package org.snomed.snowstorm.core.util;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.ErrorResponse;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ElasticsearchErrorUtil {

	private ElasticsearchErrorUtil() {
	}

	public static Optional<ErrorResponse> findErrorResponse(Throwable throwable) {
		Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Throwable current = throwable; current != null && visited.add(current); current = current.getCause()) {
			if (current instanceof ElasticsearchException elasticsearchException && elasticsearchException.response() != null) {
				return Optional.of(elasticsearchException.response());
			}
		}
		return Optional.empty();
	}

	public static List<ErrorCause> getRootCauses(Throwable throwable) {
		ErrorCause error = findErrorResponse(throwable).map(ErrorResponse::error).orElse(null);
		if (error == null) {
			return Collections.emptyList();
		}
		if (!error.rootCause().isEmpty()) {
			return error.rootCause();
		}
		// Some failures carry no root_cause list, only a caused_by chain
		ErrorCause deepest = error.causedBy();
		if (deepest == null) {
			return Collections.emptyList();
		}
		Set<ErrorCause> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		while (deepest.causedBy() != null && visited.add(deepest)) {
			deepest = deepest.causedBy();
		}
		return List.of(deepest);
	}

	public static Optional<String> getFirstRootCauseReason(Throwable throwable) {
		return getRootCauses(throwable).stream()
				.map(ErrorCause::reason)
				.filter(reason -> reason != null && !reason.isBlank())
				.findFirst();
	}

	public static String describeRootCauses(Throwable throwable) {
		return getRootCauses(throwable).stream()
				.map(cause -> "[" + cause.type() + "] " + cause.reason())
				.distinct()
				.collect(Collectors.joining(System.lineSeparator()));
	}
}
