package org.snomed.snowstorm.rest.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RequiredRoleFilterTest {

	private static final String REQUIRED_ROLE = "ROLE_required";

	private final RequiredRoleFilter filter = new RequiredRoleFilter(REQUIRED_ROLE,
			new ExcludedPathRequestMatcher(new String[]{"/version", "/swagger-ui/**", "/v3/api-docs/**"}, "/(.*)/concepts"));

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void excludedPathSkipsRoleCheck() throws Exception {
		authenticateWithRoles("ROLE_other");
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request("/swagger-ui/index.html", "/swagger-ui/index.html", null), new MockHttpServletResponse(), chain);

		assertNotNull(chain.getRequest());
	}

	@Test
	void dotDotSegmentInRawUriDoesNotSkipRoleCheck() {
		authenticateWithRoles("ROLE_other");

		assertThrows(AccessDeniedException.class, () ->
				filter.doFilter(request("/swagger-ui/../fhir-admin/load-package", "/fhir-admin/load-package", null), new MockHttpServletResponse(), new MockFilterChain()));
		assertThrows(AccessDeniedException.class, () ->
				filter.doFilter(request("/swagger-ui/..%2Ffhir/ValueSet", "/fhir", "/ValueSet"), new MockHttpServletResponse(), new MockFilterChain()));
	}

	@Test
	void decodedPathMatchingExclusionDoesNotSkipRoleCheckWhenRawUriDoesNot() {
		authenticateWithRoles("ROLE_other");

		assertThrows(AccessDeniedException.class, () ->
				filter.doFilter(request("/admin/permissions/user-group/x%2F..%2F..%2F..%2F..%2Fswagger-ui%2Fy", "/swagger-ui/y", null), new MockHttpServletResponse(), new MockFilterChain()));
	}

	@Test
	void excludedPathRewrittenToBranchControllerDoesNotSkipRoleCheck() {
		authenticateWithRoles("ROLE_other");

		assertThrows(AccessDeniedException.class, () ->
				filter.doFilter(request("/swagger-ui/MAIN/concepts", "/swagger-ui/MAIN/concepts", null), new MockHttpServletResponse(), new MockFilterChain()));
	}

	@Test
	void requestWithoutUriDoesNotSkipRoleCheck() {
		authenticateWithRoles("ROLE_other");
		MockHttpServletRequest request = request("/swagger-ui/index.html", "/swagger-ui/index.html", null);
		request.setRequestURI(null);

		assertThrows(AccessDeniedException.class, () ->
				filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain()));
	}

	@Test
	void userWithRequiredRolePasses() throws Exception {
		authenticateWithRoles(REQUIRED_ROLE);
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request("/fhir/ValueSet", "/fhir", "/ValueSet"), new MockHttpServletResponse(), chain);

		assertNotNull(chain.getRequest());
	}

	private static MockHttpServletRequest request(String requestUri, String servletPath, String pathInfo) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUri);
		request.setServletPath(servletPath);
		request.setPathInfo(pathInfo);
		return request;
	}

	private static void authenticateWithRoles(String... roles) {
		List<SimpleGrantedAuthority> authorities = Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList();
		SecurityContextHolder.getContext().setAuthentication(new PreAuthenticatedAuthenticationToken("userA", "token", authorities));
	}

}
