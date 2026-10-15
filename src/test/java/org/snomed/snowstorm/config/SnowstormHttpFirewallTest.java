package org.snomed.snowstorm.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.RequestRejectedException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnowstormHttpFirewallTest {

	private final HttpFirewall firewall = new SecurityAndUriConfig().allowUrlEncodedSlashHttpFirewall();

	@Test
	void dotDotPathSegmentIsRejected() {
		assertRejected(request("/swagger-ui/../fhir-admin/load-package", "/fhir-admin/load-package", null));
		assertRejected(request("/swagger-ui/../fhir/ValueSet", "/fhir", "/ValueSet"));
	}

	@Test
	void dotDotPathSegmentFormedByEncodedSlashIsRejected() {
		assertRejected(request("/swagger-ui/..%2Ffhir/ValueSet", "/fhir", "/ValueSet"));
		assertRejected(request("/swagger-ui/..%2ffhir-admin/load-package", "/fhir-admin/load-package", null));
		assertRejected(request("/swagger-ui%2F..%2Ffhir/ValueSet", "/fhir", "/ValueSet"));
		assertRejected(request("/admin/permissions/user-group/x%2F..%2F..%2F..%2F..%2Fswagger-ui%2Fy", "/swagger-ui/y", null));
		assertRejected(request("/swagger-ui/.%2Ffhir/ValueSet", "/swagger-ui/fhir", "/ValueSet"));
	}

	@Test
	void pathTraversalVariantsAreRejected() {
		assertRejected(request("/swagger-ui/%2E%2E/fhir/ValueSet", "/fhir", "/ValueSet"));
		assertRejected(request("/swagger-ui/..;/fhir/ValueSet", "/fhir", "/ValueSet"));
		assertRejected(request("/swagger-ui/..%3B/fhir/ValueSet", "/fhir", "/ValueSet"));
		assertRejected(request("/swagger-ui/..%5Cfhir/ValueSet", "/fhir", "/ValueSet"));
	}

	@Test
	void dotDotPathSegmentFormedByDoubleEncodingIsRejected() {
		assertRejected(request("/swagger-ui/..%252Ffhir/ValueSet", "/swagger-ui/..%2Ffhir/ValueSet", null));
		assertRejected(request("/swagger-ui/%252E%252E/fhir/ValueSet", "/swagger-ui/%2E%2E/fhir/ValueSet", null));
		assertRejected(request("/swagger-ui/..%25252Ffhir/ValueSet", "/swagger-ui/..%252Ffhir/ValueSet", null));
	}

	@Test
	void encodedSlashInBranchPathIsAllowed() {
		assertAllowed(request("/branches/MAIN%2FSNOMEDCT-A", "/branches/MAIN/SNOMEDCT-A", null));
		assertAllowed(request("/browser/MAIN%2FSNOMEDCT-A/concepts/404684003", "/browser/MAIN/SNOMEDCT-A/concepts/404684003", null));
	}

	@Test
	void encodedPercentIsRejected() {
		assertRejected(request("/MAIN%252FSNOMEDCT-A/concepts", "/MAIN%2FSNOMEDCT-A/concepts", null));
		assertRejected(request("/fhir/CodeSystem/100%25-test", "/fhir", "/CodeSystem/100%-test"));
	}

	@Test
	void multiLineParameterValueIsAllowed() {
		MockHttpServletRequest request = request("/MAIN/concepts", "/MAIN/concepts", null);
		request.addParameter("ecl", "<< 404684003\r\n\tMINUS << 64572001");
		assertDoesNotThrow(() -> firewall.getFirewalledRequest(request).getParameter("ecl"));
	}

	@Test
	void unassignedCodePointInParameterValueIsAllowed() {
		MockHttpServletRequest request = request("/MAIN/concepts", "/MAIN/concepts", null);
		request.addParameter("term", "heart ͸");
		assertDoesNotThrow(() -> firewall.getFirewalledRequest(request).getParameter("term"));
	}

	@Test
	void otherControlCharacterInParameterValueIsRejected() {
		MockHttpServletRequest request = request("/MAIN/concepts", "/MAIN/concepts", null);
		request.addParameter("ecl", "<< 404684003\u0000");
		assertThrows(RequestRejectedException.class, () -> firewall.getFirewalledRequest(request).getParameter("ecl"));
	}

	private void assertRejected(MockHttpServletRequest request) {
		assertThrows(RequestRejectedException.class, () -> firewall.getFirewalledRequest(request), request.getRequestURI());
	}

	private void assertAllowed(MockHttpServletRequest request) {
		assertDoesNotThrow(() -> firewall.getFirewalledRequest(request), request.getRequestURI());
	}

	private static MockHttpServletRequest request(String requestUri, String servletPath, String pathInfo) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUri);
		request.setServletPath(servletPath);
		request.setPathInfo(pathInfo);
		return request;
	}

}
