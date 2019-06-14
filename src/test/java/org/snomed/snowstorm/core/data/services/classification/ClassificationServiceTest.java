package org.snomed.snowstorm.core.data.services.classification;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.domain.classification.RelationshipChange;
import org.snomed.snowstorm.core.data.repositories.classification.RelationshipChangeRepository;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import static io.kaicode.elasticvc.api.VersionControlHelper.LARGE_PAGE;
import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class ClassificationServiceTest extends AbstractTest {

	@Autowired
	private ClassificationService classificationService;

	@Autowired
	private RelationshipChangeRepository relationshipChangeRepository;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	private String path = "MAIN";

	@Before
	public void setup() {
		branchService.create(path);
	}

	@Test
	public void testSaveRelationshipChanges() throws IOException, ServiceException {
		// Create concept with some stated modeling in an axiom
		conceptService.create(
				new Concept("123123123001")
						.addAxiom(
								new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT),
								new Relationship("363698007", "84301002")
						), path);

		// Save mock classification results with mix of previously stated and new triples
		classificationService.saveRelationshipChanges("123", new ByteArrayInputStream(("" +
				"id\teffectiveTime\tactive\tmoduleId\tsourceId\tdestinationId\trelationshipGroup\ttypeId\tcharacteristicTypeId\tmodifierId\n" +
				"\t\t1\t\t123123123001\t138875005\t0\t116680003\t900000000000227009\t900000000000451002\n" +
				"\t\t1\t\t123123123001\t84301002\t0\t363698007\t900000000000227009\t900000000000451002\n" +
				"\t\t1\t\t123123123001\t50960005\t0\t116676008\t900000000000227009\t900000000000451002\n" +
				"\t\t1\t\t123123123001\t247247001\t0\t116680003\t900000000000227009\t900000000000451002\n" +
				"").getBytes()));

		// Collect changes persisted to change repo (ready for author change review)
		List<RelationshipChange> relationshipChanges = relationshipChangeRepository.findByClassificationId("123", LARGE_PAGE).getContent();
		StringBuilder allChanges = new StringBuilder();
		relationshipChanges.stream().sorted(Comparator.comparing(RelationshipChange::getTypeId).thenComparing(RelationshipChange::getDestinationId))
				.forEach(change -> allChanges.append(change.getSourceId()).append(" -> ").append(change.getTypeId()).append(" -> ")
						.append(change.getDestinationId()).append(" inferredNotStated:").append(change.isInferredNotStated()).append("\n"));

		// Assert that the changes which were not previously stated are marked as inferredNotStated:true
		assertEquals("123123123001 -> 116676008 -> 50960005 inferredNotStated:true\n" +
				"123123123001 -> 116680003 -> 138875005 inferredNotStated:false\n" +
				"123123123001 -> 116680003 -> 247247001 inferredNotStated:true\n" +
				"123123123001 -> 363698007 -> 84301002 inferredNotStated:false\n", allChanges.toString());
	}

}
