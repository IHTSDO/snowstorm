package org.snomed.snowstorm.rest.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Generic interceptor for logging HTTP requests including endpoint and user information.
 * Can be configured to intercept specific path patterns.
 */
public class RequestLoggingInterceptor implements HandlerInterceptor {

	private static final Logger logger = LoggerFactory.getLogger(RequestLoggingInterceptor.class);

	private final String logPrefix;

	/**
	 * Creates a request logging interceptor with a default prefix.
	 */
	public RequestLoggingInterceptor() {
		this("Request");
	}

	/**
	 * Creates a request logging interceptor with a custom prefix.
	 * 
	 * @param logPrefix The prefix to use in log messages (e.g., "Admin request", "API request")
	 */
	public RequestLoggingInterceptor(String logPrefix) {
		this.logPrefix = logPrefix;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String username = SecurityUtil.getUsername();
		if (username == null) {
			username = "anonymous";
		}
		String method = request.getMethod();
		String endpoint = request.getRequestURI();
		String fullEndpoint = method + " " + endpoint;
		logger.info("{} - Endpoint: {}, User: {}", logPrefix, fullEndpoint, username);
		return true;
	}
}

