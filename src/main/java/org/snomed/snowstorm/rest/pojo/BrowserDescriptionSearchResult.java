package org.snomed.snowstorm.rest.pojo;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.rest.View;

import java.util.HashMap;
import java.util.Map;

public class BrowserDescriptionSearchResult {

	private String term;
	private boolean active;
	private String languageCode;
	private String module;
	private ConceptMini concept;
	private Map<String, Object> extraFields;

	public BrowserDescriptionSearchResult(String term, boolean active, String languageCode, String module, ConceptMini concept) {
		this.term = term;
		this.active = active;
		this.languageCode = languageCode;
		this.module = module;
		this.concept = concept;
	}

	public void addExtraField(String name, Object value) {
		if (extraFields == null) {
			extraFields = new HashMap<>();
		}
		extraFields.put(name, value);
	}

	@JsonView(value = View.Component.class)
	@JsonAnyGetter
	public Map<String, Object> getExtraFields() {
		return extraFields;
	}

	@JsonView(value = View.Component.class)
	public String getTerm() {
		return term;
	}

	@JsonView(value = View.Component.class)
	public boolean isActive() {
		return active;
	}

	@JsonView(value = View.Component.class)
	public String getLanguageCode() {
		return languageCode;
	}

	@JsonView(value = View.Component.class)
	public String getModule() {
		return module;
	}

	@JsonView(value = View.Component.class)
	public ConceptMini getConcept() {
		return concept;
	}
}
