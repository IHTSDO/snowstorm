package org.ihtsdo.elasticsnomed.ecl;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.AbstractTest;
import org.ihtsdo.elasticsnomed.TestConfig;
import org.ihtsdo.elasticsnomed.core.data.domain.Concept;
import org.ihtsdo.elasticsnomed.core.data.domain.Concepts;
import org.ihtsdo.elasticsnomed.core.data.domain.Relationship;
import org.ihtsdo.elasticsnomed.core.data.services.ConceptService;
import org.ihtsdo.elasticsnomed.core.data.services.QueryService;
import org.ihtsdo.elasticsnomed.core.data.services.ServiceException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.ihtsdo.elasticsnomed.core.data.domain.Concepts.CLINICAL_FINDING;
import static org.ihtsdo.elasticsnomed.core.data.domain.Concepts.ISA;
import static org.ihtsdo.elasticsnomed.core.data.domain.Concepts.SNOMEDCT_ROOT;
import static org.junit.Assert.assertEquals;

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

		allConcepts.add(new Concept(BODY_STRUCTURE).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(HEART_STRUCTURE).addRelationship(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(SKIN_STRUCTURE).addRelationship(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(THORACIC_STRUCTURE).addRelationship(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(PULMONARY_VALVE_STRUCTURE).addRelationship(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(RIGHT_VENTRICULAR_STRUCTURE).addRelationship(new Relationship(ISA, BODY_STRUCTURE)));
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

		branchCriteria = versionControlHelper.getBranchCriteria(MAIN);
	}

	@Test
	public void selectByDescendantAndAncestorOperators() throws Exception {
		assertEquals(
				Sets.newHashSet(SNOMEDCT_ROOT),
				strings(eclQueryService.selectConceptIds(SNOMEDCT_ROOT, branchCriteria, MAIN, STATED)));

		assertEquals(
				// All concepts but not root
				allConceptIds.stream().filter(id -> !SNOMEDCT_ROOT.equals(id)).collect(Collectors.toSet()),
				strings(eclQueryService.selectConceptIds("<" + SNOMEDCT_ROOT, branchCriteria, MAIN, STATED)));

		assertEquals(
				allConceptIds,
				strings(eclQueryService.selectConceptIds("<<" + SNOMEDCT_ROOT, branchCriteria, MAIN, STATED)));

		assertEquals(
				allConceptIds,
				strings(eclQueryService.selectConceptIds("<<*", branchCriteria, MAIN, STATED)));

		assertEquals(
				Sets.newHashSet(SNOMEDCT_ROOT, CLINICAL_FINDING),
				strings(eclQueryService.selectConceptIds(">" + DISORDER, branchCriteria, MAIN, STATED)));

		assertEquals(
				Sets.newHashSet(SNOMEDCT_ROOT, CLINICAL_FINDING, DISORDER),
				strings(eclQueryService.selectConceptIds(">>" + DISORDER, branchCriteria, MAIN, STATED)));
	}

	@Test
	public void selectParents() throws Exception {
		assertEquals(
				Sets.newHashSet(),
				strings(eclQueryService.selectConceptIds(">!" + SNOMEDCT_ROOT, branchCriteria, MAIN, STATED)));

		assertEquals(
				Sets.newHashSet(SNOMEDCT_ROOT),
				strings(eclQueryService.selectConceptIds(">!" + CLINICAL_FINDING, branchCriteria, MAIN, STATED)));

		assertEquals(
				Sets.newHashSet(CLINICAL_FINDING),
				strings(eclQueryService.selectConceptIds(">!" + BLEEDING, branchCriteria, MAIN, STATED)));
	}

	@Test
	public void selectChildren() throws Exception {
		assertEquals(
				Sets.newHashSet(MODEL_COMPONENT, BODY_STRUCTURE, CLINICAL_FINDING, PROCEDURE, ISA),
				strings(eclQueryService.selectConceptIds("<!" + SNOMEDCT_ROOT, branchCriteria, MAIN, STATED)));

		assertEquals(
				Sets.newHashSet(BLEEDING, DISORDER),
				strings(eclQueryService.selectConceptIds("<!" + CLINICAL_FINDING, branchCriteria, MAIN, STATED)));

		assertEquals(
				Sets.newHashSet(BLEEDING_SKIN),
				strings(eclQueryService.selectConceptIds("<!" + BLEEDING, branchCriteria, MAIN, STATED)));

		assertEquals(
				Sets.newHashSet(),
				strings(eclQueryService.selectConceptIds("<!" + BLEEDING_SKIN, branchCriteria, MAIN, STATED)));
	}

	@Test
	public void selectByAttributeType() throws Exception {
		assertEquals(
				Sets.newHashSet(),
				strings(eclQueryService.selectConceptIds("*:" + CLINICAL_FINDING + "=*", branchCriteria, MAIN, STATED)));

		assertEquals(
				Sets.newHashSet(BLEEDING, BLEEDING_SKIN, PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(eclQueryService.selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "=*", branchCriteria, MAIN, STATED)));

		assertEquals(
				Sets.newHashSet(BLEEDING),
				strings(eclQueryService.selectConceptIds(BLEEDING +":" + ASSOCIATED_MORPHOLOGY + "=*", branchCriteria, MAIN, STATED)));

		assertEquals(
				Sets.newHashSet(CHEST_IMAGING),
				strings(eclQueryService.selectConceptIds("<<" + PROCEDURE +":<" + PROCEDURE_SITE + "=*", branchCriteria, MAIN, STATED)));

		assertEquals(
				Sets.newHashSet(OPERATION_ON_HEART, CHEST_IMAGING),
				strings(eclQueryService.selectConceptIds("<<" + PROCEDURE +":<<" + PROCEDURE_SITE + "=*", branchCriteria, MAIN, STATED)));

		assertEquals(
				Sets.newHashSet(),
				strings(eclQueryService.selectConceptIds(BLEEDING +":" + NON_EXISTENT_CONCEPT + "=*", branchCriteria, MAIN, STATED)));
	}

	@Test
	public void selectByAttributeValue() throws Exception {
		assertEquals(
				Sets.newHashSet(BLEEDING, BLEEDING_SKIN),
				strings(eclQueryService.selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "=" + HEMORRHAGE, branchCriteria, MAIN, STATED)));

		assertEquals(
				Sets.newHashSet(PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(eclQueryService.selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "!=" + HEMORRHAGE, branchCriteria, MAIN, STATED)));

		assertEquals(
				Sets.newHashSet(BLEEDING, BLEEDING_SKIN, PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(eclQueryService.selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "=<<" + SNOMEDCT_ROOT, branchCriteria, MAIN, STATED)));

		assertEquals(
				Sets.newHashSet(BLEEDING_SKIN, PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(eclQueryService.selectConceptIds("*:" + FINDING_SITE + "=<<" + BODY_STRUCTURE, branchCriteria, MAIN, STATED)));

		assertEquals(
				Sets.newHashSet(),
				strings(eclQueryService.selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "=" + CLINICAL_FINDING, branchCriteria, MAIN, STATED)));

		assertEquals(
				Sets.newHashSet(),
				strings(eclQueryService.selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "=" + NON_EXISTENT_CONCEPT, branchCriteria, MAIN, STATED)));
	}

	@Test
	public void selectByAttributeConjunction() throws Exception {
		assertEquals(
				Sets.newHashSet(BLEEDING_SKIN),
				strings(eclQueryService.selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "=" + HEMORRHAGE+ " , " + FINDING_SITE + "=*", branchCriteria, MAIN, STATED)));
	}

	@Test
	public void selectByAttributeDisjunction() throws Exception {
		Collection<Long> ids = eclQueryService.selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "=" + HEMORRHAGE + " OR " + FINDING_SITE + "=*", branchCriteria, MAIN, STATED);
		assertEquals(
				Sets.newHashSet(BLEEDING_SKIN, BLEEDING, PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(ids));
	}

	@Test
	public void focusConceptConjunction() {
		assertEquals(
				Sets.newHashSet(DISORDER, BLEEDING, BLEEDING_SKIN, PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(eclQueryService.selectConceptIds("<" + SNOMEDCT_ROOT + " AND <" + CLINICAL_FINDING, branchCriteria, MAIN, STATED)));

		assertEquals(
				Sets.newHashSet(CLINICAL_FINDING),
				strings(eclQueryService.selectConceptIds("<" + SNOMEDCT_ROOT + " AND " + CLINICAL_FINDING, branchCriteria, MAIN, STATED)));

		assertEquals(
				Sets.newHashSet(PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(eclQueryService.selectConceptIds("<" + DISORDER + " AND <" + CLINICAL_FINDING, branchCriteria, MAIN, STATED)));
	}

	@Test
	public void focusConceptDisjunction() {
		assertEquals(
				Sets.newHashSet(SNOMEDCT_ROOT, CLINICAL_FINDING),
				strings(eclQueryService.selectConceptIds(SNOMEDCT_ROOT + " OR " + CLINICAL_FINDING, branchCriteria, MAIN, STATED)));

		assertEquals(
				Sets.newHashSet(HEMORRHAGE, CLINICAL_FINDING, DISORDER, BLEEDING, BLEEDING_SKIN, PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),
				strings(eclQueryService.selectConceptIds(HEMORRHAGE + " OR <<" + CLINICAL_FINDING, branchCriteria, MAIN, STATED)));

		assertEquals(
				Sets.newHashSet(HEMORRHAGE),
				strings(eclQueryService.selectConceptIds(HEMORRHAGE + " OR " + HEMORRHAGE, branchCriteria, MAIN, STATED)));
	}

	@Test
	public void focusConceptConjunctionDisjunction() {
		assertEquals(
				Sets.newHashSet(BLEEDING, BLEEDING_SKIN),
				strings(eclQueryService.selectConceptIds("<" + SNOMEDCT_ROOT + " AND (<<" + BLEEDING + " OR " + SNOMEDCT_ROOT +")", branchCriteria, MAIN, STATED)));
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
				strings(eclQueryService.selectConceptIds(eclWithoutGrouping, branchCriteria, MAIN, STATED)));

		assertEquals(
				"ECL without grouping finds only the concept with matching groups",
				Sets.newHashSet(PENTALOGY_OF_FALLOT),
				strings(eclQueryService.selectConceptIds(eclWithGrouping, branchCriteria, MAIN, STATED)));
	}

	private Set<String> strings(Collection<Long> ids) {
		return ids.stream().map(Object::toString).collect(Collectors.toSet());
	}

}
