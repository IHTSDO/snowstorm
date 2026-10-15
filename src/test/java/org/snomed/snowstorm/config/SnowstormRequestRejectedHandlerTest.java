package org.snomed.snowstorm.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.firewall.RequestRejectedException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SnowstormRequestRejectedHandlerTest {

	private final SnowstormRequestRejectedHandler handler = new SnowstormRequestRejectedHandler(new SecurityAndUriConfig().getGeneralMapper());

	@Test
	void rejectionIsReturnedAsJsonBadRequest() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.handle(new MockHttpServletRequest(), response, new RequestRejectedException("rejected \"\u001b[2J\""));

		assertEquals(400, response.getStatus());
		assertEquals("application/json;charset=UTF-8", response.getContentType());
		Map<?, ?> body = new JsonMapper().readValue(response.getContentAsString(), Map.class);
		assertEquals("BAD_REQUEST", body.get("error"));
		assertEquals("rejected \"\\u001b[2J\"", body.get("message"));
	}

	@Test
	void partiallyWrittenStreamIsReplaced() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.getOutputStream().write("partial".getBytes());

		handler.handle(new MockHttpServletRequest(), response, new RequestRejectedException("rejected"));

		assertEquals(400, response.getStatus());
		assertEquals("rejected", new JsonMapper().readValue(response.getContentAsString(), Map.class).get("message"));
	}

	@Test
	void partiallyWrittenWriterIsReplaced() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.getWriter().write("partial");
		response.setOutputStreamAccessAllowed(false);

		handler.handle(new MockHttpServletRequest(), response, new RequestRejectedException("rejected"));

		assertEquals(400, response.getStatus());
		assertEquals("rejected", new JsonMapper().readValue(response.getContentAsString(), Map.class).get("message"));
	}

	@Test
	void headersFromPartialResponseAreCleared() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.setContentLength(12345);
		response.setHeader("Content-Disposition", "attachment");

		handler.handle(new MockHttpServletRequest(), response, new RequestRejectedException("rejected"));

		assertNull(response.getHeader("Content-Disposition"));
		assertNotEquals(12345, response.getContentLength());
	}

	@Test
	void corsHeadersArePreserved() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.setHeader("Access-Control-Allow-Origin", "https://browser.example.org");
		response.addHeader("Vary", "Origin");
		response.addHeader("Vary", "Access-Control-Request-Method");
		response.setHeader("Content-Disposition", "attachment");

		handler.handle(new MockHttpServletRequest(), response, new RequestRejectedException("rejected"));

		assertEquals(400, response.getStatus());
		assertEquals("https://browser.example.org", response.getHeader("Access-Control-Allow-Origin"));
		assertEquals(List.of("Origin", "Access-Control-Request-Method"), response.getHeaders("Vary"));
		assertNull(response.getHeader("Content-Disposition"));
	}

	@Test
	void writerFallbackUsesUtf8() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.getWriter().write("partial");
		response.setOutputStreamAccessAllowed(false);

		handler.handle(new MockHttpServletRequest(), response, new RequestRejectedException("rejected 心臓"));

		assertEquals("UTF-8", response.getCharacterEncoding());
		assertEquals("rejected 心臓", new JsonMapper().readValue(response.getContentAsString(), Map.class).get("message"));
	}

	@Test
	void committedResponseIsLeftAlone() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.setStatus(200);
		response.setCommitted(true);

		handler.handle(new MockHttpServletRequest(), response, new RequestRejectedException("rejected"));

		assertEquals(200, response.getStatus());
		assertEquals("", response.getContentAsString());
	}

}
