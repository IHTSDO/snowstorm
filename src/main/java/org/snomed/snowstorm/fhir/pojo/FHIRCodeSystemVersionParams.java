package org.snomed.snowstorm.fhir.pojo;

import org.hl7.fhir.r4.model.StringType;
import org.snomed.snowstorm.fhir.services.FHIRHelper;

import static java.lang.String.format;
import static org.snomed.snowstorm.fhir.config.FHIRConstants.SNOMED_URI;
import static org.snomed.snowstorm.fhir.config.FHIRConstants.SNOMED_URI_UNVERSIONED;
import static org.snomed.snowstorm.fhir.services.FHIRCodeSystemService.SCT_ID_PREFIX;

public class FHIRCodeSystemVersionParams {

	private final String codeSystem;
	private String snomedModule;
	private String version;
	private String id;

	public FHIRCodeSystemVersionParams(String codeSystem) {
		this.codeSystem = codeSystem;
	}

	public boolean isSnomed() {
		return FHIRHelper.isSnomedUri(codeSystem) || (id != null && id.startsWith(SCT_ID_PREFIX));
	}

	public boolean isUnversionedSnomed() {
		return codeSystem != null && codeSystem.startsWith(SNOMED_URI_UNVERSIONED);
	}

	public StringType toSnomedUri() {
		if (codeSystem == null || !isSnomed()) {
			return null;
		}
		return new StringType(codeSystem + (snomedModule != null ? ( "/" + snomedModule ) : "") + (version != null ? ( "/version/" + version ) : ""));
	}

	public boolean isUnspecifiedReleasedSnomed() {
		return id == null && codeSystem != null && codeSystem.equals(SNOMED_URI) && snomedModule == null;
	}

	public FHIRCodeSystemVersionParams setVersion(String version) {
		this.version = version;
		return this;
	}

	public String getCodeSystem() {
		return codeSystem;
	}

	public String getVersion() {
		return version;
	}

	public void setSnomedModule(String snomedModule) {
		this.snomedModule = snomedModule;
	}

	public String getSnomedModule() {
		return snomedModule;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	@Override
	public String toString() {
		return "CodeSystemVersionParams{" +
				"id='" + id + '\'' +
				", system='" + codeSystem + '\'' +
				", version='" + version + '\'' +
				'}';
	}

	public String toDiagnosticString() {
		if (id != null) {
			return format("(ID:'%s')", id);
		} else if (isSnomed()) {
			return format("(%s)", toSnomedUri());
		} else if (version != null) {
			return format("(URL:'%s', Version:'%s')", codeSystem, version);
		}
		return format("(URL:'%s')", codeSystem);
	}
}
