package org.snomed.snowstorm.fhir.services;

import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.services.DescriptionService;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.util.DescriptionHelper;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service to cache small number of often-used snomed terms from the model module. Terms may be translated.
 * Cache works per code system version and language dialects requested.
 */
@Service
public class FHIRSnomedModelTermCache {

	@Autowired
	private DescriptionService snomedDescriptionService;

	private final Map<String, Map<String, String>> termCache = new HashMap<>();

	public synchronized String getSnomedTerm(String snomedCode, FHIRCodeSystemVersion snomedVersion, List<LanguageDialect> languageDialects) {
		String cacheKey = snomedVersion.getId() + "-" + languageDialects.stream().map(Object::toString).collect(Collectors.joining("|"));
		Map<String, String> versionDialectTermCache = termCache.get(cacheKey);
		if (versionDialectTermCache == null) {
			synchronized (termCache) {
				versionDialectTermCache = new HashMap<>();
				termCache.put(cacheKey, versionDialectTermCache);
			}
		}
		String term = versionDialectTermCache.get(snomedCode);
		if (term == null) {
			Set<Description> descriptions = snomedDescriptionService.findDescriptionsByConceptId(snomedVersion.getSnomedBranch(), Collections.singleton(snomedCode), true);
			Optional<Description> termOptional = DescriptionHelper.getPtDescription(descriptions, languageDialects);
			term = termOptional.map(Description::getTerm).orElse(null);
			synchronized (termCache) {
				versionDialectTermCache.put(snomedCode, term);
			}
		}
		return term;
	}

}
