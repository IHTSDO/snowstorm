package org.snomed.snowstorm.core.data.services.persistedcomponent;

import org.springframework.stereotype.Service;

@Service
public class DefaultPersistedComponentLoaderChainService extends PersistedComponentLoaderChainService {

	/**
	 * Loads the default components that are going to be
	 * persisted.
	 */
	public DefaultPersistedComponentLoaderChainService() {
		super(new PersistedConceptComponent(), new PersistedDescriptionComponent(), new PersistedReferenceSetMemberComponent(), new PersistedRelationshipComponent());
	}
}
