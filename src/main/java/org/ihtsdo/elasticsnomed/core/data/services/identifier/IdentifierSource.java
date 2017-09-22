package org.ihtsdo.elasticsnomed.core.data.services.identifier;

import java.util.Collection;
import java.util.List;

import org.ihtsdo.elasticsnomed.core.data.services.ServiceException;

public interface IdentifierSource {

	List<Long> generate(int namespace, String partitionId, int quantity) throws ServiceException;

	List<Long> reserve(int namespace, String partitionId, int quantity) throws ServiceException;

	void registerIdentifiers(int namespace, Collection<Long> idsAssigned) throws ServiceException;
	
}
