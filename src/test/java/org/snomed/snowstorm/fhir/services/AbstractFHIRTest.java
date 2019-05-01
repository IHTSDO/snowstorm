package org.snomed.snowstorm.fhir.services;

import java.util.Collections;

import org.hl7.fhir.dstu3.model.OperationOutcome.IssueType;
import org.hl7.fhir.dstu3.model.Parameters.ParametersParameterComponent;

import io.kaicode.elasticvc.api.BranchService;

import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.services.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

public abstract class AbstractFHIRTest extends AbstractTest {

	@LocalServerPort
	protected int port;

	@Autowired
	protected TestRestTemplate restTemplate;

	@Autowired
	protected BranchService branchService;

	@Autowired
	protected ConceptService conceptService;
	
	@Autowired
	protected CodeSystemService codeSystemService;

	protected final String conceptId = "257751006";
	protected final String MAIN = "MAIN";
	protected IParser fhirJsonParser;
	HttpEntity<String> defaultRequestEntity;
	ObjectMapper mapper = new ObjectMapper();

	protected final Logger logger = LoggerFactory.getLogger(getClass());
	
	@Before
	public void setup() throws ServiceException, InterruptedException {
		
		fhirJsonParser = FhirContext.forDstu3().newJsonParser();
		
		branchService.create(MAIN);
		conceptService.create(new Concept(Concepts.SNOMEDCT_ROOT), MAIN);
		
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		defaultRequestEntity = new HttpEntity<>(headers);

		// Create dummy concept with descriptions containing quotes
		Concept concept = conceptService.create(
				new Concept(conceptId)
						.addDescription(new Description("Baked potato (Substance)")
								.setTypeId(Concepts.FSN)
								.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
						.addDescription(new Description("Baked potato")
								.setTypeId(Concepts.SYNONYM)
								.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
						.addAxiom(new Relationship(Concepts.ISA, Concepts.SUBSTANCE)),
				MAIN);


		// Create a project branch and add a relationship to the dummy concept
		concept.getRelationships().add(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT));
		//Also add an inferred relationship to ensure semantic index updated to allow ECL request to work
		Relationship infParentRel = new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT);
		infParentRel.setCharacteristicType("INFERRED_RELATIONSHIP");
		concept.addRelationship(infParentRel);
		concept = conceptService.update(concept, MAIN);

		// Add another relationship and description making two relationships and three descriptions
		concept.getRelationships().add(new Relationship(Concepts.ISA, Concepts.SUBSTANCE));
		concept.getDescriptions().add(new Description("Test"));
		conceptService.update(concept, MAIN);
		
		// Version content to fill effectiveTime fields
		CodeSystem codeSystem = new CodeSystem("SNOMEDCT", MAIN);
		codeSystemService.createCodeSystem(codeSystem);
		codeSystemService.createVersion(codeSystem, 20190731, "");
		logger.info("Baked Potato test data setup complete");
	}

	protected String toString(ParametersParameterComponent p, String indent) {
		StringBuffer sb = new StringBuffer();
		sb.append(p.getName());
		if (p.getValue() != null) {
			sb.append(": " + p.getValue());
		}
		if (p.getResource() != null) {
			sb.append(": " + p.getResource());
		}
		for (ParametersParameterComponent part : p.getPart()) {
			sb.append("\n" + toString(part, indent + "  "));
		}
		return sb.toString();
	}
	
	protected void checkForError(ResponseEntity<String> response) throws FHIROperationException {
		try {
			if (response.getBody().contains("\"Status\":5") ||
				response.getBody().contains("\"Status\":4") ||
				response.getBody().contains("\"Status\":3")) {
				ErrorResponse error = mapper.readValue(response.getBody(), ErrorResponse.class);
				throw new FHIROperationException(IssueType.EXCEPTION, error.getMessage());
			}
		} catch (Exception e) {
			throw new FHIROperationException(IssueType.EXCEPTION, response.getBody());
		}
	}

}
