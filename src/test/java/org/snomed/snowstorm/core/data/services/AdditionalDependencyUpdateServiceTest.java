package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

@Testcontainers
@ExtendWith(SpringExtension.class)
class AdditionalDependencyUpdateServiceTest extends AbstractTest  {
    public static final String EXTENSION_MODULE = "11030000109";
    public static final String TM_MODULE = "11020000108";
    public static final String LOINC_MODULE = "11010000107";

    @Autowired
    private CodeSystemService codeSystemService;

    @Autowired
    private ReferenceSetMemberService referenceSetMemberService;

    @Autowired
    private BranchService branchService;

    @Autowired
    private VersionControlHelper versionControlHelper;

    @Autowired
    private ConceptService conceptService;

    private CodeSystem main;
    private CodeSystem loinc;
    private CodeSystem tm;
    private CodeSystem extension;


    @BeforeEach
    void setUp() {
        // Create MAIN
        main = new CodeSystem("SNOMEDCT", MAIN);
        codeSystemService.createCodeSystem(main);
        referenceSetMemberService.createMember(main.getBranchPath(), constructMDRS(CORE_MODULE, MODEL_MODULE, 20250501, 20250501));
        codeSystemService.createVersion(main, 20250501, "20250501 release");

        // Create LOINC
        loinc = new CodeSystem("SNOMEDCT-LOINC", "MAIN/SNOMEDCT-LOINC");
        codeSystemService.createCodeSystem(loinc);
        referenceSetMemberService.createMember(loinc.getBranchPath(), constructMDRS(LOINC_MODULE, CORE_MODULE, 20250601, 20250501));
        referenceSetMemberService.createMember(loinc.getBranchPath(), constructMDRS(LOINC_MODULE, MODEL_MODULE, 20250601, 20250501));
        codeSystemService.createVersion(loinc, 20250601, "20250601 loinc release");

        // Create Chinese Traditional Medicine
        tm = new CodeSystem("SNOMEDCT-TM", "MAIN/SNOMEDCT-TM");
        codeSystemService.createCodeSystem(tm);
        referenceSetMemberService.createMember(tm.getBranchPath(), constructMDRS(TM_MODULE, CORE_MODULE, 20250601, 20250501));
        referenceSetMemberService.createMember(tm.getBranchPath(), constructMDRS(TM_MODULE, MODEL_MODULE, 20250601, 20250501));
        codeSystemService.createVersion(tm, 20250601, "20250601 ctm release");

        // extension and additional dependency code systems
        extension = new CodeSystem("SNOMEDCT-XX", "MAIN/SNOMEDCT-XX");
        codeSystemService.createCodeSystem(extension);
        referenceSetMemberService.createMember(extension.getBranchPath(), constructMDRS(EXTENSION_MODULE, CORE_MODULE, null, 20250501));
        referenceSetMemberService.createMember(extension.getBranchPath(), constructMDRS(EXTENSION_MODULE, MODEL_MODULE, null, 20250501));

    }

    @Test
    void addMultipleDependencies() {
        // Add one MDRS entry for LOINC on extension
        referenceSetMemberService.createMember(extension.getBranchPath(), constructMDRS(EXTENSION_MODULE, LOINC_MODULE, null, 20250601));
        List<String> addtionalDependencies = branchService.findLatest(extension.getBranchPath()).getMetadata().getList(VersionControlHelper.ADDITIONAL_DEPENDENT_BRANCHES);
        assertNotNull(addtionalDependencies);
        assertEquals(1, addtionalDependencies.size());
        assertEquals("MAIN/SNOMEDCT-LOINC/2025-06-01", addtionalDependencies.get(0));

        // Add another MDRS entry for TM dependency
        ReferenceSetMember member = referenceSetMemberService.createMember(extension.getBranchPath(), constructMDRS(EXTENSION_MODULE, TM_MODULE, null, 20250601));
        member = referenceSetMemberService.findMember(extension.getBranchPath(), member.getMemberId());
        assertNotNull(member);
        addtionalDependencies = versionControlHelper.getAdditionalDependencies(branchService.findLatest(extension.getBranchPath()));
        assertNotNull(addtionalDependencies);
        assertEquals(2, addtionalDependencies.size());
        assertTrue(addtionalDependencies.contains("MAIN/SNOMEDCT-TM/2025-06-01"));
        assertTrue(addtionalDependencies.contains("MAIN/SNOMEDCT-LOINC/2025-06-01"));
    }


