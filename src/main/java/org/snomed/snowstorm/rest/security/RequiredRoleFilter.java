package org.snomed.snowstorm.rest.security;

import org.elasticsearch.common.Strings;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RequiredRoleFilter extends OncePerRequestFilter {

	private final String requiredRole;

	private final Set<String> excludedUrlPatterns = new HashSet<>();

	private final static Logger LOGGER = LoggerFactory.getLogger(RequiredRoleFilter.class);

	public RequiredRoleFilter(String requiredRole) {
		this(requiredRole, new String[]{});
	}

	public RequiredRoleFilter(String requiredRole, String... excludedUrlPatterns) {
		this.requiredRole = requiredRole;
		if (excludedUrlPatterns != null) {
			Collections.addAll(this.excludedUrlPatterns, excludedUrlPatterns);
		}
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		Authentication authentication = SecurityUtil.getAuthentication();
		return Strings.isNullOrEmpty(requiredRole) || isPathExcluded(request) || !(authentication instanceof PreAuthenticatedAuthenticationToken);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		LOGGER.debug("RequiredRoleFilter: requiredRole: {}", requiredRole);
		Authentication authentication = SecurityUtil.getAuthentication();

		List<String> roles = authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.collect(Collectors.toList());

		if (!roles.contains(requiredRole)) {
			LOGGER.info("User '{}' with roles '{}' does not have permission to access this resource: {}", SecurityUtil.getUsername(), roles, request.getRequestURI());
			throw new AccessDeniedException("The current user does not have permission to access this resource");
		}
		filterChain.doFilter(request, response);
	}

	private boolean isPathExcluded(HttpServletRequest request) {
		String requestURI = request.getRequestURI();
		String contextPath = request.getContextPath();
		if (requestURI != null) {
			if (requestURI.startsWith(contextPath)) {
				requestURI = requestURI.substring(contextPath.length());
			}
			AntPathMatcher antPathMatcher = new AntPathMatcher();
			for (String excludedUrlPattern : excludedUrlPatterns) {
				if (antPathMatcher.match(excludedUrlPattern, requestURI)) {
					LOGGER.debug("RequiredRoleFilter: path {} is excluded", requestURI);
					return true;
				}
			}
		}
		LOGGER.debug("RequiredRoleFilter: path {} is not excluded", requestURI);
		return false;
	}
}
