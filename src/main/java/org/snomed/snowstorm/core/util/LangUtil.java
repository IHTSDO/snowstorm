package org.snomed.snowstorm.core.util;

import java.util.Locale;

public class LangUtil {

	public static String convertLanguageCodeToName(String languageCode) {
		return new Locale(languageCode).getDisplayLanguage();
	}
}
