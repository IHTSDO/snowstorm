package org.snomed.snowstorm.rest.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.AntPathMatcher;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class ExcludedPathRequestMatcher implements RequestMatcher {

	private static final Logger LOGGER = LoggerFactory.getLogger(ExcludedPathRequestMatcher.class);

	private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

	private final List<String> excludedUrlPatterns;

	private final List<Pattern> rewrittenPathPatterns;

	public ExcludedPathRequestMatcher(String[] excludedUrlPatterns, String... rewrittenPathRegexes) {
		this.excludedUrlPatterns = List.of(excludedUrlPatterns);
		this.rewrittenPathPatterns = Arrays.stream(rewrittenPathRegexes).map(Pattern::compile).toList();
	}

	@Override
	public boolean matches(HttpServletRequest request) {
		String rawPath = getRawPathWithinApplication(request);
		String decodedPath = getDecodedPathWithinApplication(request);
		// Spring MVC routes on the raw URI but servlets such as HAPI route on the decoded path, so both must be excluded.
		// Paths the branch path rewrite filter forwards to branch controllers are never excluded.
		boolean excluded = rawPath != null && decodedPath != null
				&& isExcluded(rawPath) && isExcluded(decodedPath)
				&& rewrittenPathPatterns.stream().noneMatch(pattern -> pattern.matcher(rawPath).matches());
		LOGGER.debug("Path {} (decoded {}) excluded: {}", rawPath, decodedPath, excluded);
		return excluded;
	}

	private boolean isExcluded(String path) {
		return excludedUrlPatterns.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
	}

	private static String getRawPathWithinApplication(HttpServletRequest request) {
		String requestURI = request.getRequestURI();
		String contextPath = request.getContextPath();
		if (requestURI == null || contextPath == null || !requestURI.startsWith(contextPath)) {
			return requestURI;
		}
		return requestURI.substring(contextPath.length());
	}

	private static String getDecodedPathWithinApplication(HttpServletRequest request) {
		String servletPath = request.getServletPath();
		String pathInfo = request.getPathInfo();
		if (servletPath == null) {
			return pathInfo;
		}
		return pathInfo != null ? servletPath + pathInfo : servletPath;
	}

}
