package org.snomed.snowstorm.mrcm;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.langauges.ecl.domain.refinement.Operator;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.mrcm.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class MRCMUpdateServiceTest {
	@Autowired
	private MRCMUpdateService generator;
	private Map<String, List<AttributeDomain>> attributeToDomainsMap;
	private Map<String, AttributeRange> attributeToRangeMap;
	private Map<String, String> conceptToPtMap;

	@Before
	public void setUp() {
		attributeToDomainsMap = new HashMap<>();
		attributeToRangeMap = new HashMap<>();
		conceptToPtMap = new HashMap<>();
	}

	@Test
	public void testAttributeRule() throws Exception{

		AttributeDomain event= new AttributeDomain("358fdc09-ed75-43a0-b9ad-d926ed51162d", null,
				true, "255234002", "272379006", true,
				new Cardinality("0..*"), new Cardinality("0..1"), RuleStrength.MANDATORY, ContentType.ALL);

		AttributeDomain clinicFinding = new AttributeDomain("751a6dd6-fa56-4098-b4c8-ff7a48f98785", null,
				true, "255234002", "404684003", true,
				new Cardinality("0..*"), new Cardinality("0..1"), RuleStrength.MANDATORY, ContentType.ALL);

		attributeToDomainsMap.put("255234002", Arrays.asList(clinicFinding, event));
		AttributeRange range = new AttributeRange("", null, true, "255234002",
				"<< 404684003 |Clinical finding (finding)| OR << 71388002 |Procedure (procedure)| OR << 272379006 |Event (event)|",
				" ", RuleStrength.MANDATORY, ContentType.ALL);
		attributeToRangeMap.put("255234002", range);
		conceptToPtMap.put("255234002", "After");
		conceptToPtMap.put("272379006", "Event (event)");
		conceptToPtMap.put("404684003", "Clinical finding (finding)");

		Map<String, Domain> domainsByDomainIdMap = new HashMap<>();
		domainsByDomainIdMap.put("272379006", new Domain("0bbce893-2543-4125-8bb1-298c83ee75fc", null, true,
				"272379006", new Constraint("<< 272379006 |Event (event)|", "272379006", Operator.descendantorselfof),
				"",null, ""));

		domainsByDomainIdMap.put("404684003", new Domain("8a2a2554-af2f-4616-94ea-408b90b9124e", null, true,
				"404684003", new Constraint("<< 404684003 |Clinical finding (finding)|", "404684003", Operator.descendantorselfof),
				"",null, ""));

		List<AttributeRange> attributeRanges = generator.generateAttributeRule(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangeMap, conceptToPtMap);
		assertEquals(1, attributeRanges.size());
		assertTrue(attributeRanges.get(0).getAttributeRule() != null);
//		assertEquals("(<< 404684003 |Clinical finding (finding)| OR << 272379006 |Event (event)|): [0..*] { [0..1] 255234002 |After| = (<< 404684003 |Clinical finding (finding)| OR << 71388002 |Procedure (procedure)| OR << 272379006 |Event (event)|) }",
//				attributeRanges.get(0).getAttributeRule());
		assertEquals("(<< 404684003 |Clinical finding (finding)|: [0..*] { [0..1] 255234002 |After| = (<< 404684003 |Clinical finding (finding)| OR << 71388002 |Procedure (procedure)| OR << 272379006 |Event (event)|) }) " +
				"OR (<< 272379006 |Event (event)|: [0..*] { [0..1] 255234002 |After| = (<< 404684003 |Clinical finding (finding)| OR << 71388002 |Procedure (procedure)| OR << 272379006 |Event (event)|) })",
				attributeRanges.get(0).getAttributeRule());
	}

	@Test
	public void testAttributeRuleWithDifferentCardinality() throws Exception{

		AttributeDomain procedure= new AttributeDomain("016dbf3a-4665-4b44-908e-2040dc8ccf5d", null,
				true, "405815000", "71388002", true,
				new Cardinality("0..*"), new Cardinality("0..1"), RuleStrength.MANDATORY, ContentType.ALL);

		AttributeDomain observable = new AttributeDomain("58388c47-7807-4ba2-8be8-dc92cf93067", null,
				true, "405815000", "363787002", true,
				new Cardinality("0..*"), new Cardinality("0..*"), RuleStrength.MANDATORY, ContentType.ALL);

		attributeToDomainsMap.put("405815000", Arrays.asList(procedure, observable));
		AttributeRange range = new AttributeRange("b41253a7-7b17-4acf-96a1-d784ad0a6c04", null, true, "405815000",
				"<< 49062001 |Device (physical object)|",
				" ", RuleStrength.MANDATORY, ContentType.ALL);
		attributeToRangeMap.put("405815000", range);
		conceptToPtMap.put("405815000", "Procedure device");

		Map<String, Domain> domainsByDomainIdMap = new HashMap<>();
		domainsByDomainIdMap.put("71388002", new Domain("0bbce893-2543-4125-8bb1-298c83ee75fc", null, true,
				"71388002", new Constraint("<< 71388002 |Procedure (procedure)|", "71388002", Operator.descendantorselfof),
				"",null, ""));

		domainsByDomainIdMap.put("363787002", new Domain("8a2a2554-af2f-4616-94ea-408b90b9124e", null, true,
				"363787002", new Constraint("<< 363787002 |Observable entity (observable entity)|", "363787002", Operator.descendantorselfof),
				"",null, ""));

		List<AttributeRange> attributeRanges = generator.generateAttributeRule(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangeMap, conceptToPtMap);
		assertEquals(1, attributeRanges.size());
		assertTrue(attributeRanges.get(0).getAttributeRule() != null);
		assertEquals("(<< 71388002 |Procedure (procedure)|: [0..*] { [0..1] 405815000 |Procedure device| = << 49062001 |Device (physical object)| })" +
						" OR (<< 363787002 |Observable entity (observable entity)|: [0..*] { [0..*] 405815000 |Procedure device| = << 49062001 |Device (physical object)| })",
				attributeRanges.get(0).getAttributeRule());

	}
}
