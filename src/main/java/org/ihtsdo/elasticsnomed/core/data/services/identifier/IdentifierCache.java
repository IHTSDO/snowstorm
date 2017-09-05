package org.ihtsdo.elasticsnomed.core.data.services.identifier;

import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

public class IdentifierCache {
	
	private final int namespaceId;
	private final String partitionId;
	private final int maxCapacity;
	private boolean topUpInProgress = false;
	private boolean isLocked = false;
	
	Deque<String> identifiers = new ConcurrentLinkedDeque<String>();
	
	IdentifierCache (int namespaceId, String partitionId, int maxCapacity) {
		this.namespaceId = namespaceId;
		this.partitionId = partitionId;
		this.maxCapacity = maxCapacity;
	}
	
	public int getNamespaceId() {
		return namespaceId;
	}
	public String getPartitionId() {
		return partitionId;
	}
	public int getMaxCapacity() {
		return maxCapacity;
	}
	
	public int identifiersAvailable() {
		return identifiers.size();
	}
	
	String getIdentifier() {
		return identifiers.pop();
	}

	public boolean isTopUpInProgress() {
		return topUpInProgress;
	}

	public synchronized void setTopUpInProgress(boolean topUpInProgress) {
		this.topUpInProgress = topUpInProgress;
	}

	public void topUp(List<String> newIdentifiers) {
		identifiers.addAll(newIdentifiers);
	}

	public boolean lock() {
		if (isLocked) {
			return false;
		} 
		isLocked = true;
		return true;
	}
	
	public void unlock() {
		isLocked = false;
	}

}
