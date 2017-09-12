package org.ihtsdo.elasticsnomed.rest.security;

import org.elasticsearch.common.Strings;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class RequestHeaderAuthenticationDecoratorWithRequiredRole extends OncePerRequestFilter {

	private String requiredRole;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public RequestHeaderAuthenticationDecoratorWithRequiredRole(String requiredRole) {
		this.requiredRole = requiredRole;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		if (Strings.isNullOrEmpty(requiredRole)) {
			filterChain.doFilter(request, response);
			return;
		}
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
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
	}

	private void accessDenied(String msg, HttpServletResponse response) throws IOException {
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.getWriter().println(msg);
	}

}
