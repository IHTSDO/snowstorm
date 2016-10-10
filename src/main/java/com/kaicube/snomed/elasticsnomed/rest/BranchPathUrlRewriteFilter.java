package com.kaicube.snomed.elasticsnomed.rest;

import org.apache.catalina.comet.CometEvent;
import org.apache.catalina.comet.CometFilterChain;
import org.apache.catalina.filters.RequestFilter;
import org.apache.juli.logging.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

public class BranchPathUrlRewriteFilter extends RequestFilter {

	private final List<Pattern> patterns;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public BranchPathUrlRewriteFilter(List<String> patternStrings) {
		patterns = new ArrayList<>();
		patternStrings.forEach(pattern -> patterns.add(Pattern.compile(pattern)));
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		if (!"true".equals(servletRequest.getAttribute("branch-path-filtered"))) {
			String rewrittenRequestURI = rewriteUri(request.getRequestURI());
			if (rewrittenRequestURI != null) {
				logger.info("finalRequestURI {}", rewrittenRequestURI);
				servletRequest = new HttpServletRequestWrapper(request) {
					@Override
					public String getRequestURI() {
						return rewrittenRequestURI;
					}
				};
				servletRequest.setAttribute("branch-path-filtered", "true");
				servletRequest.getRequestDispatcher(rewrittenRequestURI).forward(servletRequest, servletResponse);
				return;
			}
		}
		filterChain.doFilter(servletRequest, servletResponse);
	}

	protected String rewriteUri(String requestURI) {
		if (requestURI != null) {
			for (Pattern pattern : patterns) {
				final Matcher matcher = pattern.matcher(requestURI);
				if (matcher.matches()) {
					logger.info("requestURI {} matches pattern {}", requestURI, pattern);
					final String path = matcher.group(1);
					return requestURI.replace(path, path.replace("/", "|").replace("%2F", "|"));
				}
			}
		}
		return null;
	}

	private String getDelimiterMatch(String requestURI, Set<String> delimiters) {
		if (requestURI != null) {
			for (String delimiter : delimiters) {
				if (requestURI.contains(delimiter)) {
					return delimiter;
				}
			}
		}
		return null;
	}

	@Override
	public void doFilterEvent(CometEvent cometEvent, CometFilterChain cometFilterChain) throws IOException, ServletException {
	}

	@Override
	protected Log getLogger() {
		return null;
	}
}
