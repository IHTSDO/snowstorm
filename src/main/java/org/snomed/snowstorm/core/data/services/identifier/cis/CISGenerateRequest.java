package org.snomed.snowstorm.core.data.services.identifier.cis;

final class CISGenerateRequest implements CISBulkRequest {

	private int namespace;
	private String partitionId;
	private int quantity;

	CISGenerateRequest(int namespace, String partitionId, int quantity) {
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

	@Override
	public int size() {
		return this.quantity;
	}

}
