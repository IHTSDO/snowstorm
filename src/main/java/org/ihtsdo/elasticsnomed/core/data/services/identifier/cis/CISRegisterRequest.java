package org.ihtsdo.elasticsnomed.core.data.services.identifier.cis;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

final class CISRegisterRequest implements CISBulkRequest {
	
	private int namespace;
	private Collection<RegisterId> records;
	
	public CISRegisterRequest(int namespace, Collection<Long> sctIds) {
		this.namespace = namespace;
		records = sctIds.stream().map(RegisterId::new).collect(Collectors.toList());
	}

	public int getNamespace() {
		return namespace;
	}

	public String getSoftware() {
		return CISClient.SOFTWARE_NAME;
	}
	
	public Collection<RegisterId> getRecords() {
		return records;
	}

	@Override
	public int size() {
		return records.size();
	}

	public static final class RegisterId {
		private String sctid;
		private String systemId;

		public RegisterId(Long sctid) {
			this.sctid = sctid.toString();
			systemId = UUID.randomUUID().toString();
		}

		public String getSctid() {
			return sctid;
		}

		public String getSystemId() {
			return systemId;
		}
	}
}
