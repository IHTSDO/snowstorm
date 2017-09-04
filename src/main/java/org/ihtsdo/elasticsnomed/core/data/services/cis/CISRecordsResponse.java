package org.ihtsdo.elasticsnomed.core.data.services.cis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CISRecordsResponse {

	private String sctid;

	public String getSctid() {
		return sctid;
	}

	public void setSctid(String sctid) {
		this.sctid = sctid;
	}
}
