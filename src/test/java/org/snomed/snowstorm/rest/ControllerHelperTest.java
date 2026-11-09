package org.snomed.snowstorm.rest;

import org.apache.commons.lang3.tuple.Pair;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_DIALECTS;

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

	@Test
	void parseAcceptLanguageHeader_ShouldIgnoreUnrecognisedLanguageTag() {
		List<LanguageDialect> languageDialects = ControllerHelper.parseAcceptLanguageHeader("haw");

		assertEquals(0, languageDialects.size());
	}

	@Test
	void parseAcceptLanguageHeaderWithDefaultFallback_ShouldReturnDefaults_WhenLanguageTagUnrecognised() {
		List<LanguageDialect> languageDialects = ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback("haw");

		assertEquals(DEFAULT_LANGUAGE_DIALECTS, languageDialects);
	}

	@Test
	void parseAcceptLanguageHeaderWithDefaultFallback_ShouldKeepRecognisedTags_AndSkipUnrecognised() {
		List<LanguageDialect> languageDialects = ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback("haw,fr");

		assertEquals("fr", languageDialects.get(0).getLanguageCode());
		assertEquals(DEFAULT_LANGUAGE_DIALECTS, languageDialects.subList(1, languageDialects.size()));
	}

	@Test
	void parseAcceptLanguageHeaderWithWeights_ShouldWeightUnweightedLanguageAsOne() {
		// RFC 9110 section 12.4.2: a language with no q value has weight 1
		List<Pair<LanguageDialect, Double>> weighted = ControllerHelper.parseAcceptLanguageHeaderWithWeights("en;q=0.5, fr", false);

		assertEquals(0.5, weighted.get(0).getRight());
		assertEquals(1.0, weighted.get(1).getRight());
	}

	@Test
	void parseAcceptLanguageHeaderByWeight_ShouldPutHighestWeightFirst() {
		List<LanguageDialect> languageDialects = ControllerHelper.parseAcceptLanguageHeaderByWeight("en;q=0.5, fr, nl;q=0.8");

		assertEquals(List.of("fr", "nl", "en"), languageDialects.stream().map(LanguageDialect::getLanguageCode).toList());
	}

	@Test
	void parseAcceptLanguageHeaderByWeight_ShouldKeepWrittenOrder_WhenWeightsEqual() {
		List<LanguageDialect> languageDialects = ControllerHelper.parseAcceptLanguageHeaderByWeight("nl, fr, de;q=1.0");

		assertEquals(List.of("nl", "fr", "de"), languageDialects.stream().map(LanguageDialect::getLanguageCode).toList());
	}

	@Test
	void parseAcceptLanguageHeaderByWeight_ShouldLeaveOutNotAcceptableLanguages() {
		List<LanguageDialect> languageDialects = ControllerHelper.parseAcceptLanguageHeaderByWeight("fr, en;q=0");

		assertEquals(List.of("fr"), languageDialects.stream().map(LanguageDialect::getLanguageCode).toList());
	}

	@Test
	void parseAcceptLanguageHeader_ShouldKeepWrittenOrder_ForNativeApi() {
		List<LanguageDialect> languageDialects = ControllerHelper.parseAcceptLanguageHeader("en;q=0.5, fr");

		assertEquals(List.of("en", "fr"), languageDialects.stream().map(LanguageDialect::getLanguageCode).toList());
	}
}
