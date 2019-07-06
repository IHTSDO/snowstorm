package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.snomed.snowstorm.core.pojo.TermLangPojo;

import java.util.Set;

@JsonDeserialize(as = Concept.class)
public interface ConceptView {
	String getConceptId();

	TermLangPojo getFsn();

	TermLangPojo getPt();

	String getEffectiveTime();

	boolean isActive();

	String getModuleId();

	String getDefinitionStatusId();

	Description getDescription(String descriptionId);

	Set<Description> getDescriptions();

	Set<Relationship> getRelationships();

	Set<Axiom> getClassAxioms();

	Set<Axiom> getGciAxioms();
}
