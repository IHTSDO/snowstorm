package org.snomed.snowstorm.fhir.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

public class ForceBrowserJsonFilter implements Filter {

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		HttpServletRequest httpRequest = (HttpServletRequest) request;
		String acceptHeader = httpRequest.getHeader("Accept");

		// If the request looks like it's from a browser, force JSON
		if (acceptHeader != null && acceptHeader.contains("text/html")) {
			AcceptHeaderRequestWrapper wrappedRequest =
					new AcceptHeaderRequestWrapper(httpRequest, "application/fhir+json");
			chain.doFilter(wrappedRequest, response);
		} else {
			chain.doFilter(request, response);
		}
	}
}

