package org.snomed.snowstorm.rest.pojo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AcceptLanguageValues {

	private List<String> languageCodes = new ArrayList<>();
	private Set<Long> languageReferenceSets = new HashSet<>();

	public void addLanguageCode(String languageCode) {
		this.languageCodes.add(languageCode);
	}

	public void addLanguageReferenceSet(Long languageReferenceSet) {
		this.languageReferenceSets.add(languageReferenceSet);
	}

	public List<String> getLanguageCodes() {
		return languageCodes;
	}

	public Set<Long> getLanguageReferenceSets() {
		return languageReferenceSets;
	}
}
