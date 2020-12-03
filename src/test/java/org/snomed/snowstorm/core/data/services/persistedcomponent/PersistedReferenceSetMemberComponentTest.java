package org.snomed.snowstorm.core.data.services.persistedcomponent;

import org.junit.BeforeClass;
import org.junit.Test;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.pojo.PersistedComponents;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PersistedReferenceSetMemberComponentTest {

	private static PersistedReferenceSetMemberComponent persistedReferenceSetMemberComponent;

	@BeforeClass
	public static void init() {
		persistedReferenceSetMemberComponent = new PersistedReferenceSetMemberComponent();
	}

	@Test
	public void testSetPersistedComponents() {
		final PersistedComponents.Builder builder = PersistedComponents.newBuilder();
		persistedReferenceSetMemberComponent.setPersistedComponents(Collections.singleton(new ReferenceSetMember("9000002343000012004", "733073667", "3311111663")), builder);
		assertEquals("9000002343000012004", builder.build().getPersistedReferenceSetMembers().iterator().next().getModuleId());
		assertEquals("733073667", builder.build().getPersistedReferenceSetMembers().iterator().next().getRefsetId());
		assertEquals("3311111663", builder.build().getPersistedReferenceSetMembers().iterator().next().getReferencedComponentId());
	}

	@Test
	public void testGetComponent() {
		assertEquals(ReferenceSetMember.class, persistedReferenceSetMemberComponent.getComponent());
	}
}
