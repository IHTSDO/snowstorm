package org.snomed.snowstorm.fhir.services;

import java.io.IOException;
import java.util.*;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;

import io.kaicode.elasticvc.api.BranchService;

import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.pojo.CodeSystemConfiguration;
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
	
	@Autowired
	protected CodeSystemConfigurationService codeSystemConfigurationService;

	protected static final String sampleSCTID = "257751006";
	protected static final String sampleModuleId = "1234";
	protected static final String sampleVersion = "20190731";
	protected final String MAIN = "MAIN";
	protected IParser fhirJsonParser;
	HttpEntity<String> defaultRequestEntity;
	boolean setupComplete = false;
	ObjectMapper mapper = new ObjectMapper();

	protected final Logger logger = LoggerFactory.getLogger(getClass());
	
	@Before
	synchronized public void setup() throws ServiceException, InterruptedException {
		if (setupComplete) {
			return;
		}
		
		fhirJsonParser = FhirContext.forR4().newJsonParser();
		
		branchService.create(MAIN);
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		defaultRequestEntity = new HttpEntity<>(headers);

		List<Concept> concepts = new ArrayList<>();
		concepts.add(new Concept(Concepts.SNOMEDCT_ROOT));
		for (int x=1; x<=10; x++) {
			createDummyData(x, concepts);
		}
		conceptService.batchCreate(concepts, MAIN);
		
		// Version content to fill effectiveTime fields
		CodeSystem codeSystem = new CodeSystem("SNOMEDCT", MAIN);
		codeSystemService.createCodeSystem(codeSystem);
		codeSystemService.createVersion(codeSystem, 20190731, "");
		
		//Now create a new branch to hold a new edition
		String releaseBranch = MAIN + "/" + sampleVersion;
		String branchWK = releaseBranch + "/SNOMEDCT-WK";
		branchService.create(releaseBranch);
		branchService.create(branchWK);

		//And tell the configuration about that new module
		CodeSystemConfiguration config = new CodeSystemConfiguration("SNOMEDCT-WK", "SNOMEDCT-WK" ,sampleModuleId);
		codeSystemConfigurationService.getConfigurations().add(config);

		concepts.clear();
		//concepts.add(new Concept(Concepts.SNOMEDCT_ROOT));
		for (int x=11; x<=12; x++) {
			createDummyData(x, concepts);
		}
		conceptService.batchCreate(concepts, branchWK);
		CodeSystem codeSystemWK = new CodeSystem("SNOMEDCT-WK", branchWK);
		codeSystemService.createCodeSystem(codeSystemWK);
		codeSystemService.createVersion(codeSystemWK, 20190731, "");
		
		logger.info("Baked Potato test data setup complete");
		setupComplete = true;
	}

	private void createDummyData(int sequence, List<Concept> concepts) throws ServiceException {
		// Create dummy concept with descriptions and relationships
		Relationship infParentRel = new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT);
		infParentRel.setCharacteristicType("INFERRED_RELATIONSHIP");
		Concept concept = new Concept("25775" + sequence + "006")
						.addRelationship(infParentRel)
						.addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT))
						.addDescription(new Description("Baked potato " + sequence + " (Substance)")
								.setTypeId(Concepts.FSN)
								.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
						.addDescription(new Description("Baked potato " + sequence)
								.setTypeId(Concepts.SYNONYM)
								.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
						.addAxiom(new Relationship(Concepts.ISA, Concepts.SUBSTANCE));
		concepts.add(concept);
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
	
	protected void checkForError(String json) throws FHIROperationException {
		try {
			if (json.contains("\"status\":5") ||
					json.contains("\"status\":4") ||
					json.contains("\"status\":3")) {
				ErrorResponse error = mapper.readValue(json, ErrorResponse.class);
				throw new FHIROperationException(IssueType.EXCEPTION, error.getMessage());
			} else if (json.contains("\"resourceType\":\"OperationOutcome\"")) {
				OperationOutcome outcome = fhirJsonParser.parseResource(OperationOutcome.class, json);
				//TODO Find or write pretty print to give structured output of OperationOutcome
				throw new FHIROperationException(IssueType.EXCEPTION, json);
			}
		} catch (IOException e) {
			throw new FHIROperationException(IssueType.EXCEPTION, json);
		}
	}

}
