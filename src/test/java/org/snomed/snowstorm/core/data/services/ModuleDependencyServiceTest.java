package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.ComponentService;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.rest.CodeSystemController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

class ModuleDependencyServiceTest extends AbstractTest {
	@Autowired
	private ModuleDependencyService mdService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private CodeSystemController codeSystemController;

	@Autowired
	private CodeSystemUpgradeService codeSystemUpgradeService;

	@Test
	void clearSourceAndTargetEffectiveTimes_ShouldReturnExpected_WhenGivenNull() {
		// when
		boolean success = mdService.clearSourceAndTargetEffectiveTimes(null);

		// then
		assertFalse(success);
	}

	@Test
	void clearSourceAndTargetEffectiveTimes_ShouldReturnExpected_WhenGivenUnknownBranch() {
		// when
		boolean success = mdService.clearSourceAndTargetEffectiveTimes("MAIN/i-dont-exist");

		// then
		assertFalse(success);
	}

	@Test
	void clearSourceAndTargetEffectiveTimes_ShouldReturnExpected_WhenBranchHasNoMetadata() {
		// given
		branchService.create("MAIN/projectA");

		// before
		Page<ReferenceSetMember> page = referenceSetMemberService.findMembers("MAIN/projectA", new MemberSearchRequest().referenceSet(MODULE_DEPENDENCY_REFERENCE_SET), ComponentService.LARGE_PAGE);
		assertTrue(page.isEmpty());

		// when
		boolean success = mdService.clearSourceAndTargetEffectiveTimes("MAIN/projectA");
		assertFalse(success);

		// after
		page = referenceSetMemberService.findMembers("MAIN/projectA", new MemberSearchRequest().referenceSet(MODULE_DEPENDENCY_REFERENCE_SET), ComponentService.LARGE_PAGE);
		assertTrue(page.isEmpty());
	}

