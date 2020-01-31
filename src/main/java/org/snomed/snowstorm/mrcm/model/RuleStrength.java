package org.snomed.snowstorm.mrcm.model;

public enum  RuleStrength {

	MANDATORY("723597001"), OPTIONAL("723598006");

	private String conceptId;

	RuleStrength(String conceptId) {
		this.conceptId = conceptId;
	}

	public static RuleStrength lookupByConceptId(String conceptId) {
		for (RuleStrength value : values()) {
			if (value.conceptId.equals(conceptId)) {
				return value;
			}
		}
		return null;
	}
}

