package org.ihtsdo.elasticsnomed.core.data.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kaicode.elasticvc.api.BranchService;

import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.ihtsdo.elasticsnomed.TestConfig;
import org.ihtsdo.elasticsnomed.TestUtil;
import org.ihtsdo.elasticsnomed.core.data.domain.Concept;
import org.ihtsdo.elasticsnomed.core.data.domain.Description;
import org.ihtsdo.elasticsnomed.core.data.domain.Relationship;
import org.ihtsdo.elasticsnomed.core.data.services.authoringmirror.TraceabilityActivity;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class AuthoringMirrorServiceTest {

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private AuthoringMirrorService authoringMirrorService;

	@Autowired
	private ObjectMapper mapper;

	@Autowired
	private TestUtil testUtil;
	
	@Autowired
	private JmsTemplate jmsTemplate;

	@Value("${authoring.mirror.traceability.queue.name}")
	private String mirrorQueueName;

	@Test
	public void testInactivateFSNWithoutReasonCreateTwoNewDescriptionsWithMsg() throws Exception {
		String testPath = "/traceability-mirror/new-and-updated-descriptions/";
		String branchPath = "MAIN/CONREQEXT/CONREQEXT-442";
		runTestViaJms(testPath, branchPath, 1);
	}
	
	@Test
	public void testInactivateFSNWithoutReasonCreateTwoNewDescriptions() throws IOException, ServiceException {
		String testPath = "/traceability-mirror/new-and-updated-descriptions/";
		String branchPath = "MAIN/CONREQEXT/CONREQEXT-442";
		runTest(testPath, branchPath, 1);
	}

	@Test
	public void testConceptInactivation() throws IOException, ServiceException {
		String testPath = "/traceability-mirror/concept-inactivation/";
		String branchPath = "MAIN/TESTINT1/TESTINT1-11";
		runTest(testPath, branchPath, 1);
	}
	
	@Test
	public void testConceptInactivationWithMsg() throws Exception {
		String testPath = "/traceability-mirror/concept-inactivation/";
		String branchPath = "MAIN/TESTINT1/TESTINT1-11";
		runTestViaJms(testPath, branchPath, 1);
	}

	@Test
	public void testDescriptionInactivation() throws IOException, ServiceException {
		String testPath = "/traceability-mirror/description-inactivation/";
		String branchPath = "MAIN/TESTINT1/TESTINT1-11";
		runTest(testPath, branchPath, 1);
	}

	@Test
	public void testConceptDeletion() throws IOException, ServiceException {
		String branch = "MAIN/TRAIN/TRAIN-80";
		branchService.recursiveCreate(branch);
		String conceptId = "734723005";
		conceptService.create(new Concept(conceptId), branch);
		Assert.assertTrue(conceptService.exists(conceptId, branch));

		consumeActivity("/traceability-mirror/concept-deletion/");

		Assert.assertFalse(conceptService.exists(conceptId, branch));

		// Attempting to delete again should not throw an error
		consumeActivity("/traceability-mirror/concept-deletion/");
	}

	@Test
	public void testBranchRebase() throws InterruptedException, IOException, ServiceException {
		branchService.create("MAIN");
		branchService.create("MAIN/PROJECT-A");
		Thread.sleep(100);
		testUtil.emptyCommit("MAIN");

		Assert.assertNotEquals(
				branchService.findLatest("MAIN").getHead(),
				branchService.findLatest("MAIN/PROJECT-A").getBase());

		consumeActivity("/traceability-mirror/branch-rebase/");

		Assert.assertEquals(
				branchService.findLatest("MAIN").getHead(),
				branchService.findLatest("MAIN/PROJECT-A").getBase());
	}

	@Test
	public void testBranchRebaseWithTempBranchName() throws InterruptedException, IOException, ServiceException {
		branchService.recursiveCreate("MAIN/CMTTWO/CMTTWO-417");
		Thread.sleep(100);
		testUtil.emptyCommit("MAIN/CMTTWO");

		Assert.assertNotEquals(
				branchService.findLatest("MAIN/CMTTWO").getHead(),
				branchService.findLatest("MAIN/CMTTWO/CMTTWO-417").getBase());

		consumeActivity("/traceability-mirror/branch-rebase-with-temp-branch/");

		Assert.assertEquals(
				branchService.findLatest("MAIN/CMTTWO").getHead(),
				branchService.findLatest("MAIN/CMTTWO/CMTTWO-417").getBase());
	}

	@Test
	public void testBranchPromotion() throws InterruptedException, IOException, ServiceException {
		branchService.recursiveCreate("MAIN/PROJECT-A");
		Thread.sleep(100);
		testUtil.emptyCommit("MAIN/PROJECT-A");

		Assert.assertNotEquals(
				branchService.findLatest("MAIN").getHead(),
				branchService.findLatest("MAIN/PROJECT-A").getHead());

		consumeActivity("/traceability-mirror/branch-promotion/");

		Assert.assertEquals(
				branchService.findLatest("MAIN").getHead(),
				branchService.findLatest("MAIN/PROJECT-A").getHead());
	}

	@After
	public void tearDown() {
		branchService.deleteAll();
		conceptService.deleteAll();
	}

	private void runTest(String testPath, String branchPath, int expectedChangedConceptCount) throws IOException, ServiceException {
		setupTest(testPath, branchPath);
		TraceabilityActivity activity = consumeActivity(testPath);
		assertPostActivityConceptStates(testPath, branchPath, activity, expectedChangedConceptCount);
	}
	
	private TraceabilityActivity consumeActivity(String testPath) throws IOException, ServiceException {
		String traceabilityStreamString = readTestActivity(testPath);

		TraceabilityActivity activity = mapper.readValue(traceabilityStreamString, TraceabilityActivity.class);
		authoringMirrorService.receiveActivity(activity);
		return activity;
	}

	private void runTestViaJms(String testPath, String branchPath, int expectedChangedConceptCount) throws InterruptedException, IOException, ServiceException {
		setupTest(testPath, branchPath);

		String traceabilityStreamString = readTestActivity(testPath);

		jmsTemplate.convertAndSend(mirrorQueueName, traceabilityStreamString);
		jmsTemplate.setReceiveTimeout(5_000);

		// Allow time for the activity to be consumed
		Thread.sleep(2_000);

		TraceabilityActivity activity = mapper.readValue(traceabilityStreamString, TraceabilityActivity.class);
		assertPostActivityConceptStates(testPath, branchPath, activity, expectedChangedConceptCount);
	}

	private String readTestActivity(String testPath) throws IOException {
		InputStream traceabilityStream = loadResource(testPath + "traceability-message.json");
		Assert.assertNotNull(traceabilityStream);
		String traceabilityStreamString = Streams.asString(traceabilityStream);
		traceabilityStreamString = traceabilityStreamString.replace("\"empty\": false", "");
		return traceabilityStreamString;
	}

	private void assertPostActivityConceptStates(String testPath, String branchPath, TraceabilityActivity activity, int expectedChangedConceptCount) throws IOException {
		Assert.assertEquals(expectedChangedConceptCount, activity.getChanges().size());
		for (String conceptId : activity.getChanges().keySet()) {
			Concept expected = mapper.readValue(loadResource(testPath + conceptId + "-after.json"), Concept.class);
			Concept actual = conceptService.find(conceptId, branchPath);
			assertConceptsEqual(expected, actual);
		}
	}

	private void assertConceptsEqual(Concept expected, Concept actual) {
		Assert.assertEquals(expected.getConceptId(), actual.getConceptId());
		Assert.assertEquals(expected.isActive(), actual.isActive());
		Assert.assertEquals(expected.getModuleId(), actual.getModuleId());
		Assert.assertEquals(expected.getDefinitionStatusId(), actual.getDefinitionStatusId());

		Assert.assertEquals(expected.getInactivationIndicator(), actual.getInactivationIndicator());

		// TODO: Traceability data does not currently contain the concept association targets.
		//		Assert.assertEquals(expected.getAssociationTargets(), actual.getAssociationTargets());

		// Descriptions
		Assert.assertEquals(expected.getDescriptions().size(), actual.getDescriptions().size());
		Map<String, Description> actualDescriptionsMap = actual.getDescriptions().stream().collect(Collectors.toMap(Description::getDescriptionId, Function.identity()));
		for (Description expectedDescription : expected.getDescriptions()) {
			String descriptionId = expectedDescription.getDescriptionId();
			Assert.assertNotNull(descriptionId);
			Description actualDescription = actualDescriptionsMap.get(descriptionId);

			Assert.assertNotNull(actualDescription);
			Assert.assertEquals(expectedDescription.isActive(), actualDescription.isActive());
			Assert.assertEquals(expectedDescription.getTerm(), actualDescription.getTerm());
			Assert.assertEquals(expectedDescription.getConceptId(), actualDescription.getConceptId());
			Assert.assertEquals(expectedDescription.getModuleId(), actualDescription.getModuleId());
			Assert.assertEquals(expectedDescription.getLanguageCode(), actualDescription.getLanguageCode());
			Assert.assertEquals(expectedDescription.getTypeId(), actualDescription.getTypeId());
			Assert.assertEquals(expectedDescription.getCaseSignificanceId(), actualDescription.getCaseSignificanceId());
			Assert.assertEquals(expectedDescription.getAcceptabilityMap(), actualDescription.getAcceptabilityMap());
			Assert.assertEquals(expectedDescription.getInactivationIndicator(), actualDescription.getInactivationIndicator());
			if (!expectedDescription.isActive()) {
				System.out.println("Description inactive " + descriptionId);
				System.out.println("Expected indicator " + expectedDescription.getInactivationIndicator());
				System.out.println("Actual indicator " + actualDescription.getInactivationIndicator());
				System.out.println("Actual indicator member " + actualDescription.getInactivationIndicatorMember());
			}
		}

		// Relationships
		Assert.assertEquals(expected.getRelationships().size(), actual.getRelationships().size());
		Map<String, Relationship> actualRelationshipsMap = actual.getRelationships().stream().collect(Collectors.toMap(Relationship::getRelationshipId, Function.identity()));
		for (Relationship expectedRelationship : expected.getRelationships()) {
			String relationshipId = expectedRelationship.getRelationshipId();
			Assert.assertNotNull(relationshipId);
			Relationship actualRelationship = actualRelationshipsMap.get(relationshipId);

			Assert.assertNotNull(actualRelationship);
			Assert.assertEquals(expectedRelationship.isActive(), actualRelationship.isActive());
			Assert.assertEquals(expectedRelationship.getModuleId(), actualRelationship.getModuleId());
			Assert.assertEquals(expectedRelationship.getSourceId(), actualRelationship.getSourceId());
			Assert.assertEquals(expectedRelationship.getDestinationId(), actualRelationship.getDestinationId());
			Assert.assertEquals(expectedRelationship.getGroupId(), actualRelationship.getGroupId());
			Assert.assertEquals(expectedRelationship.getTypeId(), actualRelationship.getTypeId());
			Assert.assertEquals(expectedRelationship.getCharacteristicTypeId(), actualRelationship.getCharacteristicTypeId());
			Assert.assertEquals(expectedRelationship.getModifierId(), actualRelationship.getModifierId());
		}

	}

	private void setupTest(String testPath, String branchPath) throws IOException, ServiceException {
		branchService.recursiveCreate(branchPath);
		File testFiles = new File("src/test/resources" + testPath);
		File[] files = testFiles.listFiles(file -> file.isFile() && file.getName().endsWith("-before.json"));
		org.springframework.util.Assert.notNull(files);
		for (File beforeFile : files) {
			Concept before = mapper.readValue(beforeFile, Concept.class);
			conceptService.create(before, branchPath);
		}
	}

	private InputStream loadResource(String name) {
		return getClass().getResourceAsStream(name);
	}

}
