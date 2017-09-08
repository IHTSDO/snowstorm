package org.ihtsdo.elasticsnomed.core.data.services.cis;

final class CISGenerateRequest {
	int namespace;
	String partitionId;
	int quantity;

	public CISGenerateRequest(int namespace, String partitionId, int quantity) {
		this.namespace = namespace;
		this.partitionId = partitionId;
		this.quantity = quantity;
	}

	public int getNamespace() {
		return namespace;
	}

	public String getPartitionId() {
		return partitionId;
	}

	public int getQuantity() {
		return quantity;
	}

	public String getSoftware() {
		return CISClient.SOFTWARE_NAME;
	}

}
