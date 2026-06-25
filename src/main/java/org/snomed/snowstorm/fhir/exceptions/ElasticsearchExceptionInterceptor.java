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
		// 4xx responses are expected application behaviour — don't log as errors
		if (exception instanceof BaseServerResponseException bsre && bsre.getStatusCode() < 500) {
			return serverException != null ? serverException : bsre;
		}
		if (exception.getMessage() != null && !exception.getMessage().contains("Supplement") && !exception.getMessage().contains("does not exist")) {
			//Is this a broken or bad test case?
			if (isDeliberatelyBrokenTestCase(requestDetails)) {
				//expected exception - deliberately broken test case
			} else {
                logger.error(exception.getMessage(), exception);
			}
		}
		return serverException;
	}

	private boolean isDeliberatelyBrokenTestCase(RequestDetails requestDetails) {
		return requestDetails != null && requestDetails.getUserData().values().stream()
				.anyMatch(this::hasBrokenOrBadUrl);
	}

	private boolean hasBrokenOrBadUrl(Object p) {
		if (!(p instanceof Parameters params)) {
			return false;
		}
		var urlParameter = params.getParameter("url");
		if (urlParameter == null || urlParameter.getValue() == null) {
			return false;
		}
		String url = urlParameter.getValue().toString();
		return url.contains("broken") || url.contains("bad");
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
}

