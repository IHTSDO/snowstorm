package com.kaicube.snomed.elasticsnomed.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaicube.snomed.elasticsnomed.domain.*;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

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
	public void testStoreSerialisation() throws JsonProcessingException {
		final String conceptJson = storeObjectMapper.writeValueAsString(new Concept("123", null, true, "33", "900000000000074008"));
		final JSONObject c = new JSONObject(conceptJson);

		Assert.assertEquals("900000000000074008", c.getString("definitionStatusId"));
		Assert.assertTrue(c.isNull("definitionStatus"));
	}

}
