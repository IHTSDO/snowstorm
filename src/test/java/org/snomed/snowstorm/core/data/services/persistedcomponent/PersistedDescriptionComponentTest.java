package org.snomed.snowstorm.core.data.services.persistedcomponent;

import org.junit.BeforeClass;
import org.junit.Test;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.services.pojo.PersistedComponents;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class PersistedDescriptionComponentTest {

	private static PersistedDescriptionComponent persistedDescriptionComponent;

	@BeforeClass
	public static void init() {
		persistedDescriptionComponent = new PersistedDescriptionComponent();
	}

	@Test
	public void testSetPersistedComponents() {
		final PersistedComponents.Builder builder = PersistedComponents.newBuilder();
		persistedDescriptionComponent.setPersistedComponents(Collections.singleton(new Description("This is a test")), builder);
		assertEquals("This is a test", builder.build().getPersistedDescriptions().iterator().next().getTerm());
	}

	@Test
	public void testGetComponent() {
		assertEquals(Description.class, persistedDescriptionComponent.getComponent());
	}
}
