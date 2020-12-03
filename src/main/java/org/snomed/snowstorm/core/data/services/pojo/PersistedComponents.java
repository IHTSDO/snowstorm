package org.snomed.snowstorm.core.data.services.pojo;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.Relationship;

import java.util.Collections;
import java.util.Objects;

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

	public static Builder newBuilder() {
		return new Builder();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PersistedComponents that = (PersistedComponents) o;
		return Objects.equals(persistedConcepts, that.persistedConcepts) &&
				Objects.equals(persistedDescriptions, that.persistedDescriptions) &&
				Objects.equals(persistedRelationships, that.persistedRelationships) &&
				Objects.equals(persistedReferenceSetMembers, that.persistedReferenceSetMembers);
	}

	@Override
	public int hashCode() {
		return Objects.hash(persistedConcepts, persistedDescriptions, persistedRelationships, persistedReferenceSetMembers);
	}

	public static class Builder {

		private Iterable<Concept> persistedConcepts;
		private Iterable<Description> persistedDescriptions;
		private Iterable<Relationship> persistedRelationships;
		private Iterable<ReferenceSetMember> persistedReferenceSetMembers;

		@CanIgnoreReturnValue
		public final Builder withPersistedConcepts(final Iterable<Concept> persistedConcepts) throws NullPointerException {
			this.persistedConcepts = Objects.requireNonNull(persistedConcepts);
			return this;
		}

		@CanIgnoreReturnValue
		public final Builder withPersistedDescriptions(final Iterable<Description> persistedDescriptions) {
			this.persistedDescriptions = persistedDescriptions;
			return this;
		}

		@CanIgnoreReturnValue
		public final Builder withPersistedRelationships(final Iterable<Relationship> persistedDescriptions) {
			this.persistedRelationships = persistedDescriptions;
			return this;
		}

		@CanIgnoreReturnValue
		public final Builder withPersistedReferenceSetMembers(final Iterable<ReferenceSetMember> persistedDescriptions) {
			this.persistedReferenceSetMembers = persistedDescriptions;
			return this;
		}

		public final PersistedComponents build() {
			persistedConcepts = persistedConcepts == null ? Collections.emptyList() : persistedConcepts;
			persistedDescriptions = persistedDescriptions == null ? Collections.emptyList() : persistedDescriptions;
			persistedRelationships = persistedRelationships == null ? Collections.emptyList() : persistedRelationships;
			persistedReferenceSetMembers = persistedReferenceSetMembers == null ? Collections.emptyList() : persistedReferenceSetMembers;
			return new PersistedComponents(persistedConcepts, persistedDescriptions, persistedRelationships, persistedReferenceSetMembers);
		}
	}
}
