package org.snomed.snowstorm.core.data.services.persistedcomponent;

import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.services.pojo.PersistedComponents.Builder;

public class PersistedDescriptionComponent implements PersistedComponentLoader<Description> {

	@Override
	public void setPersistedComponents(final Iterable<Description> components, final Builder builder) {
		builder.withPersistedDescriptions(components);
	}

	@Override
	public Class<Description> getComponent() {
		return Description.class;
	}
}
