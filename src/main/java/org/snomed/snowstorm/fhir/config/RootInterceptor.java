package org.snomed.snowstorm.fhir.config;

import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;

import javax.servlet.http.*;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestWrapper;

import com.amazonaws.util.IOUtils;

import ca.uhn.fhir.context.ConfigurationException;
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
			//The base URL will return a static HTML page
			if (StringUtils.isEmpty(request.getPathInfo()) || request.getPathInfo().equals("/")) {
				response.setContentType("text/html");
				InputStream ios = this.getClass().getResourceAsStream("/fhir/index.html");
				if (ios == null) {
					throw new ConfigurationException("Did not find internal resource file fhir/index.html");
				}
				IOUtils.copy(ios, response.getOutputStream());
				return false;
			}
			
			//If we detect headers that imply a browser client, then we'll tweak
			//that to return json by default, since we don't have an html response available
			if (request.getHeader("Accept") != null && request.getHeader("Accept").contains("text/html")) {
				if (request instanceof SecurityContextHolderAwareRequestWrapper) {
					HttpServletRequest unwrappedRequest = (HttpServletRequest)((SecurityContextHolderAwareRequestWrapper)request).getRequest();
					MutableServletRequest mutableRequest = new MutableServletRequest(unwrappedRequest);
					mutableRequest.putAcceptHeader("application/json");
					((SecurityContextHolderAwareRequestWrapper)request).setRequest(mutableRequest);
				} else {
					logger.warn("Incomptabile request object received: " + request.getClass().getSimpleName());
				}
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
