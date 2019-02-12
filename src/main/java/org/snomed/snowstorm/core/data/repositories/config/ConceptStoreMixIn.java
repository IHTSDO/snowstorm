package org.snomed.snowstorm.core.data.repositories.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.snomed.snowstorm.core.data.domain.Axiom;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.Relationship;

import java.util.Map;
import java.util.Set;

public abstract class ConceptStoreMixIn {

	@JsonIgnore
	abstract String getId();

	@JsonIgnore
	abstract String getFsn();

	@JsonIgnore
	abstract int getGroupId();

	@JsonIgnore
	abstract String getInactivationIndicator();

	@JsonIgnore
	abstract ReferenceSetMember getInactivationIndicatorMember();

	@JsonIgnore
	abstract String getDefinitionStatus();

	@JsonIgnore
	abstract Map<String, Set<String>> getAssociationTargets();

	@JsonIgnore
	abstract Set<Description> getDescriptions();

	@JsonIgnore
	abstract Set<Relationship> getRelationships();

	@JsonIgnore
	abstract Set<Axiom> getClassAxioms();

	@JsonIgnore
	abstract Set<Axiom> getGciAxioms();

	@JsonIgnore
	abstract Set<ReferenceSetMember> getAllOwlAxiomMembers();

}
