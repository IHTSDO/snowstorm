package org.snomed.snowstorm.core.pojo;

import java.util.Objects;

public class LanguageDialect {

	private String languageCode;
	private Long languageReferenceSet;

	public LanguageDialect(String languageCode) {
		this.languageCode = languageCode;
	}

	public LanguageDialect(String languageCode, Long languageReferenceSet) {
		this.languageCode = languageCode;
		this.languageReferenceSet = languageReferenceSet;
	}

	public String getLanguageCode() {
		return languageCode;
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
}
