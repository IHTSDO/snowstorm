package org.snomed.snowstorm.core.rf2.rf2import;

import com.google.common.collect.Lists;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.pojo.IntegrityIssueReport;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class ImportServiceTest extends AbstractTest {

	@Autowired
	private ImportService importService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	@Autowired
	private QueryService queryService;

	@Autowired
	private IntegrityService integrityService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Before
	public void setup() {
		codeSystemService.init();
		referenceSetMemberService.init();
	}

	@Test
	public void testImportFull() throws ReleaseImportException {
		final String branchPath = "MAIN";
		Assert.assertEquals(1, branchService.findAll().size());

		String importId = importService.createJob(RF2Type.FULL, branchPath);
		importService.importArchive(importId, getClass().getResourceAsStream("/MiniCT_INT_20180731.zip"));

		final List<Branch> branches = branchService.findAll();
		List<String> branchPaths = branches.stream().map(Branch::getPath).collect(Collectors.toList());
		assertEquals(Lists.newArrayList(
				"MAIN",
				"MAIN/2002-01-31",
				"MAIN/2002-07-31",
				"MAIN/2003-01-31",
				"MAIN/2003-07-31",
				"MAIN/2004-01-31",
				"MAIN/2004-07-31",
				"MAIN/2005-01-31",
				"MAIN/2005-07-31",
				"MAIN/2006-01-31",
				"MAIN/2006-07-31",
				"MAIN/2007-01-31",
				"MAIN/2007-07-31",
				"MAIN/2008-01-31",
				"MAIN/2008-07-31",
				"MAIN/2009-01-31",
				"MAIN/2009-07-31",
				"MAIN/2010-01-31",
				"MAIN/2010-07-31",
				"MAIN/2011-01-31",
				"MAIN/2011-07-31",
				"MAIN/2012-01-31",
				"MAIN/2012-07-31",
				"MAIN/2013-01-31",
				"MAIN/2013-07-31",
				"MAIN/2014-01-31",
				"MAIN/2018-07-31"), branchPaths);

		Assert.assertEquals(27, branches.size());
		int a = 0;
		Assert.assertEquals("MAIN", branches.get(a++).getPath());
		Assert.assertEquals("MAIN/2002-01-31", branches.get(a++).getPath());
		Assert.assertEquals("MAIN/2002-07-31", branches.get(a++).getPath());
		Assert.assertEquals("MAIN/2003-01-31", branches.get(a++).getPath());
		Assert.assertEquals("MAIN/2003-07-31", branches.get(a++).getPath());
		Assert.assertEquals("MAIN/2004-01-31", branches.get(a++).getPath());
		Assert.assertEquals("MAIN/2004-07-31", branches.get(a++).getPath());
		Assert.assertEquals("MAIN/2005-01-31", branches.get(a++).getPath());
		Assert.assertEquals("MAIN/2005-07-31", branches.get(a++).getPath());
		Assert.assertEquals("MAIN/2006-01-31", branches.get(a).getPath());

		a = 21;
		Assert.assertEquals("MAIN/2012-01-31", branches.get(a++).getPath());
		Assert.assertEquals("MAIN/2012-07-31", branches.get(a++).getPath());
		Assert.assertEquals("MAIN/2013-01-31", branches.get(a++).getPath());
		Assert.assertEquals("MAIN/2013-07-31", branches.get(a++).getPath());
		Assert.assertEquals("MAIN/2014-01-31", branches.get(a).getPath());

		String path = "MAIN/2002-01-31";
		Assert.assertEquals(88, conceptService.findAll(path, PageRequest.of(0, 10)).getTotalElements());
		assertNull(conceptService.find("370136006", path));

		path = "MAIN/2002-07-31";
		Assert.assertEquals(89, conceptService.findAll(path, PageRequest.of(0, 10)).getTotalElements());
		Assert.assertNotNull(conceptService.find("370136006", path));

		// Test concept's description present and active
		final Concept concept138875005in2002 = conceptService.find("138875005", path);
		Assert.assertNotNull(concept138875005in2002);
		Assert.assertEquals(6, concept138875005in2002.getDescriptions().size());
		final Description description1237157018in2002 = concept138875005in2002.getDescription("1237157018");
		Assert.assertNotNull(description1237157018in2002);
		Assert.assertEquals("SNOMED CT July 2002 Release: 20020731 [R]", description1237157018in2002.getTerm());
		Assert.assertTrue(description1237157018in2002.isActive());
		Assert.assertEquals(1, description1237157018in2002.getAcceptabilityMap().size());
		Assert.assertEquals(Concepts.descriptionAcceptabilityNames.get("900000000000549004"), description1237157018in2002.getAcceptabilityMap().get("900000000000508004"));

		path = "MAIN/2003-01-31";
		Assert.assertEquals(89, conceptService.findAll(path, PageRequest.of(0, 10)).getTotalElements());

		// Test concept's description present and inactive
		final Concept concept138875005in2003 = conceptService.find("138875005", path);
		Assert.assertNotNull(concept138875005in2003);
		Assert.assertEquals(7, concept138875005in2003.getDescriptions().size());
		final Description description1237157018in2003 = concept138875005in2003.getDescription("1237157018");
		Assert.assertNotNull(description1237157018in2003);
		Assert.assertEquals("SNOMED CT July 2002 Release: 20020731 [R]", description1237157018in2003.getTerm());
		Assert.assertFalse(description1237157018in2003.isActive());
		Assert.assertEquals(0, description1237157018in2003.getAcceptabilityMap().size());

		path = "MAIN/2014-01-31";
		Assert.assertEquals(102, conceptService.findAll(path, PageRequest.of(0, 10)).getTotalElements());
		Assert.assertEquals(102, conceptService.findAll("MAIN", PageRequest.of(0, 10)).getTotalElements());

		Assert.assertEquals(asSet("250171008, 138875005, 118222006, 246188002"), queryService.retrieveAncestors("131148009", "MAIN/2002-01-31", false));
		Assert.assertEquals(asSet("250171008, 138875005, 300577008, 118222006, 404684003"), queryService.retrieveAncestors("131148009", "MAIN/2005-01-31", false));
		Assert.assertEquals(asSet("250171008, 138875005, 118222006, 404684003"), queryService.retrieveAncestors("131148009", "MAIN/2006-01-31", false));

		IntegrityIssueReport expectedIssueReport = new IntegrityIssueReport();
		Map<Long, Long> relationshipWithInactiveSource = new HashMap<>();
		relationshipWithInactiveSource.put(108874022L, 116676008L);
		relationshipWithInactiveSource.put(108921029L, 116680003L);
		relationshipWithInactiveSource.put(20244025L, 106237007L);
		relationshipWithInactiveSource.put(127116020L, 118225008L);
		expectedIssueReport.setRelationshipsWithMissingOrInactiveSource(relationshipWithInactiveSource);
		Map<Long, Long> relationshipWithInactiveDest = new HashMap<>();
		relationshipWithInactiveDest.put(108874022L, 106237007L);
		relationshipWithInactiveDest.put(108921029L, 106237007L);
		relationshipWithInactiveDest.put(266232022L, 118225008L);
		expectedIssueReport.setRelationshipsWithMissingOrInactiveDestination(relationshipWithInactiveDest);

		IntegrityIssueReport emptyReport = new IntegrityIssueReport();

		assertConceptsMissingOrInactive("MAIN/2002-07-31", "116680003", "106237007", "116676008");

		IntegrityIssueReport report2002JulyStated = integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest("MAIN/2002-07-31"), true);
		assertNull(report2002JulyStated.getRelationshipsWithMissingOrInactiveSource());
		assertEquals(82, report2002JulyStated.getRelationshipsWithMissingOrInactiveType().size());
		assertNull(report2002JulyStated.getRelationshipsWithMissingOrInactiveDestination());

		IntegrityIssueReport report2002JulyInferred = integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest("MAIN/2002-07-31"), false);
		assertEquals(expectedIssueReport.getRelationshipsWithMissingOrInactiveSource(), report2002JulyInferred.getRelationshipsWithMissingOrInactiveSource());
		assertEquals(106, report2002JulyInferred.getRelationshipsWithMissingOrInactiveType().size());
		assertEquals(expectedIssueReport.getRelationshipsWithMissingOrInactiveDestination(), report2002JulyInferred.getRelationshipsWithMissingOrInactiveDestination());


		assertConceptsMissingOrInactive("MAIN/2010-01-31", "116680003", "106237007", "116676008");

		IntegrityIssueReport report2010JanStated = integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest("MAIN/2010-01-31"), true);
		assertEquals("{3926512020=>116680003, 4279586029=>106237007, 3924382025=>116676008}", report2010JanStated.getRelationshipsWithMissingOrInactiveSource().toString());
		assertEquals(116, report2010JanStated.getRelationshipsWithMissingOrInactiveType().size());
		assertEquals("{3878891026=>106237007}", report2010JanStated.getRelationshipsWithMissingOrInactiveDestination().toString());

		IntegrityIssueReport report2010JanInferred = integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest("MAIN/2010-01-31"), false);
		assertEquals("{20244025=>106237007, 108874022=>116676008, 144474020=>246188002, 2537147023=>116676008, 2764316020=>106237007, " +
				"108921029=>116680003, 2537148029=>116680003, 127116020=>118225008, 2530694025=>106237007}",
				report2010JanInferred.getRelationshipsWithMissingOrInactiveSource().toString());
		assertEquals(127, report2010JanInferred.getRelationshipsWithMissingOrInactiveType().size());

		assertConceptsActive("MAIN/2011-01-31", "116680003", "106237007", "116676008");
		assertConceptsMissingOrInactive("MAIN/2011-01-31", "246188002", "118225008");

		assertEquals(emptyReport, integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest("MAIN/2011-01-31"), true));
		IntegrityIssueReport report2011JanInferred = integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest("MAIN/2011-01-31"), false);
		assertEquals("{144474020=>246188002, 127116020=>118225008}", report2011JanInferred.getRelationshipsWithMissingOrInactiveSource().toString());
		assertNull(report2011JanInferred.getRelationshipsWithMissingOrInactiveType());
		assertEquals("{69485025=>246188002, 266232022=>118225008}", report2011JanInferred.getRelationshipsWithMissingOrInactiveDestination().toString());
	}

	private void assertConceptsMissingOrInactive(String path, String... ids) {
		for (String id : ids) {
			Concept notActiveConcept = conceptService.find(id, path);
			assertTrue("Concept " + id + " should be missing or inactive.", notActiveConcept == null || !notActiveConcept.isActive());
		}
	}

	private void assertConceptsActive(String path, String... ids) {
		for (String id : ids) {
			Concept activeConcept = conceptService.find(id, path);
			assertTrue("Concept " + id + " should be active.", activeConcept.isActive());
		}
	}

	@Test
	public void testImportSnapshot() throws ReleaseImportException {
		final String branchPath = "MAIN";

		assertNotNull(codeSystemService.find(CodeSystemService.SNOMEDCT));
		assertTrue(codeSystemService.findAllVersions(CodeSystemService.SNOMEDCT).isEmpty());

		String importId = importService.createJob(RF2Type.SNAPSHOT, branchPath);
		importService.importArchive(importId, getClass().getResourceAsStream("/MiniCT_INT_20180731.zip"));


		final Concept conceptBleeding = conceptService.find("131148009", branchPath);
		Assert.assertTrue(conceptBleeding.isReleased());
		Assert.assertEquals("20050131", conceptBleeding.getEffectiveTime());
		Assert.assertEquals("true|900000000000207008|900000000000073002", conceptBleeding.getReleaseHash());

		final Set<Description> descriptions = conceptBleeding.getDescriptions();
		Assert.assertEquals(2, descriptions.size());
		Description description = null;
		for (Description d : descriptions) {
			if (d.getDescriptionId().equals("210860014")) {
				description = d;
			}
		}
		Assert.assertNotNull(description);
		Assert.assertEquals("Bleeding", description.getTerm());
		final Map<String, ReferenceSetMember> members = description.getLangRefsetMembers();
		Assert.assertEquals(1, members.size());
		Assert.assertEquals("900000000000548007", members.get("900000000000508004").getAdditionalField("acceptabilityId"));

		ReferenceSetMember member = referenceSetMemberService.findMember(branchPath, "8164a2fc-cac3-4b54-9d9e-f9c597a115ea");
		assertNotNull(member);
		assertEquals("TransitiveObjectProperty(:123005000)", member.getAdditionalField("owlExpression"));

		Assert.assertEquals(7, conceptBleeding.getRelationships().size());
		Assert.assertEquals(4, conceptBleeding.getRelationships().stream().filter(r -> r.getCharacteristicTypeId().equals(Concepts.INFERRED_RELATIONSHIP)).count());
		Assert.assertEquals(3, conceptBleeding.getRelationships().stream().filter(r -> r.getCharacteristicTypeId().equals(Concepts.STATED_RELATIONSHIP)).count());

		final Page<Concept> conceptPage = conceptService.findAll(branchPath, PageRequest.of(0, 200));
		Assert.assertEquals(102, conceptPage.getNumberOfElements());
		final List<Concept> concepts = conceptPage.getContent();
		Concept conceptMechanicalAbnormality = null;
		for (Concept concept : concepts) {
			if (concept.getConceptId().equals("131148009")) {
				conceptMechanicalAbnormality = concept;
			}
		}

		Assert.assertNotNull(conceptMechanicalAbnormality);

		Assert.assertEquals(2, conceptMechanicalAbnormality.getDescriptions().size());
		Assert.assertEquals(7, conceptMechanicalAbnormality.getRelationships().size());

		// Test inactivation refset loading
		final Concept inactiveConcept = conceptService.find("118225008", branchPath);
		Assert.assertFalse(inactiveConcept.isActive());
		final ReferenceSetMember inactivationIndicator = inactiveConcept.getInactivationIndicatorMember();
		Assert.assertNotNull("Inactivation indicator should not be null", inactivationIndicator);
		Assert.assertEquals("900000000000484002", inactivationIndicator.getAdditionalField("valueId"));
		Assert.assertEquals("AMBIGUOUS", inactiveConcept.getInactivationIndicator());

		final Map<String, Set<String>> associationTargets = inactiveConcept.getAssociationTargets();
		Assert.assertNotNull(associationTargets);
		Assert.assertEquals(1, associationTargets.size());
		final Set<String> possibly_equivalent_to = associationTargets.get("POSSIBLY_EQUIVALENT_TO");
		Assert.assertEquals(3, possibly_equivalent_to.size());
		Assert.assertTrue(possibly_equivalent_to.contains("118222006"));
		Assert.assertTrue(possibly_equivalent_to.contains("413350009"));
		Assert.assertTrue(possibly_equivalent_to.contains("250171008"));

		final Description inactiveDescription = inactiveConcept.getDescription("697843019");
		Assert.assertEquals("CONCEPT_NON_CURRENT", inactiveDescription.getInactivationIndicator());

		List<CodeSystemVersion> allVersions = codeSystemService.findAllVersions(CodeSystemService.SNOMEDCT);
		assertEquals(1, allVersions.size());
		CodeSystemVersion codeSystemVersion = allVersions.get(0);
		assertEquals("SNOMEDCT", codeSystemVersion.getShortName());
		assertEquals("20180731", codeSystemVersion.getEffectiveDate());
		assertEquals("2018-07-31", codeSystemVersion.getVersion());
		assertEquals("MAIN", codeSystemVersion.getParentBranchPath());
	}

	@Test
	public void testImportSnapshotOnlyModelModule() throws ReleaseImportException {
		final String branchPath = "MAIN";

		assertNotNull(codeSystemService.find(CodeSystemService.SNOMEDCT));
		assertTrue(codeSystemService.findAllVersions(CodeSystemService.SNOMEDCT).isEmpty());

		RF2ImportConfiguration importConfiguration = new RF2ImportConfiguration(RF2Type.SNAPSHOT, branchPath);
		importConfiguration.setModuleIds(Collections.singleton(Concepts.MODEL_MODULE));
		String importId = importService.createJob(importConfiguration);
		importService.importArchive(importId, getClass().getResourceAsStream("/MiniCT_INT_20180731.zip"));

		final Page<Concept> conceptPage = conceptService.findAll(branchPath, PageRequest.of(0, 200));
		Assert.assertEquals(77, conceptPage.getNumberOfElements());

		IntegrityIssueReport emptyReport = new IntegrityIssueReport();
		assertEquals("Branch " + branchPath + " should contain no invalid stated relationships.",
				emptyReport, integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest(branchPath), true));
		assertEquals("Branch " + branchPath + " should contain no invalid inferred relationships.",
				emptyReport, integrityService.findAllComponentsWithBadIntegrity(branchService.findLatest(branchPath), false));
	}

	private Set<Long> asSet(String string) {
		Set<Long> longs = new HashSet<>();
		for (String split : string.split(",")) {
			longs.add(Long.parseLong(split.trim()));
		}
		return longs;
	}

}
