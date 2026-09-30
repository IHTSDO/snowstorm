package org.snomed.snowstorm.config;

import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.snomed.snowstorm.core.util.ElasticsearchErrorUtil;
import org.springframework.boot.logging.logback.ExtendedWhitespaceThrowableProxyConverter;

/**
 * Adds the Elasticsearch root causes to logged stack traces, which spring-data-elasticsearch otherwise
 * hides behind messages such as "[es/search] failed: [search_phase_execution_exception] all shards failed".
 */
public class ElasticsearchRootCauseThrowableConverter extends ExtendedWhitespaceThrowableProxyConverter {

	static final String ROOT_CAUSE_PREFIX = "Elasticsearch root cause: ";

	@Override
	protected String throwableProxyToString(IThrowableProxy throwableProxy) {
		String stackTrace = super.throwableProxyToString(throwableProxy);
		if (!(throwableProxy instanceof ThrowableProxy proxy)) {
			return stackTrace;
		}
		String rootCauses;
		try {
			rootCauses = ElasticsearchErrorUtil.describeRootCauses(proxy.getThrowable());
		} catch (RuntimeException e) {
			// A failure here would lose the original log event, so fall back to the plain stack trace.
			return stackTrace;
		}
		if (rootCauses.isEmpty()) {
			return stackTrace;
		}
		StringBuilder builder = new StringBuilder(System.lineSeparator());
		rootCauses.lines().forEach(line -> builder.append(ROOT_CAUSE_PREFIX).append(line).append(System.lineSeparator()));
		return builder.append(stackTrace.stripLeading()).toString();
	}
}
