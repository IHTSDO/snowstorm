package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.*;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ModuleDependencyServiceTest extends AbstractTest {
	
	private static String TEST_MODULE = "10123400";
	private static String TEST_ET = "20990131";
	private static String TEST_DEPENDENCY_ET = "19990131";
	private static String TEST_CS_INT = "SNOMEDCT-INT";
	private static String TEST_CS_MS = "SNOMEDCT-XY";
	private static String TEST_CS_PATH = "MAIN/" + TEST_CS_MS;

	@Autowired
	private CodeSystemService codeSystemService;
	
	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ModuleDependencyService mdService;
	
	@Autowired
	private ReferenceSetMemberService rmService;


	@BeforeEach
	void setUp() throws Exception {
		branchService.deleteAll();
		conceptService.deleteAll();
		branchService.create("MAIN");
		conceptService.create(new Concept(Concepts.SNOMEDCT_ROOT), Branch.MAIN);
		conceptService.create(new Concept(Concepts.CORE_MODULE).setModuleId(Concepts.CORE_MODULE), Branch.MAIN);
		conceptService.create(new Concept(Concepts.MODEL_MODULE).setModuleId(Concepts.MODEL_MODULE), Branch.MAIN);
		
		CodeSystem codeSystemINT = new CodeSystem(TEST_CS_INT, Branch.MAIN);
		codeSystemService.createCodeSystem(codeSystemINT);
		
		//This is interesting because creating a version is now generating and persisting the MDR members
		//So even the setup is a good test.
		codeSystemService.createVersion(codeSystemINT, Integer.parseInt(TEST_DEPENDENCY_ET), "TESTING");
	}
	
	@Test
	void testVersionedCodeSystemGeneration() throws InterruptedException, ServiceException {
		MemberSearchRequest searchRequest = new MemberSearchRequest().referenceSet(Concepts.REFSET_MODULE_DEPENDENCY);
		Page<ReferenceSetMember> mdrPage = rmService.findMembers(Branch.MAIN, searchRequest, PageRequest.of(0,10));
		//The model module has no dependencies, so we only expect 1 row for the core module
		assertEquals(1, mdrPage.getContent().size());
		assertEquals(Concepts.CORE_MODULE, mdrPage.getContent().get(0).getModuleId());
		assertEquals(Concepts.MODEL_MODULE, mdrPage.getContent().get(0).getReferencedComponentId());
		assertEquals(TEST_DEPENDENCY_ET, mdrPage.getContent().get(0).getEffectiveTime());
		assertEquals(TEST_DEPENDENCY_ET, mdrPage.getContent().get(0).getAdditionalField(ModuleDependencyService.SOURCE_ET));
		assertEquals(TEST_DEPENDENCY_ET, mdrPage.getContent().get(0).getAdditionalField(ModuleDependencyService.TARGET_ET));
	}

	@Test
	void testInternationalMdrGeneration() throws InterruptedException, ServiceException {
		createConcept("116680003", Concepts.CORE_MODULE, Branch.MAIN);
		createConcept("10000200", Concepts.MODEL_MODULE, Branch.MAIN);
		List<ReferenceSetMember> mdr = mdService.generateModuleDependencies(Branch.MAIN, TEST_ET, null, null);
		//The model module has no dependencies, so we only expect 1 row for the core module
		assertEquals(1, mdr.size());
		assertEquals(Concepts.CORE_MODULE, mdr.get(0).getModuleId());
		assertEquals(Concepts.MODEL_MODULE, mdr.get(0).getReferencedComponentId());
		assertEquals(TEST_ET, mdr.get(0).getEffectiveTime());
		assertEquals(TEST_ET, mdr.get(0).getAdditionalField(ModuleDependencyService.SOURCE_ET));
		assertEquals(TEST_ET, mdr.get(0).getAdditionalField(ModuleDependencyService.TARGET_ET));
	}
	
	@Test
	void testMsMdrGeneration() throws InterruptedException, ServiceException {
		createConcept("116680003", Concepts.CORE_MODULE, Branch.MAIN);
		CodeSystem codeSystemXY = new CodeSystem(TEST_CS_MS, TEST_CS_PATH);
		
		//Creating the code system after MAIN has been versioned should ensure the dependencyVersionEffectiveTime 
		//is picked up from there
		codeSystemService.createCodeSystem(codeSystemXY);
		createConcept(TEST_MODULE, TEST_MODULE, TEST_CS_PATH);
		
		List<ReferenceSetMember> mdr = mdService.generateModuleDependencies(TEST_CS_PATH, TEST_ET, null, null);
		
		//Working with a single MS module we expect to have dependencies to both the core and model module
		assertEquals(2, mdr.size());
		assertTrue(resultsContain(mdr, TEST_MODULE, Concepts.CORE_MODULE, TEST_ET, TEST_ET, TEST_DEPENDENCY_ET));
		assertTrue(resultsContain(mdr, TEST_MODULE, Concepts.MODEL_MODULE, TEST_ET, TEST_ET, TEST_DEPENDENCY_ET));
	}

	private boolean resultsContain(List<ReferenceSetMember> mdr, String sourceModule, String targetModule, String effectiveDate,
			String sourceEffectiveDate, String targetEffectiveDate) {
		for (ReferenceSetMember rm : mdr) {
			if (rm.getModuleId().equals(sourceModule) &&
					rm.getReferencedComponentId().equals(targetModule) &&
					rm.getEffectiveTime().equals(effectiveDate) &&
					rm.getAdditionalField(ModuleDependencyService.SOURCE_ET).equals(sourceEffectiveDate) &&
					rm.getAdditionalField(ModuleDependencyService.TARGET_ET).equals(targetEffectiveDate)) {
				return true;
			}
		}
		return false;
	}

	private void createConcept(String conceptId, String moduleId, String path) throws ServiceException {
		conceptService.create(
				new Concept(conceptId)
						.setModuleId(moduleId)
						.addDescription(
								new Description("Heart")
										.setCaseSignificance("CASE_INSENSITIVE")
										.setAcceptabilityMap(Collections.singletonMap(Concepts.US_EN_LANG_REFSET,
												Concepts.descriptionAcceptabilityNames.get(Concepts.ACCEPTABLE)))
						)
						.addDescription(
								new Description("Heart structure (body structure)")
										.setTypeId(Concepts.FSN)
										.setCaseSignificance("CASE_INSENSITIVE")
										.setAcceptabilityMap(Collections.singletonMap(Concepts.US_EN_LANG_REFSET,
												Concepts.descriptionAcceptabilityNames.get(Concepts.ACCEPTABLE))))
						.addRelationship(
								new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)
						)
						.addAxiom(
								new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)
						),
				path);
	}

}
