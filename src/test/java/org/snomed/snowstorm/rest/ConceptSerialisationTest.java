package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.ConceptView;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.repositories.config.ConceptStoreMixIn;
import org.snomed.snowstorm.core.data.repositories.config.DescriptionStoreMixIn;
import org.snomed.snowstorm.core.data.repositories.config.RelationshipStoreMixIn;

import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConceptSerialisationTest {

	private final ObjectMapper generalObjectMapper = new ObjectMapper();

	private final ObjectMapper storeObjectMapper = new ObjectMapper()
			.addMixIn(Concept.class, ConceptStoreMixIn.class)
			.addMixIn(Relationship.class, RelationshipStoreMixIn.class)
			.addMixIn(Description.class, DescriptionStoreMixIn.class);

	@Test
	public void testDeserialisation() throws IOException {
		final ConceptView concept = generalObjectMapper.readValue(("{" +
				"'definitionStatus': 'PRIMITIVE'," +
				"'descriptions': [{'descriptionId': '123', 'acceptabilityMap': {'a': 'b'}}]," +
				"'relationships': [{'relationshipId': '200', " +
				"	'type': {'conceptId': '116680003',\"definitionStatus\": \"FULLY_DEFINED\"}," +
				"	'target': {'conceptId': '102263004',\"definitionStatus\": \"PRIMITIVE\"}" +
				"}]" +
				"}").replace("'", "\""), ConceptView.class);

		Assert.assertEquals("900000000000074008", concept.getDefinitionStatusId());

		Assert.assertEquals(1, concept.getDescriptions().size());

		final Description description = concept.getDescriptions().iterator().next();
		Assert.assertEquals("123", description.getDescriptionId());
		Assert.assertEquals(1, description.getAcceptabilityMap().size());
		Assert.assertEquals("b", description.getAcceptabilityMap().get("a"));
		Assert.assertEquals(1, concept.getRelationships().size());

		final Relationship relationship = concept.getRelationships().iterator().next();
		Assert.assertEquals("200", relationship.getRelationshipId());
		Assert.assertEquals("116680003", relationship.getTypeId());
		Assert.assertEquals("102263004", relationship.getDestinationId());
	}

	@Test
	public void testRESTApiSerialisation() throws JsonProcessingException {
		ObjectWriter restApiWriter = generalObjectMapper.writerWithView(View.Component.class).forType(ConceptView.class);
		final String conceptJson = restApiWriter.writeValueAsString(new Concept("123", null, true, "33", "900000000000074008"));
		System.out.println(conceptJson);
		assertFalse(conceptJson.contains("internalId"));
		assertFalse(conceptJson.contains("path"));
		assertFalse(conceptJson.contains("start"));
		assertFalse(conceptJson.contains("end"));
		assertFalse(conceptJson.contains("effectiveTimeI"));
		assertFalse(conceptJson.contains("releaseHash"));
		assertFalse(conceptJson.contains("allOwlAxiomMembers"));

		assertTrue(conceptJson.contains("fsn"));
		assertTrue(conceptJson.contains("descriptions"));
		assertTrue(conceptJson.contains("relationships"));
		assertTrue(conceptJson.contains("additionalAxioms"));
		assertTrue(conceptJson.contains("gciAxioms"));
	}

	@Test
	public void testStoreSerialisation() throws JsonProcessingException {
		final String conceptJson = storeObjectMapper.writeValueAsString(new Concept("123", null, true, "33", "900000000000074008"));
		System.out.println(conceptJson);
		assertFalse(conceptJson.contains("fsn"));
		assertFalse(conceptJson.contains("idField"));
		assertFalse(conceptJson.contains("descriptions"));
		assertFalse(conceptJson.contains("relationships"));
		assertFalse(conceptJson.contains("allOwlAxiomMembers"));
		assertFalse(conceptJson.contains("additionalAxioms"));
		assertFalse(conceptJson.contains("gciAxioms"));
		assertFalse(conceptJson.contains("allOwlAxiomMembers"));
		assertFalse(conceptJson.contains("associationTargets"));

		assertTrue(conceptJson.contains("internalId"));
		assertTrue(conceptJson.contains("path"));
		assertTrue(conceptJson.contains("start"));
		assertTrue(conceptJson.contains("end"));
		assertTrue(conceptJson.contains("effectiveTimeI"));
		assertTrue(conceptJson.contains("releaseHash"));
	}

}
