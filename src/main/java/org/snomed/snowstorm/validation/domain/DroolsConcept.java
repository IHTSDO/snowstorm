package org.snomed.snowstorm.validation.domain;

import org.ihtsdo.drools.domain.OntologyAxiom;
import org.snomed.snowstorm.core.data.domain.Concept;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DroolsConcept implements org.ihtsdo.drools.domain.Concept {

	private final Concept concept;
	private final Set<DroolsDescription> descriptions;
	private final Set<DroolsRelationship> relationships;

	public DroolsConcept(Concept concept) {
		this.concept = concept;
		String conceptId = concept.getConceptId();

		descriptions = new HashSet<>();
		if (concept.getDescriptions() != null) {
			concept.getDescriptions().forEach(d -> {
				d.setConceptId(conceptId);
				descriptions.add(new DroolsDescription(d));
			});
		}

		relationships = new HashSet<>();
		if (concept.getRelationships() != null) {
			concept.getRelationships().forEach(r -> {
				r.setSourceId(conceptId);
				relationships.add(new DroolsRelationship(null, r));
			});
		}

		if (concept.getAdditionalAxioms() != null) {
			concept.getAdditionalAxioms().forEach(axiom -> {
				axiom.getRelationships().forEach(r -> {
					r.setSourceId(conceptId);
					relationships.add(new DroolsRelationship(axiom.getAxiomId(), r));
				});
			});
		}
		if (concept.getGciAxioms() != null) {
			concept.getGciAxioms().forEach(axiom -> {
				axiom.getRelationships().forEach(r -> {
					r.setSourceId(conceptId);
					relationships.add(new DroolsRelationship(axiom.getAxiomId(), r));
				});
			});
		}
		// TODO: Validate 'ontology axioms' when these are implemented. e.g. property chains.
	}

	@Override
	public String getDefinitionStatusId() {
		return concept.getDefinitionStatusId();
	}

	@Override
	public Collection<? extends org.ihtsdo.drools.domain.Description> getDescriptions() {
		return descriptions;
	}

	@Override
	public Collection<? extends org.ihtsdo.drools.domain.Relationship> getRelationships() {
		return relationships;
	}

	@Override
	public Collection<? extends OntologyAxiom> getOntologyAxioms() {
		return Collections.emptySet();
	}

	@Override
	public String getId() {
		return concept.getId();
	}

	@Override
	public boolean isActive() {
		return concept.isActive();
	}

	@Override
	public boolean isPublished() {
		return concept.getEffectiveTimeI() != null;
	}

	@Override
	public boolean isReleased() {
		return concept.isReleased();
	}

	@Override
	public String getModuleId() {
		return concept.getModuleId();
	}
}
