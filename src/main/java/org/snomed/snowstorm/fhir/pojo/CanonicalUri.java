package org.snomed.snowstorm.fhir.pojo;

import org.snomed.snowstorm.fhir.services.FHIRHelper;

import java.util.Objects;

public class CanonicalUri {

	private final String system;
	private final String version;

	private CanonicalUri(String system, String version) {
		this.system = system;
		this.version = version;
	}

	public static CanonicalUri fromString(String canonicalUriString) {
		if (canonicalUriString == null) {
			return null;
		}
		String[] split = canonicalUriString.split("\\|", 2);
		String system = split[0];
		String version = split.length > 1 ? split[1] : null;
		return new CanonicalUri(system, version);
	}

	public static CanonicalUri of(String system, String version) {
		return new CanonicalUri(system, version);
	}

	public boolean isSnomed() {
		return FHIRHelper.isSnomedUri(system);
	}

	public String getSystem() {
		return system;
	}

	public String getVersion() {
		return version;
	}

	@Override
	public String toString() {
		if (system == null) {
			return null;
		} else if (version == null) {
			return system;
		} else {
			return String.join("|", system, version);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CanonicalUri that = (CanonicalUri) o;
		return Objects.equals(system, that.system) && Objects.equals(version, that.version);
	}

	@Override
	public int hashCode() {
		return Objects.hash(system, version);
	}
}
