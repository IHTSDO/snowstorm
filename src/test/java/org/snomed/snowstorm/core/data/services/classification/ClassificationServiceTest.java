package org.snomed.snowstorm.core.data.services.classification;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.domain.classification.Classification;
import org.snomed.snowstorm.core.data.domain.classification.ClassificationStatus;
import org.snomed.snowstorm.core.data.domain.classification.RelationshipChange;
import org.snomed.snowstorm.core.data.repositories.ClassificationRepository;
import org.snomed.snowstorm.core.data.repositories.classification.RelationshipChangeRepository;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.snomed.snowstorm.core.data.domain.classification.ClassificationStatus.COMPLETED;
import static org.snomed.snowstorm.core.data.domain.classification.ClassificationStatus.SAVED;

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

	@Test
	void testSaveRelationshipChanges() throws IOException, ServiceException, InterruptedException {
		// Create concept with some stated modeling in an axiom
        final String branch = "MAIN";
        conceptService.create(
				new Concept("123123123001")
						.addAxiom(
								new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT),
								new Relationship("363698007", "84301002"),
                                Relationship.newConcrete("1142135004", "#55.5")
						), branch);

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

        Concept concept = conceptService.find("123123123001", branch);
        assertEquals(6, concept.getRelationships().size());

        Relationship concreteRelationship = concept.getRelationships().stream().filter(r -> r.getTypeId().equals("1142139005")).findFirst().orElse(null);
        assertNotNull(concreteRelationship);
        assertEquals("#5", concreteRelationship.getValue());
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

		Concept concept = conceptService.find("123123123001", extensionBranchPath);
		assertEquals(4, concept.getRelationships().size());
		for (Relationship relationship : concept.getRelationships()) {
			assertEquals("45991000052106", relationship.getModuleId(), "New inferred relationships have the configured module applied.");
			assertTrue(relationship.getId().contains("1000052" + "12"), "New inferred relationships have SCTIDs in the configured namespace and correct partition ID");
		}
	}

	@Test
	void testRemoveNotReleasedRedundantRelationships() throws IOException, ServiceException, InterruptedException {
		// Create concept with some stated modeling in an axiom
		String path = "MAIN";
		String conceptId = "123123123001";
		conceptService.create(
				new Concept(conceptId)
						.addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT))
						.addRelationship(new Relationship("363698007", "84301002"))
						.addRelationship(Relationship.newConcrete("1142135004", "#55.5"))
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
		assertEquals(3, relationships.size(), "Three relationships.");
		assertEquals(3, relationships.stream().filter(Relationship::isActive).count(), "Three active relationships.");

		assertEquals(SAVED, saveClassificationAndWaitForCompletion(path, classificationId));

		assertEquals(1, conceptService.find(conceptId, path).getRelationships().size(), "Not released redundant relationship deleted");
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
		assertEquals(2, relationships.size(), "Two relationships.");
		assertEquals(2, relationships.stream().filter(Relationship::isActive).count(), "Two active relationships.");
		assertEquals(2, relationships.stream().filter(rel -> rel.getEffectiveTime() != null).count(), "Two relationships with effective time.");

		assertEquals(SAVED, saveClassificationAndWaitForCompletion(path, classificationId));

		relationships = conceptService.find(conceptId, path).getRelationships();
		assertEquals(2, relationships.size(), "Released redundant relationship not removed.");
		assertEquals(1, relationships.stream().filter(Relationship::isActive).count(), "Released redundant relationship made inactive.");
		Relationship inactiveRelationship = relationships.stream().filter(rel -> !rel.isActive()).collect(Collectors.toList()).get(0);
		assertNotNull(inactiveRelationship);
		assertNull(inactiveRelationship.getEffectiveTime());
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
			Thread.sleep(1_000);
		}
		return classificationService.findClassification(path, classificationId).getStatus();
	}

}
