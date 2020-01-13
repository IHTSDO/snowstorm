package org.snomed.snowstorm.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import org.elasticsearch.common.Strings;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.services.NotFoundException;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.pojo.BranchTimepoint;
import org.snomed.snowstorm.rest.pojo.AcceptLanguageValues;
import org.snomed.snowstorm.rest.pojo.ConceptMiniNestedFsn;
import org.springframework.data.domain.PageRequest;
import org.snomed.snowstorm.rest.converter.SearchAfterHelper;
import org.springframework.data.elasticsearch.core.SearchAfterPageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;

public class ControllerHelper {

	public static final String DEFAULT_ACCEPT_LANG_HEADER = "en-US;q=0.8,en-GB;q=0.6";

	private static final Pattern LANGUAGE_PATTERN = Pattern.compile("([a-z]{2})");
	private static final Pattern LANGUAGE_AND_DIALECT_PATTERN = Pattern.compile("([a-z]{2})-([a-z]{2})");
	private static final Pattern LANGUAGE_AND_DIALECT_AND_REFSET_PATTERN = Pattern.compile("([a-z]{2})-([a-z]{2})-x-(" + IdentifierService.SCTID_PATTERN + ")");
	private static final Pattern LANGUAGE_AND_REFSET_PATTERN = Pattern.compile("([a-z]{2})-x-(" + IdentifierService.SCTID_PATTERN + ")");

	public static BranchTimepoint parseBranchTimepoint(String branch) {
		String[] parts = BranchPathUriUtil.decodePath(branch).split("@");
		if (parts.length == 1) {
			return new BranchTimepoint(parts[0]);
		} else if (parts.length == 2) {
			return new BranchTimepoint(parts[0], parts[1]);
		} else {
			throw new IllegalArgumentException("Malformed branch path.");
		}
	}

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

	public static PageRequest getPageRequest(int offset, int limit) {
		return getPageRequest(offset, null, limit);
	}

	public static PageRequest getPageRequest(int offset, String searchAfter, int limit) {
		if (!Strings.isNullOrEmpty(searchAfter)) {
			return SearchAfterPageRequest.of(SearchAfterHelper.fromSearchAfterToken(searchAfter), limit);
		}
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

	public static AcceptLanguageValues parseAcceptLanguageHeader(String acceptLanguageHeader) {
		// en-ie-x-21000220103;q=0.8,en-US;q=0.5
		AcceptLanguageValues values = new AcceptLanguageValues();

		String[] acceptLanguageList = acceptLanguageHeader.toLowerCase().split(",");
		for (String acceptLanguage : acceptLanguageList) {
			String[] valueAndWeight = acceptLanguage.split(";");
			// We don't use the weight, just take the value
			String value = valueAndWeight[0];

			Matcher matcher = LANGUAGE_PATTERN.matcher(value);
			if (matcher.matches()) {
				values.addLanguageCode(matcher.group(1));
				continue;
			}

			matcher = LANGUAGE_AND_DIALECT_PATTERN.matcher(value);
			if (matcher.matches()) {
				values.addLanguageCode(matcher.group(1));
				continue;
			}

			matcher = LANGUAGE_AND_DIALECT_AND_REFSET_PATTERN.matcher(value);
			if (matcher.matches()) {
				values.addLanguageCode(matcher.group(1));
				values.addLanguageReferenceSet(parseLong(matcher.group(3)));
				continue;
			}

			matcher = LANGUAGE_AND_REFSET_PATTERN.matcher(value);
			if (matcher.matches()) {
				values.addLanguageCode(matcher.group(1));
				values.addLanguageReferenceSet(parseLong(matcher.group(2)));
				continue;
			}

			throw new IllegalArgumentException("Unexpected value within Accept-Language request header '" + value + "'.");
		}
		return values;
	}

	static void validatePageSize(long offset, int limit) {
		if ((offset + limit) > 10_000) {
			throw new IllegalArgumentException("Maximum offset + page size is 10,000.");
		}
	}
}
