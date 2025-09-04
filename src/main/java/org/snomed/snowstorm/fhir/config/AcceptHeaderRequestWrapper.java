package org.snomed.snowstorm.fhir.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;

public class AcceptHeaderRequestWrapper extends HttpServletRequestWrapper {

	private final String acceptHeader;

	public AcceptHeaderRequestWrapper(HttpServletRequest request, String acceptHeader) {
		super(request);
		this.acceptHeader = acceptHeader;
	}

	@Override
	public String getHeader(String name) {
		if ("Accept".equalsIgnoreCase(name)) {
			return acceptHeader;
		}
		return super.getHeader(name);
	}

	@Override
	public Enumeration<String> getHeaders(String name) {
		if ("Accept".equalsIgnoreCase(name)) {
			return Collections.enumeration(Collections.singletonList(acceptHeader));
		}
		return super.getHeaders(name);
	}
}

