package com.kaicube.snomed.elasticsnomed.rest;

import org.springframework.util.Assert;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;

public class ControllerHelper {

	public static String extractBranchPath(HttpServletRequest request, String end) {
		String mappingPath = (String) request.getAttribute(
				HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
		String path = mappingPath.substring(0, mappingPath.lastIndexOf(end));
		Assert.isTrue(!path.isEmpty(), "A branch path is required.");
		path = path.substring(1);
		return path;
	}

}
