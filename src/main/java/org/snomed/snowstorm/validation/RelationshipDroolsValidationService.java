package org.snomed.snowstorm.validation;

import org.snomed.snowstorm.core.data.domain.Concepts;

public class RelationshipDroolsValidationService implements org.ihtsdo.drools.service.RelationshipService {

	private final DisposableQueryService queryService;

	RelationshipDroolsValidationService(DisposableQueryService queryService) {
		this.queryService = queryService;
	}

	@Override
	public boolean hasActiveInboundStatedRelationship(String conceptId) {
		if (conceptId.contains("-")) {
			return false;
		}
		return hasActiveInboundStatedRelationship(conceptId, null);
	}

	@Override
	public boolean hasActiveInboundStatedRelationship(String conceptId, String relationshipTypeId) {
		if (conceptId.contains("-")) {
			return false;
		}
		String ecl;
		if (Concepts.ISA.equals(relationshipTypeId)) {
			// Concept is parent of any concept
			ecl = "<!" + conceptId;
		} else if (relationshipTypeId != null) {
			// Concept is target of specific attribute type
			ecl = "*:" + relationshipTypeId + "=" + conceptId;
		} else {
			// Concept is target of any attribute type
			ecl = "*:*=" + conceptId;
		}

		return queryService.isAnyResults(queryService.createQueryBuilder(true).ecl(ecl));
	}
}
