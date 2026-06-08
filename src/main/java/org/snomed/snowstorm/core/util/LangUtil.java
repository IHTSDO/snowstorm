package org.snomed.snowstorm.core.util;

import java.util.Locale;

public class LangUtil {

    private LangUtil() {
    }

    public static String convertLanguageCodeToName(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return "";
        }
        return Locale.of(languageCode).getDisplayLanguage();
    }
}
