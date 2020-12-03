package org.snomed.snowstorm.core.data.services.persistedcomponent;

import org.junit.BeforeClass;
import org.junit.Test;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.pojo.PersistedComponents;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PersistedRelationshipComponentTest {

	private static PersistedRelationshipComponent persistedRelationshipComponent;

	@BeforeClass
	public static void init() {
		persistedRelationshipComponent = new PersistedRelationshipComponent();
	}

	@Test
	public void testSetPersistedComponents() {
		final PersistedComponents.Builder builder = PersistedComponents.newBuilder();
		persistedRelationshipComponent.setPersistedComponents(Collections.singleton(new Relationship("21103855022")), builder);
		assertEquals("21103855022", builder.build().getPersistedRelationships().iterator().next().getRelationshipId());
	}

	@Test
	public void testGetComponent() {
		assertEquals(Relationship.class, persistedRelationshipComponent.getComponent());
	}
}
