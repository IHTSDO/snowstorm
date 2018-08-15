package org.snomed.snowstorm.rest.security;

import org.elasticsearch.common.Strings;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RequestHeaderAuthenticationDecoratorWithRequiredRole extends OncePerRequestFilter {

	private String requiredRole;

	private final Logger logger = LoggerFactory.getLogger(getClass());
	private final List<String> excludedPaths;

	public RequestHeaderAuthenticationDecoratorWithRequiredRole(String requiredRole) {
		this.requiredRole = requiredRole;
		excludedPaths = new ArrayList<>();
	}

	public RequestHeaderAuthenticationDecoratorWithRequiredRole addExcludedPath(String pathPrefix) {
		excludedPaths.add(pathPrefix);
		return this;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		if (Strings.isNullOrEmpty(requiredRole) || isPathExcluded(request)) {
			filterChain.doFilter(request, response);
			return;
		}
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof PreAuthenticatedAuthenticationToken) {
			// IMS filter in use
			if (authentication == null) {
				accessDenied("Please log in.", response);
				return;
			}
			List<String> roles = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList());
			if (roles.contains(requiredRole)) {
				filterChain.doFilter(request, response);
				return;
			}
			logger.info("User does not have permission. username:{} - roles:{}", SecurityUtil.getUsername(), roles);
			accessDenied("The current user does not have permission to access this resource.", response);
		} else {
			// IMS not in use - just allow through
			logger.info("Granting direct access to {}. Although IMS security is configured, no headers were provided.", request.getServletPath());
			filterChain.doFilter(request, response);
		}
	}

	private boolean isPathExcluded(HttpServletRequest request) {
		String requestURI = request.getRequestURI();
		String contextPath = request.getContextPath();
		if (contextPath != null) {
			requestURI = requestURI.substring(contextPath.length());
		}
		if (requestURI != null) {
			for (String excludedPath : excludedPaths) {
				if (requestURI.startsWith(excludedPath)) {
					return true;
				}
			}
		}
		return false;
	}

	private void accessDenied(String msg, HttpServletResponse response) throws IOException {
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.getWriter().println(msg);
	}

}
