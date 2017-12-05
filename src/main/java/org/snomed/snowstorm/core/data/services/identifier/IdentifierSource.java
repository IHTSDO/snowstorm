package org.snomed.snowstorm.core.data.services.identifier;

import org.snomed.snowstorm.core.data.services.ServiceException;

import java.util.Collection;
import java.util.List;

public interface IdentifierSource {

	List<Long> reserve(int namespace, String partitionId, int quantity) throws ServiceException;

	void registerIdentifiers(int namespace, Collection<Long> idsAssigned) throws ServiceException;
	
}
