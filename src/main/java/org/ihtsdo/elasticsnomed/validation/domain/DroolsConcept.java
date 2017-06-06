package org.ihtsdo.elasticsnomed.validation.domain;

import org.ihtsdo.elasticsnomed.core.data.domain.Concept;

import java.util.Collection;
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
				relationships.add(new DroolsRelationship(r));
			});
		}
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
	public String getId() {
		return concept.getId();
	}

	@Override
	public boolean isActive() {
		return concept.isActive();
	}

	@Override
	public boolean isPublished() {
		return concept.getEffectiveTime() != null;
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
