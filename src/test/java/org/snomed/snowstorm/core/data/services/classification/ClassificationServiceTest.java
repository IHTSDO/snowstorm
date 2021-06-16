package org.snomed.snowstorm.core.data.services.classification;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Metadata;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.classification.Classification;
import org.snomed.snowstorm.core.data.domain.classification.ClassificationStatus;
import org.snomed.snowstorm.core.data.domain.classification.RelationshipChange;
import org.snomed.snowstorm.core.data.repositories.ClassificationRepository;
import org.snomed.snowstorm.core.data.repositories.classification.RelationshipChangeRepository;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.VersionControlHelper.LARGE_PAGE;
import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.core.data.domain.classification.ClassificationStatus.*;

class ClassificationServiceTest extends AbstractTest {

	@Autowired
	private ClassificationService classificationService;

	@Autowired
	private ClassificationRepository classificationRepository;

	@Autowired
	private RelationshipChangeRepository relationshipChangeRepository;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	@Test
	void testSaveRelationshipChanges() throws IOException, ServiceException, InterruptedException {
		// Create concept with some stated modeling in an axiom
		final String branch = "MAIN";
		createRangeConstraint("1142135004", "dec(>#0..)");
		createRangeConstraint("1142139005", "int(>#0..)");
		clearActivities();

		conceptService.create(
				new Concept("123123123001")
						.addAxiom(
								new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT),
								new Relationship("363698007", "84301002"),
								Relationship.newConcrete("1142135004", ConcreteValue.newDecimal("#55.5"))
						), branch);

		// Disabled until the ActiveMQ broker can restart under Jenkins.
//		Activity activity = getTraceabilityActivity();
//		assertEquals(0, getTraceabilityActivitiesLogged().size());
//		assertNotNull(activity);
//		assertEquals("Creating concept null", activity.getCommitComment());
//		assertEquals(1, activity.getChanges().size());

		// Save mock classification results with mix of previously stated and new triples
		String classificationId = UUID.randomUUID().toString();
		Classification classification = createClassification(branch, classificationId);

		// Standard relationships
		classificationService.saveRelationshipChanges(classification, new ByteArrayInputStream(("" +
				"id\teffectiveTime\tactive\tmoduleId\tsourceId\tdestinationId\trelationshipGroup\ttypeId\tcharacteristicTypeId\tmodifierId\n" +
				"\t\t1\t\t123123123001\t138875005\t0\t116680003\t900000000000227009\t900000000000451002\n" +
				"\t\t1\t\t123123123001\t84301002\t0\t363698007\t900000000000227009\t900000000000451002\n" +
				"\t\t1\t\t123123123001\t50960005\t0\t116676008\t900000000000227009\t900000000000451002\n" +
				"\t\t1\t\t123123123001\t247247001\t0\t116680003\t900000000000227009\t900000000000451002\n" +
				"").getBytes()), false);
		// Concrete relationships
		classificationService.saveRelationshipChanges(classification, new ByteArrayInputStream(("" +
				"id\teffectiveTime\tactive\tmoduleId\tsourceId\tvalue\trelationshipGroup\ttypeId\tcharacteristicTypeId\tmodifierId\n" +
				"\t\t1\t\t123123123001\t#5\t0\t1142139005\t900000000000227009\t900000000000451002\n" +
				"\t\t1\t\t123123123001\t#55.5\t0\t1142135004\t900000000000227009\t900000000000451002\n" +
				"").getBytes()), true);

		// Collect changes persisted to change repo (ready for author change review)
		List<RelationshipChange> relationshipChanges = relationshipChangeRepository.findByClassificationId(classificationId, LARGE_PAGE).getContent();
		StringBuilder allChanges = new StringBuilder();
		relationshipChanges.stream().sorted(Comparator.comparing(RelationshipChange::getTypeId).thenComparing(RelationshipChange::getDestinationId))
				.forEach(change -> allChanges.append(change.getSourceId()).append(" -> ").append(change.getTypeId()).append(" -> ")
						.append(change.getDestinationOrValue()).append(" inferredNotStated:").append(change.isInferredNotStated()).append("\n"));

		// Assert that the changes which were not previously stated are marked as inferredNotStated:true
		assertEquals("123123123001 -> 1142135004 -> #55.5 inferredNotStated:false\n" +
				"123123123001 -> 1142139005 -> #5 inferredNotStated:true\n" +
				"123123123001 -> 116676008 -> 50960005 inferredNotStated:true\n" +
				"123123123001 -> 116680003 -> 138875005 inferredNotStated:false\n" +
				"123123123001 -> 116680003 -> 247247001 inferredNotStated:true\n" +
				"123123123001 -> 363698007 -> 84301002 inferredNotStated:false\n", allChanges.toString());

