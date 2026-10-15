package org.snomed.snowstorm.rest.security;

import com.google.common.base.Strings;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class RequiredRoleFilter extends OncePerRequestFilter {

	private final String requiredRole;

	private final RequestMatcher excludedPaths;

	private final static Logger LOGGER = LoggerFactory.getLogger(RequiredRoleFilter.class);

	public RequiredRoleFilter(String requiredRole) {
		this(requiredRole, request -> false);
	}

	public RequiredRoleFilter(String requiredRole, RequestMatcher excludedPaths) {
		this.requiredRole = requiredRole;
		this.excludedPaths = excludedPaths;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		Authentication authentication = SecurityUtil.getAuthentication();
		return Strings.isNullOrEmpty(requiredRole) || !(authentication instanceof PreAuthenticatedAuthenticationToken) || excludedPaths.matches(request);
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

}
