package org.snomed.snowstorm.rest;

import org.elasticsearch.common.Strings;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.services.NotFoundException;
import org.snomed.snowstorm.rest.pojo.ConceptMiniNestedFsn;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ControllerHelper {

	public static final String DEFAULT_ACCEPT_LANG_HEADER = "en-US;q=0.8,en-GB;q=0.6";

	static ResponseEntity<Void> getCreatedResponse(String id) {
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setLocation(ServletUriComponentsBuilder
				.fromCurrentRequest().path("/{id}")
				.buildAndExpand(id).toUri());
		return new ResponseEntity<>(httpHeaders, HttpStatus.CREATED);
	}

	static List<ConceptMiniNestedFsn> nestConceptMiniFsn(Collection<ConceptMini> minis) {
		return minis.stream().map(ConceptMiniNestedFsn::new).collect(Collectors.toList());
	}

	static String requiredParam(String value, String paramName) {
		if (Strings.isNullOrEmpty(value)) {
			throw new IllegalArgumentException(paramName + " is a required parameter.");
		}
		return value;
	}

	static <T> T requiredParam(T value, String paramName) {
		if (value == null) {
			throw new IllegalArgumentException(paramName + " is a required parameter.");
		}
		return value;
	}

	public static <T> T throwIfNotFound(String type, T component) {
		if (component == null) {
			throw new NotFoundException(type + " not found");
		}
		return component;
	}

	public static PageRequest getPageRequest(@RequestParam(required = false, defaultValue = "0") int offset, @RequestParam(required = false, defaultValue = "50") int limit) {
		if (offset % limit != 0) {
			throw new IllegalArgumentException("Offset must be a multiplication of the limit param in order to map to Spring pages.");
		}

		int page = ((offset + limit) / limit) - 1;
		int size = limit;
		return PageRequest.of(page, size);
	}

	public static List<String> getLanguageCodes(String acceptLanguageHeader) {
		List<Locale.LanguageRange> languageRanges = Locale.LanguageRange.parse(acceptLanguageHeader);

		List<String> languageCodes = new ArrayList<>(languageRanges.stream()
				.map(languageRange -> languageRange.getRange().contains("-") ? languageRange.getRange().substring(0, languageRange.getRange().indexOf("-")) : languageRange.getRange())
				.collect(Collectors.toCollection((Supplier<Set<String>>) LinkedHashSet::new)));

		// Always include en at the end if not already present because this is the default language.
		if (!languageCodes.contains("en")) {
			languageCodes.add("en");
		}
		return languageCodes;
	}
}
