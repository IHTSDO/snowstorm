package org.snomed.snowstorm.validation;

import io.kaicode.elasticvc.api.BranchCriteria;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.springframework.data.domain.PageRequest;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class RelationshipDroolsValidationService implements org.ihtsdo.drools.service.RelationshipService {

	private final QueryService queryService;
	private final String branchPath;
	private BranchCriteria branchCriteria;

	RelationshipDroolsValidationService(String branchPath, BranchCriteria branchCriteria, QueryService queryService) {
		this.branchPath = branchPath;
		this.branchCriteria = branchCriteria;
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
			ecl = "<" + conceptId;
		} else if (relationshipTypeId != null) {
			// Concept is target of specific attribute type
			ecl = "*:" + relationshipTypeId + "=" + conceptId;
		} else {
			// Concept is target of any attribute type
			ecl = "*:*=" + conceptId;
		}

		return queryService.searchForIds(queryService.createQueryBuilder(true).ecl(ecl), branchPath, branchCriteria, PageRequest.of(0, 1)).getTotalElements() > 0;
	}
}
