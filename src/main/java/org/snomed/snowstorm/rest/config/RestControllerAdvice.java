package org.snomed.snowstorm.rest.config;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import io.kaicode.elasticvc.api.BranchNotFoundException;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.langauges.ecl.ECLException;
import org.snomed.snowstorm.core.data.services.NotFoundException;
import org.snomed.snowstorm.core.data.services.TooCostlyException;
import org.snomed.snowstorm.core.data.services.postcoordination.TransformationException;
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class RestControllerAdvice {

	private static final Logger logger = LoggerFactory.getLogger(RestControllerAdvice.class);

	@ExceptionHandler({
			IllegalArgumentException.class,
			IllegalStateException.class,
			HttpRequestMethodNotSupportedException.class,
			HttpMediaTypeNotSupportedException.class,
			MethodArgumentNotValidException.class,
			MethodArgumentTypeMismatchException.class,
			MissingServletRequestParameterException.class,
			ECLException.class,
			TransformationException.class,
			HttpMessageNotReadableException.class
	})
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ResponseBody
	public Map<String, Object> handleIllegalArgumentException(Exception exception) {
		HashMap<String, Object> result = new HashMap<>();
		result.put("error", HttpStatus.BAD_REQUEST);
		result.put("message", exception.getMessage());
		if (exception.getCause() != null) {
			result.put("causeMessage", exception.getCause().getMessage());
		}
		logger.info("bad request {}", exception.getMessage());
		logger.debug("bad request {}", exception.getMessage(), exception);
		return result;
	}

	@ExceptionHandler(TooCostlyException.class)
	@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
	@ResponseBody
	public Map<String,Object> handleTooExpensiveException(TooCostlyException exception) {
		HashMap<String, Object> result = new HashMap<>();
		result.put("error", HttpStatus.UNPROCESSABLE_ENTITY);
		result.put("message", exception.getMessage());
		logger.info("Too Costly request {}", exception.getMessage());
		logger.debug("Too Costly request {}", exception.getMessage(), exception);
		return result;
	}

	@ExceptionHandler({BranchNotFoundException.class, NotFoundException.class, NoResourceFoundException.class})
	@ResponseStatus(HttpStatus.NOT_FOUND)
	@ResponseBody
	public Map<String,Object> handleNotFoundException(Exception exception) {
		HashMap<String, Object> result = new HashMap<>();
		result.put("error", HttpStatus.NOT_FOUND);
		result.put("message", exception.getMessage());
		logger.debug("Not Found {}", exception.getMessage(), exception);
		return result;
	}

	@ExceptionHandler(AccessDeniedException.class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	@ResponseBody
	public Map<String,Object> handleAccessDeniedException(AccessDeniedException exception) {
		HashMap<String, Object> result = new HashMap<>();
		result.put("error", HttpStatus.FORBIDDEN);
		result.put("message", exception.getMessage());
		return result;
	}

	@ExceptionHandler(ClientAbortException.class)
	@ResponseStatus(HttpStatus.OK)
	@ResponseBody
	public void handleClientAbortException(Exception exception) {
		logger.info("A client aborted an HTTP connection, probably a page refresh during loading.");
		logger.debug("ClientAbortException.", exception);
	}

	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ResponseBody
	public Map<String,Object> handleException(Exception exception) {
		logger.error(exception.getMessage(), exception);
		HashMap<String, Object> result = new HashMap<>();
		result.put("error", HttpStatus.INTERNAL_SERVER_ERROR);
		result.put("message", exception.getMessage());
		return result;
	}

	@ExceptionHandler({UncategorizedElasticsearchException.class, ElasticsearchException.class})
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ResponseBody
	public Map<String,Object> handleElasticsearchException(Exception exception) {
		logger.error(exception.getMessage(), exception);
		HashMap<String, Object> result = new HashMap<>();
		result.put("error", HttpStatus.INTERNAL_SERVER_ERROR);
		result.put("message", exception.getMessage());
		if (exception instanceof UncategorizedElasticsearchException uncategorizedElasticsearchException) {
			// Get root cause from UncategorizedElasticsearchException
			 Throwable rootCause = uncategorizedElasticsearchException.getRootCause();
			 if (rootCause instanceof ElasticsearchException elasticsearchException) {
				 ErrorResponse errorResponse = elasticsearchException.response();
				 result.put("message", errorResponse.toString());
				 logger.error("Root cause message: {}", errorResponse);
			 } else if (rootCause != null) {
				 result.put("message", rootCause.getMessage());
				 logger.error("Root cause message: {}", rootCause.getMessage(), rootCause);
			 }
		} else if (exception instanceof ElasticsearchException elasticsearchException) {
			// Get ErrorResponse from ElasticsearchException
			ErrorResponse errorResponse = elasticsearchException.response();
			result.put("message", errorResponse.toString());
			logger.error("Error response message: {}", errorResponse);
		}
		return result;
	}
}
