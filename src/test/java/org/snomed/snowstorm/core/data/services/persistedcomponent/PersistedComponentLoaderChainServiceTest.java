package org.snomed.snowstorm.core.data.services.persistedcomponent;

import org.junit.Test;

public class PersistedComponentLoaderChainServiceTest {

	@Test(expected = IllegalArgumentException.class)
	public void testCreatingPersistedComponentLoaderChainServiceThrowsExceptionWhenSpecifiedArrayIsNull() {
		new PersistedComponentLoaderChainService(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testCreatingPersistedComponentLoaderChainServiceThrowsExceptionWhenSpecifiedArrayIsEmpty() {
		new PersistedComponentLoaderChainService();
	}
}
