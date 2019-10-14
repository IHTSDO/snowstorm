package org.snomed.snowstorm.fhir.config;

import java.util.Collections;
import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestWrapper;

import ca.uhn.fhir.rest.server.interceptor.InterceptorAdapter;

public class RootInterceptor extends InterceptorAdapter {
	
	public static String ACCEPT = "Accept";
	
	private final Logger logger = LoggerFactory.getLogger(getClass());

	/**
	* Override the incomingRequestPreProcessed method, which is called
	* for each incoming request before any processing is done
	*/
	@Override
	public boolean incomingRequestPreProcessed(HttpServletRequest request, HttpServletResponse response) {
		try {
			//If we detect headers that imply a browser client, then we'll tweak
			//that to return json by default, since we don't have an html response available
			if (request.getHeader("Accept").contains("text/html")) {
				if (request instanceof SecurityContextHolderAwareRequestWrapper) {
					HttpServletRequest unwrappedRequest = (HttpServletRequest)((SecurityContextHolderAwareRequestWrapper)request).getRequest();
					MutableServletRequest mutableRequest = new MutableServletRequest(unwrappedRequest);
					mutableRequest.putAcceptHeader("application/json");
					((SecurityContextHolderAwareRequestWrapper)request).setRequest(mutableRequest);
				} else {
					logger.warn("Incomptabile request object received: " + request.getClass().getSimpleName());
				}
			}
			
			//Anyone attempting to access the root will be redirected to metadata for
			//the CapabilityStatement
			if (request.getPathInfo() == null || request.getPathInfo().equals("/")) {
				logger.info("Attempt to access root context redirected to CapabilityStatement (/metadata)");
				response.setStatus(HttpServletResponse.SC_FOUND);
				response.addHeader("Location","metadata");
				return false;
			}
		} catch (Exception e) {
			logger.error("Failed to intercept request", e);
		}
		return true;
	}
	
	class MutableServletRequest extends HttpServletRequestWrapper {
		HttpServletRequest request;
		String acceptHeader;
		
		MutableServletRequest (HttpServletRequest request) {
			super(request);
			this.request = request;
			this.acceptHeader = request.getHeader(ACCEPT);
		}
		
		@Override
		public String getHeader(String name) {
			if (name.equals(ACCEPT)) {
				return acceptHeader;
			}
			return request.getHeader(name);
		}
		
		public void putAcceptHeader(String acceptHeader) {
			this.acceptHeader = acceptHeader;
		}
		
		@Override
		public Enumeration<String> getHeaders(String name) {
			if (name.equals(ACCEPT)) {
				return Collections.enumeration(Collections.singleton(acceptHeader));
			}
			return request.getHeaders(name);
		}
	}
}
