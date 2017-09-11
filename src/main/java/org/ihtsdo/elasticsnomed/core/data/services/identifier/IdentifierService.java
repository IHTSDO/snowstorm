package org.ihtsdo.elasticsnomed.core.data.services.identifier;

import com.google.common.base.Strings;

import java.util.Collection;
import java.util.regex.Pattern;

import org.ihtsdo.elasticsnomed.core.data.domain.ComponentType;
import org.ihtsdo.elasticsnomed.core.data.domain.Concept;
import org.ihtsdo.elasticsnomed.core.data.domain.Description;
import org.ihtsdo.elasticsnomed.core.data.domain.Relationship;
import org.ihtsdo.elasticsnomed.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;

public class IdentifierService {
	
	private static final Pattern SCTID_PATTERN = Pattern.compile("\\d{6,18}");

	private static final String PARTITION_PART1_INTERNATIONAL = "0";
	private static final String PARTITION_PART1_EXTENSION = "1";

	private static final String PARTITION_PART2_CONCEPT = "0";
	private static final String PARTITION_PART2_DESCRIPTION = "1";
	private static final String PARTITION_PART2_RELATIONSHIP = "2";

	@Autowired
	private IdentifierCacheManager cacheManager;
	
	@Autowired
	private IdentifierStorage identifierStorage;
	
	public static boolean isConceptId(String sctid) {
		return sctid != null && SCTID_PATTERN.matcher(sctid).matches() && PARTITION_PART2_CONCEPT.equals(getPartitionIdPart(sctid));
	}

	public static boolean isDescriptionId(String sctid) {
		return sctid != null && SCTID_PATTERN.matcher(sctid).matches() && PARTITION_PART2_DESCRIPTION.equals(getPartitionIdPart(sctid));
	}
	
	public static boolean isRelationshipId(String sctid) {
		return sctid != null && SCTID_PATTERN.matcher(sctid).matches() && PARTITION_PART2_RELATIONSHIP.equals(getPartitionIdPart(sctid));
	}
	
	public static String isValidId(String sctid, ComponentType componentType) {
		String errorMsg = null;
		if (!VerhoeffCheck.validateLastChecksumDigit(sctid)) {
			errorMsg = sctid + " does not have a valid check digit";
		} else if (componentType != null) {
			boolean isValid = false;
			switch (componentType) {
				case Concept : isValid = isConceptId(sctid);
								break;
				case Description : isValid = isDescriptionId(sctid);
								break;
				case Relationship : isValid = isRelationshipId(sctid);
			}
			if (!isValid) {
				errorMsg = sctid + " is not a valid id for a " + componentType;
			}
		}
		//TODO Could also add check for expected namespace
		return errorMsg;
	}

	private static String getPartitionIdPart(String sctid) {
		if (!Strings.isNullOrEmpty(sctid) && sctid.length() > 4) {
			return sctid.substring(sctid.length() - 2, sctid.length() - 1);
		}
		return null;
	}

	private IdentifierReservedBlock getReservedBlock(int namespace, int conceptIds, int descriptionIds, int relationshipIds) throws ServiceException {
		String partition_part1 = namespace == 0 ? PARTITION_PART1_INTERNATIONAL : PARTITION_PART1_EXTENSION;
		IdentifierReservedBlock idBlock = new IdentifierReservedBlock();
		//TODO Run these in parallel
		try {
			cacheManager.populateIdBlock(idBlock, conceptIds, namespace, partition_part1 + PARTITION_PART2_CONCEPT);
			cacheManager.populateIdBlock(idBlock, descriptionIds, namespace, partition_part1 + PARTITION_PART2_DESCRIPTION);
			cacheManager.populateIdBlock(idBlock, relationshipIds, namespace, partition_part1 + PARTITION_PART2_RELATIONSHIP);
		} catch (ServiceException e) {
			throw new ServiceException ("Unable to obtain sctids", e);
		}
		return idBlock;
	}

	public void registerAssignedIds(IdentifierReservedBlock reservedBlock) throws ServiceException {
		for (ComponentType componentType : ComponentType.values()) {
			//TODO Make this call asynchronous - no need to hold up saving a concept
			//TODO Need a way of generating externally visible errors if this operation should fail,
			//otherwise identifier could be reassigned elsewhere later.
			//TODO Work out namespace for identifier blocks
			//TODO If some other part of the process fails, these ids could be returned to the cache
			identifierStorage.registerIdentifiers(0, reservedBlock.getIdsAssigned(componentType));
		}
	}

	public IdentifierReservedBlock reserveIdentifierBlock(Collection<Concept> concepts) throws ServiceException {
		//Work out how many new concept, description and relationship sctids we're going to need, and request these
		int conceptIds = 0, descriptionIds = 0, relationshipIds = 0;
		
		//TODO check the moduleId of each concept and have the reserved block store these separately
		for (Concept c : concepts) {
			if (c.getId() == null || c.getId().isEmpty()) {
				conceptIds++;
			}
				
			for (Description d : c.getDescriptions()) {
				if (d.getId() == null || d.getId().isEmpty()) {
					descriptionIds++;
				}
			}
				
			for (Relationship r : c.getRelationships()) {
				if (r.getId() == null || r.getId().isEmpty()) {
					relationshipIds++;
				}
			}
		}
		int namespace = 0;
		return getReservedBlock(namespace, conceptIds, descriptionIds, relationshipIds);
	}

}
