package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import org.ihtsdo.drools.domain.Component;
import org.ihtsdo.drools.response.InvalidContent;
import org.ihtsdo.drools.response.Severity;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.repositories.config.ConceptStoreMixIn;
import org.snomed.snowstorm.core.data.repositories.config.DescriptionStoreMixIn;
import org.snomed.snowstorm.core.data.repositories.config.RelationshipStoreMixIn;
import org.snomed.snowstorm.rest.config.ComponentMixIn;
import org.snomed.snowstorm.rest.config.InvalidContentMixIn;
import org.snomed.snowstorm.validation.domain.DroolsConcept;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ConceptSerialisationTest {

	/*
	Mirrors SecurityAndUriConfig.getGeneralMapper(). This was previously a bare ObjectMapper, whose
	Jackson 2 default of DEFAULT_VIEW_INCLUSION=true let untagged properties survive a view - a
	configuration production has never used, since it disables view inclusion. Now that the mapper
	matches production the REST tests below can exercise the real @JsonView filtering.
	*/
	private final ObjectMapper generalObjectMapper = JsonMapper.builderWithJackson2Defaults()
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL).withContentInclusion(JsonInclude.Include.NON_NULL))
			// These two carry the @JsonView tags for the Drools validation types, so the view keeps them
			.addMixIn(InvalidContent.class, InvalidContentMixIn.class)
			.addMixIn(Component.class, ComponentMixIn.class)
			.build();

	private final ObjectMapper storeObjectMapper = JsonMapper.builderWithJackson2Defaults()
			.addMixIn(Concept.class, ConceptStoreMixIn.class)
			.addMixIn(Relationship.class, RelationshipStoreMixIn.class)
			.addMixIn(Description.class, DescriptionStoreMixIn.class)
			.build();

	@Test
	void testDeserialisation() throws IOException {
		final ConceptView concept = generalObjectMapper.readValue(("{" +
				"'definitionStatus': 'PRIMITIVE'," +
				"'descriptions': [{'descriptionId': '123', 'acceptabilityMap': {'a': 'b'}}]," +
				"'relationships': [{'relationshipId': '200', " +
				"	'type': {'conceptId': '116680003',\"definitionStatus\": \"FULLY_DEFINED\"}," +
				"	'target': {'conceptId': '102263004',\"definitionStatus\": \"PRIMITIVE\"}" +
				"}]" +
				"}").replace("'", "\""), ConceptView.class);

		assertEquals("900000000000074008", concept.getDefinitionStatusId());

		assertEquals(1, concept.getDescriptions().size());

		final Description description = concept.getDescriptions().iterator().next();
		assertEquals("123", description.getDescriptionId());
		assertEquals(1, description.getAcceptabilityMap().size());
		assertEquals("b", description.getAcceptabilityMap().get("a"));
		assertEquals(1, concept.getRelationships().size());

		final Relationship relationship = concept.getRelationships().iterator().next();
		assertEquals("200", relationship.getRelationshipId());
		assertEquals("116680003", relationship.getTypeId());
		assertEquals("102263004", relationship.getDestinationId());
	}

	@Test
	void testRESTApiSerialisation() throws JacksonException {
		// As the REST layer writes it: @JsonView(View.Component) from the controller method, and no forType -
		// AbstractJacksonHttpMessageConverter only narrows to the declared type for container types.
		ObjectWriter restApiWriter = generalObjectMapper.writerWithView(View.Component.class);
		Concept concept = new Concept("123", null, true, "33", "900000000000074008");
		concept.setDescendantCount(123L);
		final String conceptJson = restApiWriter.writeValueAsString(concept);
		System.out.println(conceptJson);
		// Property names are matched with their quotes and colon: a bare "end" also matches descendantCount
		assertFalse(conceptJson.contains("\"internalId\":"));
		assertFalse(conceptJson.contains("\"path\":"));
		assertFalse(conceptJson.contains("\"start\":"));
		assertFalse(conceptJson.contains("\"end\":"));
		assertFalse(conceptJson.contains("\"effectiveTimeI\":"));
		assertFalse(conceptJson.contains("\"releaseHash\":"));
		assertFalse(conceptJson.contains("\"allOwlAxiomMembers\":"));

		// descendantCount is @JsonView(View.Component) tagged and the API does return it when populated.
		// It was previously absent here only because the test narrowed the writer to ConceptView, which
		// the REST layer does not do for a single (non-container) return value.
		assertTrue(conceptJson.contains("\"descendantCount\":"));
		assertTrue(conceptJson.contains("\"fsn\":"));
		assertTrue(conceptJson.contains("\"pt\":"));
		assertTrue(conceptJson.contains("\"descriptions\":"));
		assertTrue(conceptJson.contains("\"relationships\":"));
		assertTrue(conceptJson.contains("\"classAxioms\":"));
		assertTrue(conceptJson.contains("\"gciAxioms\":"));
	}

	/*
	The ConceptView surface as the REST API actually exercises it, in both directions and nothing else:
	write a response the way the controllers do (@JsonView(View.Component), runtime class - see
	testRESTApiSerialisation), then read it back the way createConcept/updateConcept bind it
	(@RequestBody ConceptView). That is the round trip a client performs when it GETs a browser concept,
	edits it and PUTs it back, and it is the only place ConceptView is load-bearing: both controllers
	cast the bound body straight to Concept, which is safe only because of @JsonDeserializeAs.

	Note ConceptView is never the *serialisation* type - every ConceptView return value is a single
	object, and AbstractJacksonHttpMessageConverter narrows to the declared type only for container
	types - so there is deliberately no test writing through forType(ConceptView.class). Under the
	production mapper that combination emits "{}" anyway: the @JsonView tags are on Concept, not on the
	interface, leaving every property untagged for DEFAULT_VIEW_INCLUSION=false to drop.
	*/
	@Test
	void testConceptViewSurvivesRESTApiRoundTrip() throws JacksonException {
		Concept concept = new Concept("123", null, true, "33", "900000000000074008");
		concept.addDescription(new Description("d1", "Round trip test"));
		concept.addRelationship(new Relationship("r1", "116680003", "102263004"));
		concept.addAxiom(new Relationship("116680003", "102263004"));
		concept.addGeneralConceptInclusionAxiom(new Relationship("116680003", "138875005"));
		concept.addIdentifier(new Identifier("alt-1", null, true, "33", "705114005", "123"));

		// Out: exactly as the controllers write a response
		String responseJson = generalObjectMapper.writerWithView(View.Component.class).writeValueAsString(concept);
		System.out.println(responseJson);

		// In: exactly as createConcept/updateConcept bind a request body
		ConceptView roundTripped = generalObjectMapper.readValue(responseJson, ConceptView.class);

		// The cast both controllers perform on the bound body
		assertInstanceOf(Concept.class, roundTripped);

		assertEquals("123", roundTripped.getConceptId());
		assertTrue(roundTripped.isActive());
		assertEquals("33", roundTripped.getModuleId());
		assertEquals("900000000000074008", roundTripped.getDefinitionStatusId());
		assertEquals(1, roundTripped.getDescriptions().size());
		assertEquals("Round trip test", roundTripped.getDescription("d1").getTerm());
		assertEquals(1, roundTripped.getRelationships().size());
		assertEquals(1, roundTripped.getClassAxioms().size());
		assertEquals(1, roundTripped.getGciAxioms().size());
		assertEquals(1, roundTripped.getIdentifiers().size());
		assertEquals("alt-1", roundTripped.getIdentifiers().get(0).getAlternateIdentifier());
	}

	@Test
	void testCreateConceptFailsAfterValidationSerialisation() throws JacksonException {
		final Concept concept = new Concept("123", null, true, "33", "900000000000074008");
		final InvalidContent invalidContent = new InvalidContent("123", new DroolsConcept(concept), "This is a test to see the serialised content", Severity.ERROR);
		concept.setValidationResults(Collections.singletonList(invalidContent));
		final String conceptJson = generalObjectMapper.writerWithView(View.Component.class).writeValueAsString(concept);
		assertNotNull(conceptJson);
		assertTrue(conceptJson.contains("conceptId"));
		assertTrue(conceptJson.contains("component"));
		assertTrue(conceptJson.contains("published"));
		assertTrue(conceptJson.contains("active"));
		assertTrue(conceptJson.contains("moduleId"));
		assertTrue(conceptJson.contains("released"));
		assertTrue(conceptJson.contains("id"));
		assertTrue(conceptJson.contains("message"));
		assertTrue(conceptJson.contains("severity"));
		assertTrue(conceptJson.contains("ignorePublishedCheck"));
		assertTrue(conceptJson.contains("published"));
	}

	@Test
	void testStoreSerialisation() throws JacksonException {
		// Dummy data to serialise
		Concept concept = new Concept("123", null, true, "33", "900000000000074008");
		concept.setDescendantCount(123L);

		final String conceptJson = storeObjectMapper.writeValueAsString(concept);
		System.out.println(conceptJson);

		// Concept fields which should not be serialised
		assertFalse(conceptJson.contains("fsn"));
		assertFalse(conceptJson.contains("\"fsn\""));
		assertFalse(conceptJson.contains("\"pt\""));
		assertFalse(conceptJson.contains("idField"));
		assertFalse(conceptJson.contains("descriptions"));
		assertFalse(conceptJson.contains("relationships"));
		assertFalse(conceptJson.contains("allOwlAxiomMembers"));
		assertFalse(conceptJson.contains("classAxioms"));
		assertFalse(conceptJson.contains("gciAxioms"));
		assertFalse(conceptJson.contains("allOwlAxiomMembers"));
		assertFalse(conceptJson.contains("associationTargets"));
		assertFalse(conceptJson.contains("descendantCount"));

		assertTrue(conceptJson.contains("internalId"));
		assertTrue(conceptJson.contains("path"));
		assertTrue(conceptJson.contains("start"));
		assertTrue(conceptJson.contains("end"));
		assertTrue(conceptJson.contains("effectiveTimeI"));
		assertTrue(conceptJson.contains("releaseHash"));


		Description description = new Description("1234", 20200131, false, "123123", "123", "en", Concepts.FSN, "term", Concepts.ENTIRE_TERM_CASE_SENSITIVE);
		description.addLanguageRefsetMember(Concepts.GB_EN_LANG_REFSET, Concepts.PREFERRED);

		ReferenceSetMember inactivationIndicatorMember = new ReferenceSetMember();
		inactivationIndicatorMember.setAdditionalField("valueId", Concepts.OUTDATED);
		description.addInactivationIndicatorMember(inactivationIndicatorMember);

		ReferenceSetMember member = new ReferenceSetMember("123123", Concepts.NOT_SEMANTICALLY_EQUIVALENT, "1234");
		member.setAdditionalField("targetComponentId", "1231235");
		description.addAssociationTargetMember(member);

		final String descriptionJson = storeObjectMapper.writeValueAsString(description);
		System.out.println(descriptionJson);
		// Description fields (or name prefix) which should not be serialised
		assertFalse(descriptionJson.contains("acceptability"));
		assertFalse(descriptionJson.contains("inactivation"));
		assertFalse(descriptionJson.contains("association"));


	}

	@Test
	public void writeValueAsString_ShouldReturnCorrectString_WhenWritingOldDomain() throws JacksonException {
		//given
		final Relationship relationship = new Relationship("200001001", 20170131, true, "900000000000012004", "900000000000441003", "138875005", 0, "116680003", "900000000000011006", "900000000000451002");

		//when
		final String result = storeObjectMapper.writeValueAsString(relationship);
		final boolean containsDestinationId = result.contains("destinationId");
		final boolean containsConcreteValue = result.contains("concreteValue");

		//then
		assertTrue(containsDestinationId);
		assertFalse(containsConcreteValue);
	}

	@Test
	public void writeValueAsString_ShouldReturnCorrectString_WhenWritingConcreteString() throws JacksonException {
		//given
		final Relationship relationship = new Relationship("200001001", 20170131, true, "900000000000012004", "900000000000441003", "\"Two pills two times a day.\"", 0, "116680003", "900000000000011006", "900000000000451002");
		relationship.setConcreteValue("\"Two pills two times a day.\"", "str");

		//when
		final String result = storeObjectMapper.writeValueAsString(relationship);
		final boolean containsDestinationId = result.contains("destinationId");
		final boolean containsConcreteValue = result.contains("concreteValue");

		//then
		assertFalse(containsDestinationId);
		assertTrue(containsConcreteValue);
	}

	@Test
	public void writeValueAsString_ShouldReturnCorrectString_WhenWritingConcreteInteger() throws JacksonException {
		//given
		final Relationship relationship = new Relationship("200001001", 20170131, true, "900000000000012004", "900000000000441003", "#3.14", 0, "116680003", "900000000000011006", "900000000000451002");
		relationship.setConcreteValue("#2", "int");

		//when
		final String result = storeObjectMapper.writeValueAsString(relationship);
		final boolean containsDestinationId = result.contains("destinationId");
		final boolean containsConcreteValue = result.contains("concreteValue");

		//then
		assertFalse(containsDestinationId);
		assertTrue(containsConcreteValue);
	}

	@Test
	public void writeValueAsString_ShouldReturnCorrectString_WhenWritingConcreteDecimal() throws JacksonException {
		//given
		final Relationship relationship = new Relationship("200001001", 20170131, true, "900000000000012004", "900000000000441003", "#3.14", 0, "116680003", "900000000000011006", "900000000000451002");
		relationship.setConcreteValue("#3.14", "dec");

		//when
		final String result = storeObjectMapper.writeValueAsString(relationship);
		final boolean containsDestinationId = result.contains("destinationId");
		final boolean containsConcreteValue = result.contains("concreteValue");

		//then
		assertFalse(containsDestinationId);
		assertTrue(containsConcreteValue);
	}

	/*
	 * When deserializing a Relationship with a concrete value,
	 * the Relationship.Value field should not be present.
	 * This is difficult to assert as 'value' is ambiguous with
	 * Relationship.ConcreteValue.Value.
	 *
	 * Therefore, the assertion is for the format of Relationship.Value.
	 * */
	@Test
	public void writeValueAsString_ShouldNotReturnRelationshipValueField_WhenWritingConcreteData() throws JacksonException {
		//given
		final Relationship relationship = new Relationship("200001001", 20170131, true, "900000000000012004", "900000000000441003", "#3.14", 0, "116680003", "900000000000011006", "900000000000451002");
		relationship.setConcreteValue("#3.14", "dec");

		//when
		final String result = storeObjectMapper.writeValueAsString(relationship);
		final boolean containsValue = result.contains("\"value\":\"#3.14\"");

		//then
		assertFalse(containsValue);
	}
}
