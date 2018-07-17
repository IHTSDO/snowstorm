package org.snomed.snowstorm.core.data.services.identifier;

import org.snomed.snowstorm.core.data.domain.ComponentType;
import org.snomed.snowstorm.core.data.services.RuntimeServiceException;

import java.util.*;

public class IdentifierReservedBlock {

	private Map<ComponentType, Queue<Long>> idsReserved = new HashMap<>();
	private Map<ComponentType, Queue<Long>> idsAssigned = new HashMap<>();
	
	IdentifierReservedBlock() {
		for (ComponentType componentType : ComponentType.values()) {
			idsReserved.put(componentType, new LinkedList<>());
			idsAssigned.put(componentType, new LinkedList<>());
		}
	}

	public Long getNextId(ComponentType componentType) {
		Long id = idsReserved.get(componentType).poll();
		
		if (id == null) {
			throw new RuntimeServiceException("Unexpected (excessive?) request for identifier of type " + componentType);
		}
		idsAssigned.get(componentType).add(id);
		return id;
	}
	
	void addId(ComponentType componentType, Long sctId) {
		idsReserved.get(componentType).add(sctId);
	}

	public void addAll(ComponentType componentType, List<Long> sctIds) {
		idsReserved.get(componentType).addAll(sctIds);
	}

	Collection<Long> getIdsAssigned(ComponentType componentType) {
		return idsAssigned.get(componentType);
	}
	
	public int size(ComponentType componentType) {
		return idsReserved.get(componentType).size();
	}
}
