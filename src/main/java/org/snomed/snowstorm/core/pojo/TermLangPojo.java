package org.snomed.snowstorm.core.pojo;

import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.rest.View;

public class TermLangPojo {

	private String term;
	private String lang;

	public TermLangPojo() {
	}

	public TermLangPojo(String term, String lang) {
		this.term = term;
		this.lang = lang;
	}

	@JsonView(value = View.Component.class)
	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		this.term = term;
	}

	@JsonView(value = View.Component.class)
	public String getLang() {
		return lang;
	}

	public void setLang(String lang) {
		this.lang = lang;
	}
}
