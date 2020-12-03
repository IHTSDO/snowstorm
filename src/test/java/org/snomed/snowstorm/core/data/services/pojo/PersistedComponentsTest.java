package org.snomed.snowstorm.core.data.services.pojo;

import org.junit.Test;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.Relationship;

import java.util.Collections;
import java.util.stream.StreamSupport;

import static org.junit.Assert.assertEquals;

public class PersistedComponentsTest {

	@Test
	public void testBuilderWithNoSettingReturnsEmptyListForEachPersistedComponent() {
		final PersistedComponents persistedComponents = PersistedComponents.newBuilder().build();
		assertEquals(0L, StreamSupport.stream(persistedComponents.getPersistedConcepts().spliterator(), false).count());
		assertEquals(0L, StreamSupport.stream(persistedComponents.getPersistedDescriptions().spliterator(), false).count());
		assertEquals(0L, StreamSupport.stream(persistedComponents.getPersistedRelationships().spliterator(), false).count());
		assertEquals(0L, StreamSupport.stream(persistedComponents.getPersistedReferenceSetMembers().spliterator(), false).count());
	}

	@Test
	public void testBuilderWithPersistedConcepts() {
		final Concept concept = new Concept("3311481223");
		assertEquals(concept, PersistedComponents.newBuilder().withPersistedConcepts(Collections.singleton(concept)).build().getPersistedConcepts().iterator().next());
	}

	@Test
	public void testBuilderWithPersistedDescriptions() {
		final Description description = new Description("This is a test");
		assertEquals(description, PersistedComponents.newBuilder().withPersistedDescriptions(Collections.singleton(description)).build().getPersistedDescriptions().iterator().next());
	}

	@Test
	public void testBuilderWithPersistedRelationships() {
		final Relationship relationship = new Relationship("21103855022");
		assertEquals(relationship, PersistedComponents.newBuilder().withPersistedRelationships(Collections.singleton(relationship)).build().getPersistedRelationships().iterator().next());
	}

	@Test
	public void testBuilderWithPersistedReferenceSetMembers() {
		final ReferenceSetMember referenceSetMember = new ReferenceSetMember("9000002343000012004", "733073667", "3311111663");
		assertEquals(referenceSetMember, PersistedComponents.newBuilder().withPersistedReferenceSetMembers(
				Collections.singleton(referenceSetMember)).build().getPersistedReferenceSetMembers().iterator().next());
	}
}
