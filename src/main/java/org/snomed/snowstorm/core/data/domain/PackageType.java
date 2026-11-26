package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The type of release package a code system is published in.
 * The tag is the text used for the type in release package and RF2 file names, e.g. {@code SnomedCT_InternationalEdition_...}.
 */
public enum PackageType {

	EDITION("Edition"),
	EXTENSION("Extension"),
	DERIVATIVE("Derivative");

	private final String tag;

	PackageType(String tag) {
		this.tag = tag;
	}

	@JsonValue
	public String getTag() {
		return tag;
	}

	/**
	 * @param value the tag or enum name, in any case, e.g. {@code Edition} or {@code EDITION}
	 * @return the matching package type, or null when the value is null or blank
	 * @throws IllegalArgumentException when the value is not a known package type
	 */
	@JsonCreator
	public static PackageType fromTag(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.trim();
		for (PackageType packageType : values()) {
			if (packageType.tag.equalsIgnoreCase(trimmed)) {
				return packageType;
			}
		}
		throw new IllegalArgumentException("Unknown package type '" + value + "', expected one of Edition, Extension or Derivative.");
	}
}
