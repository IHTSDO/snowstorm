package org.snomed.snowstorm.core.data.services;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.pojo.LanguageDialect;

import java.util.*;

public class DialectConfigurationService {
	
	private static DialectConfigurationService singleton;
	
	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final Map<String, String> config = new HashMap<>();

	private Map<String, DialectConfiguration> dialects;

	// Used by Spring to fill the properties
	public Map<String, String> getConfig() {
		return config;
	}

	@PostConstruct
	private void init() {
		Map<String, DialectConfiguration> dialects = new HashMap<>();
		for (String key : config.keySet()) {
			String dialectCode = key.substring(key.lastIndexOf(".") + 1).toLowerCase();
			long languageRefsetId = Long.parseLong(config.get(key));
			dialects.put(dialectCode, new DialectConfiguration(dialectCode, languageRefsetId));
			logger.info("Known dialect " + dialectCode + " - refset: " + languageRefsetId);
		}
		this.dialects = Collections.unmodifiableMap(dialects);
		singleton = this;
	}

	public static DialectConfigurationService instance() {
		return singleton;
	}

	public Long findRefsetForDialect(String dialectCode) {
		if (dialects.containsKey(dialectCode)) {
			return dialects.get(dialectCode).languageRefsetId;
		}
		return null;
	}

	public LanguageDialect getLanguageDialect(String dialectCode) {
		//Do we know about this language code?
		dialectCode = dialectCode.toLowerCase();
		if (dialects.containsKey(dialectCode)) {
			String lang = dialectCode.split("-")[0];
			Long refsetId = findRefsetForDialect(dialectCode);
			return new LanguageDialect(lang, refsetId);
		}
		return new LanguageDialect(dialectCode);
	}

	public void report() {
		logger.info(dialects.size() + " known dialects configured");
	}

	public static class DialectConfiguration {
		String dialectCode;
		Long languageRefsetId;

		DialectConfiguration(String dialectCode, Long languageRefsetId) {
			this.dialectCode = dialectCode;
			this.languageRefsetId = languageRefsetId;
		}
	}

}
