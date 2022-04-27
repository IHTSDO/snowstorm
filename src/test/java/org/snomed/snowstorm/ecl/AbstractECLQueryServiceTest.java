package org.snomed.snowstorm.ecl;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.snomed.snowstorm.TestConcepts.NON_EXISTENT_CONCEPT;
import static org.snomed.snowstorm.TestConcepts.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

abstract class AbstractECLQueryServiceTest {

	protected static final String MAIN = "MAIN";
	protected static final boolean INFERRED = false;

	@Autowired
	protected ECLQueryService eclQueryService;

	@Autowired
	protected ConceptService conceptService;

	@Autowired
	protected VersionControlHelper versionControlHelper;

	protected Set<String> allConceptIds;
	protected BranchCriteria branchCriteria;

	// Please note the concrete domains ECL tests are in SemanticIndexUpdateServiceTest

	@Test
	void selectByDescendantAndAncestorOperators() {
		// Self
		assertEquals(
				Sets.newHashSet(SNOMEDCT_ROOT),
				strings(selectConceptIds(SNOMEDCT_ROOT)));

		// Descendant of Self
		assertEquals(
				// All concepts but not root
				allConceptIds.stream().filter(id -> !SNOMEDCT_ROOT.equals(id)).collect(Collectors.toSet()),
				strings(selectConceptIds("<" + SNOMEDCT_ROOT)));

		// Descendant or self
		assertEquals(
				allConceptIds,
				strings(selectConceptIds("<<" + SNOMEDCT_ROOT)));

		// Descendant or self wildcard
		assertEquals(
				allConceptIds,
				strings(selectConceptIds("<<*")));

		Set<String> allConceptsButRoot = new HashSet<>(allConceptIds);
		allConceptsButRoot.remove(SNOMEDCT_ROOT);

		// Descendants of wildcard
		assertEquals(
				allConceptsButRoot,
				strings(selectConceptIds("<*")));

		// Child of wildcard
		assertEquals(
				allConceptsButRoot,
				strings(selectConceptIds("<!*")));

		// Ancestor of
		assertEquals(
				Sets.newHashSet(SNOMEDCT_ROOT, CLINICAL_FINDING),
				strings(selectConceptIds(">" + DISORDER)));

		// Ancestor or Self of
		assertEquals(
				Sets.newHashSet(SNOMEDCT_ROOT, CLINICAL_FINDING, DISORDER),
				strings(selectConceptIds(">>" + DISORDER)));

		// Ancestor or Self of attribute
		assertEquals(
				Sets.newHashSet(PROCEDURE_SITE_DIRECT, PROCEDURE_SITE, CONCEPT_MODEL_OBJECT_ATTRIBUTE, CONCEPT_MODEL_ATTRIBUTE, MODEL_COMPONENT, SNOMEDCT_ROOT),
				strings(selectConceptIds(">>" + PROCEDURE_SITE_DIRECT)));
	}

	@Test
	void selectParents() {
		// Direct Parents
		assertEquals(
				Sets.newHashSet(),
				strings(selectConceptIds(">!" + SNOMEDCT_ROOT)));

		assertEquals(
				Sets.newHashSet(SNOMEDCT_ROOT),
				strings(selectConceptIds(">>!" + SNOMEDCT_ROOT)));

		assertEquals(
				Sets.newHashSet(SNOMEDCT_ROOT),
				strings(selectConceptIds(">!" + CLINICAL_FINDING)));

		assertEquals(
				Sets.newHashSet(CLINICAL_FINDING),
				strings(selectConceptIds(">!" + BLEEDING)));

		assertEquals(
				Sets.newHashSet(CLINICAL_FINDING, BLEEDING),
				strings(selectConceptIds(">>!" + BLEEDING)));
	}

	@Test
	void selectChildren() {
		// Direct Children
		assertEquals(
				Sets.newHashSet(MODEL_COMPONENT, RIGHT, BODY_STRUCTURE, CLINICAL_FINDING, PROCEDURE, ISA),
				strings(selectConceptIds("<!" + SNOMEDCT_ROOT)));

		assertEquals(
				Sets.newHashSet(SNOMEDCT_ROOT, MODEL_COMPONENT, RIGHT, BODY_STRUCTURE, CLINICAL_FINDING, PROCEDURE, ISA),
				strings(selectConceptIds("<<!" + SNOMEDCT_ROOT)));

		assertEquals(
				Sets.newHashSet(BLEEDING, DISORDER),
				strings(selectConceptIds("<!" + CLINICAL_FINDING)));

		assertEquals(
				Sets.newHashSet(BLEEDING_SKIN),
				strings(selectConceptIds("<!" + BLEEDING)));

		assertEquals(
				Sets.newHashSet(),
				strings(selectConceptIds("<!" + BLEEDING_SKIN)));
	}

