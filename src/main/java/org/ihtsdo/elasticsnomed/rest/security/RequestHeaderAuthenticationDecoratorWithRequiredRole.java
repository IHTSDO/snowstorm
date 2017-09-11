package org.ihtsdo.elasticsnomed.rest.security;

import org.elasticsearch.common.Strings;
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

public class RequestHeaderAuthenticationDecoratorWithRequiredRole extends OncePerRequestFilter {

	private String requiredRole;

	public RequestHeaderAuthenticationDecoratorWithRequiredRole(String requiredRole) {
		this.requiredRole = requiredRole;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		if (!Strings.isNullOrEmpty(requiredRole)) {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication == null) {
				accessDenied("Please log in.", response);
				return;
			}
			for (GrantedAuthority grantedAuthority : authentication.getAuthorities()) {
				if (grantedAuthority.getAuthority().equals(requiredRole)) {
					filterChain.doFilter(request, response);
					return;
				}
			}
			accessDenied("The current user does not have permission to access this resource.", response);
		}
	}

	private void accessDenied(String msg, HttpServletResponse response) throws IOException {
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.getWriter().println(msg);
	}

}
