package org.snomed.snowstorm.mrcm;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.langauges.ecl.domain.refinement.Operator;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.mrcm.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;
import java.util.stream.Collectors;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class MRCMUpdateServiceTest {
	@Autowired
	private MRCMUpdateService mrcmUpdateService;

	private Map<String, List<AttributeDomain>> attributeToDomainsMap;
	private Map<String, List<AttributeRange>> attributeToRangesMap;
	private Map<String, String> conceptToPtMap;

	private Logger logger = LoggerFactory.getLogger(MRCMUpdateServiceTest.class);

	@Before
	public void setUp() {
		attributeToDomainsMap = new HashMap<>();
		attributeToRangesMap = new HashMap<>();
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
		attributeToRangesMap.put("255234002", Arrays.asList(range));
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

		List<AttributeRange> attributeRanges = mrcmUpdateService.generateAttributeRule(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangesMap, conceptToPtMap);
		assertEquals(1, attributeRanges.size());
		assertTrue(attributeRanges.get(0).getAttributeRule() != null);
		String expected = "(<< 272379006 |Event (event)|: [0..*] { [0..1] 255234002 |After| = (<< 272379006 |Event (event)| OR << 404684003 |Clinical finding (finding)| OR << 71388002 |Procedure (procedure)|) })" +
				" OR (<< 404684003 |Clinical finding (finding)|: [0..*] { [0..1] 255234002 |After| = (<< 272379006 |Event (event)| OR << 404684003 |Clinical finding (finding)| OR << 71388002 |Procedure (procedure)|) })";

		assertEquals(expected, attributeRanges.get(0).getAttributeRule());
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
		attributeToRangesMap.put("405815000", Arrays.asList(range));
		conceptToPtMap.put("405815000", "Procedure device");

		Map<String, Domain> domainsByDomainIdMap = new HashMap<>();
		domainsByDomainIdMap.put("71388002", new Domain("0bbce893-2543-4125-8bb1-298c83ee75fc", null, true,
				"71388002", new Constraint("<< 71388002 |Procedure (procedure)|", "71388002", Operator.descendantorselfof),
				"",null, "", "", ""));

		domainsByDomainIdMap.put("363787002", new Domain("8a2a2554-af2f-4616-94ea-408b90b9124e", null, true,
				"363787002", new Constraint("<< 363787002 |Observable entity (observable entity)|", "363787002", Operator.descendantorselfof),
				"",null, "", "", ""));

		List<AttributeRange> attributeRanges = mrcmUpdateService.generateAttributeRule(domainsByDomainIdMap, attributeToDomainsMap, attributeToRangesMap, conceptToPtMap);
		assertEquals(1, attributeRanges.size());
		assertTrue(attributeRanges.get(0).getAttributeRule() != null);
		assertEquals("(<< 363787002 |Observable entity (observable entity)|: [0..*] { [0..*] 405815000 |Procedure device| = << 49062001 |Device (physical object)| }) " +
						"OR (<< 71388002 |Procedure (procedure)|: [0..*] { [0..1] 405815000 |Procedure device| = << 49062001 |Device (physical object)| })",
				attributeRanges.get(0).getAttributeRule());
	}


	@Test
	public void testPrecoodinationDomainTemplate() throws Exception {
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
		attributeToRangeMap.put("726542003", Arrays.asList(hasDispositionRange));
		attributeToRangeMap.put("738774007", Arrays.asList(isModificationRange));

		conceptToPtMap.put("738774007", "Is modification of");
		conceptToPtMap.put("726542003", "Has disposition");

		List<Domain> domains = mrcmUpdateService.generateDomainTemplates(domainsByDomainIdMap, domainToAttributesMap, attributeToRangeMap, conceptToPtMap);
		assertEquals(1, domains.size());
		assertEquals("[[+id(<< 105590001 |Substance (substance)|)]]: [[0..*]] 726542003 |Has disposition| = [[+id(<< 726711005 |Disposition (disposition)|)]]," +
						" [[0..*]] 738774007 |Is modification of| = [[+id(<< 105590001 |Substance (substance)|)]]",
				domains.get(0).getDomainTemplateForPrecoordination());
	}

	@Test
	public void testPrecoodinationDomainTemplateWithParentDomain() throws Exception {
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
		domainToAttributesMap.put("723264001", Arrays.asList(laterality));
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

		attributeToRangeMap.put("272741003", Arrays.asList(lateralityRange));
		attributeToRangeMap.put("733928003", Arrays.asList(allOrPartOfRange));
		attributeToRangeMap.put("733931002", Arrays.asList(constitutionalPartOfRange));
		attributeToRangeMap.put("733930001", Arrays.asList(regionalPartOfRange));
		attributeToRangeMap.put("733933004", Arrays.asList(lateralhalfOfRange));
		attributeToRangeMap.put("733932009", Arrays.asList(systemicPartOfRange));
		attributeToRangeMap.put("774081006", Arrays.asList(properPartOfRange));

		conceptToPtMap.put("272741003", "Laterality");
		conceptToPtMap.put("733928003", "All or part of");
		conceptToPtMap.put("733931002", "Constitutional part of");
		conceptToPtMap.put("733930001", "Regional part of");
		conceptToPtMap.put("733933004", "Lateral half of");
		conceptToPtMap.put("733932009", "Systemic part of");
		conceptToPtMap.put("774081006", "Proper part of");

		List<Domain> domains = mrcmUpdateService.generateDomainTemplates(domainsByDomainIdMap, domainToAttributesMap, attributeToRangeMap, conceptToPtMap);
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

		diff(expected, actual);
		assertEquals(expected, actual);

	}


	@Test
	public void testSortExpressionConstraintByConceptId() {
		String rangeConstraint = "<< 420158005 |Performer of method (person)|" +
				" OR << 419358007 |Subject of record or other provider of history (person)|" +
				" OR << 444018008 |Person with characteristic related to subject of record (person)|";
		String expected = "<< 419358007 |Subject of record or other provider of history (person)|" +
				" OR << 420158005 |Performer of method (person)|" +
				" OR << 444018008 |Person with characteristic related to subject of record (person)|";
		assertEquals(expected,
				mrcmUpdateService.sortExpressionConstraintByConceptId(rangeConstraint, "1a9b01ce-6385-11ea-9b6e-3c15c2c6e32e"));
	}

	private void diff(String published, String actual) {
		List<String> publsihedSorted = split(published);
		List<String> actualSorted = split(actual);
		assertEquals(publsihedSorted.size(), actualSorted.size());
		for (String token : publsihedSorted) {
			if (!actualSorted.contains(token)) {
				System.out.println("expected token " + token + " but not found");
			}
		}
	}

	private List<String> split(String expression) {
		List<String> result = new ArrayList<>();
		for (String part : expression.split(",", -1)) {
			if (part.contains(":")) {
				result.addAll(Arrays.asList(part.split(":", -1)));
			} else {
				result.add(part);
			}
		}
		Collections.sort(result);
		return result;
	}


	private void runPostcoordinationDiffReport(List<Domain> updatedDomains, Map<String,Domain> domainMapByDomainId) {
		if (updatedDomains == null || updatedDomains.isEmpty()) {
			return;
		}
		int sameCounter = 0;
		int sameWhenSortingIngored = 0;
		int diffCounter = 0;
		for (Domain domain : updatedDomains) {
			String domainId = domain.getReferencedComponentId();
			String published = domainMapByDomainId.get(domainId).getDomainTemplateForPostcoordination();
			String actual = domain.getDomainTemplateForPostcoordination();
			if (!published.equals(actual)) {
				logger.info("Analyzing postcoordination domain template for domain id " + domainId);
				if (hasDiff(published, actual, false)) {
					diffCounter++;
					logger.info("before = " + published);
					logger.info("after = " + actual);
				} else {
					logger.info("domain template is the same when cardinality and sorting is ignored " + domain.getReferencedComponentId());
					sameWhenSortingIngored++;
				}
			} else {
				sameCounter++;
				logger.info("domain template is the same for " + domainId);
			}
		}
		logger.info("Total templates updated = " + updatedDomains.size());
		logger.info("Total templates are the same without change = " + sameCounter);
		logger.info("Total templates are the same when cardinality and sorting is ignored = " + sameWhenSortingIngored);
		logger.info("Total templates found with diffs = " + diffCounter);
	}

	private void runAttributeRulesDiffReport(List<AttributeRange> attributeRanges, Map<String, List<AttributeRange>> attributeToRangesMap) {
		int sameCounter = 0;
		int sameWhenSortingIngored = 0;
		int diffCounter = 0;
		for (AttributeRange range : attributeRanges) {
			String attributeId = range.getReferencedComponentId();
			String publishedRule = null;
			for (AttributeRange published : attributeToRangesMap.get(attributeId)) {
				if (range.getId().equals(published.getId())) {
					publishedRule = published.getAttributeRule();
					break;
				}
			}
			String actual = range.getAttributeRule();
			if (!actual.equals(publishedRule)) {
				logger.info("Analyzing attribute rule for attribute " + attributeId + " with id = " + range.getId());
				if (hasDiff(publishedRule, actual, true)) {
					diffCounter++;
					logger.info("before = " + publishedRule);
					logger.info("after = " + actual);
				} else {
					logger.info("Attribute rules are the same when cardinality and sorting are ignored " + attributeId);
					sameWhenSortingIngored++;
				}
			} else {
				sameCounter++;
				logger.info("Attribute rule is the same for " + attributeId);
			}
		}
		logger.info("Total templates updated = " + attributeRanges.size());
		logger.info("Total templates are the same without change = " + sameCounter);
		logger.info("Total templates are the same when cardinality and sorting is ignored = " + sameWhenSortingIngored);
		logger.info("Total templates found with diffs = " + diffCounter);
	}

	private void runPrecoordinationDiffReport(List<Domain> updatedDomains, Map<String,Domain> domainMapByDomainId) {
		if (updatedDomains == null || updatedDomains.isEmpty()) {
			return;
		}
		int sameCounter = 0;
		int sameWhenSortingIngored = 0;
		int diffCounter = 0;
		for (Domain domain : updatedDomains) {
			String domainId = domain.getReferencedComponentId();
			String published = domainMapByDomainId.get(domainId).getDomainTemplateForPrecoordination();
			String actual = domain.getDomainTemplateForPrecoordination();
			if (!published.equals(actual)) {
				logger.info("Analyzing precoordinationdomain template for domain id " + domainId);
				if (hasDiff(published, actual, false)) {
					diffCounter++;
					logger.info("before = " + published);
					logger.info("after = " + actual);
				} else {
					logger.info("domain template is the same when cardinality and sorting is ignored " + domain.getReferencedComponentId());
					sameWhenSortingIngored++;
				}
			} else {
				sameCounter++;
				logger.info("domain template is the same for " + domainId);
			}
		}
		logger.info("Total templates updated = " + updatedDomains.size());
		logger.info("Total templates are the same without change = " + sameCounter);
		logger.info("Total templates are the same when cardinality and sorting is ignored = " + sameWhenSortingIngored);
		logger.info("Total templates found with diffs = " + diffCounter);
	}

	private boolean hasDiff(String published, String actual, boolean ignoreCardinality) {
		boolean hasDiff = false;
		List<String> publishedSorted = split(published, ignoreCardinality);
		List<String> actualSorted = split(actual, ignoreCardinality);

		logger.info("Published but missing in the new generated");
		for (String token : publishedSorted) {
			if (!actualSorted.contains(token)) {
				System.out.println(token);
				hasDiff = true;
			}
		}

		logger.info("In the new generated but missing from the published");
		for (String token : actualSorted) {
			if (!publishedSorted.contains(token)) {
				hasDiff = true;
			}
		}
		return hasDiff;
	}

	private List<String> split(String expression, boolean ignoreCardinality) {
		List<String> result = new ArrayList<>();
		for (String part : expression.split(",", -1)) {
			if (part.contains(":")) {
				result.addAll(Arrays.asList(part.split(":", -1)));
			} else {
				result.add(part.trim());
			}
		}
		if (ignoreCardinality) {
			List<String> updated = new ArrayList<>();
			for (String token : result) {
				if (token.contains("..")) {
					if (token.endsWith("}")) {
						token = token.replace("}", "");
					}
					updated.add(token.substring(token.lastIndexOf("..") + 5, token.length()).trim());
				}
			}
			result = updated;
		}
		Collections.sort(result);
		return result;
	}
}
