package org.ihtsdo.elasticsnomed.core.data.services.identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.ihtsdo.elasticsnomed.core.data.services.ServiceException;

public class DummyIdentifierSource implements IdentifierSource {

	@Override
	public List<Long> generate(int namespace, String partitionId, int quantity)
			throws ServiceException {
		List<Long> response = new ArrayList<>();
		for (int x=0; x<quantity; x++) {
			String sctidWithoutCheck = getHackId()  + partitionId;
			char verhoeff = VerhoeffCheck.calculateChecksum(sctidWithoutCheck, 0, false);
			String sctid = sctidWithoutCheck + verhoeff;
			response.add(Long.parseLong(sctid));
		}
		return response;
	}

	public static String getHackId() {
		String id;
		while ((id = "" + Math.round(Math.random() * 100000000000000000f)).length() < 15) {
		}
		return id.substring(0, 15);
	}

	@Override
	public List<Long> reserve(int namespace, String partitionId, int quantity)
			throws ServiceException {
		//The difference between reserved and generated is internal to CIS, so we can just re-use that method
		return generate(namespace, partitionId, quantity);
	}

	@Override
	public void registerIdentifiers(int namespace,
			Collection<Long> idsAssigned) throws ServiceException {
		//Nothing to do here
	}
}
