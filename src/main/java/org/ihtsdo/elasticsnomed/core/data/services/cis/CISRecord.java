package org.ihtsdo.elasticsnomed.core.data.services.cis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CISRecord {

	private String sctid;
	
	CISRecord () {}
	
	CISRecord (String sctid) {
		this.sctid = sctid;
	}

	public String getSctid() {
		return sctid;
	}

	public void setSctid(String sctid) {
		this.sctid = sctid;
	}
}
