package org.snomed.snowstorm;

import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.classification.ClassificationService;
import org.snomed.snowstorm.core.data.services.servicehook.CommitServiceHookClient;
import org.snomed.snowstorm.core.data.services.traceability.Activity;
import org.snomed.snowstorm.ecl.ReferencedConceptsLookupService;
import org.snomed.snowstorm.rest.View;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Stack;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.snomed.snowstorm.core.data.domain.Concepts.REFSET_MRCM_ATTRIBUTE_RANGE_INTERNATIONAL;

@ExtendWith(SpringExtension.class)
@Testcontainers
@ContextConfiguration(classes = TestConfig.class)
public abstract class AbstractTest {

	public static final String MAIN = "MAIN";

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private ClassificationService classificationService;

	@Autowired
	private PermissionService permissionService;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	@Autowired
	private IdentifierComponentService identifierComponentService;

	@MockBean
	protected CommitServiceHookClient commitServiceHookClient; // Mocked as calls on external service.

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private ReferencedConceptsLookupService referencedConceptsLookupService;

	@Value("${ims-security.roles.enabled}")
	private boolean rolesEnabled;

	private static final Stack<Activity> traceabilityActivitiesLogged = new Stack<>();

	@BeforeEach
	void before() throws InterruptedException {
		// Setup security
		if (!rolesEnabled) {
			PreAuthenticatedAuthenticationToken authentication = new PreAuthenticatedAuthenticationToken("test-admin", "1234", Sets.newHashSet(new SimpleGrantedAuthority("USER")));
			SecurityContextHolder.setContext(new SecurityContextImpl(authentication));
		} else {
			SecurityContextHolder.clearContext();
		}
		deleteAll();
		branchService.create(MAIN);
		clearActivities();
	}

	@AfterEach
	void deleteAll() throws InterruptedException {
		try {
			branchService.deleteAll();
			conceptService.deleteAll();
			codeSystemService.deleteAll();
			classificationService.deleteAll();
			permissionService.deleteAll();
			identifierComponentService.deleteAll();
			referencedConceptsLookupService.deleteAll();
		} catch (OptimisticLockingFailureException e) {
			// Try again
			Thread.sleep(100);
			deleteAll();
		}
	}

	@BeforeAll
	static void checkTestContainerIsRunning() {
		if (!TestConfig.useLocalElasticsearch) {
			assertTrue(TestConfig.getElasticsearchContainerInstance().isRunning(), "Test container is not running");
		}
	}

	protected ReferenceSetMember createRangeConstraint(String referencedComponentId, String rangeConstraint) {
		return createRangeConstraint(MAIN, referencedComponentId, rangeConstraint);
	}

	protected ReferenceSetMember createRangeConstraint(String branchPath, String referencedComponentId, String rangeConstraint) {
		ReferenceSetMember rangeMember = new ReferenceSetMember("900000000000207008", REFSET_MRCM_ATTRIBUTE_RANGE_INTERNATIONAL, referencedComponentId);
		rangeMember.setAdditionalField("rangeConstraint", rangeConstraint);
		rangeMember.setAdditionalField("attributeRule", "");
		rangeMember.setAdditionalField("ruleStrengthId", "723597001");
		rangeMember.setAdditionalField("contentTypeId", "723596005");
		return referenceSetMemberService.createMember(branchPath, rangeMember);
	}

	@JmsListener(destination = "${jms.queue.prefix}.traceability")
	void messageConsumer(Activity activity) {
		traceabilityActivitiesLogged.push(activity);
	}

	public Activity getTraceabilityActivity() throws InterruptedException {
		return getTraceabilityActivityWithTimeout(20);
	}
	public Activity getTraceabilityActivityWithTimeout(int maxWaitSeconds) throws InterruptedException {
		int waited = 0;
		while (traceabilityActivitiesLogged.isEmpty() && waited < maxWaitSeconds) {
			Thread.sleep(1_000);
			waited++;
		}
		if (traceabilityActivitiesLogged.isEmpty()) {
			return null;
		}
		return traceabilityActivitiesLogged.pop();
	}

	public void clearActivities() {
		traceabilityActivitiesLogged.clear();
	}

	public Stack<Activity> getTraceabilityActivitiesLogged() {
		return traceabilityActivitiesLogged;
	}

	public Concept simulateRestTransfer(Concept concept) {
		try {
			final ObjectWriter componentWriter = objectMapper.writerWithView(View.Component.class);
			String conceptJson = componentWriter.writeValueAsString(concept);
			return objectMapper.readValue(conceptJson, Concept.class);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	protected void deleteAllQueryConceptsAndRefresh() {
		NativeQuery deleteQuery = new NativeQueryBuilder().withQuery(new MatchAllQuery.Builder().build()._toQuery()).build();
		elasticsearchOperations.delete(deleteQuery, QueryConcept.class, elasticsearchOperations.getIndexCoordinatesFor(QueryConcept.class));
		elasticsearchOperations.indexOps(QueryConcept.class).refresh();
	}

	protected void simulateElasticsearchException() {
		// Create match query on effectiveTimeI to simulate exception
		NativeQuery query = new NativeQueryBuilder().withQuery(QueryBuilders.match(q -> q.field("effectiveTimeI").query("2024-09-01"))).build();
		elasticsearchOperations.search(query, Concept.class);
	}

}
