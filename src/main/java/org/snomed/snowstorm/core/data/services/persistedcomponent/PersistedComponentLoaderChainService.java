package org.snomed.snowstorm.core.data.services.persistedcomponent;

import io.kaicode.elasticvc.domain.Commit;
import org.snomed.snowstorm.core.data.services.pojo.PersistedComponents;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class PersistedComponentLoaderChainService extends AbstractPersistedComponentLoader {

	private final List<PersistedComponentLoader> persistedComponentLoaders = new LinkedList<>();

	/**
	 * Creates an instance which contains a {@code List} of {@link PersistedComponentLoader}
	 * that are going to be persisted.
	 *
	 * @param persistedComponentLoaders The {@code List} of {@link PersistedComponentLoader}
	 * that are going to be persisted.
	 */
	public PersistedComponentLoaderChainService(final PersistedComponentLoader... persistedComponentLoaders) {
		if (persistedComponentLoaders == null || persistedComponentLoaders.length == 0) {
			throw new IllegalArgumentException("No persisted components specified.");
		}
		Collections.addAll(this.persistedComponentLoaders, persistedComponentLoaders);
	}

	/**
	 * Loads the persisted components that correspond to the {@link Commit}.
	 *
	 * @param commit Which is used to get the {@link io.kaicode.elasticvc.api.BranchCriteria}.
	 * @return {@link PersistedComponents} which contains all the components that
	 * are persisted and correspond to the {@link Commit}.
	 */
	@SuppressWarnings("unchecked")
	public PersistedComponents load(final Commit commit) {
		final PersistedComponents.Builder persistedComponents = PersistedComponents.newBuilder();
		persistedComponentLoaders.forEach(persistedComponentLoader -> load(commit, persistedComponentLoader, persistedComponentLoader.getComponent(), persistedComponents));
		return persistedComponents.build();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		if (!super.equals(o)) return false;
		PersistedComponentLoaderChainService that = (PersistedComponentLoaderChainService) o;
		return Objects.equals(persistedComponentLoaders, that.persistedComponentLoaders);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), persistedComponentLoaders);
	}
}
