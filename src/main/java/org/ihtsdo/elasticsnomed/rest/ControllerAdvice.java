package org.ihtsdo.elasticsnomed.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.HashMap;
import java.util.Map;

@org.springframework.web.bind.annotation.ControllerAdvice
public class ControllerAdvice {

	private static final Logger logger = LoggerFactory.getLogger(ControllerAdvice.class);

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ResponseBody
	public Map<String,Object> handleIndexNotFoundException(IllegalArgumentException exception) {
		HashMap<String, Object> result = new HashMap<>();
		result.put("error", HttpStatus.BAD_REQUEST);
		result.put("message", exception.getMessage());
		return result;
	}

	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ResponseBody
	public Map<String,Object> handleException(Exception exception) {
		logger.error("Unhandled exception.", exception);
		HashMap<String, Object> result = new HashMap<>();
		result.put("error", HttpStatus.INTERNAL_SERVER_ERROR);
		result.put("message", exception.getMessage());
		return result;
	}

}
