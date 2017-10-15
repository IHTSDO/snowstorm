package org.ihtsdo.elasticsnomed.ecl.domain.query;

import java.util.Map;
import java.util.Set;

public class ESQuery {

	private ESBooleanQuery bool;
	private Map<String, String> term;
	private Map<String, Set<String>> terms;

	public ESQuery() {
	}

	public ESBooleanQuery getBool() {
		return bool;
	}

	public void setBool(ESBooleanQuery bool) {
		this.bool = bool;
	}

	public Map<String, String> getTerm() {
		return term;
	}

	public void setTerm(Map<String, String> term) {
		this.term = term;
	}

	public Map<String, Set<String>> getTerms() {
		return terms;
	}

	public void setTerms(Map<String, Set<String>> terms) {
		this.terms = terms;
	}
}
