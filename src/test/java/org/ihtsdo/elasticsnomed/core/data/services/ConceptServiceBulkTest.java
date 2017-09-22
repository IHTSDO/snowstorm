package org.ihtsdo.elasticsnomed.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.elasticsnomed.TestConfig;
import org.ihtsdo.elasticsnomed.core.data.domain.*;
import org.ihtsdo.elasticsnomed.core.data.services.pojo.ResultMapPage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;

import static org.ihtsdo.elasticsnomed.core.data.domain.Concepts.ISA;
import static org.ihtsdo.elasticsnomed.core.data.domain.Concepts.SNOMEDCT_ROOT;
import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@ActiveProfiles("bulk-tests")
public class ConceptServiceBulkTest {

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private RelationshipService relationshipService;

	@Before
	public void setup() {
		branchService.create("MAIN");
	}

	@Test
	public void testCreateUpdate10KConcepts() throws ServiceException {
		branchService.create("MAIN/A");
		conceptService.create(new Concept(SNOMEDCT_ROOT), "MAIN/A");

		List<Concept> concepts = new ArrayList<>();
		final int tenThousand = 10 * 1000;
		for (int i = 0; i < tenThousand; i++) {
			concepts.add(
					new Concept(null, Concepts.CORE_MODULE)
							.addDescription(new Description("Concept " + i))
							.addDescription(new Description("Concept " + i + "(finding)"))
							.addRelationship(new Relationship(Concepts.ISA, SNOMEDCT_ROOT))
			);
		}

		final Iterable<Concept> conceptsCreated = conceptService.create(concepts, "MAIN/A");

		final Page<Concept> page = conceptService.findAll("MAIN/A", new PageRequest(0, 100));
		assertEquals(concepts.size() + 1, page.getTotalElements());
		assertEquals(Concepts.CORE_MODULE, page.getContent().get(50).getModuleId());

		ResultMapPage<String, ConceptMini> conceptDescendants = conceptService.findConceptDescendants(SNOMEDCT_ROOT, "MAIN/A", Relationship.CharacteristicType.stated, new PageRequest(0, 50));
		assertEquals(10 * 1000, conceptDescendants.getTotalElements());

		List<Relationship> inboundRelationships = relationshipService.findInboundRelationships(SNOMEDCT_ROOT, "MAIN/A", Relationship.CharacteristicType.stated);
		assertEquals(10 * 1000, inboundRelationships.size());

		final String anotherModule = "123123";
		List<Concept> toUpdate = new ArrayList<>();
		conceptsCreated.forEach(concept -> {
			concept.setModuleId(anotherModule);
			toUpdate.add(concept);
		});

		conceptService.createUpdate(toUpdate, "MAIN/A");

		final Page<Concept> pageAfterUpdate = conceptService.findAll("MAIN/A", new PageRequest(0, 100));
		assertEquals(tenThousand + 1, pageAfterUpdate.getTotalElements());
		Concept someConcept = pageAfterUpdate.getContent().get(50);
		if (someConcept.getId().equals(SNOMEDCT_ROOT)) {
			someConcept = pageAfterUpdate.getContent().get(51);
		}
		assertEquals(anotherModule, someConcept.getModuleId());
		assertEquals(1, someConcept.getRelationships().size());

		// Move all concepts in hierarchy
		conceptService.create(new Concept(Concepts.CLINICAL_FINDING).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)), "MAIN/A");
		concepts.forEach(c -> {
			c.getRelationships().iterator().next().setActive(false);
			c.addRelationship(new Relationship(ISA, Concepts.CLINICAL_FINDING));
		});
		conceptService.createUpdate(concepts, "MAIN/A");
	}

	@After
	public void tearDown() {
		conceptService.deleteAll();
		branchService.deleteAll();
	}

}
