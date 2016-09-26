package com.kaicube.snomed.elasticsnomed.rest;

import org.apache.catalina.comet.CometEvent;
import org.apache.catalina.comet.CometFilterChain;
import org.apache.catalina.filters.RequestFilter;
import org.apache.juli.logging.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

public class BranchPathUrlRewriteFilter extends RequestFilter {

	private final Set<String> delimiters;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public BranchPathUrlRewriteFilter(Set<String> delimiters) {
		this.delimiters = delimiters;
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

	private String rewriteUri(String requestURI) {
		if (requestURI != null) {
			for (String delimiter : delimiters) {
				final int i = requestURI.indexOf(delimiter);
				if (i == 0) {
					logger.info("requestURI {}", requestURI);
					final int end = requestURI.indexOf("/", delimiter.length()) + i;
					return delimiter + "/" + requestURI.substring(delimiter.length(), end).replace("/", "|").replace("%2F", "|") + requestURI.substring(end);
				} else if (i > 0) {
					logger.info("requestURI {}", requestURI);
					return "/" + requestURI.substring(1, i).replace("/", "|").replace("%2F", "|") + requestURI.substring(i);
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
