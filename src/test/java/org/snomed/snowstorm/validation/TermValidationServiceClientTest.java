package org.snomed.snowstorm.validation;

import org.ihtsdo.drools.response.InvalidContent;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Description;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TermValidationServiceClientTest {

	private final TermValidationServiceClient client = new TermValidationServiceClient(null, 0.6f);

	@Test
	void testHandleResponse() throws IOException {
		final List<InvalidContent> invalidContents = new ArrayList<>();

		// The content of this response is intensionally inconsistent.
		// It's made from parts of different example responses that trigger all the error cases.
		final String dummyResponse = "/term-validation-service/response-1.json";

		client.handleResponse(client.getObjectMapper().readValue(getClass().getResourceAsStream(dummyResponse),
				TermValidationServiceClient.ValidationResponse.class),
				new Concept(Concepts.CLINICAL_FINDING).addDescription(
						new Description("2148514019", "Atypical diabetes mellitus (disorder)")
								.setTypeId(Concepts.FSN)
								.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED)),
				"MAIN/TEST/TEST-123", invalidContents);

		final Map<String, InvalidContent> idToInvalidContentMap = invalidContents.stream().collect(Collectors.toMap(InvalidContent::getRuleId, Function.identity()));
		assertEquals(4, invalidContents.size());

		final InvalidContent duplicate = idToInvalidContentMap.get(TermValidationServiceClient.DUPLICATE_RULE_ID);
		assertNotNull(duplicate);
		assertEquals("FSN is similar to description 'Atypical diabetes mellitus (disorder)' in concept 530558861000132104. Is this a duplicate?", duplicate.getMessage());
		assertEquals("404684003", duplicate.getConceptId());
		assertEquals("2148514019", duplicate.getComponentId());// FSN

		final InvalidContent similarToInactive = idToInvalidContentMap.get(TermValidationServiceClient.SIMILAR_TO_INACTIVE_RULE_ID);
		assertNotNull(similarToInactive);
		assertEquals("This concept is similar to many that have been made inactive in the past with inactivation reason 'Limited'.", similarToInactive.getMessage());
		assertEquals("404684003", similarToInactive.getConceptId());
		assertEquals("404684003", similarToInactive.getComponentId());// Same as concept

		final InvalidContent fsnCoverage = idToInvalidContentMap.get(TermValidationServiceClient.FSN_COVERAGE_RULE_ID);
		assertNotNull(fsnCoverage);
		assertEquals("The word 'left' in the FSN of this defined concept does not occur in the descriptions of any concept in the inferred relationships. " +
						"Is the description and modeling correct?",
				fsnCoverage.getMessage());
		assertEquals("404684003", fsnCoverage.getConceptId());
		assertEquals("2148514019", fsnCoverage.getComponentId());// FSN

		final InvalidContent localContext = idToInvalidContentMap.get(TermValidationServiceClient.LOCAL_CONTEXT_RULE_ID);
		assertNotNull(localContext);
		assertEquals("Description 'Atypical diabetes mellitus (disorder)' seems better suited to concept 73211009, which has description 'Diabetes mellitus (disorder)'. " +
						"Perhaps it should be moved or removed?",
				localContext.getMessage());
		assertEquals("404684003", localContext.getConceptId());
		assertEquals("2148514019", localContext.getComponentId());// FSN
	}
}
