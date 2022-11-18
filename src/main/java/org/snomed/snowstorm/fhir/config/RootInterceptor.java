package org.snomed.snowstorm.fhir.config;

import ca.uhn.fhir.context.ConfigurationException;
import ca.uhn.fhir.rest.server.interceptor.InterceptorAdapter;
import com.amazonaws.util.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;

public class RootInterceptor extends InterceptorAdapter {
	
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
		} catch (Exception e) {
			logger.error("Failed to intercept request", e);
		}
		return true;
	}
	
}
