package org.snomed.snowstorm.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import org.elasticsearch.common.Strings;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.services.DialectConfigurationService;
import org.snomed.snowstorm.core.data.services.NotFoundException;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.pojo.BranchTimepoint;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.rest.converter.SearchAfterHelper;
import org.snomed.snowstorm.rest.pojo.ConceptMiniNestedFsn;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.SearchAfterPageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_DIALECTS;

@Component
public class ControllerHelper {

	private static final Pattern LANGUAGE_PATTERN = Pattern.compile("([a-z]{2})");
	private static final Pattern LANGUAGE_AND_REFSET_PATTERN = Pattern.compile("([a-z]{2})-x-(" + IdentifierService.SCTID_PATTERN + ")");
	private static final Pattern LANGUAGE_AND_DIALECT_PATTERN = Pattern.compile("([a-z]{2})-([a-z]{2})");
	private static final Pattern LANGUAGE_AND_DIALECT_AND_REFSET_PATTERN = Pattern.compile("([a-z]{2})-([a-z]{2,5})-x-(" + IdentifierService.SCTID_PATTERN + ")");
	public static final String IS_A_REQUIRED_PARAMETER = " is a required parameter.";

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
		return getCreatedResponse(id, null);
	}

	static ResponseEntity<Void> getCreatedResponse(String id, String removePathPart) {
		HttpHeaders httpHeaders = getCreatedLocationHeaders(id, removePathPart);
		return new ResponseEntity<>(httpHeaders, HttpStatus.CREATED);
	}

	static HttpHeaders getCreatedLocationHeaders(String id) {
		return getCreatedLocationHeaders(id, null);
	}

	static HttpHeaders getCreatedLocationHeaders(String id, String removePathPart) {
		RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
		Assert.state(attrs instanceof ServletRequestAttributes, "No current ServletRequestAttributes");
		HttpServletRequest request = ((ServletRequestAttributes) attrs).getRequest();

		String requestUrl = request.getRequestURL().toString();
		// Decode branch path
		requestUrl = requestUrl.replace("%7C", "/");
		if (!Strings.isNullOrEmpty(removePathPart)) {
			requestUrl = requestUrl.replace(removePathPart, "");
		}

		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setLocation(ServletUriComponentsBuilder.fromHttpUrl(requestUrl).path("/{id}").buildAndExpand(id).toUri());
		return httpHeaders;
	}

	static List<ConceptMiniNestedFsn> nestConceptMiniFsn(Collection<ConceptMini> minis) {
		return minis.stream().map(ConceptMiniNestedFsn::new).collect(Collectors.toList());
	}

	static String requiredParam(String value, String paramName) {
		if (Strings.isNullOrEmpty(value)) {
			throw new IllegalArgumentException(paramName + IS_A_REQUIRED_PARAMETER);
		}
		return value;
	}

	static <T> T requiredParam(T value, String paramName) {
		if (value == null) {
			throw new IllegalArgumentException(paramName + IS_A_REQUIRED_PARAMETER);
		}
		return value;
	}

	static String requiredParamConceptIdFormat(String value, String paramName) {
		if (Strings.isNullOrEmpty(value)) {
			throw new IllegalArgumentException(paramName + IS_A_REQUIRED_PARAMETER);
		}
		if (!IdentifierService.isConceptId(value)) {
			throw new IllegalArgumentException(paramName + " must be a concept id.");
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
		validatePageSize(offset, limit);
		return getPageRequest(offset, limit, null);
	}

	public static PageRequest getPageRequest(int offset, int limit, Sort sort) {
		if (offset % limit != 0) {
			throw new IllegalArgumentException("Offset must be a multiplication of the limit param in order to map to Spring pages.");
		}

		int page = ((offset + limit) / limit) - 1;
		int size = limit;
		return sort == null ? PageRequest.of(page, size) : PageRequest.of(page, size, sort);
	}

	public static PageRequest getPageRequest(int offset, int limit, Sort sort, String searchAfter) {
		validatePageSize(offset, limit);
		if (!Strings.isNullOrEmpty(searchAfter)) {
			return SearchAfterPageRequest.of(SearchAfterHelper.fromSearchAfterToken(searchAfter), limit, sort);
		} else {
			return ControllerHelper.getPageRequest(offset, limit, sort);
		}
	}

	//use parseAcceptLanguageHeader and work with LanguageDialects instead
	@Deprecated
	public static List<String> getLanguageCodes(String acceptLanguageHeader) {
		return parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader).stream().map(LanguageDialect::getLanguageCode).collect(Collectors.toList());
	}
	
	public static List<LanguageDialect> parseAcceptLanguageHeaderWithDefaultFallback(String acceptLanguageHeader) {
		List<LanguageDialect> languageDialects = parseAcceptLanguageHeader(acceptLanguageHeader);
		languageDialects.addAll(DEFAULT_LANGUAGE_DIALECTS);
		return languageDialects;
	}

	public static List<LanguageDialect> parseAcceptLanguageHeader(String acceptLanguageHeader) {
		// en-ie-x-21000220103;q=0.8,en-US;q=0.5
		List<LanguageDialect> languageDialects = new ArrayList<>();

		if (acceptLanguageHeader == null) {
			acceptLanguageHeader = "";
		}

		acceptLanguageHeader = acceptLanguageHeader.replaceAll("\\s+", "");
		String[] acceptLanguageList = acceptLanguageHeader.toLowerCase().split(",");
		for (String acceptLanguage : acceptLanguageList) {
			if (acceptLanguage.isEmpty()) {
				continue;
			}

			String languageCode;
			Long languageReferenceSet = null;

			String[] valueAndWeight = acceptLanguage.split(";");
			// We don't use the weight, just take the value
			String value = valueAndWeight[0];

			Matcher matcher = LANGUAGE_PATTERN.matcher(value);
			if (matcher.matches()) {
				languageCode = matcher.group(1);
			} else if ((matcher = LANGUAGE_AND_REFSET_PATTERN.matcher(value)).matches()) {
				languageCode = matcher.group(1);
				languageReferenceSet = parseLong(matcher.group(2));
			} else if ((matcher = LANGUAGE_AND_DIALECT_PATTERN.matcher(value)).matches()) {
				languageCode = matcher.group(1);
				languageReferenceSet = DialectConfigurationService.instance().findRefsetForDialect(value); 
			} else if ((matcher = LANGUAGE_AND_DIALECT_AND_REFSET_PATTERN.matcher(value)).matches()) {
				languageCode = matcher.group(1);
				languageReferenceSet = parseLong(matcher.group(3));
			} else {
				throw new IllegalArgumentException("Unexpected value within Accept-Language request header '" + value + "'.");
			}
			
			LanguageDialect languageDialect = new LanguageDialect(languageCode, languageReferenceSet);
			if (!languageDialects.contains(languageDialect)) {
				//Would normally use a Set here, but the order may be important
				languageDialects.add(languageDialect);
			}
		}
		return languageDialects;
	}

	static void validatePageSize(long offset, int limit) {
		if (limit < 1) {
			throw new IllegalArgumentException("Limit must be greater than 0.");
		}

		if ((offset + limit) > 10_000) {
			throw new IllegalArgumentException("Maximum unsorted offset + page size is 10,000.");
		}
	}
}
