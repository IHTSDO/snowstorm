package org.snomed.snowstorm.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.firewall.FirewalledRequest;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public class SnowstormHttpFirewall extends StrictHttpFirewall {

	private static final Pattern ALLOWED_PARAMETER_VALUES = Pattern.compile("[[^\\p{IsControl}]\\t\\n\\r]*");

	public SnowstormHttpFirewall() {
		setAllowUrlEncodedSlash(true);
		// Multi-line ECL is sent in query parameters.
		setAllowedParameterValues(value -> ALLOWED_PARAMETER_VALUES.matcher(value).matches());
	}

	@Override
	public FirewalledRequest getFirewalledRequest(HttpServletRequest request) throws RequestRejectedException {
		FirewalledRequest firewalledRequest = super.getFirewalledRequest(request);
		// Tomcat decodes %2F (EncodedSolidusHandling.DECODE) and normalises the servlet path before this runs, so "..%2F"
		// only shows up as a "/../" segment once the raw URI the request matchers use is decoded.
		if (hasDotSegmentAfterDecoding(request.getRequestURI())) {
			throw new RequestRejectedException("The request was rejected because the decoded URL contained a \".\" or \"..\" path segment.");
		}
		return firewalledRequest;
	}

	private static boolean hasDotSegmentAfterDecoding(String requestUri) {
		if (requestUri == null) {
			return false;
		}
		String decoded;
		try {
			decoded = UriUtils.decode(requestUri, StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			throw new RequestRejectedException("The request was rejected because the URL contained an invalid percent-encoding.");
		}
		for (String segment : decoded.split("/", -1)) {
			if (segment.equals(".") || segment.equals("..")) {
				return true;
			}
		}
		return false;
	}

}
