package org.snomed.snowstorm.ecl.domain.refinement;

public enum Operator {

	childof("<!"), descendantorselfof("<<"), descendantof("<"), parentof(">!"), ancestororselfof(">>"), ancestorof(">"), memberOf("^");

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
