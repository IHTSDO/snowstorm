package org.snomed.snowstorm.core.data.services.persistedcomponent;

import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.pojo.PersistedComponents.Builder;

public class PersistedRelationshipComponent implements PersistedComponentLoader<Relationship> {

	@Override
	public void setPersistedComponents(final Iterable<Relationship> components, final Builder builder) {
		builder.withPersistedRelationships(components);
	}

	@Override
	public Class<Relationship> getComponent() {
		return Relationship.class;
	}
}
