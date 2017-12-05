package org.snomed.snowstorm.core.data.services.identifier;

import org.snomed.snowstorm.core.data.services.ServiceException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class LocalIdentifierSource implements IdentifierSource {

	public static String getHackId() {
		String id;
		while ((id = "" + Math.round(Math.random() * 100000000000000000f)).length() < 15) {
		}
		return id.substring(0, 15);
	}

	@Override
	public List<Long> reserve(int namespace, String partitionId, int quantity)
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

	@Override
	public void registerIdentifiers(int namespace,
			Collection<Long> idsAssigned) throws ServiceException {
		//TODO Persist current max sequence for each namespace / partition
		//and generate in sequence rather than randomly
	}
}
