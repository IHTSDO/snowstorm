package org.snomed.snowstorm.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Not HttpStatusRequestRejectedHandler: its sendError dispatches to the secured /error page, which turns the 400 into a 403.
public class SnowstormRequestRejectedHandler implements RequestRejectedHandler {

	private static final Logger logger = LoggerFactory.getLogger(SnowstormRequestRejectedHandler.class);

	private final JsonMapper jsonMapper;

	public SnowstormRequestRejectedHandler(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, RequestRejectedException exception) throws IOException {
		String message = SnowstormHttpFirewall.safeMessage(exception);
		if (response.isCommitted()) {
			logger.debug("bad request {}, response already committed", message);
			return;
		}
		logger.debug("bad request {}", message);
		// reset() also drops headers set by earlier filters; CORS headers are restored so browsers can read the 400.
		Map<String, List<String>> corsHeaders = new LinkedHashMap<>();
		for (String name : response.getHeaderNames()) {
			if (name.regionMatches(true, 0, "Access-Control-", 0, "Access-Control-".length()) || name.equalsIgnoreCase(HttpHeaders.VARY)) {
				corsHeaders.put(name, new ArrayList<>(response.getHeaders(name)));
			}
		}
		response.reset();
		corsHeaders.forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
		response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8);
		Map<String, Object> body = Map.of("error", HttpStatus.BAD_REQUEST, "message", message);
		OutputStream outputStream;
		try {
			outputStream = response.getOutputStream();
		} catch (IllegalStateException e) {
			// The rejection was thrown after downstream code had already called getWriter().
			jsonMapper.writeValue(response.getWriter(), body);
			return;
		}
		jsonMapper.writeValue(outputStream, body);
	}

}
