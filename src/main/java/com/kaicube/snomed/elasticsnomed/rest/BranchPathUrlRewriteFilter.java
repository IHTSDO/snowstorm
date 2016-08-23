package com.kaicube.snomed.elasticsnomed.rest;

import org.apache.catalina.comet.CometEvent;
import org.apache.catalina.comet.CometFilterChain;
import org.apache.catalina.filters.RequestFilter;
import org.apache.juli.logging.Log;
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

	public BranchPathUrlRewriteFilter(Set<String> delimiters) {
		this.delimiters = delimiters;
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		String requestURI = request.getRequestURI();
		String delimiterMatch = getDelimiterMatch(requestURI, delimiters);
		if (delimiterMatch != null && !"true".equals(servletRequest.getAttribute("branch-path-filtered"))) {
			final int i = requestURI.indexOf(delimiterMatch);
			LoggerFactory.getLogger(getClass()).info("requestURI {}", requestURI);
			final String finalRequestURI = "/" + requestURI.substring(1, i).replace("/", "|").replace("%2F", "|") + requestURI.substring(i);
			LoggerFactory.getLogger(getClass()).info("finalRequestURI {}", finalRequestURI);
			servletRequest = new HttpServletRequestWrapper(request) {
				@Override
				public String getRequestURI() {
					return finalRequestURI;
				}
			};
			servletRequest.setAttribute("branch-path-filtered", "true");
			servletRequest.getRequestDispatcher(finalRequestURI).forward(servletRequest, servletResponse);
		} else {
			filterChain.doFilter(servletRequest, servletResponse);
		}
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
