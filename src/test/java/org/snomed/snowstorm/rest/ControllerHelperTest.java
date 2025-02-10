package org.snomed.snowstorm.rest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ControllerHelperTest {
	private final HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
	private final ServletRequestAttributes servletRequestAttributes = new ServletRequestAttributes(httpServletRequest);

	@Test
	void getPageRequestZero() {
		PageRequest pageRequest = ControllerHelper.getPageRequest(0, 100);
		assertEquals(0, pageRequest.getPageNumber());
		assertEquals(100, pageRequest.getPageSize());
	}

	@Test
	void getPageRequestOne() {
		PageRequest pageRequest = ControllerHelper.getPageRequest(100, 100);
		assertEquals(1, pageRequest.getPageNumber());
		assertEquals(100, pageRequest.getPageSize());
	}

	@Test
	void getPageRequestTwo() {
		PageRequest pageRequest = ControllerHelper.getPageRequest(200, 100);
		assertEquals(2, pageRequest.getPageNumber());
		assertEquals(100, pageRequest.getPageSize());
	}

	@Test
	void getPageRequestTwoAndAHalf() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> ControllerHelper.getPageRequest(250, 100));
	}

	@Test
	void getCreatedLocationHeaders_ShouldReturnExpectedLocationFromHttpHeaders_WhenRequestingFromLocalhost() {
		//given
		RequestContextHolder.setRequestAttributes(servletRequestAttributes);
		when(httpServletRequest.getRequestURL()).thenReturn(new StringBuffer().append("http://localhost:8080/imports"));
		final String id = UUID.randomUUID().toString();

		//when
		final HttpHeaders createdLocationHeaders = ControllerHelper.getCreatedLocationHeaders(id);
		final String actualLocation = createdLocationHeaders.getLocation().toString();
		final String expectedLocation = "http://localhost:8080/imports/" + id;

		//then
		assertEquals(expectedLocation, actualLocation);
	}

	@Test
	void parseAcceptLanguageHeader_ShouldReturnExpectedLanguage_WhenParsingLanguageWithRegionalDialect() {
		//given
		String acceptLanguageHeader = "es-419,es;q=0.9";

		//when
		final List<LanguageDialect> languageDialects = ControllerHelper.parseAcceptLanguageHeader(acceptLanguageHeader);

		//then
		assertEquals("es", languageDialects.get(0).getLanguageCode());
	}
}
