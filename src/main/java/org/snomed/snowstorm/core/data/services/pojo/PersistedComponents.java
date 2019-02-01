package org.snomed.snowstorm.core.data.services.pojo;

import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.Relationship;

import java.util.Collections;

public class PersistedComponents {

	private final Iterable<Concept> persistedConcepts;
	private final Iterable<Description> persistedDescriptions;
	private final Iterable<Relationship> persistedRelationships;
	private final Iterable<ReferenceSetMember> persistedReferenceSetMembers;

	public PersistedComponents() {
		persistedConcepts = Collections.emptyList();
		persistedDescriptions = Collections.emptyList();
		persistedRelationships = Collections.emptyList();
		persistedReferenceSetMembers = Collections.emptyList();
	}

	public PersistedComponents(Iterable<Concept> persistedConcepts, Iterable<Description> persistedDescriptions,
			Iterable<Relationship> persistedRelationships, Iterable<ReferenceSetMember> persistedReferenceSetMembers) {

		this.persistedConcepts = persistedConcepts;
		this.persistedDescriptions = persistedDescriptions;
		this.persistedRelationships = persistedRelationships;
		this.persistedReferenceSetMembers = persistedReferenceSetMembers;
	}

	public Iterable<Concept> getPersistedConcepts() {
		return persistedConcepts;
	}

	public Iterable<Description> getPersistedDescriptions() {
		return persistedDescriptions;
	}

	public Iterable<Relationship> getPersistedRelationships() {
		return persistedRelationships;
	}

	public Iterable<ReferenceSetMember> getPersistedReferenceSetMembers() {
		return persistedReferenceSetMembers;
	}
}
