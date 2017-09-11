package org.ihtsdo.elasticsnomed.core.data.services.identifier.cis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class CISRegisterRequest {
	private int namespace;
	private List<CISRecord> records;
	
	public CISRegisterRequest(int namespace, Collection<String> sctIds) {
		this.namespace = namespace;
		records = new ArrayList<>();
		for (String sctId : sctIds) {
			records.add(new CISRecord(sctId));
		}
	}

	public int getNamespace() {
		return namespace;
	}

	public String getSoftware() {
		return CISClient.SOFTWARE_NAME;
	}
}
