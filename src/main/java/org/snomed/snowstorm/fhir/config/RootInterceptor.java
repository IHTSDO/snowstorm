package org.snomed.snowstorm.fhir.config;

import ca.uhn.fhir.context.ConfigurationException;
import ca.uhn.fhir.rest.server.interceptor.InterceptorAdapter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;

public class RootInterceptor extends InterceptorAdapter {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private static final String FHIR_RESOURCE_ROOT = "/fhir";

	/**
	 * Override the incomingRequestPreProcessed method, which is called
	 * for each incoming request before any processing is done
	 */
	@Override
	public boolean incomingRequestPreProcessed(HttpServletRequest request, HttpServletResponse response) {
		try {
			String pathInfo = request.getPathInfo();

			// The base URL will return a static HTML page
			if (!"POST".equals(request.getMethod()) && (StringUtils.isEmpty(pathInfo) || pathInfo.equals("/"))) {
				response.setContentType("text/html; charset=UTF-8");
				try (InputStream ios = getClass().getResourceAsStream(FHIR_RESOURCE_ROOT + "/index.html")) {
					if (ios == null) {
						throw new ConfigurationException("Did not find internal resource file fhir/index.html");
					}
					IOUtils.copy(ios, response.getOutputStream());
				}
				return false;
			}

			if (serveDashboardStaticIfApplicable(request, response, pathInfo)) {
				return false;
			}
		} catch (Exception e) {
			logger.error("Failed to intercept request", e);
		}
		return true;
	}

	/**
	 * Dashboard assets live under classpath:/fhir/{css,js,images}/ and are requested as /fhir/css/..., etc.
	 * (The FHIR servlet is mapped to /fhir/* so Spring's static handler never sees these paths.)
	 */
	private boolean serveDashboardStaticIfApplicable(HttpServletRequest request, HttpServletResponse response, String pathInfo)
			throws IOException {
		if (!pathInfo.startsWith("/css/") && !pathInfo.startsWith("/js/") && !pathInfo.startsWith("/images/")) {
			return false;
		}
		if (pathInfo.contains("..")) {
			return false;
		}
		String method = request.getMethod();
		if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
			return false;
		}

		String classpathLocation = FHIR_RESOURCE_ROOT + pathInfo;
		try (InputStream ios = getClass().getResourceAsStream(classpathLocation)) {
			if (ios == null) {
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
				return true;
			}

			String contentType = contentTypeForPath(pathInfo);
			if (contentType != null) {
				response.setContentType(contentType);
			}
			if ("HEAD".equalsIgnoreCase(method)) {
				return true;
			}
			IOUtils.copy(ios, response.getOutputStream());
		}
		return true;
	}

	private static String contentTypeForPath(String pathInfo) {
		String lower = pathInfo.toLowerCase();
		if (lower.endsWith(".css")) {
			return "text/css; charset=UTF-8";
		}
		if (lower.endsWith(".js")) {
			return "text/javascript; charset=UTF-8";
		}
		if (lower.endsWith(".svg")) {
			return "image/svg+xml";
		}
		if (lower.endsWith(".ico")) {
			return "image/x-icon";
		}
		return null;
	}

}
