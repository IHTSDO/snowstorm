package org.ihtsdo.elasticsnomed.repositories.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.ihtsdo.elasticsnomed.domain.Description;
import org.ihtsdo.elasticsnomed.domain.ReferenceSetMember;
import org.ihtsdo.elasticsnomed.domain.Relationship;

import java.util.Set;

public abstract class ConceptStoreMixIn {

	@JsonIgnore
	abstract String getId();

	@JsonIgnore
	abstract int getGroupId();

	@JsonIgnore
	abstract String getInactivationIndicator();

	@JsonIgnore
	abstract ReferenceSetMember getInactivationIndicatorMember();

	@JsonIgnore
	abstract String getDefinitionStatus();

	@JsonIgnore
	abstract Set<Description> getDescriptions();

	@JsonIgnore
	abstract Set<Relationship> getRelationships();

}
