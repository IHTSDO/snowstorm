package org.ihtsdo.elasticsnomed.core.data.services.identifier.cis;

import java.util.ArrayList;
import java.util.Collection;

final class CISRegisterRequest implements CISBulkRequest {
	
	private int namespace;
	private Collection<CISRecord> records;
	
	public CISRegisterRequest(int namespace, Collection<Long> sctIds) {
		this.namespace = namespace;
		records = new ArrayList<>();
		for (Long sctId : sctIds) {
			records.add(new CISRecord(sctId));
		}
	}

	public int getNamespace() {
		return namespace;
	}

	public String getSoftware() {
		return CISClient.SOFTWARE_NAME;
	}
	
	public Collection<CISRecord> getRecords() {
		return records;
	}

	@Override
	public int size() {
		return records.size();
	}
}