		// Save the classification results to branch
		assertEquals(SAVED, saveClassificationAndWaitForCompletion(branch, classification.getId()));

		assertEquals(Boolean.TRUE, BranchClassificationStatusService.getClassificationStatus(branchService.findLatest(branch)));

		Concept concept = conceptService.find("123123123001", branch);
		assertEquals(6, concept.getRelationships().size());

		Relationship concreteRelationship = concept.getRelationships().stream().filter(r -> r.getTypeId().equals("1142139005")).findFirst().orElse(null);
		assertNotNull(concreteRelationship);
		assertEquals("#5", concreteRelationship.getValue());

		// Disabled until the ActiveMQ broker can restart under Jenkins.
//		activity = getTraceabilityActivity();
//		assertEquals(0, getTraceabilityActivitiesLogged().size());
//		assertNotNull(activity, "Traceability must be logged for classification results.");
//		assertEquals("Classified ontology.", activity.getCommitComment());
//		assertEquals(1, activity.getChanges().size());
	}

	@Test
	void testSaveRelationshipChangesFailsWithLoop() throws IOException, ServiceException, InterruptedException {
		// Create concept with some stated modeling in an axiom
		final String branch = "MAIN";
		branchService.updateMetadata(branch, new Metadata());
		createRangeConstraint("1142135004", "dec(>#0..)");
		createRangeConstraint("1142139005", "int(>#0..)");
		conceptService.create(
				new Concept(Concepts.ISA)
						.addAxiom(
								new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)
						)
						.addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)),
				branch);
		conceptService.create(
				new Concept("10000000001")
						.addAxiom(
								new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT),
								new Relationship("363698007", "84301002"),
								Relationship.newConcrete("1142135004", ConcreteValue.newDecimal("#55.5"))
						)
						.addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)),
				branch);
		conceptService.create(
				new Concept("20000000001")
						.addAxiom(
								new Relationship(Concepts.ISA, "10000000001")
						)
						.addRelationship(new Relationship(Concepts.ISA, "10000000001")),
				branch);

		// Save mock classification results with a transitive closure loop
		String classificationId = UUID.randomUUID().toString();
		Classification classification = createClassification(branch, classificationId);

		// Standard relationships
		classificationService.saveRelationshipChanges(classification, new ByteArrayInputStream(("" +
				"id\teffectiveTime\tactive\tmoduleId\tsourceId\tdestinationId\trelationshipGroup\ttypeId\tcharacteristicTypeId\tmodifierId\n" +
				"\t\t1\t\t10000000001\t20000000001\t0\t116680003\t900000000000227009\t900000000000451002\n" +
				"").getBytes()), false);
		// Concrete relationships
		classificationService.saveRelationshipChanges(classification, new ByteArrayInputStream(("" +
				"id\teffectiveTime\tactive\tmoduleId\tsourceId\tvalue\trelationshipGroup\ttypeId\tcharacteristicTypeId\tmodifierId\n" +
				"").getBytes()), true);

		// Collect changes persisted to change repo (ready for author change review)
		List<RelationshipChange> relationshipChanges = relationshipChangeRepository.findByClassificationId(classificationId, LARGE_PAGE).getContent();
		assertEquals(1, relationshipChanges.size());

		// One inferred relationship before
		assertEquals(1, conceptService.find("10000000001", branch).getRelationships().size());

		// Save the classification results to branch
		assertEquals(SAVE_FAILED, saveClassificationAndWaitForCompletion(branch, classification.getId()));
		assertNotEquals(Boolean.TRUE, BranchClassificationStatusService.getClassificationStatus(branchService.findLatest(branch)));


		// One inferred relationship after
		assertEquals(1, conceptService.find("10000000001", branch).getRelationships().size());
	}

	@Test
	void testSaveRelationshipChangesInExtension() throws IOException, ServiceException, InterruptedException {
		// Create concept with some stated modeling in an axiom
		conceptService.create(
				new Concept("123123123001")
						.addAxiom(
								new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT),
								new Relationship("363698007", "84301002")
						), "MAIN");

		String extensionBranchPath = "MAIN/SNOMEDCT-SE";
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-SE", extensionBranchPath));
		branchService.updateMetadata(extensionBranchPath, ImmutableMap.of(Config.DEFAULT_MODULE_ID_KEY, "45991000052106", Config.DEFAULT_NAMESPACE_KEY, "1000052"));

		// Save mock classification results
		Classification classification = createClassification(extensionBranchPath, UUID.randomUUID().toString());
		classificationService.saveRelationshipChanges(classification, new ByteArrayInputStream(("" +
				"id\teffectiveTime\tactive\tmoduleId\tsourceId\tdestinationId\trelationshipGroup\ttypeId\tcharacteristicTypeId\tmodifierId\n" +
				"\t\t1\t\t123123123001\t138875005\t0\t116680003\t900000000000227009\t900000000000451002\n" +
				"\t\t1\t\t123123123001\t84301002\t0\t363698007\t900000000000227009\t900000000000451002\n" +
				"\t\t1\t\t123123123001\t50960005\t0\t116676008\t900000000000227009\t900000000000451002\n" +
				"\t\t1\t\t123123123001\t247247001\t0\t116680003\t900000000000227009\t900000000000451002\n" +
				"").getBytes()), false);

		assertEquals(SAVED, saveClassificationAndWaitForCompletion(extensionBranchPath, classification.getId()));
		final Branch latest = branchService.findLatest(extensionBranchPath);
		assertEquals(Boolean.TRUE, BranchClassificationStatusService.getClassificationStatus(latest));

		Concept concept = conceptService.find("123123123001", extensionBranchPath);
		assertEquals(4, concept.getRelationships().size());
		for (Relationship relationship : concept.getRelationships()) {
			assertEquals(relationship.getModuleId(), "45991000052106", "New inferred relationships have the configured module applied.");
			assertTrue(relationship.getId().contains("1000052" + "12"), "New inferred relationships have SCTIDs in the configured namespace and correct partition ID");
		}

		concept.addFSN("thing");
		concept = conceptService.update(concept, extensionBranchPath);
		assertEquals(Boolean.TRUE, BranchClassificationStatusService.getClassificationStatus(branchService.findLatest(extensionBranchPath)));

		concept.getClassAxioms().iterator().next().setDefinitionStatusId(Concepts.FULLY_DEFINED);
		conceptService.update(concept, extensionBranchPath);
		assertEquals(Boolean.FALSE, BranchClassificationStatusService.getClassificationStatus(branchService.findLatest(extensionBranchPath)));
	}

	@Test
	void testSaveRelationshipChangesInExtensionThenDeleteInChildBranch() throws IOException, ServiceException, InterruptedException {
		// Create concept with some stated modeling in an axiom
		conceptService.create(
				new Concept("123123123001")
						.addAxiom(
								new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT),
								new Relationship("363698007", "84301002")
						), "MAIN");

		String extensionBranchPath = "MAIN/SNOMEDCT-SE";
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-SE", extensionBranchPath));
		branchService.updateMetadata(extensionBranchPath, ImmutableMap.of(Config.DEFAULT_MODULE_ID_KEY, "45991000052106", Config.DEFAULT_NAMESPACE_KEY, "1000052"));

		// Save mock classification results
		Classification classification = createClassification(extensionBranchPath, UUID.randomUUID().toString());
		classificationService.saveRelationshipChanges(classification, new ByteArrayInputStream(("" +
				"id\teffectiveTime\tactive\tmoduleId\tsourceId\tdestinationId\trelationshipGroup\ttypeId\tcharacteristicTypeId\tmodifierId\n" +
				"\t\t1\t\t123123123001\t138875005\t0\t116680003\t900000000000227009\t900000000000451002\n" +
				"\t\t1\t\t123123123001\t84301002\t0\t363698007\t900000000000227009\t900000000000451002\n" +
				"\t\t1\t\t123123123001\t50960005\t0\t116676008\t900000000000227009\t900000000000451002\n" +
				"\t\t1\t\t123123123001\t247247001\t0\t116680003\t900000000000227009\t900000000000451002\n" +
				"").getBytes()), false);

		assertEquals(SAVED, saveClassificationAndWaitForCompletion(extensionBranchPath, classification.getId()));
		final Branch latest = branchService.findLatest(extensionBranchPath);
		assertEquals(Boolean.TRUE, BranchClassificationStatusService.getClassificationStatus(latest));

		String childBranch = extensionBranchPath + "/SE/SE-10";
		branchService.create(extensionBranchPath + "/SE");
		branchService.create(childBranch);
		final Concept concept = conceptService.find("123123123001", childBranch);
		final Relationship relationship = concept.getRelationships().iterator().next();
		concept.getRelationships().remove(relationship);
		conceptService.update(concept, childBranch);
		assertEquals(Boolean.TRUE, BranchClassificationStatusService.getClassificationStatus(branchService.findLatest(extensionBranchPath)));
		assertEquals(Boolean.FALSE, BranchClassificationStatusService.getClassificationStatus(branchService.findLatest(childBranch)));
	}

	@Test
	void testRemoveNotReleasedRedundantRelationships() throws IOException, ServiceException, InterruptedException {
		// Create concept with some stated modeling in an axiom
		String path = "MAIN";
		String conceptId = "123123123001";
		createRangeConstraint("1142135004", "dec(>#0..)");
		conceptService.create(
				new Concept(conceptId)
						.addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT))
						.addRelationship(new Relationship("363698007", "84301002"))
						.addRelationship(Relationship.newConcrete("1142135004", ConcreteValue.newDecimal("#55.5")))
				, path);

        final Concept savedConcept = conceptService.find(conceptId, path);
        Relationship relationship = savedConcept.getRelationships().stream().filter(r -> r.getTypeId().equals("363698007")).findFirst().orElse(null);
        Relationship concreteRelationship = savedConcept.getRelationships().stream().filter(r -> r.getTypeId().equals("1142135004")).findFirst().orElse(null);
        assertNotNull(relationship);
        assertNotNull(concreteRelationship);

		String classificationId = UUID.randomUUID().toString();
		Classification classification = createClassification(path, classificationId);

		// Standard relationships
		classificationService.saveRelationshipChanges(classification, new ByteArrayInputStream(("" +
				"id\teffectiveTime\tactive\tmoduleId\tsourceId\tdestinationId\trelationshipGroup\ttypeId\tcharacteristicTypeId\tmodifierId\n" +
				relationship.getId() + "\t\t0\t\t123123123001\t84301002\t0\t363698007\t900000000000011006\t900000000000451002\n" +
				"").getBytes()), false);

        // Concrete relationships
        classificationService.saveRelationshipChanges(classification, new ByteArrayInputStream(("" +
                "id\teffectiveTime\tactive\tmoduleId\tsourceId\tvalue\trelationshipGroup\ttypeId\tcharacteristicTypeId\tmodifierId\n" +
                concreteRelationship.getId() + "\t\t0\t\t123123123001\t#55.5\t0\t1142135004\t900000000000227009\t900000000000451002\n" +
                "").getBytes()), true);



        Set<Relationship> relationships = conceptService.find(conceptId, path).getRelationships();
		assertEquals(relationships.size(), 3, "Three relationships.");
		assertEquals(relationships.stream().filter(Relationship::isActive).count(), 3, "Three active relationships.");

		assertEquals(SAVED, saveClassificationAndWaitForCompletion(path, classificationId));

		assertEquals(conceptService.find(conceptId, path).getRelationships().size(), 1, "Not released redundant relationship deleted");
	}

	@Test
	void testRemoveReleasedRedundantRelationships() throws IOException, ServiceException, InterruptedException {
		// Create concept with some stated modeling in an axiom
		String path = "MAIN";
		String conceptId = "123123123001";
		conceptService.create(
				new Concept(conceptId)
						.addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT))
						.addRelationship(new Relationship("363698007", "84301002"))
				, path);

		// Release content
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", path));
		CodeSystem codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20200131, "");

		Relationship relationship = conceptService.find(conceptId, path).getRelationships().stream().filter(r -> r.getTypeId().equals("363698007")).collect(Collectors.toList()).get(0);

		String classificationId = UUID.randomUUID().toString();
		Classification classification = createClassification(path, classificationId);
		classificationService.saveRelationshipChanges(classification, new ByteArrayInputStream(("" +
				"id\teffectiveTime\tactive\tmoduleId\tsourceId\tdestinationId\trelationshipGroup\ttypeId\tcharacteristicTypeId\tmodifierId\n" +
				relationship.getId() + "\t\t0\t\t123123123001\t84301002\t0\t363698007\t900000000000011006\t900000000000451002\n" +
				"").getBytes()), false);


		Set<Relationship> relationships = conceptService.find(conceptId, path).getRelationships();
		assertEquals(relationships.size(), 2, "Two relationships.");
		assertEquals(relationships.stream().filter(Relationship::isActive).count(), 2, "Two active relationships.");
		assertEquals(relationships.stream().filter(rel -> rel.getEffectiveTime() != null).count(), 2, "Two relationships with effective time.");

		assertEquals(SAVED, saveClassificationAndWaitForCompletion(path, classificationId));

		relationships = conceptService.find(conceptId, path).getRelationships();
		assertEquals(relationships.size(), 2, "Released redundant relationship not removed.");
		assertEquals(relationships.stream().filter(Relationship::isActive).count(), 1, "Released redundant relationship made inactive.");
		Relationship inactiveRelationship = relationships.stream().filter(rel -> !rel.isActive()).collect(Collectors.toList()).get(0);
		assertNotNull(inactiveRelationship);
		assertNull(inactiveRelationship.getEffectiveTime());
	}

	@Test
	void testAxiomConcreteValueDataTypeWhenSavingClassification() throws Exception {
		// Create concept with some stated modeling in an axiom
		final String branch = "MAIN";
		createRangeConstraint("1142135004", "dec(>#0..)");
		createRangeConstraint("1142139005", "int(>#0..)");
		conceptService.create(
				new Concept("123123123001")
						.addAxiom(
								new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT),
								new Relationship("363698007", "84301002"),
								Relationship.newConcrete("1142135004", ConcreteValue.newDecimal("#500")),
								Relationship.newConcrete("1142139005", ConcreteValue.newInteger("#10"))
						), branch);

		// verify the data type in axiom
		MemberSearchRequest axiomMemberRequest = new MemberSearchRequest().referencedComponentId("123123123001").referenceSet(Concepts.OWL_AXIOM_REFERENCE_SET);
		Page<ReferenceSetMember> axioms = referenceSetMemberService.findMembers(branch, axiomMemberRequest, PageRequest.of(0, 10));
		assertNotNull(axioms.getContent());
		assertEquals(1, axioms.getContent().size());
		String owlExpression = axioms.getContent().get(0).getAdditionalField("owlExpression");
		String expected = "SubClassOf(:123123123001 ObjectIntersectionOf(:138875005 ObjectSomeValuesFrom(:609096000 ObjectSomeValuesFrom(:363698007 :84301002)) " +
				"ObjectSomeValuesFrom(:609096000 DataHasValue(:1142135004 \"500\"^^xsd:decimal)) " +
				"ObjectSomeValuesFrom(:609096000 DataHasValue(:1142139005 \"10\"^^xsd:integer))))";
		assertEquals(expected, owlExpression);

		// Save mock classification results with mix of previously stated and new triples
		String classificationId = UUID.randomUUID().toString();
		Classification classification = createClassification(branch, classificationId);

		// Concrete relationships
		classificationService.saveRelationshipChanges(classification, new ByteArrayInputStream(("" +
				"id\teffectiveTime\tactive\tmoduleId\tsourceId\tvalue\trelationshipGroup\ttypeId\tcharacteristicTypeId\tmodifierId\n" +
				"\t\t1\t\t123123123001\t#10\t0\t1142139005\t900000000000227009\t900000000000451002\n" +
				"\t\t1\t\t123123123001\t#500.0\t0\t1142135004\t900000000000227009\t900000000000451002\n" +
				"").getBytes()), true);
		// Save the classification results to branch
		assertEquals(SAVED, saveClassificationAndWaitForCompletion(branch, classification.getId()));

		// check the axiom data type after save

		axioms = referenceSetMemberService.findMembers(branch, axiomMemberRequest, PageRequest.of(0, 10));
		assertNotNull(axioms.getContent());
		assertEquals(1, axioms.getContent().size());
		String owlExpressionAfterSave = axioms.getContent().get(0).getAdditionalField("owlExpression");
		assertEquals(expected, owlExpressionAfterSave);
	}

	Classification createClassification(String path, String classificationId) {
		Classification classification = new Classification();
		classification.setId(classificationId);
		classification.setPath(path);
		classification.setStatus(COMPLETED);
		classification.setLastCommitDate(branchService.findLatest(path).getHead());
		classification.setInferredRelationshipChangesFound(true);
		classificationRepository.save(classification);
		return classification;
	}

	ClassificationStatus saveClassificationAndWaitForCompletion(String path, String classificationId) throws InterruptedException {
		classificationService.saveClassificationResultsToBranch(path, classificationId, SecurityContextHolder.getContext());
		Set<ClassificationStatus> inProgressStatuses = Sets.newHashSet(COMPLETED, ClassificationStatus.SAVING_IN_PROGRESS);
		for (int i = 0; inProgressStatuses.contains(classificationService.findClassification(path, classificationId).getStatus()) && i < 20; i++) {
			Thread.sleep(2_000);
		}
		return classificationService.findClassification(path, classificationId).getStatus();
	}

}
