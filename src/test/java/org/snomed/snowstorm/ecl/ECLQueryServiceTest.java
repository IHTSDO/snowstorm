package org.snomed.snowstorm.ecl;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.elasticsearch.index.query.QueryBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class ECLQueryServiceTest extends AbstractTest {

	// Model
	private static final String MODEL_COMPONENT = "900000000000441003";

	// Attributes
	private static final String FINDING_SITE = "363698007";
	private static final String ASSOCIATED_MORPHOLOGY = "116676008";
	private static final String PROCEDURE_SITE = "363704007";
	private static final String PROCEDURE_SITE_DIRECT = "405813007";
	private static final String LATERALITY = "272741003";

	// Qualifier Value
	private static final String RIGHT = "24028007";

	// Body Structure
	private static final String BODY_STRUCTURE = "123037004";
	private static final String HEART_STRUCTURE = "80891009";
	private static final String SKIN_STRUCTURE = "39937001";
	private static final String THORACIC_STRUCTURE = "51185008";
	private static final String PULMONARY_VALVE_STRUCTURE = "39057004";
	private static final String RIGHT_VENTRICULAR_STRUCTURE = "53085002";
	private static final String STENOSIS = "415582006";
	private static final String HYPERTROPHY = "56246009";
	private static final String HEMORRHAGE = "50960005";

	// Finding
	private static final String DISORDER = "64572001";
	private static final String BLEEDING = "131148009";
	private static final String BLEEDING_SKIN = "297968009";
	private static final String PENTALOGY_OF_FALLOT = "204306007";
	private static final String PENTALOGY_OF_FALLOT_INCORRECT_GROUPING = "999204306007";

	// Procedure
	private static final String PROCEDURE = "71388002";
	private static final String OPERATION_ON_HEART = "64915003";
	private static final String CHEST_IMAGING = "413815006";

	private static final String NON_EXISTENT_CONCEPT = "12345001";

	private static final String MAIN = "MAIN";
	private static final boolean STATED = true;

	@Autowired
	private ECLQueryService eclQueryService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ReferenceSetMemberService memberService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	private Set<String> allConceptIds;
	private QueryBuilder branchCriteria;

	@Before
	public void setup() throws ServiceException {
		branchService.create(MAIN);

		List<Concept> allConcepts = new ArrayList<>();

		allConcepts.add(new Concept(SNOMEDCT_ROOT));
		allConcepts.add(new Concept(ISA).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(MODEL_COMPONENT).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(FINDING_SITE).addRelationship(new Relationship(ISA, MODEL_COMPONENT)));
		allConcepts.add(new Concept(ASSOCIATED_MORPHOLOGY).addRelationship(new Relationship(ISA, MODEL_COMPONENT)));
		allConcepts.add(new Concept(PROCEDURE_SITE).addRelationship(new Relationship(ISA, MODEL_COMPONENT)));
		allConcepts.add(new Concept(PROCEDURE_SITE_DIRECT).addRelationship(new Relationship(ISA, PROCEDURE_SITE)));
		allConcepts.add(new Concept(LATERALITY).addRelationship(new Relationship(ISA, MODEL_COMPONENT)));
		allConcepts.add(new Concept(RIGHT).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));

		allConcepts.add(new Concept(BODY_STRUCTURE).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(HEART_STRUCTURE).addRelationship(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(SKIN_STRUCTURE).addRelationship(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(THORACIC_STRUCTURE).addRelationship(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(PULMONARY_VALVE_STRUCTURE).addRelationship(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(RIGHT_VENTRICULAR_STRUCTURE)
				.addRelationship(new Relationship(ISA, BODY_STRUCTURE))
				.addRelationship(new Relationship(LATERALITY, RIGHT))
		);
		allConcepts.add(new Concept(STENOSIS).addRelationship(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(HYPERTROPHY).addRelationship(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(HEMORRHAGE).addRelationship(new Relationship(ISA, BODY_STRUCTURE)));

		allConcepts.add(new Concept(CLINICAL_FINDING).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(DISORDER).addRelationship(new Relationship(ISA, CLINICAL_FINDING)));
		allConcepts.add(new Concept(BLEEDING)
				.addRelationship(new Relationship(ISA, CLINICAL_FINDING))
				.addRelationship(new Relationship(ASSOCIATED_MORPHOLOGY, HEMORRHAGE))
		);
		allConcepts.add(new Concept(BLEEDING_SKIN)
				.addRelationship(new Relationship(ISA, BLEEDING))
				.addRelationship(new Relationship(ASSOCIATED_MORPHOLOGY, HEMORRHAGE))
				.addRelationship(new Relationship(FINDING_SITE, SKIN_STRUCTURE))
		);
		/*
			 <  404684003 |Clinical finding| :
				{  363698007 |Finding site|  = <<  39057004 |Pulmonary valve structure| ,
					 116676008 |Associated morphology|  = <<  415582006 |Stenosis| },
				{  363698007 |Finding site|  = <<  53085002 |Right ventricular structure| ,
					 116676008 |Associated morphology|  = <<  56246009 |Hypertrophy| }
		 */

		allConcepts.add(new Concept(PENTALOGY_OF_FALLOT)
				.addRelationship(new Relationship(ISA, DISORDER))
				.addRelationship(new Relationship(FINDING_SITE, PULMONARY_VALVE_STRUCTURE).setGroupId(1))
				.addRelationship(new Relationship(ASSOCIATED_MORPHOLOGY, STENOSIS).setGroupId(1))
				.addRelationship(new Relationship(FINDING_SITE, RIGHT_VENTRICULAR_STRUCTURE).setGroupId(2))
				.addRelationship(new Relationship(ASSOCIATED_MORPHOLOGY, HYPERTROPHY).setGroupId(2))
		);
		allConcepts.add(new Concept(PENTALOGY_OF_FALLOT_INCORRECT_GROUPING)
				.addRelationship(new Relationship(ISA, DISORDER))
				.addRelationship(new Relationship(FINDING_SITE, PULMONARY_VALVE_STRUCTURE).setGroupId(1))
				.addRelationship(new Relationship(ASSOCIATED_MORPHOLOGY, STENOSIS).setGroupId(2))// <-- was group 1
				.addRelationship(new Relationship(FINDING_SITE, RIGHT_VENTRICULAR_STRUCTURE).setGroupId(2))
				.addRelationship(new Relationship(ASSOCIATED_MORPHOLOGY, HYPERTROPHY).setGroupId(1))// <-- was group 2
		);

		allConcepts.add(new Concept(PROCEDURE).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(OPERATION_ON_HEART)
				.addRelationship(new Relationship(ISA, PROCEDURE))
				.addRelationship(new Relationship(PROCEDURE_SITE, HEART_STRUCTURE))
		);
		allConcepts.add(new Concept(CHEST_IMAGING)
				.addRelationship(new Relationship(ISA, PROCEDURE))
				.addRelationship(new Relationship(PROCEDURE_SITE_DIRECT, THORACIC_STRUCTURE))
		);


		conceptService.create(allConcepts, MAIN);

		allConceptIds = allConcepts.stream().map(Concept::getId).collect(Collectors.toSet());

		memberService.createMembers(MAIN, Sets.newHashSet(
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN, CLINICAL_FINDING),
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN, BODY_STRUCTURE)));

		branchCriteria = versionControlHelper.getBranchCriteria(MAIN);
	}

	@Test
	public void selectByDescendantAndAncestorOperators() throws Exception {
		// Self
		assertEquals(
				Sets.newHashSet(SNOMEDCT_ROOT),
				strings(selectConceptIds(SNOMEDCT_ROOT)));

		// Not Self
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
				strings(selectConceptIds("<*")));

		// Ancestor of
		assertEquals(
				Sets.newHashSet(SNOMEDCT_ROOT, CLINICAL_FINDING),
				strings(selectConceptIds(">" + DISORDER)));

		// Ancestor or Self of
		assertEquals(
				Sets.newHashSet(SNOMEDCT_ROOT, CLINICAL_FINDING, DISORDER),
				strings(selectConceptIds(">>" + DISORDER)));
	}

	@Test
	public void selectParents() throws Exception {
		// Direct Parents
		assertEquals(
				Sets.newHashSet(),
				strings(selectConceptIds(">!" + SNOMEDCT_ROOT)));

		assertEquals(
				Sets.newHashSet(SNOMEDCT_ROOT),
				strings(selectConceptIds(">!" + CLINICAL_FINDING)));

		assertEquals(
				Sets.newHashSet(CLINICAL_FINDING),
				strings(selectConceptIds(">!" + BLEEDING)));
	}

	@Test
	public void selectChildren() throws Exception {
		// Direct Children
		assertEquals(
				Sets.newHashSet(MODEL_COMPONENT, RIGHT, BODY_STRUCTURE, CLINICAL_FINDING, PROCEDURE, ISA),
				strings(selectConceptIds("<!" + SNOMEDCT_ROOT)));

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
	public void selectMemberOfReferenceSet() throws Exception {
		// Member of x
		assertEquals(
				Sets.newHashSet(CLINICAL_FINDING, BODY_STRUCTURE),
				strings(selectConceptIds("^" + REFSET_MRCM_ATTRIBUTE_DOMAIN))
		);

		// Member of any reference set
		assertEquals(
				Sets.newHashSet(CLINICAL_FINDING, BODY_STRUCTURE),
				strings(selectConceptIds("^*"))
		);
	}

	@Test
	public void selectByAttributeType() throws Exception {
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
				Sets.newHashSet(OPERATION_ON_HEART, CHEST_IMAGING),
				strings(selectConceptIds("<<" + PROCEDURE + ":<<" + PROCEDURE_SITE + "=*")));

		assertEquals(
				Sets.newHashSet(),
				strings(selectConceptIds(BLEEDING + ":" + NON_EXISTENT_CONCEPT + "=*")));
	}

	@Test
	public void selectByAttributeValue() throws Exception {
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
	public void selectByAttributeConjunction() throws Exception {
		assertEquals(
				Sets.newHashSet(BLEEDING_SKIN),
				strings(selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "=" + HEMORRHAGE+ " , " + FINDING_SITE + "=*")));
	}

	@Test
	public void selectByAttributeDisjunction() throws Exception {
		Collection<Long> ids = selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "=" + HEMORRHAGE + " OR " + FINDING_SITE + "=*");
		assertEquals(
				Sets.newHashSet(BLEEDING_SKIN, BLEEDING, PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(ids));
	}

	@Test
	public void focusConceptConjunction() {
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
	public void focusConceptDisjunction() {
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
	public void focusConceptExclusion() {
		assertEquals(
				Sets.newHashSet(CLINICAL_FINDING, BLEEDING, BLEEDING_SKIN),
				strings(selectConceptIds("<<" + CLINICAL_FINDING + " MINUS <<" + DISORDER)));

		assertEquals(
				Sets.newHashSet(),
				strings(selectConceptIds(HEMORRHAGE + " MINUS " + HEMORRHAGE)));
	}

	@Test
	public void focusConceptConjunctionDisjunction() {
		assertEquals(
				Sets.newHashSet(BLEEDING, BLEEDING_SKIN),
				strings(selectConceptIds("<" + SNOMEDCT_ROOT + " AND (<<" + BLEEDING + " OR " + SNOMEDCT_ROOT +")")));
	}

	@Test
	public void attributeGroups() {
		String eclWithoutGrouping =
				"<404684003 |Clinical finding| :\n" +
						"	363698007 |Finding site| = <<39057004 |Pulmonary valve structure|,\n" +
						"	116676008 |Associated morphology| = <<415582006 |Stenosis|" +
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
	public void attributeGroupCardinality() {
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
	public void attributeGroupDisjunction() {
		assertEquals(
				"Match clinical finding with at least one grouped finding site attributes.",
				Sets.newHashSet(PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),// No bleeding because |Associated morphology| must be grouped
				strings(selectConceptIds("<404684003 |Clinical finding|: { 363698007 |Finding site| = * } OR { 116676008 |Associated morphology| = * }")));
	}

	@Test
	public void reverseFlagAttributes() {
		// Select the Finding sites of descendants of Disorder
		// Using Reverse Flag
		assertEquals(
				Sets.newHashSet(RIGHT_VENTRICULAR_STRUCTURE, PULMONARY_VALVE_STRUCTURE),
				strings(selectConceptIds("*:R " + FINDING_SITE + " = <" + DISORDER)));

		// Using Dot notation
		assertEquals(
				Sets.newHashSet(RIGHT_VENTRICULAR_STRUCTURE, PULMONARY_VALVE_STRUCTURE),
				strings(selectConceptIds("<" + DISORDER + "." + FINDING_SITE)));

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

	// TODO: Add reverse flag with cardinality

	@Test
	public void attributeCardinality() {
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

	}

	private Set<String> strings(Collection<Long> ids) {
		return ids.stream().map(Object::toString).collect(Collectors.toSet());
	}

	private Collection<Long> selectConceptIds(String ecl) {
		return selectConceptIds(ecl, null);
	}

	private Collection<Long> selectConceptIds(String ecl, PageRequest pageRequest) {
		return eclQueryService.selectConceptIds(ecl, branchCriteria, MAIN, STATED, pageRequest).getContent();
	}
}
