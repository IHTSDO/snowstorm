package org.ihtsdo.elasticsnomed.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.ihtsdo.elasticsnomed.TestConfig;
import org.ihtsdo.elasticsnomed.TestUtil;
import org.ihtsdo.elasticsnomed.domain.Concept;
import org.ihtsdo.elasticsnomed.domain.Description;
import org.ihtsdo.elasticsnomed.domain.Relationship;
import org.ihtsdo.elasticsnomed.services.authoringmirror.TraceabilityActivity;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

	@Test
	public void testInactivateFSNWithoutReasonCreateTwoNewDescriptions() throws IOException {
		String testPath = "/traceability-mirror/new-and-updated-descriptions/";
		String branchPath = "MAIN/CONREQEXT/CONREQEXT-442";
		runTest(testPath, branchPath, 1);
	}

	@Test
	public void testBranchRebase() throws InterruptedException, IOException {
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
	public void testBranchPromotion() throws InterruptedException, IOException {
		branchService.create("MAIN");
		branchService.create("MAIN/PROJECT-A");
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
	}

	private void runTest(String testPath, String branchPath, int expectedChangedConceptCount) throws IOException {
		setupTest(testPath, branchPath);
		TraceabilityActivity activity = consumeActivity(testPath);
		assertPostActivityConceptStates(testPath, branchPath, activity, expectedChangedConceptCount);
	}

	private TraceabilityActivity consumeActivity(String testPath) throws IOException {
		InputStream traceabilityStream = loadResource(testPath + "traceability-message.json");
		Assert.assertNotNull(traceabilityStream);
		TraceabilityActivity activity = mapper.readValue(traceabilityStream, TraceabilityActivity.class);
		authoringMirrorService.receiveActivity(activity);
		return activity;
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

		// TODO Check inactivationIndicatorMember
		// TODO Check associationTargets

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

			// TODO Check inactivationIndicatorMember

		}

		// Relationships
		Assert.assertEquals(expected.getRelationships().size(), actual.getRelationships().size());
		Map<String, Relationship> actualRelationshipsMap = actual.getRelationships().stream().collect(Collectors.toMap(Relationship::getRelationshipId, Function.identity()));
		for (Relationship expectedRelationship : expected.getRelationships()) {
			String relationshipId = expectedRelationship.getRelationshipId();
			Assert.assertNotNull(relationshipId);
			Relationship actualRelationship = actualRelationshipsMap.get(relationshipId);

			Assert.assertNotNull(actualRelationship);
//			Assert.assertEquals();
			// TODO: finish relationships
		}

	}

	private void setupTest(String testPath, String branchPath) throws IOException {
		testUtil.createBranchAndParents(branchPath);
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
