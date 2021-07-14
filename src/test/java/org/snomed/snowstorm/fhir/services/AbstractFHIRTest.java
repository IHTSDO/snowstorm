package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.pojo.CodeSystemConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.assertNotNull;
import static org.snomed.snowstorm.core.data.domain.Concepts.REFSET_MRCM_ATTRIBUTE_RANGE_INTERNATIONAL;

/**
 * 	The data that is set up here is used by the majority of test cases in a read-only manner
 *	so it makes sense to perform this expensive operation (especially the setup of the JSONParser)
 *	only once.  Similarly we keep a count of the number of classes run to ensure that the final
 *  teardown is called only once.
 *
 *	Ideally this would run in a @BeforeAll method, but that wouldn't allow access to the autowired
 *	member variables, so the boolean "setupComplete" is simulating that behaviour
 *
 */
@ExtendWith(SpringExtension.class)
@Testcontainers
@ContextConfiguration(classes = TestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractFHIRTest {

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
	protected ReferenceSetMemberService memberService;
	
	@Autowired
	protected CodeSystemConfigurationService codeSystemConfigurationService;

	@Value("${ims-security.roles.enabled}")
	private boolean rolesEnabled;

	protected static final String sampleSCTID = "257751006";
	protected static final String sampleInactiveSCTID = "60363000";
	protected static final String sampleConcreteSCTID = "2577513006";
	protected static final String sampleModuleId = "1234";
	protected static final String sampleVersion = "20190731";
	protected static final String STRENGTH_NUMERATOR = "1142135004";
	protected final String MAIN = "MAIN";
	static String baseUrl;
	static HttpHeaders headers;
	
	static protected IParser fhirJsonParser;
	static HttpEntity<String> defaultRequestEntity;
	static boolean setupComplete = false;
	static ObjectMapper mapper = new ObjectMapper();

	protected final Logger logger = LoggerFactory.getLogger(getClass());
	protected static final Logger slogger = LoggerFactory.getLogger(AbstractFHIRTest.class);
	
	private static final int TOTAL_TEST_CLASSES = 6;
	private static int testClassesRun = 0;

	@AfterAll
	void finalTearDown() {
		if (testClassesRun >= TOTAL_TEST_CLASSES) {
			logger.warn("Digging up the Potatoes");
			branchService.deleteAll();
			conceptService.deleteAll();
			codeSystemService.deleteAll();
		}
	}

	@BeforeAll
	void setup() throws ServiceException, InterruptedException {
		testClassesRun++;
		if (setupComplete) {
			return;
		}
		// Setup security
		if (!rolesEnabled) {
			PreAuthenticatedAuthenticationToken authentication = new PreAuthenticatedAuthenticationToken("test-admin", "123", Sets.newHashSet(new SimpleGrantedAuthority("USER")));
			SecurityContextHolder.setContext(new SecurityContextImpl(authentication));
		} else {
			SecurityContextHolder.clearContext();
		}
		
		baseUrl = "http://localhost:" + port + "/fhir/ValueSet";
		headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "fhir+json", StandardCharsets.UTF_8));
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		
		fhirJsonParser = FhirContext.forR4().newJsonParser();
		
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		defaultRequestEntity = new HttpEntity<>(headers);

		List<Concept> concepts = new ArrayList<>();
		concepts.add(new Concept(Concepts.SNOMEDCT_ROOT));
		for (int x=1; x<=10; x++) {
			createDummyData(x, concepts, false);
		}
		branchService.create(MAIN);
		concepts.add(new Concept(sampleInactiveSCTID).setActive(false));
		conceptService.batchCreate(concepts, MAIN);
		
		// Version content to fill effectiveTime fields
		CodeSystem codeSystem = new CodeSystem("SNOMEDCT", MAIN);

		codeSystemService.createCodeSystem(codeSystem);
		codeSystemService.createVersion(codeSystem, Integer.parseInt(sampleVersion), "");
		
		//Now create a new branch to hold a new edition
		String releaseBranch = MAIN + "/" + sampleVersion;
		String branchWK = releaseBranch + "/SNOMEDCT-WK";
		branchService.create(releaseBranch);
		branchService.create(branchWK);

		//And tell the configuration about that new module
		CodeSystemConfiguration config = new CodeSystemConfiguration("SNOMEDCT-WK", "SNOMEDCT-WK", sampleModuleId, null, null);
		codeSystemConfigurationService.getConfigurations().add(config);

		concepts.clear();
		//The new module will inherit the 10 concepts from MAIN.  Add two new unqique to MAIN/SNOMEDCT-WK
		for (int x=11; x<=12; x++) {
			createDummyData(x, concepts, false);
		}
		// add MRCM constraint for concrete attribute
		createRangeConstraint(branchWK, STRENGTH_NUMERATOR, "dec(>#0..)");
		createDummyData(13, concepts, true);
		conceptService.batchCreate(concepts, branchWK);
		CodeSystem codeSystemWK = new CodeSystem("SNOMEDCT-WK", branchWK);
		codeSystemService.createCodeSystem(codeSystemWK);
		codeSystemService.createVersion(codeSystemWK, 20190731, "Unit Test Version");
		
		assertNotNull(codeSystemService.findByDefaultModule(sampleModuleId));
		
		logger.info("Baked Potato test data setup complete");
		
		ReferenceSetMember member = new ReferenceSetMember(null, Concepts.REFSET_SAME_AS_ASSOCIATION, sampleSCTID);
		member.setAdditionalField(ReferenceSetMember.AssociationFields.TARGET_COMP_ID, "88189002");
		memberService.createMember(MAIN, member);
		
		setupComplete = true;
	}
	
	private void createDummyData(int sequence, List<Concept> concepts, boolean concrete) throws ServiceException {
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
								.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED));
		
		if (concrete) {
			concept.addAxiom(new Relationship(Concepts.ISA, Concepts.SUBSTANCE),
					Relationship.newConcrete(STRENGTH_NUMERATOR, ConcreteValue.newDecimal("#500")));
		} else {
			concept.addAxiom(new Relationship(Concepts.ISA, Concepts.SUBSTANCE));
		}
		concepts.add(concept);
	}

	protected String toString(ParametersParameterComponent p, String indent) {
		StringBuffer sb = new StringBuffer();
		sb.append(p.getName() + " (" + p.fhirType() + ")");
		if (p.getValue() != null) {
			sb.append(": " + toString(p.getValue()));
		}
		if (p.getResource() != null) {
			sb.append(": " + p.getResource());
		}
		for (ParametersParameterComponent part : p.getPart()) {
			sb.append("\n" + toString(part, indent + "  "));
		}
		return sb.toString();
	}
	
	protected String toString(Type value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Coding) {
			Coding codingValue = (Coding)value;
			return "[ " + codingValue.getSystem() + " : " + codingValue.getCode() + "|" + codingValue.getDisplay()  + "| ]";
		} else if (value instanceof CodeType) {
			CodeType codeValue = (CodeType)value;
			return  codeValue.getCode();
		} else if (value instanceof StringType) {
			return value.castToString(value).asStringValue();
		} else if (value instanceof BooleanType) {
			return value.castToString(value).asStringValue();
		} else {
			return value.toString();
		}
	}

	protected Boolean toBoolean(Type value) {
		if (value instanceof BooleanType) {
			return ((BooleanType) value).booleanValue();
		}

		return null;
	}

	protected void checkForError(ResponseEntity<String> response) throws FHIROperationException {
		String body = response.getBody();
		try {
			if (!HttpStatus.OK.equals(response.getStatusCode())) {
				if (body.contains("\"status\":5") ||
						body.contains("\"status\":4") ||
						body.contains("\"status\":3")) {
					ErrorResponse error = mapper.readValue(body, ErrorResponse.class);
					throw new FHIROperationException(IssueType.EXCEPTION, error.getMessage());
				} else if (body.contains("\"resourceType\":\"OperationOutcome\"")) {
					OperationOutcome outcome = fhirJsonParser.parseResource(OperationOutcome.class, body);
					//TODO Find or write pretty print to give structured output of OperationOutcome
					throw new FHIROperationException(IssueType.EXCEPTION, body);
				}
			}
		} catch (IOException e) {
			throw new FHIROperationException(IssueType.EXCEPTION, body);
		}
	}
	
	protected Parameters get(String url) throws FHIROperationException {
		ResponseEntity<String> response = this.restTemplate.exchange(url, HttpMethod.GET, defaultRequestEntity, String.class);
		checkForError(response);
		return fhirJsonParser.parseResource(Parameters.class, response.getBody());
	}
	
	protected Type getProperty(Parameters params, String propertyName) {
		Map<String, Type> propertyMap = new HashMap<>();
		for (ParametersParameterComponent p : params.getParameter()) {
			if (p.getName().equals("property")) {
				populatePropertyMap(propertyMap, p.getPart());
			}
			if (p.getName().equals(propertyName)) {
				return p.getValue();
			}
		}
		return propertyMap.get(propertyName);
	}
	

	private void populatePropertyMap(Map<String, Type> propertyMap, List<ParametersParameterComponent> parts) {
		String key = null;
		Type value = null;
		for (ParametersParameterComponent part : parts) {
			if (part.getName().equals("code")) {
				key = part.getValue().castToString(part.getValue()).asStringValue();
			} else {
				value = part.getValue();
			}
		}
		propertyMap.put(key, value);
	}

	protected ReferenceSetMember createRangeConstraint(String branchPath, String referencedComponentId, String rangeConstraint) {
		ReferenceSetMember rangeMember = new ReferenceSetMember("900000000000207008", REFSET_MRCM_ATTRIBUTE_RANGE_INTERNATIONAL, referencedComponentId);
		rangeMember.setAdditionalField("rangeConstraint", rangeConstraint);
		rangeMember.setAdditionalField("attributeRule", "");
		rangeMember.setAdditionalField("ruleStrengthId", "723597001");
		rangeMember.setAdditionalField("contentTypeId", "723596005");
		return memberService.createMember(branchPath, rangeMember);
	}

}
