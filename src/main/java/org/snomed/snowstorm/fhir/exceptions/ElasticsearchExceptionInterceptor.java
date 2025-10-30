package org.snomed.snowstorm.fhir.exceptions;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import org.hl7.fhir.instance.model.api.IBaseOperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException;

@Interceptor
public class ElasticsearchExceptionInterceptor {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Hook(Pointcut.SERVER_PRE_PROCESS_OUTGOING_EXCEPTION)
	public BaseServerResponseException preProcessOutgoingException(RequestDetails requestDetails,
	                                        Throwable exception,
	                                        IBaseOperationOutcome operationOutcome,
	                                        BaseServerResponseException serverException) {
		// exception = the original Throwable
		// operationOutcome = the OperationOutcome HAPI is about to send
		// serverException = the HAPI-wrapped exception (e.g. InternalErrorException)
		logRootCauseIfElastic(exception);
		if (!exception.getMessage().contains("Supplement") && ! exception.getMessage().contains("does not exist")) {
			//Is this a broken or bad test case?
			if (requestDetails != null && requestDetails.getUserData().values().stream()
								.anyMatch(p -> p instanceof Parameters params &&
										(params.getParameter("url").getValue().toString().contains("broken")
								|| params.getParameter("url").getValue().toString().contains("bad")) )) {
				//expected exception - deliberately broken test case
			} else {
				logger.info("Check unexpected exception");
			}
		}
		return serverException;
	}

	private void logRootCauseIfElastic(Throwable exception) {
		if (exception instanceof UncategorizedElasticsearchException uncategorizedElasticsearchException) {
			Throwable rootCause = uncategorizedElasticsearchException.getRootCause();
			if (rootCause != null && rootCause instanceof ElasticsearchException esException) {
				ErrorCause rootErrorCause = esException.response().error().rootCause().get(0);
				logger.error("Elasticsearch error root cause: {}", rootErrorCause);
			}
		} else if (exception.getCause() != null) {
			logRootCauseIfElastic(exception.getCause());
		}
	}

/*	@Hook(Pointcut.SERVER_PRE_PROCESS_OUTGOING_EXCEPTION)
	public boolean handleException(RequestDetails requestDetails, Throwable exception) {
		if (exception == null) {
			return true;
		}

		Throwable root = getRootCause(exception);

		if (root instanceof UncategorizedElasticsearchException) {
			// This is the raw Spring Data ES wrapper
			UncategorizedElasticsearchException esEx = (UncategorizedElasticsearchException) root;
			// Log full cause chain or extract details
			logger.error("Elasticsearch error: {}", esEx.getMessage(), esEx);
		}

		if (root instanceof co.elastic.clients.elasticsearch._types.ElasticsearchException) {
			co.elastic.clients.elasticsearch._types.ElasticsearchException esEx =
					(co.elastic.clients.elasticsearch._types.ElasticsearchException) root;
			logger.error("ES API error [{}]: {}", esEx.error().type(), esEx.error().reason(), esEx);
		}

		// Return false to let HAPI continue its normal exception handling
		return false;
	}*/

	private Throwable getRootCause(Throwable ex) {
		Throwable cause = ex;
		while (cause.getCause() != null && cause.getCause() != cause) {
			cause = cause.getCause();
		}
		return cause;
	}
}

