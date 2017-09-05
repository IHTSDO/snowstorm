package org.ihtsdo.elasticsnomed.core.data.services.identifier;

import com.google.common.base.Strings;

import java.util.LinkedList;
import java.util.regex.Pattern;

import org.ihtsdo.elasticsnomed.core.data.domain.ComponentType;
import org.ihtsdo.elasticsnomed.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;

public class IdentifierService {
	
	public static final Pattern SCTID_PATTERN = Pattern.compile("\\d{7,18}");
	
	public static final String PARTITION_PART1_INTERNATIONAL = "0";
	public static final String PARTITION_PART1_EXTENSION = "1";
	
	public static final String PARTITION_PART2_CONCEPT = "0";
	public static final String PARTITION_PART2_DESCRIPTION = "1";
	public static final String PARTITION_PART2_RELATIONSHIP = "2";

	@Autowired
	IdentifierCacheManager cacheManager; 
	
	@Autowired
	IdentifierStorage identifierStorage;
	
	public static boolean isConceptId(String sctid) {
		return sctid != null && SCTID_PATTERN.matcher(sctid).matches() && PARTITION_PART2_CONCEPT.equals(getPartitionIdPart(sctid));
	}

	public static boolean isDescriptionId(String sctid) {
		return sctid != null && SCTID_PATTERN.matcher(sctid).matches() && PARTITION_PART2_DESCRIPTION.equals(getPartitionIdPart(sctid));
	}

	private static String getPartitionIdPart(String sctid) {
		if (!Strings.isNullOrEmpty(sctid) && sctid.length() > 4) {
			return sctid.substring(sctid.length() - 2, sctid.length() - 1);
		}
		return null;
	}

	public IdentifierReservedBlock getReservedBlock(int namespace, int conceptIds, int descriptionIds, int relationshipIds) throws ServiceException {
		String partition_part1 = namespace == 0 ? PARTITION_PART1_INTERNATIONAL : PARTITION_PART1_EXTENSION;
		IdentifierReservedBlock idBlock = new IdentifierReservedBlock();
		//TODO Run these in parallel
		try {
			cacheManager.populateIdBlock(idBlock, conceptIds, namespace, partition_part1 + PARTITION_PART2_CONCEPT);
			cacheManager.populateIdBlock(idBlock, descriptionIds, namespace, partition_part1 + PARTITION_PART2_DESCRIPTION);
			cacheManager.populateIdBlock(idBlock, relationshipIds, namespace, partition_part1 + PARTITION_PART2_RELATIONSHIP);
		} catch (ServiceException | InterruptedException e) {
			throw new ServiceException ("Unable to obtain sctids", e);
		}
		return idBlock;
	}

	public void registerAssignedIds(IdentifierReservedBlock reservedBlock) {
		for (ComponentType componentType : ComponentType.values()) {
			//TODO Make this call asynchronous - no need to hold up saving a concept
			//TODO Need a way of generating externally visible errors if this operation should fail,
			//otherwise identifier could be reassigned elsewhere later.
			//TODO Work out namespace for identifier blocks
			//TODO If some other part of the process fails, these ids could be returned to the cache
			identifierStorage.registerIdentifiers(0, reservedBlock.getIdsAssigned(componentType));
		}
	}

}
