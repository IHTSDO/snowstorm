package org.snomed.snowstorm.fhir.exceptions;

import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.snomed.snowstorm.core.util.ElasticsearchErrorTestData.*;

class ElasticsearchExceptionInterceptorTest {

	private final ElasticsearchExceptionInterceptor interceptor = new ElasticsearchExceptionInterceptor();

	@Test
	void handlesElasticsearchFailureWithRootCause() {
		InternalErrorException serverException = new InternalErrorException("wrapped");

		assertSame(serverException, interceptor.preProcessOutgoingException(null, searchPhaseFailure(), null, serverException));
	}

	@Test
	void handlesElasticsearchFailureWithoutRootCause() {
		InternalErrorException serverException = new InternalErrorException("wrapped");

		assertSame(serverException, interceptor.preProcessOutgoingException(null, failureWithoutRootCause(), null, serverException));
	}
}
