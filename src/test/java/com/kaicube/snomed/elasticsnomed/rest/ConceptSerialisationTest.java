package com.kaicube.snomed.elasticsnomed.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaicube.snomed.elasticsnomed.domain.ConceptView;
import com.kaicube.snomed.elasticsnomed.domain.Description;
import com.kaicube.snomed.elasticsnomed.domain.Relationship;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class ConceptSerialisationTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	public void testDeserialisation() throws IOException {
		final ConceptView concept = objectMapper.readValue(("{" +
				"'descriptions': [{'descriptionId': '123', 'acceptabilityMap': {'a': 'b'}}]," +
				"'relationships': [{'relationshipId': '200', " +
				"	'type': {'conceptId': '116680003',\"definitionStatusId\": \"900000000000074008\"}," +
				"	'destination': {'conceptId': '102263004',\"definitionStatusId\": \"900000000000074008\"}" +
				"}]" +
				"}").replace("'", "\""), ConceptView.class);
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

}
