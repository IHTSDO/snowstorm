package org.snomed.snowstorm.core.pojo;

public class TermLangPojo {

	private String term;
	private String lang;

	public TermLangPojo() {
	}

	public TermLangPojo(String term, String lang) {
		this.term = term;
		this.lang = lang;
	}

	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		this.term = term;
	}

	public String getLang() {
		return lang;
	}

	public void setLang(String lang) {
		this.lang = lang;
	}
}
