package org.snomed.snowstorm.mrcm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.langauges.ecl.domain.refinement.Operator;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.mrcm.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class MRCMDomainTemplatesAndRuleGeneratorTest extends AbstractTest {
	@Autowired
	private MRCMDomainTemplatesAndRuleGenerator generator;

	private Map<String, List<AttributeDomain>> attributeToDomainsMap;

	private Map<String, List<AttributeRange>> attributeToRangesMap;

	private Map<String, String> conceptToPtMap;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ECLQueryBuilder eclQueryBuilder;

	@BeforeEach
	void setUp() {
		attributeToDomainsMap = new HashMap<>();
		attributeToRangesMap = new HashMap<>();
		conceptToPtMap = new HashMap<>();
	}


	@Test
	void testPreCoordinationDomainTemplateWithParentDomain() throws Exception {
		Domain bodyStructure = new Domain("273f1341-03c9-44a1-9797-9b5106c07e8", "", true, "123037004",
				new Constraint("<< 123037004 |Body structure (body structure)|", "123037004", Operator.descendantorselfof),
				"",
				new Constraint("<< 123037004 |Body structure (body structure)|", "123037004", Operator.descendantorselfof),
				"", "", "");

		AttributeDomain systemicPartOf = new AttributeDomain("", "", true, "733932009",
				"123037004", false, new Cardinality("0..*"), new Cardinality("0..0"), RuleStrength.MANDATORY, ContentType.PRECOORDINATED);

		AttributeDomain lateralHalfOf = new AttributeDomain("", "", true, "733933004",
				"123037004", false, new Cardinality("0..*"), new Cardinality("0..0"), RuleStrength.MANDATORY, ContentType.PRECOORDINATED);

		AttributeDomain allOrPartOf = new AttributeDomain("", "", true, "733928003",
				"123037004", false, new Cardinality("0..*"), new Cardinality("0..0"), RuleStrength.MANDATORY, ContentType.PRECOORDINATED);

		AttributeDomain constitutionalPartOf = new AttributeDomain("", "", true, "733931002",
				"123037004", false, new Cardinality("0..*"), new Cardinality("0..0"), RuleStrength.MANDATORY, ContentType.PRECOORDINATED);

		AttributeDomain regionalPartOf = new AttributeDomain("", "", true, "733930001",
				"123037004", false, new Cardinality("0..*"), new Cardinality("0..0"), RuleStrength.MANDATORY, ContentType.PRECOORDINATED);

		AttributeDomain properPartOf = new AttributeDomain("", "", true, "774081006",
				"123037004", false, new Cardinality("0..*"), new Cardinality("0..0"), RuleStrength.MANDATORY, ContentType.PRECOORDINATED);

		Domain anatomical = new Domain("", "", true, "91723000",
				new Constraint("<< 91723000 |Anatomical structure (body structure)|", "91723000", Operator.descendantorselfof),
				"123037004 |Body structure (body structure)|",
				new Constraint("<< 91723000 |Anatomical structure (body structure)|", "91723000", Operator.descendantorselfof),
				"", "", "");

		Domain lateralizable = new Domain("19d3f679-5369-42fb-9543-8795fdee5dce", null, true, "723264001",
				new Constraint("^ 723264001 |Lateralizable body structure reference set (foundation metadata concept)|", "723264001", Operator.memberOf),
				"91723000 |Anatomical structure (body structure)|", new Constraint("^ 723264001 |Lateralizable body structure reference set (foundation metadata concept)|", "723264001", Operator.memberOf),
				"", "", "");

		AttributeDomain laterality = new AttributeDomain("a8c88cca-305c-40e8-bf03-2d6d03d47755", null, true,
				"272741003", "723264001", false, new Cardinality("0..1"), new Cardinality("0..0"), RuleStrength.MANDATORY, ContentType.ALL);

		Map<String, Domain> domainsByDomainIdMap = new HashMap<>();
		domainsByDomainIdMap.put("123037004", bodyStructure);
		domainsByDomainIdMap.put("723264001", lateralizable);
		domainsByDomainIdMap.put("91723000", anatomical);

		Map<String, List<AttributeDomain>> domainToAttributesMap = new HashMap<>();
		domainToAttributesMap.put("723264001", Collections.singletonList(laterality));
		domainToAttributesMap.put("123037004", Arrays.asList(systemicPartOf, lateralHalfOf, allOrPartOf, constitutionalPartOf, regionalPartOf, properPartOf));

		Map<String, List<AttributeRange>> attributeToRangeMap = new HashMap<>();

		AttributeRange lateralityRange = new AttributeRange("", "", true, "272741003",
				"<< 182353008|Side (qualifier value)|", "", RuleStrength.MANDATORY, ContentType.ALL);

		AttributeRange allOrPartOfRange = new AttributeRange("", "", true, "733928003",
				"<< 123037004|Body structure (body structure)|", "", RuleStrength.MANDATORY, ContentType.ALL);

		AttributeRange constitutionalPartOfRange = new AttributeRange("", "", true, "733931002",
				"<< 123037004|Body structure (body structure)|", "", RuleStrength.MANDATORY, ContentType.ALL);

		AttributeRange regionalPartOfRange = new AttributeRange("", "", true, "733930001",
				"<< 123037004|Body structure (body structure)|", "", RuleStrength.MANDATORY, ContentType.ALL);

		AttributeRange lateralhalfOfRange = new AttributeRange("", "", true, "733933004",
				"<< 123037004|Body structure (body structure)|", "", RuleStrength.MANDATORY, ContentType.ALL);

		AttributeRange systemicPartOfRange = new AttributeRange("", "", true, "733932009",
				"<< 123037004|Body structure (body structure)|", "", RuleStrength.MANDATORY, ContentType.ALL);

		AttributeRange properPartOfRange = new AttributeRange("", "", true, "774081006",
				"<< 123037004|Body structure (body structure)|", "", RuleStrength.MANDATORY, ContentType.ALL);

		attributeToRangeMap.put("272741003", Collections.singletonList(lateralityRange));
		attributeToRangeMap.put("733928003", Collections.singletonList(allOrPartOfRange));
		attributeToRangeMap.put("733931002", Collections.singletonList(constitutionalPartOfRange));
		attributeToRangeMap.put("733930001", Collections.singletonList(regionalPartOfRange));
		attributeToRangeMap.put("733933004", Collections.singletonList(lateralhalfOfRange));
		attributeToRangeMap.put("733932009", Collections.singletonList(systemicPartOfRange));
		attributeToRangeMap.put("774081006", Collections.singletonList(properPartOfRange));

		conceptToPtMap.put("272741003", "Laterality");
		conceptToPtMap.put("733928003", "All or part of");
		conceptToPtMap.put("733931002", "Constitutional part of");
		conceptToPtMap.put("733930001", "Regional part of");
		conceptToPtMap.put("733933004", "Lateral half of");
		conceptToPtMap.put("733932009", "Systemic part of");
		conceptToPtMap.put("774081006", "Proper part of");

		List<Domain> domains = generator.generateDomainTemplates(domainsByDomainIdMap, domainToAttributesMap, attributeToRangeMap, conceptToPtMap);
		assertEquals(3, domains.size());
		Set<Domain> result = domains.stream().filter(d -> "723264001".equals(d.getReferencedComponentId())).collect(Collectors.toSet());
		assertEquals(1, result.size());
		String actual = result.iterator().next().getDomainTemplateForPrecoordination();
		String expected = "[[+id(^ 723264001 |Lateralizable body structure reference set (foundation metadata concept)|)]]:" +
				" [[0..1]] 272741003 |Laterality| = [[+id(<< 182353008|Side (qualifier value)|)]]," +
				" [[0..*]] 733928003 |All or part of| = [[+id(<< 123037004|Body structure (body structure)|)]]," +
				" [[0..*]] 733930001 |Regional part of| = [[+id(<< 123037004|Body structure (body structure)|)]]," +
				" [[0..*]] 733931002 |Constitutional part of| = [[+id(<< 123037004|Body structure (body structure)|)]]," +
				" [[0..*]] 733932009 |Systemic part of| = [[+id(<< 123037004|Body structure (body structure)|)]]," +
				" [[0..*]] 733933004 |Lateral half of| = [[+id(<< 123037004|Body structure (body structure)|)]]," +
				" [[0..*]] 774081006 |Proper part of| = [[+id(<< 123037004|Body structure (body structure)|)]]";

		String published = "[[+id(^ 723264001 |Lateralizable body structure reference set (foundation metadata concept)|)]]:" +
				" [[0..1]] 272741003 |Laterality| = [[+id(<< 182353008 |Side (qualifier value)|)]]," +
				" [[0..*]] 733928003 |All or part of| = [[+id(<< 123037004|Body structure (body structure)|)]]," +
				" [[0..*]] 733931002 |Constitutional part of| = [[+id(<< 123037004|Body structure (body structure)|)]]," +
				" [[0..*]] 733930001 |Regional part of| = [[+id(<< 123037004|Body structure (body structure)|)]]," +
				" [[0..*]] 733933004 |Lateral half of| = [[+id(<< 123037004|Body structure (body structure)|)]]," +
				" [[0..*]] 733932009 |Systemic part of| = [[+id(<< 123037004|Body structure (body structure)|)]]," +
				" [[0..*]] 774081006 |Proper part of| = [[+id(<< 123037004 |Body structure (body structure)|)]]";

		assertEquals(expected, actual);
	}


	@Test
	void testAttributeRuleAndConstraint() throws Exception{

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
		attributeToRangesMap.put("255234002", Collections.singletonList(range));
		conceptToPtMap.put("255234002", "After");
		conceptToPtMap.put("272379006", "Event (event)");
		conceptToPtMap.put("404684003", "Clinical finding (finding)");

		Map<String, Domain> domainsByDomainIdMap = new HashMap<>();
		domainsByDomainIdMap.put("272379006", new Domain("0bbce893-2543-4125-8bb1-298c83ee75fc", null, true,
				"272379006", new Constraint("<< 272379006 |Event (event)|", "272379006", Operator.descendantorselfof),
				"",null, "", "", ""));

		domainsByDomainIdMap.put("404684003", new Domain("8a2a2554-af2f-4616-94ea-408b90b9124e", null, true,
				"404684003", new Constraint("<< 404684003 |Clinical finding (finding)|", "404684003", Operator.descendantorselfof),
				"",null, "", "", ""));

		List<AttributeRange> attributeRanges = generator.generateAttributeRules(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangesMap, conceptToPtMap, Collections.emptyList());
		assertEquals(1, attributeRanges.size());
		assertTrue(attributeRanges.get(0).getAttributeRule() != null);
		String expected = "(<< 272379006 |Event (event)| OR << 404684003 |Clinical finding (finding)|): [0..*] { [0..1] 255234002 |After| = (<< 272379006 |Event (event)| " +
				"OR << 404684003 |Clinical finding (finding)| OR << 71388002 |Procedure (procedure)|) }";

		assertEquals(expected, attributeRanges.get(0).getAttributeRule());

		assertEquals("<< 272379006 |Event (event)| OR << 404684003 |Clinical finding (finding)| OR << 71388002 |Procedure (procedure)|",
				attributeRanges.get(0).getRangeConstraint());
	}

	@Test
	void testAttributeRuleWithDifferentCardinality() throws Exception{

		AttributeDomain procedure= new AttributeDomain("016dbf3a-4665-4b44-908e-2040dc8ccf5d", null,
				true, "405815000", "71388002", true,
				new Cardinality("0..*"), new Cardinality("0..*"), RuleStrength.MANDATORY, ContentType.ALL);

		AttributeDomain observable = new AttributeDomain("58388c47-7807-4ba2-8be8-dc92cf93067", null,
				true, "405815000", "363787002", true,
				new Cardinality("0..*"), new Cardinality("0..1"), RuleStrength.MANDATORY, ContentType.ALL);

		AttributeDomain observable_optional = new AttributeDomain(null,
				null,true, "405815000", "363787002", true,
				new Cardinality("0..1"), new Cardinality("1..1"), RuleStrength.OPTIONAL, ContentType.ALL);

		AttributeDomain procedurePreCoordinated = new AttributeDomain("016dbf3a-4665-4b44-908e-2040dc8ccf5d", null,
				true, "405815000", "71388002", true,
				new Cardinality("1..*"), new Cardinality("0..1"), RuleStrength.MANDATORY, ContentType.PRECOORDINATED);

		attributeToDomainsMap.put("405815000", Arrays.asList(procedure, observable, observable_optional, procedurePreCoordinated));
		AttributeRange range = new AttributeRange("b41253a7-7b17-4acf-96a1-d784ad0a6c04", null, true, "405815000",
				"<< 49062001 |Device (physical object)|",
				" ", RuleStrength.MANDATORY, ContentType.ALL);
		attributeToRangesMap.put("405815000", Collections.singletonList(range));
		conceptToPtMap.put("405815000", "Procedure device");

		Map<String, Domain> domainsByDomainIdMap = new HashMap<>();
		domainsByDomainIdMap.put("71388002", new Domain("0bbce893-2543-4125-8bb1-298c83ee75fc", null, true,
				"71388002", new Constraint("<< 71388002 |Procedure (procedure)|", "71388002", Operator.descendantorselfof),
				"",null, "", "", ""));

		domainsByDomainIdMap.put("363787002", new Domain("8a2a2554-af2f-4616-94ea-408b90b9124e", null, true,
				"363787002", new Constraint("<< 363787002 |Observable entity (observable entity)|", "363787002", Operator.descendantorselfof),
				"",null, "", "", ""));

		List<AttributeRange> attributeRanges = generator.generateAttributeRules(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangesMap, conceptToPtMap, Collections.emptyList());
		assertEquals(1, attributeRanges.size());
		assertNotNull(attributeRanges.get(0).getAttributeRule());
		assertEquals("(<< 363787002 |Observable entity (observable entity)|: [0..*] { [0..1] 405815000 |Procedure device| = << 49062001 |Device (physical object)| }) OR " +
						"(<< 71388002 |Procedure (procedure)|: [0..*] { [0..*] 405815000 |Procedure device| = << 49062001 |Device (physical object)| })",
				attributeRanges.get(0).getAttributeRule());
	}


	@Test
	void testAttributeRuleAndConstraintWithConcreteValueRange() throws Exception {
		String attributeId = "3264475007";
		Map<String, Domain> domainsByDomainIdMap = constructConcreteDomainTestData(attributeId, "dec(>#0..)");
		List<AttributeRange> attributeRanges = generator.generateAttributeRules(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangesMap, conceptToPtMap, Collections.singletonList(Long.valueOf("3264475007")));
		assertEquals(1, attributeRanges.size());
		assertEquals("dec(>#0..)", attributeRanges.get(0).getRangeConstraint());
		assertTrue(attributeRanges.get(0).getAttributeRule() != null);
		String expected = "<< 373873005 |Pharmaceutical / biologic product (product)|: [0..*] { [0..1] 3264475007 |CD has presentation strength numerator value| > #0 }";
		assertEquals(expected, attributeRanges.get(0).getAttributeRule());

		// without sign
		AttributeRange range = attributeToRangesMap.get(attributeId).get(0);
		range.setRangeConstraint("int(#0..)");
		attributeRanges = generator.generateAttributeRules(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangesMap, conceptToPtMap, Collections.singletonList(Long.valueOf("3264475007")));
		assertEquals(1, attributeRanges.size());
		assertEquals("int(#0..)", attributeRanges.get(0).getRangeConstraint());
		assertNotNull(attributeRanges.get(0).getAttributeRule());
		expected = "<< 373873005 |Pharmaceutical / biologic product (product)|: [0..*] { [0..1] 3264475007 |CD has presentation strength numerator value| >= #0 }";
		assertEquals(expected, attributeRanges.get(0).getAttributeRule());

		// with both min and max constraints
		range.setRangeConstraint("int(#10..#20)");
		attributeRanges = generator.generateAttributeRules(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangesMap, conceptToPtMap, Collections.singletonList(Long.valueOf("3264475007")));
		assertEquals(1, attributeRanges.size());
		assertEquals("int(#10..#20)", attributeRanges.get(0).getRangeConstraint());
		assertNotNull(attributeRanges.get(0).getAttributeRule());
		expected = "<< 373873005 |Pharmaceutical / biologic product (product)|: [0..*] { [0..1] 3264475007 |CD has presentation strength numerator value| >= #10, [0..1] 3264475007 |CD has presentation strength numerator value| <= #20 }";
		assertEquals(expected, attributeRanges.get(0).getAttributeRule());
	}

	@Test
	void testInvalidConstraints() throws ServiceException {
		String attributeId = "3264475007";
		// missing #
		Map<String, Domain> domainsByDomainIdMap = constructConcreteDomainTestData(attributeId, "int(10)");
		Exception exceptionThrown = assertThrows(IllegalArgumentException.class,
				() -> generator.generateAttributeRules(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangesMap, conceptToPtMap, Collections.singletonList(Long.valueOf(attributeId))));
		assertEquals("Number constraint 10 does not start with #", exceptionThrown.getMessage());

		// missing both
		AttributeRange range = attributeToRangesMap.get(attributeId).get(0);
		range.setRangeConstraint("int(..)");
		exceptionThrown = assertThrows(IllegalArgumentException.class,
				() -> generator.generateAttributeRules(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangesMap, conceptToPtMap, Collections.singletonList(Long.valueOf(attributeId))),
				"");
		assertEquals("Both minimum and maximum range values are missing ..", exceptionThrown.getMessage());

		// minimum range missing #
		range = attributeToRangesMap.get(attributeId).get(0);
		range.setRangeConstraint("int(10..)");
		exceptionThrown = assertThrows(IllegalArgumentException.class,
				() -> generator.generateAttributeRules(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangesMap, conceptToPtMap, Collections.singletonList(Long.valueOf(attributeId))));
		assertEquals("Number constraint 10 is missing #", exceptionThrown.getMessage());

		// minimum range with >=
		range = attributeToRangesMap.get(attributeId).get(0);
		range.setRangeConstraint("int(>=10..)");
		exceptionThrown = assertThrows(IllegalArgumentException.class,
				() -> generator.generateAttributeRules(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangesMap, conceptToPtMap, Collections.singletonList(Long.valueOf(attributeId))));
		assertEquals("Number constraint >=10 is missing #", exceptionThrown.getMessage());

		// minimum range with >=
		range = attributeToRangesMap.get(attributeId).get(0);
		range.setRangeConstraint("int(>=#10..)");
		exceptionThrown = assertThrows(IllegalArgumentException.class,
				() -> generator.generateAttributeRules(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangesMap, conceptToPtMap, Collections.singletonList(Long.valueOf(attributeId))));
		assertEquals("Only > is allowed before the minimum value but got >=#10", exceptionThrown.getMessage());

		// minimum range with <
		range = attributeToRangesMap.get(attributeId).get(0);
		range.setRangeConstraint("int(<#10..)");
		exceptionThrown = assertThrows(IllegalArgumentException.class,
				() -> generator.generateAttributeRules(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangesMap, conceptToPtMap, Collections.singletonList(Long.valueOf(attributeId))));
		assertEquals("Only > is allowed before the minimum value but got <#10", exceptionThrown.getMessage());

		// maximum range missing #
		range = attributeToRangesMap.get(attributeId).get(0);
		range.setRangeConstraint("int(..10)");
		exceptionThrown = assertThrows(IllegalArgumentException.class,
				() -> generator.generateAttributeRules(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangesMap, conceptToPtMap, Collections.singletonList(Long.valueOf(attributeId))),
				"");
		assertEquals("Number constraint 10 does not have #", exceptionThrown.getMessage());

		// maximum range with <=
		range = attributeToRangesMap.get(attributeId).get(0);
		range.setRangeConstraint("int(..<=#20)");
		exceptionThrown = assertThrows(IllegalArgumentException.class,
				() -> generator.generateAttributeRules(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangesMap, conceptToPtMap, Collections.singletonList(Long.valueOf(attributeId))));
		assertEquals("Only < is allowed before the maximum value but got <=#20", exceptionThrown.getMessage());

		// maximum range with >
		range = attributeToRangesMap.get(attributeId).get(0);
		range.setRangeConstraint("int(..>#20)");
		exceptionThrown = assertThrows(IllegalArgumentException.class,
				() -> generator.generateAttributeRules(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangesMap, conceptToPtMap, Collections.singletonList(Long.valueOf(attributeId))));
		assertEquals("Only < is allowed before the maximum value but got >#20", exceptionThrown.getMessage());

		// no number
		range = attributeToRangesMap.get(attributeId).get(0);
		range.setRangeConstraint("int(#..#)");
		exceptionThrown = assertThrows(IllegalArgumentException.class,
				() -> generator.generateAttributeRules(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangesMap, conceptToPtMap, Collections.singletonList(Long.valueOf(attributeId))),
				"");
		assertEquals("Number constraint contains no value after #", exceptionThrown.getMessage());

		// empty value
		range = attributeToRangesMap.get(attributeId).get(0);
		range.setRangeConstraint("str( )");
		exceptionThrown = assertThrows(IllegalArgumentException.class,
				() -> generator.generateAttributeRules(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangesMap, conceptToPtMap, Collections.singletonList(Long.valueOf(attributeId))),
				"");
		assertEquals("Constraint contains no value.", exceptionThrown.getMessage());

		// invalid value type
		range = attributeToRangesMap.get(attributeId).get(0);
		range.setRangeConstraint("int(#1.00..#10)");
		exceptionThrown = assertThrows(IllegalArgumentException.class,
				() -> generator.generateAttributeRules(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangesMap, conceptToPtMap, Collections.singletonList(Long.valueOf(attributeId))),
				"");
		assertEquals("1.00 is not a type of int", exceptionThrown.getMessage());

		// invalid value type
		range = attributeToRangesMap.get(attributeId).get(0);
		range.setRangeConstraint("dec(#10..#5.05)");
		exceptionThrown = assertThrows(IllegalArgumentException.class,
				() -> generator.generateAttributeRules(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangesMap, conceptToPtMap, Collections.singletonList(Long.valueOf(attributeId))),
				"");
		assertEquals("Minimum value of 10 can not be great than the maximum value of 5.05", exceptionThrown.getMessage());
	}

	@Test
	void testAttributeRuleWithConcreteValues() throws Exception {
		String attributeId = "3264475007";
		Map<String, Domain> domainsByDomainIdMap = constructConcreteDomainTestData(attributeId, "int(#10)");
		// one value
		List<AttributeRange> attributeRanges = generator.generateAttributeRules(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangesMap, conceptToPtMap, Collections.singletonList(Long.valueOf(attributeId)));
		assertEquals(1, attributeRanges.size());
		assertEquals("int(#10)", attributeRanges.get(0).getRangeConstraint());
		assertTrue(attributeRanges.get(0).getAttributeRule() != null);
		String expected = "<< 373873005 |Pharmaceutical / biologic product (product)|: [0..*] { [0..1] 3264475007 |CD has presentation strength numerator value| = #10 }";
		assertEquals(expected, attributeRanges.get(0).getAttributeRule());

		// multiple with OR
		AttributeRange range = attributeToRangesMap.get(attributeId).get(0);
		range.setRangeConstraint("int(#10 #20)");
		attributeRanges = generator.generateAttributeRules(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangesMap, conceptToPtMap, Collections.singletonList(Long.valueOf(attributeId)));
		assertEquals(1, attributeRanges.size());
		assertEquals("int(#10 #20)", attributeRanges.get(0).getRangeConstraint());
		assertTrue(attributeRanges.get(0).getAttributeRule() != null);
		expected = "<< 373873005 |Pharmaceutical / biologic product (product)|: [0..*] { [0..1] 3264475007 |CD has presentation strength numerator value| = #10 OR [0..1] 3264475007 |CD has presentation strength numerator value| = #20 }";
		assertEquals(expected, attributeRanges.get(0).getAttributeRule());

		// multiple with AND
		range.setRangeConstraint("str(\"a\",\"b\")");
		attributeRanges = generator.generateAttributeRules(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangesMap, conceptToPtMap, Collections.singletonList(Long.valueOf(attributeId)));
		assertEquals(1, attributeRanges.size());
		assertEquals("str(\"a\",\"b\")", attributeRanges.get(0).getRangeConstraint());
		assertTrue(attributeRanges.get(0).getAttributeRule() != null);
		expected = "<< 373873005 |Pharmaceutical / biologic product (product)|: [0..*] { [0..1] 3264475007 |CD has presentation strength numerator value| = \"a\" AND [0..1] 3264475007 |CD has presentation strength numerator value| = \"b\" }";
		assertEquals(expected, attributeRanges.get(0).getAttributeRule());
	}

	@Test
	void testPreCoordinationDomainTemplate() throws Exception {
		Domain substance = new Domain("19d3f679-5369-42fb-9543-8795fdee5dce", null, true, "105590001",
				new Constraint("<< 105590001 |Substance (substance)|", "105590001", Operator.descendantorselfof),
				"", new Constraint("<< 105590001 |Substance (substance)|", "105590001", Operator.descendantorselfof),
				"", "", "");

		AttributeDomain hasDisposition = new AttributeDomain("a549df1e-9ef3-4fa7-a29b-9b151d9a2daf", null, true,
				"726542003", "105590001", false, new Cardinality("0..*"), new Cardinality("0..0"), RuleStrength.MANDATORY, ContentType.ALL);

		AttributeDomain isModification = new AttributeDomain("20ea3358-e436-4ac2-966e-d3a10f966142", null, true,
				"738774007", "105590001", false, new Cardinality("0..*"), new Cardinality("0..0"), RuleStrength.MANDATORY, ContentType.ALL);

		Map<String, Domain> domainsByDomainIdMap = new HashMap<>();
		domainsByDomainIdMap.put("105590001", substance);
		Map<String, List<AttributeDomain>> domainToAttributesMap = new HashMap<>();
		domainToAttributesMap.put("105590001", Arrays.asList(hasDisposition, isModification));

		Map<String, List<AttributeRange>> attributeToRangeMap = new HashMap<>();
		AttributeRange hasDispositionRange = new AttributeRange("", "", true,
				"726542003", "<< 726711005 |Disposition (disposition)|",
				"", RuleStrength.MANDATORY, ContentType.ALL);
		AttributeRange isModificationRange = new AttributeRange("", "", true,
				"738774007", "<< 105590001 |Substance (substance)|",
				"", RuleStrength.MANDATORY, ContentType.ALL);
		attributeToRangeMap.put("726542003", Collections.singletonList(hasDispositionRange));
		attributeToRangeMap.put("738774007", Collections.singletonList(isModificationRange));

		conceptToPtMap.put("738774007", "Is modification of");
		conceptToPtMap.put("726542003", "Has disposition");

		List<Domain> domains = generator.generateDomainTemplates(domainsByDomainIdMap, domainToAttributesMap, attributeToRangeMap, conceptToPtMap);
		assertEquals(1, domains.size());
		assertEquals("[[+id(<< 105590001 |Substance (substance)|)]]: [[0..*]] 726542003 |Has disposition| = [[+id(<< 726711005 |Disposition (disposition)|)]]," +
						" [[0..*]] 738774007 |Is modification of| = [[+id(<< 105590001 |Substance (substance)|)]]",
				domains.get(0).getDomainTemplateForPrecoordination());
	}

	@Test
	void testSortExpressionConstraintByConceptId() {
		String rangeConstraint = "<< 420158005 |Performer of method (person)|" +
				" OR << 419358007 |Subject of record or other provider of history (person)|" +
				" OR << 444018008 |Person with characteristic related to subject of record (person)|";
		String expected = "<< 419358007 |Subject of record or other provider of history (person)|" +
				" OR << 420158005 |Performer of method (person)|" +
				" OR << 444018008 |Person with characteristic related to subject of record (person)|";
		assertEquals(expected,
				generator.sortExpressionConstraintByConceptId(rangeConstraint, "1a9b01ce-6385-11ea-9b6e-3c15c2c6e32e"));
	}


	@Test
	void testECLWithConcreteValues() {

		String validEcl = "< 27658006 |Amoxicillin| :411116001 |Has dose form| = << 385055001 |Tablet dose form| ," +
				"{179999999100 |Has basis of strength| = (219999999102 |Amoxicillin only| :189999999103 |Has strength magnitude| >= #500," +
				" 189999999103 |Has strength magnitude| <= #800, 199999999101 |Has strength unit| = 258684004 |mg| )}";
		ExpressionConstraint constraint = eclQueryBuilder.createQuery(validEcl);
		assertNotNull(constraint);

		String rangeRule = "<< 373873005 |Pharmaceutical / biologic product (product)|:" +
				" [0..*] { ([0..1] 3264475007 |CD has presentation strength numerator value| >= #10 AND " +
				"[0..1] 3264475007 |CD has presentation strength numerator value| <= #20) }";
		constraint = eclQueryBuilder.createQuery(rangeRule);
		assertNotNull(constraint);

		String numberValueRule = "<< 373873005 |Pharmaceutical / biologic product (product)|: " +
				"[0..*] { ([0..1] 3264475007 |CD has presentation strength numerator value| = #10 " +
				"OR [0..1] 3264475007 |CD has presentation strength numerator value| = #20) }";
		constraint = eclQueryBuilder.createQuery(numberValueRule);
		assertNotNull(constraint);

		String valueRule = "<< 373873005 |Pharmaceutical / biologic product (product)|:" +
				" (3264475007 |CD has presentation strength numerator value| = \"ten\"" + " OR " +
				"3264475007 |CD has presentation strength numerator value| = \"twenty\")";

		constraint = eclQueryBuilder.createQuery(valueRule);
		assertNotNull(constraint);

		// grouped
		String grouped = "<< 373873005 |Pharmaceutical / biologic product (product)|: " +
				"[0..*] { ([0..1] 3264475007 |CD has presentation strength numerator value| = #10 " +
				"OR [0..1] 3264475007 |CD has presentation strength numerator value| = #20) }";
		constraint = eclQueryBuilder.createQuery(grouped);
		assertNotNull(constraint);
	}

	private Map<String, Domain> constructConcreteDomainTestData(String attributeId, String rangeConstraint) throws ServiceException {
		AttributeDomain biologicProduct = new AttributeDomain("", null,
				true, attributeId, "373873005", true,
				new Cardinality("0..*"), new Cardinality("0..1"), RuleStrength.MANDATORY, ContentType.ALL);

		attributeToDomainsMap.put(attributeId, Collections.singletonList(biologicProduct));
		AttributeRange range = new AttributeRange("", null, true, attributeId,
				rangeConstraint," ", RuleStrength.MANDATORY, ContentType.ALL);
		attributeToRangesMap.put(attributeId, Collections.singletonList(range));
		conceptToPtMap.put(attributeId, "CD has presentation strength numerator value");
		conceptToPtMap.put("373873005", "Pharmaceutical / biologic product (product)");

		Map<String, Domain> domainsByDomainIdMap = new HashMap<>();
		domainsByDomainIdMap.put("373873005", new Domain("718bd028-e0c6-4b22-a7d4-58327c6d9b59", null, true,
				"373873005", new Constraint("<< 373873005 |Pharmaceutical / biologic product (product)|", "373873005", Operator.descendantorselfof),
				"",null, "", "", ""));
		return domainsByDomainIdMap;
	}
}
