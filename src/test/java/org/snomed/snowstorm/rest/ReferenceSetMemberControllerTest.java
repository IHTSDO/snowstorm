package org.snomed.snowstorm.rest;

import com.sun.net.httpserver.HttpServer;
import io.kaicode.elasticvc.api.BranchService;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.loadtest.ItemsPagePojo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.http.HttpHeaders;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
// Boot 4 no longer auto-registers TestRestTemplate; it needs this annotation.
@AutoConfigureTestRestTemplate
class ReferenceSetMemberControllerTest extends AbstractTest {
	private static final ParameterizedTypeReference<ItemsPagePojo<ReferenceSetMember>> FIND_REFSET_MEMBERS_RESPONSE_TYPE = new ParameterizedTypeReference<>() {
	};

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private ConceptService conceptService;

	@Test
	void findRefsetMembers_ShouldPage_WhenGivenSearchAfterRequestParameter() throws InterruptedException {
		// create & assert first reference set member
		ReferenceSetMember memberA = new ReferenceSetMember(Concepts.MODEL_MODULE, Concepts.REFSET_DESCRIPTOR_REFSET, Concepts.REFSET_MOVED_FROM_ASSOCIATION);
		referenceSetMemberService.createMember("MAIN", memberA);
		ItemsPagePojo<ReferenceSetMember> firstRequest = findRefsetMembers();
		assertEquals(1, firstRequest.getItems().size()); // memberA

		// create & assert second reference set member
		ReferenceSetMember memberB = new ReferenceSetMember(Concepts.MODEL_MODULE, Concepts.REFSET_DESCRIPTOR_REFSET, Concepts.REFSET_MOVED_TO_ASSOCIATION);
		referenceSetMemberService.createMember("MAIN", memberB);
		assertEquals(2, findRefsetMembers().getItems().size()); // memberA & memberB

		// query first page (using offset & limit)
		ItemsPagePojo<ReferenceSetMember> firstPageMembers = findRefsetMembers(0, 1);
		assertEquals(1, firstPageMembers.getItems().size());
		ReferenceSetMember firstPageMember = getReferenceSetMember(firstPageMembers);

		// query second page (using searchAfter token from firstPage)
		ItemsPagePojo<ReferenceSetMember> secondPageMembers = findRefsetMembers(firstPageMembers.getSearchAfter());
		Thread.sleep(10000);
		assertEquals(1, secondPageMembers.getItems().size());
		ReferenceSetMember secondPageMember = getReferenceSetMember(secondPageMembers);

		// should be different, thus paged
		assertNotEquals(firstPageMember, secondPageMember);
	}

	@Test
	void findRefsetMembers_ShouldReturnSameMember_WhenUsingDifferentPagingMechanisms() {
		// given
		Set<ReferenceSetMember> referenceSetMembers = new HashSet<>();
		Collections.addAll(
				referenceSetMembers,
				// Members are assigned UUIDs & returned in descending order. Cannot know order returned from API beforehand for more specific assertions.
				new ReferenceSetMember(Concepts.MODEL_MODULE, Concepts.REFSET_DESCRIPTOR_REFSET, Concepts.REFSET_MOVED_FROM_ASSOCIATION),
				new ReferenceSetMember(Concepts.MODEL_MODULE, Concepts.REFSET_DESCRIPTOR_REFSET, Concepts.REFSET_MOVED_TO_ASSOCIATION),
				new ReferenceSetMember(Concepts.MODEL_MODULE, Concepts.REFSET_DESCRIPTOR_REFSET, Concepts.REFSET_HISTORICAL_ASSOCIATION),
				new ReferenceSetMember(Concepts.MODEL_MODULE, Concepts.REFSET_DESCRIPTOR_REFSET, Concepts.REFSET_MODULE_DEPENDENCY),
				new ReferenceSetMember(Concepts.MODEL_MODULE, Concepts.REFSET_DESCRIPTOR_REFSET, Concepts.REFSET_SIMPLE)
		);
		referenceSetMemberService.createMembers("MAIN", referenceSetMembers);
		ItemsPagePojo<ReferenceSetMember> searchAfterTokenRequest = findRefsetMembers(0, 1);

		// when
		ItemsPagePojo<ReferenceSetMember> firstRequest = findRefsetMembers(searchAfterTokenRequest.getSearchAfter(), 1); // Requesting second entry (skipping first via searchAfter token)
		ItemsPagePojo<ReferenceSetMember> secondRequest = findRefsetMembers(1, 1); // Requesting second entry (skipping first via offset)

		// then
		ReferenceSetMember firstRequestMember = getReferenceSetMember(firstRequest);
		ReferenceSetMember secondRequestMember = getReferenceSetMember(secondRequest);
		assertEquals(firstRequestMember.getMemberId(), secondRequestMember.getMemberId());
		assertEquals(firstRequestMember.getReferencedComponentId(), secondRequestMember.getReferencedComponentId());
	}

	private ReferenceSetMember getReferenceSetMember(ItemsPagePojo<ReferenceSetMember> firstPageMembers) {
		return firstPageMembers.getItems().iterator().next();
	}

