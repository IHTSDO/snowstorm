package org.ihtsdo.elasticsnomed.ecl.domain;

public enum Operator {

	childof("<!"), descendantorselfof("<<"), descendantof("<"), parentof(">!"), ancestororselfof(">>"), ancestorof(">");

	private String text;

	Operator(String text) {
		this.text = text;
	}

	public static Operator textLookup(String text) {
		for (Operator operator : values()) {
			if (operator.text.equals(text)) return operator;
		}
		return null;
	}
}
