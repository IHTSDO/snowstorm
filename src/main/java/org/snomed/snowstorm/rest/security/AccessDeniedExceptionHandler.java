package org.snomed.snowstorm.rest.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AccessDeniedExceptionHandler implements AccessDeniedHandler {

	protected static final Logger LOGGER = LoggerFactory.getLogger(AccessDeniedExceptionHandler.class);

	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) throws IOException {
		LOGGER.error("Request '{}' raised: " + exception.getMessage(), request.getRequestURL(), exception);
		response.setStatus(HttpStatus.FORBIDDEN.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(new JSONObject(getErrorPayload(exception, HttpStatus.FORBIDDEN)).toString());
	}

	private Map<String, String> getErrorPayload(Exception exception, HttpStatus httpStatus) {
		Map<String, String> result = new HashMap<>();
		result.put("error", httpStatus.toString());
		result.put("message", exception.getMessage());
		return result;
	}

}
