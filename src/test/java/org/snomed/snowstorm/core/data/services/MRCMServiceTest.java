package org.snomed.snowstorm.core.data.services;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.mrcm.MRCMService;
import org.snomed.snowstorm.mrcm.model.ContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Collection;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class MRCMServiceTest extends AbstractTest {

	@Autowired
	private MRCMService mrcmService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private QueryService queryService;

	@Before
	public void setup() throws ServiceException {
		conceptService.create(new Concept(Concepts.SNOMEDCT_ROOT).addFSN("SNOMED CT"), "MAIN");
		conceptService.create(new Concept(Concepts.ISA).addFSN("Term").addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT).setInferred(true)), "MAIN");
		boolean stated = false;
		assertEquals(2, queryService.eclSearch("*", stated, "MAIN", LARGE_PAGE).getTotalElements());
		assertEquals(1, queryService.eclSearch(Concepts.ISA, stated, "MAIN", LARGE_PAGE).getTotalElements());
	}

	@Test
	public void testNullParentIds() throws ServiceException {
		// When no parents are supplied, Is a (attribute) should be returned.
		Collection<ConceptMini> attributes = mrcmService.retrieveDomainAttributes(ContentType.NEW_PRECOORDINATED, true, null, "MAIN", null);
		assertEquals(1, attributes.size());
		assertEquals(Concepts.ISA, attributes.iterator().next().getId());
	}

	@Test
	public void testRetrieveAttributeValue() throws ServiceException {
		Collection<ConceptMini> result = mrcmService.retrieveAttributeValues(ContentType.NEW_PRECOORDINATED, Concepts.ISA, Concepts.ISA, "MAIN", null);
		assertEquals(1, result.size());
		assertEquals(Concepts.ISA, result.iterator().next().getConceptId());
	}
}
