package org.snomed.snowstorm.core.data.services.persistedcomponent;

import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.services.pojo.PersistedComponents.Builder;

public class PersistedConceptComponent implements PersistedComponentLoader<Concept> {

	@Override
	public void setPersistedComponents(final Iterable<Concept> components, final Builder builder) {
		builder.withPersistedConcepts(components);
	}

	@Override
	public Class<Concept> getComponent() {
		return Concept.class;
	}
}
