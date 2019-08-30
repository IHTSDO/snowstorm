package org.snomed.snowstorm.core.data.services;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.mrcm.MRCMService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class MRCMServiceTest extends AbstractTest {

	@Autowired
	private MRCMService mrcmService;
	
	@Autowired
	private ConceptService conceptService;
	
	@Before
	public void setup() throws ServiceException {
		conceptService.create(new Concept(Concepts.ISA, "10000111"), "MAIN");
		mrcmService.loadFromFiles();
	}
	
	@Test
	public void testNullParentIds() {
		//When no parents are supplied, IS_A should be returned
		Collection<ConceptMini> attributes = mrcmService.retrieveDomainAttributes("MAIN", null, null);
		assertEquals(1, attributes.size());
		assertEquals(Concepts.ISA, attributes.iterator().next().getId());
	}

	@Test
	public void testRetrieveAttributeValue() {
		Collection<ConceptMini> result = mrcmService.retrieveAttributeValues("MAIN", Concepts.ISA, Concepts.ISA, null);
		assertEquals(1, result.size());
		assertEquals(Concepts.ISA, result.iterator().next().getConceptId());
	}
}
