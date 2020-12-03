package org.snomed.snowstorm.core.data.services.persistedcomponent;

import org.snomed.snowstorm.core.data.domain.SnomedComponent;
import org.snomed.snowstorm.core.data.services.pojo.PersistedComponents.Builder;

/**
 * Contains the relevant operations to set and retrieve the persisted component.
 *
 * @param <T> Of the persisted component.
 */
public interface PersistedComponentLoader<T extends SnomedComponent<T>> {

	/**
	 * Sets the components inside the {@link Builder}.
	 *
	 * @param components Being set inside the {@link Builder}.
	 * @param builder    Which contains the components to be persisted.
	 */
	void setPersistedComponents(Iterable<T> components, Builder builder);

	/**
	 * Returns the component which is being persisted.
	 *
	 * @return The component which is being persisted.
	 */
	Class<T> getComponent();
}