	@Test
	void selectMemberOfReferenceSet() {
		// Member of x
		assertEquals(
				Sets.newHashSet(CLINICAL_FINDING, BODY_STRUCTURE),
				strings(selectConceptIds("^" + REFSET_MRCM_ATTRIBUTE_DOMAIN))
		);

		assertEquals(
				Sets.newHashSet(BODY_STRUCTURE),
				strings(selectConceptIds("^" + REFSET_SIMPLE))
		);

		// Member of any reference set
		assertEquals(
				Sets.newHashSet(CLINICAL_FINDING, BODY_STRUCTURE),
				strings(selectConceptIds("^*"))
		);
	}

	@Test
	void selectByAttributeType() {
		assertEquals(
				Sets.newHashSet(),
				strings(selectConceptIds("*:" + CLINICAL_FINDING + "=*")));

		assertEquals(
				Sets.newHashSet(BLEEDING, BLEEDING_SKIN, PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "=*")));

		assertEquals(
				Sets.newHashSet(BLEEDING),
				strings(selectConceptIds(BLEEDING +":" + ASSOCIATED_MORPHOLOGY + "=*")));

		// Attribute constraint
		assertEquals(
				Sets.newHashSet(CHEST_IMAGING),
				strings(selectConceptIds("<<" + PROCEDURE +":<" + PROCEDURE_SITE + "=*")));

		// Attribute constraint
		assertEquals(
				Sets.newHashSet(OPERATION_ON_HEART, CHEST_IMAGING, AMPUTATION_FOOT_LEFT, AMPUTATION_FOOT_RIGHT, AMPUTATION_FOOT_BILATERAL),
				strings(selectConceptIds("<<" + PROCEDURE + ":<<" + PROCEDURE_SITE + "=*")));

