package org.snomed.snowstorm.core.data.services.persistedcomponent;

import org.junit.BeforeClass;
import org.junit.Test;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.services.pojo.PersistedComponents;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PersistedConceptComponentTest {

	private static PersistedConceptComponent persistedConceptComponent;

	@BeforeClass
	public static void init() {
		persistedConceptComponent = new PersistedConceptComponent();
	}

	@Test
	public void testSetPersistedComponents() {
		final PersistedComponents.Builder builder = PersistedComponents.newBuilder();
		persistedConceptComponent.setPersistedComponents(Collections.singleton(new Concept("3311221773")), builder);
		assertEquals("3311221773", builder.build().getPersistedConcepts().iterator().next().getConceptId());
	}

	@Test
	public void testGetComponent() {
		assertEquals(Concept.class, persistedConceptComponent.getComponent());
	}
}