	@Test
	void clearSourceAndTargetEffectiveTimes_ShouldDoExpected_WhenGivenInternational() throws ServiceException {
		String intMain = "MAIN";
		Map<String, String> intPreferred = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED));
		String ci = "CASE_INSENSITIVE";
		Concept concept;
		CodeSystem codeSystem;

		// Create International
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
		branchService.updateMetadata(intMain, Map.of(Config.DEFAULT_MODULE_ID_KEY, CORE_MODULE));

		// Create module dependency entry manually
		ReferenceSetMember mdrs = new ReferenceSetMember();
		mdrs.setModuleId(CORE_MODULE);
		mdrs.setReferencedComponentId(CORE_MODULE);
		mdrs.setActive(true);
		mdrs.setRefsetId(Concepts.MODULE_DEPENDENCY_REFERENCE_SET);
		mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME, "");
		mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME, "");
		String mdrsId = referenceSetMemberService.createMember(intMain, mdrs).getMemberId();

		// Create top level concept
		concept = new Concept()
				.addDescription(new Description("Medicine (medicine)").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addDescription(new Description("Medicine").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		String medicineId = concept.getConceptId();

		// Version CodeSystem
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20250101, "20250101");
		codeSystemController.updateVersion("SNOMEDCT", 20250101, "20250101.zip");

		// Assert Concept post versioning
		concept = conceptService.find(medicineId, intMain);
		assertTrue(concept.isReleased());
		assertEquals(20250101, concept.getEffectiveTimeI());
		assertEquals(20250101, concept.getReleasedEffectiveTime());

		// Assert MDRS post versioning
		mdrs = referenceSetMemberService.findMember(intMain, mdrsId);
		assertTrue(mdrs.isReleased());
		assertEquals(20250101, mdrs.getEffectiveTimeI());
		assertEquals(20250101, mdrs.getReleasedEffectiveTime());
		assertEquals("20250101", mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME));
		assertEquals("20250101", mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME));

		// Start new authoring cycle
		boolean clearSourceAndTargetEffectiveTimes = mdService.clearSourceAndTargetEffectiveTimes(intMain);
		assertTrue(clearSourceAndTargetEffectiveTimes);

		// Assert MDRS post new cycle
		mdrs = referenceSetMemberService.findMember(intMain, mdrsId);
		assertTrue(mdrs.isReleased());
		assertNull(mdrs.getEffectiveTimeI());
		assertEquals(20250101, mdrs.getReleasedEffectiveTime());
		assertNull(mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME));
		assertNull(mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME));

		// Create another top level concept
		concept = new Concept()
				.addDescription(new Description("Vehicle (vehicle)").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addDescription(new Description("Vehicle").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		String vehicleId = concept.getConceptId();

		// Version CodeSystem
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20250201, "20250201");
		codeSystemController.updateVersion("SNOMEDCT", 20250201, "20250201.zip");

		// Assert Concept post versioning
		concept = conceptService.find(vehicleId, intMain);
		assertTrue(concept.isReleased());
		assertEquals(20250201, concept.getEffectiveTimeI());
		assertEquals(20250201, concept.getReleasedEffectiveTime());

		// Assert MDRS post versioning
		mdrs = referenceSetMemberService.findMember(intMain, mdrsId);
		assertTrue(mdrs.isReleased());
		assertEquals(20250201, mdrs.getEffectiveTimeI());
		assertEquals(20250201, mdrs.getReleasedEffectiveTime());
		assertEquals("20250201", mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME));
		assertEquals("20250201", mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME));
	}

	@Test
	void clearSourceAndTargetEffectiveTimes_ShouldDoExpected_WhenGivenExtension() throws ServiceException {
		String intMain = "MAIN";
		String extMain = "MAIN/SNOMEDCT-XX";
		Map<String, String> intPreferred = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED));
		String ci = "CASE_INSENSITIVE";
		Concept concept;
		CodeSystem codeSystem;

		// Create International
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));

		// International authors
		ReferenceSetMember mdrs = new ReferenceSetMember();
		mdrs.setModuleId(CORE_MODULE);
		mdrs.setReferencedComponentId(CORE_MODULE);
		mdrs.setActive(true);
		mdrs.setRefsetId(Concepts.MODULE_DEPENDENCY_REFERENCE_SET);
		mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME, "");
		mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME, "");

		concept = new Concept()
				.addDescription(new Description("Medicine (medicine)").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addDescription(new Description("Medicine").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);

		// International versions
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20250101, "20250101");
		codeSystemController.updateVersion("SNOMEDCT", 20250101, "20250101.zip");

		// Extension created
		codeSystem = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-XX", extMain));
		concept = conceptService.create(
				new Concept()
						.addDescription(new Description("Extension module (module)").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
						.addDescription(new Description("Extension module").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
						.addAxiom(new Relationship(ISA, MODULE)),
				extMain
		);
		String extModuleId = concept.getConceptId();
		branchService.updateMetadata(
				extMain,
				Map.of(Config.DEFAULT_MODULE_ID_KEY, extModuleId,
						Config.EXPECTED_EXTENSION_MODULES, List.of(extModuleId),
						Config.DEPENDENCY_PACKAGE, "20250101.zip"
				)
		);

		// Extension authors
		ReferenceSetMember mdrsExtension = new ReferenceSetMember();
		mdrsExtension.setModuleId(extModuleId);
		mdrsExtension.setReferencedComponentId(CORE_MODULE);
		mdrsExtension.setActive(true);
		mdrsExtension.setRefsetId(Concepts.MODULE_DEPENDENCY_REFERENCE_SET);
		mdrsExtension.setAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME, "");
		mdrsExtension.setAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME, "20250101");
		String mdrsExtensionId = referenceSetMemberService.createMember(extMain, mdrsExtension).getMemberId();

		concept = new Concept()
				.addDescription(new Description("Vehicle (vehicle)").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addDescription(new Description("Vehicle").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, extMain);
		String vehicleId = concept.getConceptId();

		// Extension versions
		codeSystem = codeSystemService.find("SNOMEDCT-XX");
		codeSystemService.createVersion(codeSystem, 20250102, "20250102");
		codeSystemController.updateVersion("SNOMEDCT-XX", 20250102, "20250102.zip");

		// Assert Extension's MDRS before new authoring cycle
		mdrs = referenceSetMemberService.findMember(extMain, mdrsExtensionId);
		assertTrue(mdrs.isReleased());
		assertEquals(20250102, mdrs.getEffectiveTimeI());
		assertEquals(20250102, mdrs.getReleasedEffectiveTime());
		assertEquals("20250101", mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME));
		assertEquals("20250102", mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME));

		// Start new authoring cycle
		boolean clearSourceAndTargetEffectiveTimes = mdService.clearSourceAndTargetEffectiveTimes(extMain);
		assertTrue(clearSourceAndTargetEffectiveTimes);

		// Assert Extension's MDRS after new authoring cycle
		mdrs = referenceSetMemberService.findMember(extMain, mdrsExtensionId);
		assertTrue(mdrs.isReleased());
		assertEquals(20250102, mdrs.getReleasedEffectiveTime());
		assertNull(mdrs.getEffectiveTimeI());
		assertNull(mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME));
		assertEquals("20250101", mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME));
	}

	@Test
	void setSourceAndTargetEffectiveTimes_ShouldReturnExpected_WhenGivenInvalidParameters() {
		// given
		List<Object[]> testCases = new ArrayList<>();
		testCases.add(new Object[]{null, 20250101});
		testCases.add(new Object[]{"MAIN", null});
		testCases.add(new Object[]{"unknown", 20250101});

		for (Object[] testCase : testCases) {
			Object first = testCase[0];
			Object second = testCase[1];

			// when
			boolean success = mdService.setSourceAndTargetEffectiveTimes(
					first == null ? null : String.valueOf(first),
					second == null ? null : Integer.parseInt(String.valueOf(second))
			);

			// then
			assertFalse(success);
		}
	}

	@Test
	void setSourceAndTargetEffectiveTimes_ShouldReturnExpected_WhenGivenBranchNotFoundForCodeSystem() {
		// given
		branchService.deleteAll();

		// when
		boolean success = mdService.setSourceAndTargetEffectiveTimes("MAIN", 20250101);

		// then
		assertFalse(success);
	}

	@Test
	void setSourceAndTargetEffectiveTimes_ShouldReturnExpected_WhenNoMDRS() {
		// when
		boolean success = mdService.setSourceAndTargetEffectiveTimes("MAIN", 20250101);

		// then
		assertFalse(success);
	}

	@Test
	void setSourceAndTargetEffectiveTimes_ShouldUpdateMDRS_WhenGivenInternational() throws ServiceException {
		String intMain = "MAIN";
		Map<String, String> intPreferred = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED));
		String ci = "CASE_INSENSITIVE";
		Concept concept;
		CodeSystem codeSystem;

		// Create International
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
		branchService.updateMetadata(intMain, Map.of(Config.DEFAULT_MODULE_ID_KEY, CORE_MODULE));

		// Create module dependency entry manually
		ReferenceSetMember mdrs = new ReferenceSetMember();
		mdrs.setModuleId(CORE_MODULE);
		mdrs.setReferencedComponentId(CORE_MODULE);
		mdrs.setActive(true);
		mdrs.setRefsetId(Concepts.MODULE_DEPENDENCY_REFERENCE_SET);
		mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME, "");
		mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME, "");
		String mdrsId = referenceSetMemberService.createMember(intMain, mdrs).getMemberId();

		// Create top level concept
		concept = new Concept()
				.addDescription(new Description("Medicine (medicine)").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addDescription(new Description("Medicine").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		String medicineId = concept.getConceptId();

		// Version CodeSystem
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20250101, "20250101");
		codeSystemController.updateVersion("SNOMEDCT", 20250101, "20250101.zip");

		// Assert Concept post versioning
		concept = conceptService.find(medicineId, intMain);
		assertTrue(concept.isReleased());
		assertEquals(20250101, concept.getEffectiveTimeI());
		assertEquals(20250101, concept.getReleasedEffectiveTime());

		// Assert MDRS post versioning
		mdrs = referenceSetMemberService.findMember(intMain, mdrsId);
		assertTrue(mdrs.isReleased());
		assertEquals(20250101, mdrs.getEffectiveTimeI());
		assertEquals(20250101, mdrs.getReleasedEffectiveTime());
		assertEquals("20250101", mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME));
		assertEquals("20250101", mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME));
	}

	@Test
	void setSourceAndTargetEffectiveTimes_ShouldUpdateMDRS_WhenGivenExtension() throws ServiceException {
		String intMain = "MAIN";
		String extMain = "MAIN/SNOMEDCT-XX";
		Map<String, String> intPreferred = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED));
		String ci = "CASE_INSENSITIVE";
		Concept concept;
		CodeSystem codeSystem;

		// Create International
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
		branchService.updateMetadata(intMain, Map.of(Config.DEFAULT_MODULE_ID_KEY, CORE_MODULE));

		// Create module dependency entry manually
		ReferenceSetMember mdrs = new ReferenceSetMember();
		mdrs.setModuleId(CORE_MODULE);
		mdrs.setReferencedComponentId(CORE_MODULE);
		mdrs.setActive(true);
		mdrs.setRefsetId(Concepts.MODULE_DEPENDENCY_REFERENCE_SET);
		mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME, "");
		mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME, "");
		referenceSetMemberService.createMember(intMain, mdrs).getMemberId();

		// Create top level concept
		concept = new Concept()
				.addDescription(new Description("Medicine (medicine)").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addDescription(new Description("Medicine").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);
		concept.getConceptId();

		// Version CodeSystem
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20250101, "20250101");
		codeSystemController.updateVersion("SNOMEDCT", 20250101, "20250101.zip");

		// Extension created
		codeSystem = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-XX", extMain));
		concept = conceptService.create(
				new Concept()
						.addDescription(new Description("Extension module (module)").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
						.addDescription(new Description("Extension module").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
						.addAxiom(new Relationship(ISA, MODULE)),
				extMain
		);
		String extModuleId = concept.getConceptId();
		branchService.updateMetadata(
				extMain,
				Map.of(Config.DEFAULT_MODULE_ID_KEY, extModuleId,
						Config.EXPECTED_EXTENSION_MODULES, List.of(extModuleId),
						Config.DEPENDENCY_PACKAGE, "20250101.zip"
				)
		);

		// Extension authors
		ReferenceSetMember mdrsExtension = new ReferenceSetMember();
		mdrsExtension.setModuleId(extModuleId);
		mdrsExtension.setReferencedComponentId(CORE_MODULE);
		mdrsExtension.setActive(true);
		mdrsExtension.setRefsetId(Concepts.MODULE_DEPENDENCY_REFERENCE_SET);
		mdrsExtension.setAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME, "");
		mdrsExtension.setAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME, "20250101");
		String mdrsExtensionId = referenceSetMemberService.createMember(extMain, mdrsExtension).getMemberId();

		concept = new Concept()
				.addDescription(new Description("Vehicle (vehicle)").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addDescription(new Description("Vehicle").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, extMain);

		// Extension versions
		codeSystem = codeSystemService.find("SNOMEDCT-XX");
		codeSystemService.createVersion(codeSystem, 20250102, "20250102");
		codeSystemController.updateVersion("SNOMEDCT-XX", 20250102, "20250102.zip");

		// Assert MDRS post versioning
		mdrs = referenceSetMemberService.findMember(extMain, mdrsExtensionId);
		assertTrue(mdrs.isReleased());
		assertEquals(20250102, mdrs.getEffectiveTimeI());
		assertEquals(20250102, mdrs.getReleasedEffectiveTime());
		assertEquals("20250102", mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME));
		assertEquals("20250101", mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME));
	}

	@Test
	void setTargetEffectiveTime_ShouldReturnExpected_WhenGivenNullPath() {
		// when
		boolean success = mdService.setTargetEffectiveTime(null, 20250101);

		// then
		assertFalse(success);
	}

	@Test
	void setTargetEffectiveTime_ShouldReturnExpected_WhenGivenNullEffectiveTime() {
		// when
		boolean success = mdService.setTargetEffectiveTime("MAIN", null);

		// then
		assertFalse(success);
	}

	@Test
	void setTargetEffectiveTime_ShouldReturnExpected_WhenGivenRoot() {
		// when
		boolean success = mdService.setTargetEffectiveTime("MAIN", 20250101);

		// then
		assertFalse(success);
	}

	@Test
	void setTargetEffectiveTime_ShouldReturnExpected_WhenNoMDRS() throws ServiceException {
		// given
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-XX", "MAIN/SNOMEDCT-XX"));
		Concept concept = conceptService.create(
				new Concept()
						.addDescription(new Description("Extension module (module)").setTypeId(FSN))
						.addDescription(new Description("Extension module").setTypeId(SYNONYM))
						.addAxiom(new Relationship(ISA, MODULE)),
				"MAIN/SNOMEDCT-XX"
		);
		String extModuleId = concept.getConceptId();
		branchService.updateMetadata(
				"MAIN/SNOMEDCT-XX",
				Map.of(Config.DEFAULT_MODULE_ID_KEY, extModuleId,
						Config.EXPECTED_EXTENSION_MODULES, List.of(extModuleId),
						Config.DEPENDENCY_PACKAGE, "20250101.zip"
				)
		);

		// when
		boolean success = mdService.setTargetEffectiveTime("MAIN/SNOMEDCT-XX", 20250101);

		// then
		assertFalse(success);
	}

	@Test
	void setTargetEffectiveTime_ShouldNotUpdateMDRS_WhenGivenInternational() throws ServiceException {
		String intMain = "MAIN";
		Map<String, String> intPreferred = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED));
		String ci = "CASE_INSENSITIVE";
		Concept concept;
		CodeSystem codeSystem;

		// Create International
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
		branchService.updateMetadata(intMain, Map.of(Config.DEFAULT_MODULE_ID_KEY, CORE_MODULE));

		// Create module dependency entry manually
		ReferenceSetMember mdrs = new ReferenceSetMember();
		mdrs.setModuleId(CORE_MODULE);
		mdrs.setReferencedComponentId(CORE_MODULE);
		mdrs.setActive(true);
		mdrs.setRefsetId(Concepts.MODULE_DEPENDENCY_REFERENCE_SET);
		mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME, "");
		mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME, "");
		String mdrsId = referenceSetMemberService.createMember(intMain, mdrs).getMemberId();

		// Create top level concept
		concept = new Concept()
				.addDescription(new Description("Medicine (medicine)").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addDescription(new Description("Medicine").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);

		// Version CodeSystem
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20250101, "20250101");
		codeSystemController.updateVersion("SNOMEDCT", 20250101, "20250101.zip");

		// Assert MDRS post versioning
		mdrs = referenceSetMemberService.findMember(intMain, mdrsId);
		assertTrue(mdrs.isReleased());
		assertEquals(20250101, mdrs.getEffectiveTimeI());
		assertEquals(20250101, mdrs.getReleasedEffectiveTime());
		assertEquals("20250101", mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME));
		assertEquals("20250101", mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME));

		// Upgrade CodeSystem
		boolean success = mdService.setTargetEffectiveTime("MAIN", 20250201);
		assertFalse(success);

		// Assert MDRS post upgrade (nothing has changed)
		mdrs = referenceSetMemberService.findMember(intMain, mdrsId);
		assertTrue(mdrs.isReleased());
		assertEquals(20250101, mdrs.getEffectiveTimeI());
		assertEquals(20250101, mdrs.getReleasedEffectiveTime());
		assertEquals("20250101", mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME));
		assertEquals("20250101", mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME));
	}

	@Test
	void setTargetEffectiveTime_ShouldUpdateMDRS_WhenGivenExtension() throws ServiceException {
		String intMain = "MAIN";
		String extMain = "MAIN/SNOMEDCT-XX";
		Map<String, String> intPreferred = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED));
		String ci = "CASE_INSENSITIVE";
		Concept concept;
		CodeSystem codeSystem;

		// Create International
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));

		// Create module dependency entry manually
		ReferenceSetMember mdrs = new ReferenceSetMember();
		mdrs.setModuleId(CORE_MODULE);
		mdrs.setReferencedComponentId(CORE_MODULE);
		mdrs.setActive(true);
		mdrs.setRefsetId(Concepts.MODULE_DEPENDENCY_REFERENCE_SET);
		mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME, "");
		mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME, "");
		String mdrsId = referenceSetMemberService.createMember(intMain, mdrs).getMemberId();

		// Create top level concept
		concept = new Concept()
				.addDescription(new Description("Medicine (medicine)").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addDescription(new Description("Medicine").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);

		// Version CodeSystem
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20250101, "20250101");
		codeSystemController.updateVersion("SNOMEDCT", 20250101, "20250101.zip");

		codeSystem = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-XX", extMain));
		concept = conceptService.create(
				new Concept()
						.addDescription(new Description("Extension module (module)").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
						.addDescription(new Description("Extension module").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
						.addAxiom(new Relationship(ISA, MODULE)),
				extMain
		);
		String extModuleId = concept.getConceptId();
		branchService.updateMetadata(
				extMain,
				Map.of(Config.DEFAULT_MODULE_ID_KEY, extModuleId,
						Config.EXPECTED_EXTENSION_MODULES, List.of(extModuleId),
						Config.DEPENDENCY_PACKAGE, "20250101.zip"
				)
		);

		// Extension authors
		ReferenceSetMember mdrsExtension = new ReferenceSetMember();
		mdrsExtension.setModuleId(extModuleId);
		mdrsExtension.setReferencedComponentId(CORE_MODULE);
		mdrsExtension.setActive(true);
		mdrsExtension.setRefsetId(Concepts.MODULE_DEPENDENCY_REFERENCE_SET);
		mdrsExtension.setAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME, "");
		mdrsExtension.setAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME, "20250101");
		String mdrsExtensionId = referenceSetMemberService.createMember(extMain, mdrsExtension).getMemberId();

		concept = new Concept()
				.addDescription(new Description("Vehicle (vehicle)").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addDescription(new Description("Vehicle").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, extMain);

		// Extension versions
		codeSystem = codeSystemService.find("SNOMEDCT-XX");
		codeSystemService.createVersion(codeSystem, 20250102, "20250102");
		codeSystemController.updateVersion("SNOMEDCT-XX", 20250102, "20250102.zip");

		// International authors
		concept = new Concept()
				.addDescription(new Description("Food (food)").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addDescription(new Description("Food").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
				.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT));
		concept = conceptService.create(concept, intMain);

		// International version
		codeSystem = codeSystemService.find("SNOMEDCT");
		codeSystemService.createVersion(codeSystem, 20250201, "20250201");
		codeSystemController.updateVersion("SNOMEDCT", 20250201, "20250201.zip");

		// Assert Extension before upgrade
		mdrs = referenceSetMemberService.findMember(extMain, mdrsExtensionId);
		assertTrue(mdrs.isReleased());
		assertEquals(20250102, mdrs.getReleasedEffectiveTime());
		assertEquals(20250102, mdrs.getEffectiveTimeI());
		assertEquals("20250102", mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME));
		assertEquals("20250101", mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME));

		// Upgrade Extension
		codeSystem = codeSystemService.find("SNOMEDCT-XX");
		codeSystemUpgradeService.upgrade(null, codeSystem, 20250201, true);

		// Assert Extension after upgrade
		mdrs = referenceSetMemberService.findMember(extMain, mdrsExtensionId);
		assertTrue(mdrs.isReleased());
		assertEquals(20250102, mdrs.getReleasedEffectiveTime());
		assertNull(mdrs.getEffectiveTimeI());
		assertEquals("20250102", mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME));
		assertEquals("20250201", mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME));
	}


	@Test
	void getCodeSystemBranchByModuleId() {
		// Create International CodeSystem
		CodeSystem main = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
		// Add MDRS
		ReferenceSetMember coreMdrs = new ReferenceSetMember();
		coreMdrs.setModuleId(CORE_MODULE);
		coreMdrs.setReferencedComponentId(MODEL_MODULE);
		coreMdrs.setActive(true);
		coreMdrs.setRefsetId(Concepts.MODULE_DEPENDENCY_REFERENCE_SET);
		coreMdrs.setAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME, "");
		coreMdrs.setAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME, "");
		referenceSetMemberService.createMember("MAIN", coreMdrs);
		// Version MAIN here to avoid unpublished content from MAIN is being saved by extension during versioning
		codeSystemService.createVersion(main, 20250101, "20250321 International release");

		// Create LOINC CodeSystem
		CodeSystem loincCodeSystem = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-LOINC", "MAIN/SNOMEDCT-LOINC"));
		// Add MDRS
		final String LOINC_MODULE = "11010000107";
		ReferenceSetMember mdrs = new ReferenceSetMember();
		mdrs.setModuleId(LOINC_MODULE);
		mdrs.setReferencedComponentId(CORE_MODULE);
		mdrs.setActive(true);
		mdrs.setRefsetId(Concepts.MODULE_DEPENDENCY_REFERENCE_SET);
		mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME, "");
		mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME, "20250101");
		referenceSetMemberService.createMember(loincCodeSystem.getBranchPath(), mdrs);

		// Version LOINC
		codeSystemService.createVersion(loincCodeSystem, 20250321, "20250321 loinc release");

		// Create an extension CodeSystem
		CodeSystem codeSystem = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-XX", "MAIN/SNOMEDCT-XX"));

		// Create MDRS
		final String extensionModuleId = "2011000195101";
		mdrs = new ReferenceSetMember();
		mdrs.setModuleId(extensionModuleId);
		mdrs.setReferencedComponentId(CORE_MODULE);
		mdrs.setActive(true);
		mdrs.setRefsetId(Concepts.MODULE_DEPENDENCY_REFERENCE_SET);
		mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME, "");
		mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME, "20250101");
		referenceSetMemberService.createMember(codeSystem.getBranchPath(), mdrs);

		// Additional dependency
		mdrs = new ReferenceSetMember();
		mdrs.setModuleId(extensionModuleId);

		mdrs.setReferencedComponentId(LOINC_MODULE);
		mdrs.setActive(true);
		mdrs.setRefsetId(Concepts.MODULE_DEPENDENCY_REFERENCE_SET);
		mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME, "");
		mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME, "20250321");
		referenceSetMemberService.createMember(codeSystem.getBranchPath(), mdrs);

		Map<String, String> results = mdService.getCodeSystemBranchByModuleId(Set.of(CORE_MODULE, MODEL_MODULE, LOINC_MODULE, extensionModuleId));
		assertEquals(3, results.size());
		assertEquals("MAIN", results.get(CORE_MODULE));
		assertEquals("MAIN/SNOMEDCT-LOINC", results.get(LOINC_MODULE));
		assertEquals("MAIN/SNOMEDCT-XX", results.get(extensionModuleId));
	}

}