		assertEquals(
				Sets.newHashSet(),
				strings(selectConceptIds(BLEEDING + ":" + NON_EXISTENT_CONCEPT + "=*")));
	}

	@Test
	void selectByAttributeValue() {
		// Attribute Value Equals
		assertEquals(
				Sets.newHashSet(BLEEDING, BLEEDING_SKIN),
				strings(selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "=" + HEMORRHAGE)));

		// Attribute Value Not Equals
		assertEquals(
				Sets.newHashSet(PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "!=" + HEMORRHAGE)));

		// Attribute Value Equals - Descendant or self
		assertEquals(
				Sets.newHashSet(BLEEDING, BLEEDING_SKIN, PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "= <<" + SNOMEDCT_ROOT)));

		assertEquals(
				Sets.newHashSet(BLEEDING_SKIN, PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(selectConceptIds("*:" + FINDING_SITE + "= <<" + BODY_STRUCTURE)));

		assertEquals(
				Sets.newHashSet(),
				strings(selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "=" + CLINICAL_FINDING)));

		assertEquals(
				Sets.newHashSet(),
				strings(selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "=" + NON_EXISTENT_CONCEPT)));
	}

	@Test
	void selectByAttributeConjunction() {
		assertEquals(
				Sets.newHashSet(BLEEDING_SKIN),
				strings(selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "=" + HEMORRHAGE+ " , " + FINDING_SITE + "=*")));
	}

	@Test
	void selectByAttributeDisjunction() {
		Collection<Long> ids = selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "=" + HEMORRHAGE + " OR " + FINDING_SITE + "=*");
		assertEquals(
				Sets.newHashSet(BLEEDING_SKIN, BLEEDING, PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(ids));
	}

	@Test
	void focusConceptConjunction() {
		assertEquals(
				Sets.newHashSet(DISORDER, BLEEDING, BLEEDING_SKIN, PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(selectConceptIds("<" + SNOMEDCT_ROOT + " AND <" + CLINICAL_FINDING)));

		assertEquals(
				Sets.newHashSet(CLINICAL_FINDING),
				strings(selectConceptIds("<" + SNOMEDCT_ROOT + " AND " + CLINICAL_FINDING)));

		assertEquals(
				Sets.newHashSet(PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(selectConceptIds("<" + DISORDER + " AND <" + CLINICAL_FINDING)));
	}

	@Test
	void focusConceptDisjunction() {
		assertEquals(
				Sets.newHashSet(SNOMEDCT_ROOT, CLINICAL_FINDING),
				strings(selectConceptIds(SNOMEDCT_ROOT + " OR " + CLINICAL_FINDING)));

		assertEquals(
				Sets.newHashSet(HEMORRHAGE, CLINICAL_FINDING, DISORDER, BLEEDING, BLEEDING_SKIN, PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(selectConceptIds(HEMORRHAGE + " OR <<" + CLINICAL_FINDING)));

		assertEquals(
				Sets.newHashSet(HEMORRHAGE),
				strings(selectConceptIds(HEMORRHAGE + " OR " + HEMORRHAGE)));
	}

	@Test
	void focusConceptExclusion() {
		assertEquals(
				Sets.newHashSet(CLINICAL_FINDING, BLEEDING, BLEEDING_SKIN),
				strings(selectConceptIds("<<" + CLINICAL_FINDING + " MINUS <<" + DISORDER)));

		assertEquals(
				Sets.newHashSet(),
				strings(selectConceptIds(HEMORRHAGE + " MINUS " + HEMORRHAGE)));
	}

	@Test
	void focusConceptConjunctionDisjunction() {
		assertEquals(
				Sets.newHashSet(BLEEDING, BLEEDING_SKIN),
				strings(selectConceptIds("<" + SNOMEDCT_ROOT + " AND (<<" + BLEEDING + " OR " + SNOMEDCT_ROOT +")")));
	}

	@Test
	void attributeGroups() {
		String eclWithoutGrouping =
				"<404684003 |Clinical finding| :\n" +
						"	363698007 |Finding site| = <<39057004 |Pulmonary valve structure| , \n" +
						"	116676008 |Associated morphology| = <<415582006 |Stenosis|," +
						"	363698007 |Finding site| = <<53085002 |Right ventricular structure|,\n" +
						"	116676008 |Associated morphology| = <<56246009 |Hypertrophy|";
		String eclWithGrouping =
				"<404684003 |Clinical finding| :\n" +
						"	{" +
						"		363698007 |Finding site| = <<39057004 |Pulmonary valve structure|,\n" +
						"		116676008 |Associated morphology| = <<415582006 |Stenosis|" +
						"	},\n" +
						"	{" +
						"		363698007 |Finding site| = <<53085002 |Right ventricular structure|,\n" +
						"		116676008 |Associated morphology| = <<56246009 |Hypertrophy|" +
						"	}";



		assertEquals(
				"ECL without grouping finds both concepts",
				Sets.newHashSet(PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(selectConceptIds(eclWithoutGrouping)));

		assertEquals(
				"ECL with grouping finds only the concept with matching groups",
				Sets.newHashSet(PENTALOGY_OF_FALLOT),
				strings(selectConceptIds(eclWithGrouping)));
	}

	@Test
	void attributeGroupCardinality() {
		assertEquals(
				"Match clinical finding with at least one grouped finding site attributes.",
				Sets.newHashSet(PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(selectConceptIds("<404684003 |Clinical finding|: { 363698007 |Finding site| = * }")));

		assertEquals(
				"Match clinical finding with zero grouped finding site attributes.",
				Sets.newHashSet(DISORDER, BLEEDING, BLEEDING_SKIN),
				strings(selectConceptIds("<404684003 |Clinical finding|: [0..0]{ 363698007 |Finding site| = * }")));

		assertEquals(
				"Match clinical finding with one grouped finding site attributes.",
				Sets.newHashSet(),
				strings(selectConceptIds("<404684003 |Clinical finding|: [1..1]{ 363698007 |Finding site| = * }")));

		assertEquals(
				"Match clinical finding with one or two grouped finding site attributes.",
				Sets.newHashSet(PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(selectConceptIds("<404684003 |Clinical finding|: [1..2]{ 363698007 |Finding site| = * }")));

		assertEquals(
				"Match clinical finding with three or more grouped finding site attributes.",
				Sets.newHashSet(),
				strings(selectConceptIds("<404684003 |Clinical finding|: [3..*]{ 363698007 |Finding site| = * }")));
	}

	@Test
	void attributeGroupDisjunction() {
		assertEquals(
				"Match clinical finding with at least one grouped finding site attributes.",
				Sets.newHashSet(PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),// No bleeding because |Associated morphology| must be grouped
				strings(selectConceptIds("<404684003 |Clinical finding|: { 363698007 |Finding site| = * } OR { 116676008 |Associated morphology| = * }")));
	}
	
	@Test
	void attributeGroupDisjunction2() {
		//Searching for Right OR Left foot site amputations should return ALL foot amputations: left, right and bilateral
		assertEquals(
			"Match procedure with left OR right foot (grouped)",
			Sets.newHashSet(AMPUTATION_FOOT_LEFT, AMPUTATION_FOOT_RIGHT, AMPUTATION_FOOT_BILATERAL),
			strings(selectConceptIds("< 71388002 |Procedure|: { 363704007 |Procedure site| = 22335008 |Left Foot| } OR { 363704007 |Procedure site| = 7769000 |Right Foot| }")));

	}

	@Test
	void reverseFlagAttributes() {
		// Select the Finding sites of descendants of Disorder
		// Using Reverse Flag
		assertEquals(
				Sets.newHashSet(RIGHT_VENTRICULAR_STRUCTURE, PULMONARY_VALVE_STRUCTURE),
				strings(selectConceptIds("*:R " + FINDING_SITE + " = <" + DISORDER)));

		// Using Dot notation
		assertEquals(
				Sets.newHashSet(RIGHT_VENTRICULAR_STRUCTURE, PULMONARY_VALVE_STRUCTURE),
				strings(selectConceptIds("<" + DISORDER + "." + FINDING_SITE)));

		// Dot notation against empty set of concepts
		assertEquals(
				Sets.newHashSet(),
				strings(selectConceptIds("<" + RIGHT_VENTRICULAR_STRUCTURE + "." + LATERALITY)));

		// Select the Finding sites of descendants of Clinical finding
		assertEquals(
				Sets.newHashSet(RIGHT_VENTRICULAR_STRUCTURE, PULMONARY_VALVE_STRUCTURE, SKIN_STRUCTURE),
				strings(selectConceptIds("<" + CLINICAL_FINDING + "." + FINDING_SITE)));

		// Select the just the first two Finding sites of descendants of Clinical finding
		PageRequest pageRequest = PageRequest.of(0, 2);
		assertEquals(
				Sets.newHashSet(RIGHT_VENTRICULAR_STRUCTURE, SKIN_STRUCTURE),
				strings(selectConceptIds("<" + CLINICAL_FINDING + "." + FINDING_SITE, pageRequest)));

		// Select the second page of Finding sites of descendants of Clinical finding
		pageRequest = PageRequest.of(1, 2);
		assertEquals(
				Sets.newHashSet(PULMONARY_VALVE_STRUCTURE),
				strings(selectConceptIds("<" + CLINICAL_FINDING + "." + FINDING_SITE, pageRequest)));

		// Select the Laterality of Finding sites of descendants of Clinical finding
		assertEquals(
				Sets.newHashSet(RIGHT),
				strings(selectConceptIds("<" + CLINICAL_FINDING + "." + FINDING_SITE + "." + LATERALITY)));
	}

	@Test
	void conjunctionWithReverseFlag() {
		assertEquals(
				Sets.newHashSet(RIGHT_VENTRICULAR_STRUCTURE, PULMONARY_VALVE_STRUCTURE),
				strings(selectConceptIds("<" + BODY_STRUCTURE + " AND (<" + DISORDER + "." + FINDING_SITE + ")")));
	}

	// TODO: Add reverse flag with cardinality

	@Test
	void attributeCardinality() {
		assertEquals(
				Sets.newHashSet(CLINICAL_FINDING, DISORDER, BLEEDING, BLEEDING_SKIN, PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(selectConceptIds("<<" + CLINICAL_FINDING + ":[0..*]" + FINDING_SITE + "=*")));

		assertEquals(
				Sets.newHashSet(CLINICAL_FINDING, DISORDER, BLEEDING, BLEEDING_SKIN, PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(selectConceptIds("<<" + CLINICAL_FINDING + ":[0..2]" + FINDING_SITE + "=*")));

		assertEquals(
				Sets.newHashSet(BLEEDING_SKIN, PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(selectConceptIds("<<" + CLINICAL_FINDING + ":[1..2]" + FINDING_SITE + "=*")));

		assertEquals(
				Sets.newHashSet(BLEEDING_SKIN),
				strings(selectConceptIds("<<" + CLINICAL_FINDING + ":[1..1]" + FINDING_SITE + "=*")));

		assertEquals(
				Sets.newHashSet(BLEEDING_SKIN, CLINICAL_FINDING, DISORDER, BLEEDING),
				strings(selectConceptIds("<<" + CLINICAL_FINDING + ":[0..1]" + FINDING_SITE + "=*")));

		assertEquals(
				Sets.newHashSet(CLINICAL_FINDING, DISORDER, BLEEDING),
				strings(selectConceptIds("<<" + CLINICAL_FINDING + ":[0..0]" + FINDING_SITE + "=*")));

		assertEquals(
				Sets.newHashSet(BLEEDING_SKIN, PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(selectConceptIds("<<" + CLINICAL_FINDING + ":[1..2]" + FINDING_SITE + "= <<" + BODY_STRUCTURE)));

		assertEquals(
				Sets.newHashSet(BLEEDING_SKIN),
				strings(selectConceptIds(
						"<<" + CLINICAL_FINDING + ":" +
								"[1..2]" + FINDING_SITE + "= <<" + BODY_STRUCTURE + "," +
								"[0..0]" + ASSOCIATED_MORPHOLOGY + "= <<" + STENOSIS)));

		assertEquals(
				Sets.newHashSet(BLEEDING_SKIN, PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(selectConceptIds(
						"<<" + CLINICAL_FINDING + ":" +
								"[1..2]" + FINDING_SITE + "= <<" + BODY_STRUCTURE + "," +
								"[0..1]" + ASSOCIATED_MORPHOLOGY + "= <<" + STENOSIS)));

		assertEquals(
				Sets.newHashSet(PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(selectConceptIds(
						"<<" + CLINICAL_FINDING + ":" +
								"[1..2]" + FINDING_SITE + "= <<" + BODY_STRUCTURE + "," +
								"[1..*]" + ASSOCIATED_MORPHOLOGY + "= <<" + STENOSIS)));

		assertEquals(
				Sets.newHashSet(PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(selectConceptIds(
						"<<" + CLINICAL_FINDING + ":" +
								"{" +
								"[1..1]" + FINDING_SITE + " != <<" + PULMONARY_VALVE_STRUCTURE + "," +
								"[1..*]" + ASSOCIATED_MORPHOLOGY + "= <<" + STENOSIS + "" +
								"}")));

		assertEquals(
				Sets.newHashSet(PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(selectConceptIds(
						"<<" + CLINICAL_FINDING + ":" +
								"[0..0]" + FINDING_SITE + " != <<" + BODY_STRUCTURE + "," +
								"[1..*]" + ASSOCIATED_MORPHOLOGY + "= <<" + STENOSIS)));

	}

	@Test
	void testAncestorOfWildcard() {
		// All non-leaf concepts
		assertEquals(
				"[123037004, 71388002, 410662002, 138875005, 131148009, 363704007, 762705008, 64572001, 900000000000441003, 404684003]",
				strings(selectConceptIds(">*")).toString());

		// All non-leaf concepts
		assertEquals(
				"[123037004, 71388002, 410662002, 138875005, 131148009, 363704007, 762705008, 64572001, 900000000000441003, 404684003]",
				strings(selectConceptIds(">!*")).toString());

		// All leaf concepts
		assertEquals(
				"[723312009, 297968009, 50960005, 24028007, 22335008, 39057004, 180030006, " +
				"64915003, 413815006, 272741003, " + 
				"999204306007, 7769000, 723311002, 204306007, 116680003, " +
				"363698007, 116676008, 56246009, 53085002, " +
				"39937001, 405813007, 80891009, 415582006, 51185008]",
				strings(selectConceptIds("* MINUS >*")).toString());
	}

	protected Set<String> strings(Collection<Long> ids) {
		return ids.stream().map(Object::toString).collect(Collectors.toSet());
	}

	protected Collection<Long> selectConceptIds(String ecl) {
		return selectConceptIds(ecl, PageRequest.of(0, 10000));
	}

	protected Collection<Long> selectConceptIds(String ecl, PageRequest pageRequest) {
		return eclQueryService.selectConceptIds(ecl, branchCriteria, INFERRED, pageRequest).getContent();
	}
}
