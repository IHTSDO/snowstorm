package org.snomed.snowstorm.core.data.services.identifier;

import org.snomed.snowstorm.core.data.services.ServiceException;

import java.util.Collection;
import java.util.List;

public interface IdentifierSource {

	List<Long> reserveIds(int namespace, String partitionId, int quantity) throws ServiceException;

	void registerIds(int namespace, Collection<Long> idsAssigned) throws ServiceException;
	
}
