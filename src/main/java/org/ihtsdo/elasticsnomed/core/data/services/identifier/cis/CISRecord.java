package org.ihtsdo.elasticsnomed.core.data.services.identifier.cis;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CISRecord implements Serializable {

	private static final long serialVersionUID = -2727499661155145447L;
	
	private String sctid;
	private String status;
	
	CISRecord() {

	}
	
	public CISRecord(Long sctid) {
		this.sctid = sctid.toString();
	}

	public String getSctid() {
		return sctid;
	}
	
	public Long getSctidAsLong() {
		return Long.parseLong(sctid);
	}

	public void setSctid(String sctid) {
		this.sctid = sctid;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
}
