package org.snomed.snowstorm.mrcm.model;

public enum ContentType {

	PRECOORDINATED("723594008"),
	NEW_PRECOORDINATED("723593002"),
	POSTCOORDINATED("723595009"),
	ALL("723596005");

	private String conceptId;

	ContentType(String conceptId) {
		this.conceptId = conceptId;
	}

	public static ContentType lookupByConceptId(String conceptId) {
		for (ContentType value : values()) {
			if (value.conceptId.equals(conceptId)) {
				return value;
			}
		}
		return null;
	}

	public String getName() {
		return name();
	}

	public boolean ruleAppliesToContentType(ContentType contentType) {
		if (contentType == NEW_PRECOORDINATED) {
			return this == ALL || this == PRECOORDINATED || this == NEW_PRECOORDINATED;
		} else if (contentType == PRECOORDINATED) {
			return this == ALL || this == PRECOORDINATED;
		} else if (contentType == POSTCOORDINATED) {
			return this == ALL || this == POSTCOORDINATED;
		} else if (contentType == ALL) {
			throw new IllegalArgumentException("Content type can not be ALL, this only applies to rule types.");
		}
		return false;
	}
}
