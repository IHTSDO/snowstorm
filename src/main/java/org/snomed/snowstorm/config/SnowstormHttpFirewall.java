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

	private static final Pattern CONTROL_CHARACTER = Pattern.compile("\\p{IsControl}");

	private static final int MAX_MESSAGE_LENGTH = 500;

	public SnowstormHttpFirewall() {
		setAllowUrlEncodedSlash(true);
		// Thrown here because StrictHttpFirewall's own messages quote the value, which may be a cookie or token.
		// Multi-line ECL is sent in query parameters.
		setAllowedParameterValues(value -> {
			if (!ALLOWED_PARAMETER_VALUES.matcher(value).matches()) {
				throw new RequestRejectedException("The request was rejected because a parameter value is not allowed.");
			}
			return true;
		});
		setAllowedHeaderValues(value -> {
			if (!ALLOWED_HEADER_VALUES.test(value)) {
				throw new RequestRejectedException("The request was rejected because a header value is not allowed.");
			}
			return true;
		});
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

	// Rejection messages quote the rejected value, which can hold control characters that forge log lines.
	public static String safeMessage(RequestRejectedException exception) {
		String message = String.valueOf(exception.getMessage());
		if (message.length() > MAX_MESSAGE_LENGTH) {
			message = message.substring(0, MAX_MESSAGE_LENGTH) + "...";
		}
		return CONTROL_CHARACTER.matcher(message).replaceAll(match -> String.format("\\\\u%04x", (int) match.group().charAt(0)));
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
