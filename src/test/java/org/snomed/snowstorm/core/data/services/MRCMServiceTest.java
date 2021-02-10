package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Sets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.mrcm.MRCMService;
import org.snomed.snowstorm.mrcm.model.AttributeRange;
import org.snomed.snowstorm.mrcm.model.ContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static org.junit.Assert.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.ISA;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class MRCMServiceTest extends AbstractTest {

	protected static final String STRENGTH_NUMERATOR = "1142135004";

	@Autowired
	private MRCMService mrcmService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private QueryService queryService;

	@Autowired
	protected ReferenceSetMemberService memberService;

	@BeforeEach
	void setup() throws ServiceException {
		conceptService.create(new Concept(Concepts.SNOMEDCT_ROOT).addFSN("SNOMED CT"), "MAIN");
		conceptService.create(new Concept(Concepts.ISA).addFSN("Term").addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT).setInferred(true)), "MAIN");
		boolean stated = false;
		assertEquals(2, queryService.eclSearch("*", stated, "MAIN", LARGE_PAGE).getTotalElements());
		assertEquals(1, queryService.eclSearch(Concepts.ISA, stated, "MAIN", LARGE_PAGE).getTotalElements());
	}

	@Test
	void testNullParentIds() throws ServiceException {
		// When no parents are supplied, Is a (attribute) should be returned.
		Collection<ConceptMini> attributes = mrcmService.retrieveDomainAttributes(ContentType.NEW_PRECOORDINATED, true, null, "MAIN", null);
		assertEquals(1, attributes.size());
		assertEquals(Concepts.ISA, attributes.iterator().next().getId());
	}

	@Test
	void testRetrieveAttributeValue() throws ServiceException {
		Collection<ConceptMini> result = mrcmService.retrieveAttributeValues(ContentType.NEW_PRECOORDINATED, Concepts.ISA, Concepts.ISA, "MAIN", null);
		assertEquals(1, result.size());
		assertEquals(Concepts.ISA, result.iterator().next().getConceptId());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testExtraConceptMiniFields() throws ServiceException {
		final Concept inConcept = new Concept("12345678910");
		inConcept.addAxiom(new Relationship(ISA, "12345"), Relationship.newConcrete(Concepts.ISA, ConcreteValue.newInteger("#1")));
		createRangeConstraint(Concepts.ISA, "dec(>#0..)");
		conceptService.create(inConcept, "MAIN");

		Collection<ConceptMini> attributes = mrcmService.retrieveDomainAttributes(ContentType.ALL, true,
				Sets.newHashSet(12345678910L), "MAIN", null);
		assertNotNull(attributes);
		attributes.forEach(attribute -> {
			final Map<String, Object> extraFields = attribute.getExtraFields();
			final List<AttributeRange> attributeRanges = (List<AttributeRange>) extraFields.get("attributeRange");
			assertFalse(attributeRanges.isEmpty());
			final AttributeRange attributeRange = attributeRanges.get(0);
			assertEquals(ContentType.ALL, attributeRange.getContentType());
			assertEquals(">#0", attributeRange.getRangeMin());
			assertEquals("", attributeRange.getRangeMax());
			assertEquals(ISA, attributeRange.getReferencedComponentId());
			assertEquals(ConcreteValue.DataType.DECIMAL, attributeRange.getDataType());
		});
	}
}
