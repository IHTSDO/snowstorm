package org.snomed.snowstorm.rest.pojo;

import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.core.data.domain.ConceptMicro;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.rest.View;

import java.util.List;

public record InactivationImpactResponse(
		@JsonView(View.Component.class) List<InactivationImpactConcept> affectedChildren,
		@JsonView(View.Component.class) List<InactivationImpactConcept> affectedAttributeConcepts,
		@JsonView(View.Component.class) List<InactivationImpactConcept> affectedGcis,
		@JsonView(View.Component.class) List<ReferenceSetMember> existingHistoricalAssociations,
		@JsonView(View.Component.class) int totalAffectedConcepts) {

	public record InactivationImpactConcept(
			@JsonView(View.Component.class) ConceptMicro concept,
			@JsonView(View.Component.class) List<ConceptMicro> currentParents) {
	}
}