    @Test
    void shouldThrowExceptionWhenVersionedBranchNotFound() {
        ReferenceSetMember member = constructMDRS(EXTENSION_MODULE, LOINC_MODULE, null, 20250501);
        String branchPath = extension.getBranchPath();
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                referenceSetMemberService.createMember(branchPath, member)
        );
        assertEquals("Failed to find versioned branch MAIN/SNOMEDCT-LOINC/2025-05-01", exception.getMessage());
    }

    @Test
    void testRemoveAdditionalDependency() {
        // Add one MDRS entry for LOINC on extension
        ReferenceSetMember loincMdrs = referenceSetMemberService.createMember(extension.getBranchPath(), constructMDRS(EXTENSION_MODULE, LOINC_MODULE, null, 20250601));
        // Add another MDRS entry for TM dependency
        ReferenceSetMember tmMdrs = referenceSetMemberService.createMember(extension.getBranchPath(), constructMDRS(EXTENSION_MODULE, TM_MODULE, null, 20250601));
        List<String> additionalDependencies = versionControlHelper.getAdditionalDependencies(branchService.findLatest(extension.getBranchPath()));
        assertEquals(2, additionalDependencies.size());

        // Remove LOINC dependency
        referenceSetMemberService.deleteMember(extension.getBranchPath(), loincMdrs.getMemberId());
        additionalDependencies = branchService.findLatest(extension.getBranchPath()).getMetadata().getList(VersionControlHelper.ADDITIONAL_DEPENDENT_BRANCHES);
        assertEquals(1, additionalDependencies.size());
        assertEquals("MAIN/SNOMEDCT-TM/2025-06-01", additionalDependencies.get(0));

        // Remove TM dependency
        referenceSetMemberService.deleteMember(extension.getBranchPath(), tmMdrs.getMemberId());
        additionalDependencies = versionControlHelper.getAdditionalDependencies(branchService.findLatest(extension.getBranchPath()));
        assertTrue(additionalDependencies.isEmpty());
    }

    @Test
    void testUpdateAdditionalDependency() {
        // Add one MDRS entry for LOINC on extension
        ReferenceSetMember member = referenceSetMemberService.createMember(extension.getBranchPath(), constructMDRS(EXTENSION_MODULE, LOINC_MODULE, null, 20250601));
        List<String> additionalDependencies = versionControlHelper.getAdditionalDependencies(branchService.findLatest(extension.getBranchPath()));
        assertNotNull(additionalDependencies);
        assertEquals(1, additionalDependencies.size());
        assertEquals("MAIN/SNOMEDCT-LOINC/2025-06-01", additionalDependencies.get(0));

        codeSystemService.createVersion(loinc, 20250701, "20250701 loinc release");
        // Change LOINC dependency effectiveTime
        member.setAdditionalField("targetEffectiveTime", "20250701");
        referenceSetMemberService.updateMember(extension.getBranchPath(), member);
        additionalDependencies = versionControlHelper.getAdditionalDependencies(branchService.findLatest(extension.getBranchPath()));
        assertNotNull(additionalDependencies);
        assertEquals(1, additionalDependencies.size());
        assertEquals("MAIN/SNOMEDCT-LOINC/2025-07-01", additionalDependencies.get(0));

       // Replace LOINC with TM in the same commit
        member.setActive(false);
        // Add another MDRS entry for TCM dependency
        ReferenceSetMember mdrsForTM = referenceSetMemberService.createMember(extension.getBranchPath(), constructMDRS(EXTENSION_MODULE, TM_MODULE, null, 20250601));
        List<ReferenceSetMember> mdrs = Arrays.asList(member, mdrsForTM);

        try (Commit commit = branchService.openCommit(extension.getBranchPath(), "Update MDRS")) {
            referenceSetMemberService.doSaveBatchMembers(mdrs, commit);
            commit.markSuccessful();
        }
        additionalDependencies = versionControlHelper.getAdditionalDependencies(branchService.findLatest(extension.getBranchPath()));
        assertNotNull(additionalDependencies);
        assertEquals(1, additionalDependencies.size());
        assertEquals("MAIN/SNOMEDCT-TM/2025-06-01", additionalDependencies.get(0));
    }

    @Test
    void testVersioningCodeSystemWithAdditionalDependency() throws ServiceException {
        // Add a holding module for LOINC in extension
        Concept holdingModule = new Concept("123456", EXTENSION_MODULE).addDescription(new Description("LOINC holding module"))
                .addRelationship(Concepts.ISA, MODULE);
        conceptService.create(holdingModule, extension.getBranchPath());

        // Add modules to branch metadata
        HashMap<String, Object> metaData = new HashMap<>();
        metaData.put(BranchMetadataKeys.EXPECTED_EXTENSION_MODULES, List.of(EXTENSION_MODULE, holdingModule.getId()));
        metaData.put(BranchMetadataKeys.DEFAULT_MODULE_ID, EXTENSION_MODULE);
        branchService.updateMetadata(extension.getBranchPath(), metaData);

        // Add MDRS to extension default module
        ReferenceSetMember holdingModuleMdrs = referenceSetMemberService.createMember(extension.getBranchPath(), constructMDRS(holdingModule.getConceptId(), EXTENSION_MODULE, null, null));

        // Add one MDRS entry for LOINC on extension using the holding module
        ReferenceSetMember loincMdrs = referenceSetMemberService.createMember(extension.getBranchPath(), constructMDRS(holdingModule.getConceptId(), LOINC_MODULE, null, 20250601));
        List<String> additionalDependencies = versionControlHelper.getAdditionalDependencies(branchService.findLatest(extension.getBranchPath()));
        assertNotNull(additionalDependencies);
        assertEquals(1, additionalDependencies.size());
        assertEquals("MAIN/SNOMEDCT-LOINC/2025-06-01", additionalDependencies.get(0));
        codeSystemService.createVersion(extension, 20250701, "20250701 extension release");

        // Verify after versioning
        holdingModuleMdrs = referenceSetMemberService.findMember(extension.getBranchPath(), holdingModuleMdrs.getMemberId());
        assertEquals("20250701", holdingModuleMdrs.getAdditionalField("sourceEffectiveTime"));
        assertEquals("20250701", holdingModuleMdrs.getAdditionalField("targetEffectiveTime"));

        loincMdrs = referenceSetMemberService.findMember(extension.getBranchPath(), loincMdrs.getMemberId());
        assertNotNull(loincMdrs);
        assertEquals("20250701", loincMdrs.getAdditionalField("sourceEffectiveTime"));
        assertEquals("20250601", loincMdrs.getAdditionalField("targetEffectiveTime"));
        // No LOINC version change
        additionalDependencies = versionControlHelper.getAdditionalDependencies(branchService.findLatest(extension.getBranchPath()));
        assertNotNull(additionalDependencies);
        assertEquals(1, additionalDependencies.size());
        assertEquals("MAIN/SNOMEDCT-LOINC/2025-06-01", additionalDependencies.get(0));
    }

    private ReferenceSetMember constructMDRS(String moduleId, String dependantModuleId, Integer sourceEffectiveTime, Integer targetEffectiveTime) {
        ReferenceSetMember mdrs = new ReferenceSetMember(moduleId, Concepts.MODULE_DEPENDENCY_REFERENCE_SET, dependantModuleId);
        if (sourceEffectiveTime != null) {
            mdrs.setAdditionalField("sourceEffectiveTime", sourceEffectiveTime.toString());
        }
        if (targetEffectiveTime != null) {
            mdrs.setAdditionalField("targetEffectiveTime", targetEffectiveTime.toString());
        }
        return mdrs;
    }
}