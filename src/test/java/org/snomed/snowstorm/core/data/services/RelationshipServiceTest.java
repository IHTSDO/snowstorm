package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.VersionControlHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.collections.Sets;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.validation.RelationshipDroolsValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class RelationshipServiceTest extends AbstractTest {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private RelationshipService relationshipService;

	private static final String MAIN = "MAIN";

	@Before
	public void setup() throws ServiceException {
		conceptService.create(new Concept(Concepts.SNOMEDCT_ROOT), MAIN);
		conceptService.create(new Concept(Concepts.CORE_MODULE).addRelationship(new Relationship("200000022", Concepts.ISA, Concepts.SNOMEDCT_ROOT)), MAIN);
		conceptService.create(new Concept(Concepts.CLINICAL_FINDING).addRelationship(new Relationship("1000000022", Concepts.ISA, Concepts.SNOMEDCT_ROOT)), MAIN);
	}

	@Test
	public void testDelete() {
		assertEquals(2, findAll().size());
		relationshipService.deleteRelationship("1000000022", MAIN, false);
		assertEquals(1, findAll().size());
	}

	@Test
	public void testBulkDelete() {
		assertEquals(2, findAll().size());
		relationshipService.deleteRelationships(Collections.emptySet(), MAIN, false);
		assertEquals(2, findAll().size());
		relationshipService.deleteRelationships(Sets.newSet("200000022", "1000000022"), MAIN, false);
		assertEquals(0, findAll().size());
	}

	public List<Relationship> findAll() {
		return relationshipService.findRelationships(MAIN, null, null, null, null, null, null, null, null, null, PageRequest.of(0, 10)).getContent();
	}

}
