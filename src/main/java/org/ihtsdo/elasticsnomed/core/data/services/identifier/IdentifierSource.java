package org.ihtsdo.elasticsnomed.core.data.services.identifier;

import java.util.Collection;
import java.util.List;

import org.ihtsdo.elasticsnomed.core.data.services.ServiceException;

public interface IdentifierSource {

	List<String> generate(int namespace, String partitionId, int quantity) throws ServiceException;

	List<String> reserve(int namespace, String partitionId, int quantity) throws ServiceException;

	void registerIdentifiers(int namespace, Collection<String> idsAssigned) throws ServiceException;
	
}
