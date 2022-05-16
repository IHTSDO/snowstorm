package org.snomed.snowstorm.fhir.pojo;

import org.hl7.fhir.r4.model.StringType;
import org.snomed.snowstorm.fhir.services.FHIRHelper;

public class CodeSystemVersionPojo {

	private final String codeSystem;
	private String version;

	public CodeSystemVersionPojo(String codeSystem) {
		this.codeSystem = codeSystem;
	}

	public CodeSystemVersionPojo(String codeSystem, String version) {
		this.codeSystem = codeSystem;
		this.version = version;
	}

	public boolean isSnomed() {
		return FHIRHelper.isSnomedUri(codeSystem);
	}

	public StringType toSnomedUri() {
		return new StringType(codeSystem + (version != null ? ( "/version/" + version ) : ""));
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getCodeSystem() {
		return codeSystem;
	}

	public String getVersion() {
		return version;
	}
}