	@Test
	void createMember_ShouldRejectOwlImport_WithoutFetchingOrLeakingDetail() throws IOException {
		AtomicInteger importRequests = new AtomicInteger();
		HttpServer importTarget = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		importTarget.createContext("/", exchange -> {
			importRequests.incrementAndGet();
			exchange.sendResponseHeaders(404, -1);
			exchange.close();
		});
		importTarget.start();
		try {
			String owlExpression = "Import(<http://127.0.0.1:" + importTarget.getAddress().getPort() + "/import>) SubClassOf(:404684003 :138875005)";
			Map<String, Object> member = Map.of(
					"active", true,
					"moduleId", Concepts.CORE_MODULE,
					"refsetId", Concepts.OWL_AXIOM_REFERENCE_SET,
					"referencedComponentId", "404684003",
					"additionalFields", Map.of(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION, owlExpression));

			ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:" + port + "/MAIN/members", member, String.class);

			assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
			assertFalse(response.getBody().contains("Unsupported axiom expression"), "Toolkit error detail must not reach the caller");
			assertEquals(0, importRequests.get(), "No request may reach the import URL");
			assertTrue(referenceSetMemberService.findMembers(MAIN, new MemberSearchRequest().referenceSet(Concepts.OWL_AXIOM_REFERENCE_SET), LARGE_PAGE).isEmpty());
			assertFalse(branchService.findLatest(MAIN).isLocked(), "Branch should be unlocked after rollback");
		} finally {
			importTarget.stop(0);
		}
	}

	@Test
	void createMember_ShouldUseDefaultModuleId_WhenModuleIdOmittedFromPostBody() throws Exception {
		String extBranch = "MAIN/SNOMEDCT-XX";

		// Create CodeSystem
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-XX", extBranch));
		Concept extModuleConcept = conceptService.create(
				new Concept()
						.addDescription(new Description("Extension module (module)").setTypeId(Concepts.FSN))
						.addDescription(new Description("Extension module").setTypeId(Concepts.SYNONYM))
						.addAxiom(new Relationship(Concepts.ISA, Concepts.MODULE)),
				extBranch);
		String extModuleId = extModuleConcept.getConceptId();
		branchService.updateMetadata(extBranch, Map.of(Config.DEFAULT_MODULE_ID_KEY, extModuleId));

		// Create Concepts
		Concept vehicle = conceptService.create(
				new Concept()
						.addDescription(new Description("Vehicle (vehicle)").setTypeId(Concepts.FSN))
						.addDescription(new Description("Vehicle").setTypeId(Concepts.SYNONYM))
						.addAxiom(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)),
				extBranch);

		Concept car = conceptService.create(
				new Concept()
						.addDescription(new Description("Car (vehicle)").setTypeId(Concepts.FSN))
						.addDescription(new Description("Car").setTypeId(Concepts.SYNONYM))
						.addAxiom(new Relationship(Concepts.ISA, vehicle.getConceptId())),
				extBranch);

		Concept motorisedVehicle = conceptService.create(
				new Concept()
						.addDescription(new Description("Motorised vehicle (vehicle)").setTypeId(Concepts.FSN))
						.addDescription(new Description("Motorised vehicle").setTypeId(Concepts.SYNONYM))
						.addAxiom(new Relationship(Concepts.ISA, vehicle.getConceptId())),
				extBranch);

		// Version CodeSystem
		codeSystemService.createVersion(codeSystemService.find("SNOMEDCT-XX"), 20240201, "2024-02-01");

		// Inactivate
		motorisedVehicle = conceptService.find(motorisedVehicle.getConceptId(), extBranch);
		motorisedVehicle.setActive(false);
		conceptService.update(motorisedVehicle, extBranch);

		// Add inactivation indicator (via post)
		Map<String, Object> memberBody = Map.of(
				"refsetId", Concepts.REFSET_POSSIBLY_EQUIVALENT_TO_ASSOCIATION,
				"referencedComponentId", motorisedVehicle.getConceptId(),
				"additionalFields", Map.of(ReferenceSetMember.AssociationFields.TARGET_COMP_ID, car.getConceptId())
		);
		ResponseEntity<ReferenceSetMember> postResponse = postReferenceSetMember(extBranch, memberBody);
		String memberId = postResponse.getBody().getMemberId();
		assertNotNull(memberId);

		// Assert
		ReferenceSetMember referenceSetMember = referenceSetMemberService.findMember(extBranch, memberId);
		assertEquals(extModuleId, referenceSetMember.getModuleId());
	}

	private ItemsPagePojo<ReferenceSetMember> findRefsetMembers() {
		return this.restTemplate.exchange("http://localhost:" + port + "/MAIN/members", HttpMethod.GET, new HttpEntity<>((HttpHeaders) null), FIND_REFSET_MEMBERS_RESPONSE_TYPE).getBody();
	}

	private ItemsPagePojo<ReferenceSetMember> findRefsetMembers(int offset, int limit) {
		return this.restTemplate.exchange("http://localhost:" + port + "/MAIN/members?offset=" + offset + "&limit=" + limit, HttpMethod.GET, new HttpEntity<>((HttpHeaders) null), FIND_REFSET_MEMBERS_RESPONSE_TYPE).getBody();
	}

	private ItemsPagePojo<ReferenceSetMember> findRefsetMembers(String searchAfter) {
		return this.restTemplate.exchange("http://localhost:" + port + "/MAIN/members?searchAfter=" + searchAfter, HttpMethod.GET, new HttpEntity<>((HttpHeaders) null), FIND_REFSET_MEMBERS_RESPONSE_TYPE).getBody();
	}

	private ItemsPagePojo<ReferenceSetMember> findRefsetMembers(String searchAfter, int limit) {
		return this.restTemplate.exchange("http://localhost:" + port + "/MAIN/members?searchAfter=" + searchAfter + "&limit=" + limit, HttpMethod.GET, new HttpEntity<>((HttpHeaders) null), FIND_REFSET_MEMBERS_RESPONSE_TYPE).getBody();
	}

	private ResponseEntity<ReferenceSetMember> postReferenceSetMember(String branchPath, Map<String, Object> memberBody){
		return restTemplate.postForEntity("http://localhost:" + port + "/" + branchPath + "/members", memberBody, ReferenceSetMember.class);
	}
}
