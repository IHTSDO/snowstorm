package org.snomed.snowstorm.core.data.services.identifier;

import org.snomed.cis.client.CISClient;
import org.snomed.cis.client.CISClientException;
import org.snomed.snowstorm.core.data.services.ServiceException;

import java.util.Collection;
import java.util.List;

public class SnowstormCISClient extends CISClient implements IdentifierSource {

	public SnowstormCISClient(String cisApiUrl, String username, String password, String softwareName, int timeoutSeconds) {
		super(cisApiUrl, username, password, softwareName, timeoutSeconds);
	}

	@Override
	public List<Long> reserveIds(int namespace, String partitionId, int quantity) throws ServiceException {
		try {
			return super.reserve(namespace, partitionId, quantity);
		} catch (CISClientException e) {
			throw new ServiceException("CIS client error.", e);
		}
	}

	@Override
	public void registerIds(int namespace, Collection<Long> idsAssigned) throws ServiceException {
		try {
			super.registerIdentifiers(namespace, idsAssigned);
		} catch (CISClientException e) {
			throw new ServiceException("CIS client error.", e);
		}
	}
}
