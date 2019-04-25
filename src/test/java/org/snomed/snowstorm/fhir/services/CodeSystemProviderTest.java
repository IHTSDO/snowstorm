package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.dstu3.model.Parameters.ParametersParameterComponent;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
public class CodeSystemProviderTest extends AbstractTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;
	
	@Autowired
	private CodeSystemService codeSystemService;

	private String conceptId = "257751006";
	
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	@Before
	public void setup() throws ServiceException, InterruptedException {
		branchService.create("MAIN");

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
				"MAIN");


		// Create a project branch and add a relationship to the dummy concept
		branchService.create("MAIN/projectA");
		concept.getRelationships().add(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT));
		concept = conceptService.update(concept, "MAIN");

		// Add another relationship and description making two relationships and three descriptions
		concept.getRelationships().add(new Relationship(Concepts.ISA, Concepts.SUBSTANCE));
		concept.getDescriptions().add(new Description("Test"));
		conceptService.update(concept, "MAIN");
		
		// Version content to fill effectiveTime fields
		CodeSystem codeSystem = new CodeSystem("SNOMEDCT", "MAIN");
		codeSystemService.createCodeSystem(codeSystem);
		codeSystemService.createVersion(codeSystem, 20190731, "");
	}

	@Test
	public void testSingleConceptRecovery() {
		Parameters concept = this.restTemplate.getForObject("http://localhost:" + port + "/fhir/CodeSystem/$lookup?system=http://snomed.info/sct&code=" + conceptId + "&_format=json", Parameters.class);
		//String str = concept.toString();
		for (ParametersParameterComponent parameter : concept.getParameter()) {
			logger.info(toString(parameter, ""));
		}
		assertNotNull(concept);
	}

	private String toString(ParametersParameterComponent p, String indent) {
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

}
