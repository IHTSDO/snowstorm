package org.snomed.snowstorm.core.pojo;

import org.drools.util.StringUtils;

import java.io.Serializable;
import java.util.*;


public class LanguageDialect implements Serializable {

	private static final long serialVersionUID = -2108861640664721439L;
	private String languageCode;
	private Long languageReferenceSet;

	public LanguageDialect() { }

	public LanguageDialect(String languageCode) {
		this.languageCode = languageCode;
	}

	public LanguageDialect(String languageCode, Long languageReferenceSet) {
		this.languageCode = languageCode;
		this.languageReferenceSet = languageReferenceSet;
	}

	public void setLanguageCode(String languageCode) {
		this.languageCode = languageCode;
	}

	public String getLanguageCode() {
		return languageCode;
	}

	public void setLanguageReferenceSet(Long languageReferenceSet) {
		this.languageReferenceSet = languageReferenceSet;
	}

	public Long getLanguageReferenceSet() {
		return languageReferenceSet;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		LanguageDialect that = (LanguageDialect) o;
		return languageCode.equals(that.languageCode) &&
				Objects.equals(languageReferenceSet, that.languageReferenceSet);
	}

	@Override
	public int hashCode() {
		return Objects.hash(languageCode, languageReferenceSet);
	}

	public static List<String> toLanguageCodes(List<LanguageDialect> languageDialects) {
		List<String> languageCodes = new ArrayList<>();
		if (languageDialects != null) {
			for (LanguageDialect languageDialect : languageDialects) {
				if (!StringUtils.isEmpty(languageDialect.getLanguageCode())) {
					languageCodes.add(languageDialect.getLanguageCode());
				}
			}
		}
		return languageCodes;
	}
	
	public String toString() {
		String str = "";
		if (languageCode != null) {
			str = languageCode;
			if (languageReferenceSet != null) {
				str += " - ";
			}
		}
		
		if (languageReferenceSet != null) {
			str += languageReferenceSet;
		}
		return str;
	}
}
