package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.review.MergeReview;
import org.snomed.snowstorm.core.data.domain.review.MergeReviewConceptVersions;
import org.snomed.snowstorm.core.data.services.pojo.CodeSystemUpgradeJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;
import static org.snomed.snowstorm.core.data.domain.review.ReviewStatus.PENDING;

@ExtendWith(SpringExtension.class)
class CodeSystemUpgradeServiceTest extends AbstractTest {
    @Autowired
    private BranchMergeService branchMergeService;

    @Autowired
    private BranchReviewService reviewService;

    @Autowired
    private BranchService branchService;

    @Autowired
    private ConceptService conceptService;

    @Autowired
    private CodeSystemUpgradeService codeSystemUpgradeService;

    @Autowired
    private ReferenceSetMemberService referenceSetMemberService;

    @Autowired
    private CodeSystemService codeSystemService;

    private CodeSystem MAIN;
    private CodeSystem extension;
    private CodeSystem LOINC;

    @BeforeEach
    void setup() {
        MAIN = new CodeSystem("SNOMEDCT", "MAIN");
        codeSystemService.createCodeSystem(MAIN);
    }


        @Test
        void testNoDuplicateCNCMembersCreatedAfterUpgrade() throws ServiceException, InterruptedException {
        String intMain = "MAIN";
        String extMain = "MAIN/SNOMEDCT-XX";
        Map<String, String> intPreferred = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED));
        Map<String, String> intAcceptable = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(ACCEPTABLE));
        String ci = "CASE_INSENSITIVE";
        Concept concept;
        // Create root Concept
        conceptService.create(new Concept(Concepts.SNOMEDCT_ROOT), Branch.MAIN);

        // Create Concept
        concept = new Concept()
                .addDescription(new Description("Vehicle (vehicle)").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
                .addDescription(new Description("Vehicle").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
                .addAxiom(new Relationship(ISA, SNOMEDCT_ROOT));
        concept = conceptService.create(concept, intMain);
        String vehicleId = concept.getConceptId();

        // Version International
        codeSystemService.createVersion(MAIN, 20220131, "20220131");

        // Create Extension
        CodeSystem extension = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-XX", extMain));
        concept = conceptService.create(
                new Concept()
                        .addDescription(new Description("Extension default module").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
                        .addDescription(new Description("Extension default").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
                        .addAxiom(new Relationship(ISA, MODULE)),
                extMain
        );
        String extDefaultModule = concept.getConceptId();
        concept = conceptService.create(
                new Concept()
                        .addDescription(new Description("Extension drug module").setTypeId(FSN).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
                        .addDescription(new Description("Extension drug").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intPreferred))
                        .addAxiom(new Relationship(ISA, MODULE)),
                extMain
        );
        String extDrugModule = concept.getConceptId();
        branchService.updateMetadata(extMain, Map.of(Config.DEFAULT_MODULE_ID_KEY, extDefaultModule, Config.EXPECTED_EXTENSION_MODULES, new String[]{extDefaultModule, extDrugModule}));

        // Create Extension project
        String extProject = "MAIN/SNOMEDCT-XX/projectA";
        branchService.create(extProject);

        // Create Extension task A
        String extTaskA = "MAIN/SNOMEDCT-XX/projectA/taskA";
        branchService.create(extTaskA);

        // Add translation to Concept on Extension
        concept = conceptService.find(vehicleId, extTaskA);
        concept.addDescription(new Description("Bil").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intAcceptable));
        conceptService.update(concept, extTaskA);

        // Promote Extension task A to Extension project
        branchMergeService.mergeBranchSync(extTaskA, extProject, Collections.emptySet());

        // International adds synonym to concept
        concept = conceptService.find(vehicleId, intMain);
        concept.addDescription(new Description("Car").setTypeId(SYNONYM).setCaseSignificance(ci).setAcceptabilityMap(intAcceptable));
        conceptService.update(concept, intMain);

        // Version International for February
        codeSystemService.createVersion(MAIN, 20220228, "20220228");

        // Upgrade Extension to use February
        codeSystemUpgradeService.upgrade(null, extension, 20220228, true);

        // Rebase Extension project
        MergeReview review = getMergeReviewInCurrentState(extMain, extProject);
        Collection<MergeReviewConceptVersions> conflicts = reviewService.getMergeReviewConflictingConcepts(review.getId(), new ArrayList<>());
        assertEquals(1, conflicts.size());
        for (MergeReviewConceptVersions conflict : conflicts) {
            reviewService.persistManuallyMergedConcept(review, Long.parseLong(vehicleId), conflict.getAutoMergedConcept());
        }
        reviewService.applyMergeReview(review);

        // Promote Extension project to CodeSystem
        branchMergeService.mergeBranchSync(extProject, extMain, Collections.emptySet());

        // International inactivates Concept
        concept = conceptService.find(vehicleId, intMain);
        inactivate(concept, "OUTDATED");
        conceptService.update(concept, "MAIN");

        // Assert CNC on International
        concept = conceptService.find(vehicleId, intMain);
        Map<String, Collection<ReferenceSetMember>> mapCNCByTerm = mapCNCByTerm(concept);
        assertEquals(3, mapCNCByTerm.size());
        assertEquals(1, mapCNCByTerm.get("Vehicle").size());
        assertEquals(1, mapCNCByTerm.get("Vehicle (vehicle)").size());
        assertEquals(1, mapCNCByTerm.get("Car").size());

        // Version International for March
        codeSystemService.createVersion(MAIN, 20220331, "20220331");

        // Upgrade Extension to use March
        codeSystemUpgradeService.upgrade(null, extension, 20220331, true);

        // Assert CNC on Extension
        concept = conceptService.find(vehicleId, extMain);
        mapCNCByTerm = mapCNCByTerm(concept);
        assertEquals(4, mapCNCByTerm.size());
        assertEquals(1, mapCNCByTerm.get("Bil").size());
        assertEquals(1, mapCNCByTerm.get("Vehicle").size());
        assertEquals(1, mapCNCByTerm.get("Vehicle (vehicle)").size());
        assertEquals(1, mapCNCByTerm.get("Car").size());
    }

    @Test
    void upgradeAllowed_whenAllDependenciesCompatible() throws ServiceException {
        setUpAdditionalDependencies();
        // Version MAIN
        codeSystemService.createVersion(MAIN, 20250101, "International Jan 2025");
        // Upgrade LOINC to 20250101 and version
        codeSystemUpgradeService.upgrade(null, LOINC, 20250101, true);
        codeSystemService.createVersion(LOINC, 20250201, "LOINC Jan 2025");
        CodeSystemUpgradeJob job = new CodeSystemUpgradeJob(extension.getShortName(), 20250101);
        assertDoesNotThrow(() -> codeSystemUpgradeService.preUpgradeChecks(extension, 20250101, job));
    }

    @Test
    void upgradeBlocked_whenMissingAdditionalDependency() {
        setUpAdditionalDependencies();
        // Version MAIN
        codeSystemService.createVersion(MAIN, 20250101, "International Jan 2025");
        // Upgrade should be blocked
        CodeSystemUpgradeJob job = new CodeSystemUpgradeJob(extension.getShortName(), 20250101);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> codeSystemUpgradeService.preUpgradeChecks(extension, 20250101, job));
        String expected = "No compatible release found for dependent code system SNOMEDCT-LOINC with the requested International version 20250101." +
                " Please wait for a compatible release before proceeding.";
        assertEquals(expected, ex.getMessage());
    }

    @Test
    void upgradeBlocked_withRecommendation() throws ServiceException {
       setUpAdditionalDependencies();
        // Version MAIN
        codeSystemService.createVersion(MAIN, 20250101, "International Jan 2025");

        codeSystemService.createVersion(MAIN, 20250201, "International Feb 2025");
        // Upgrade LOINC to 20250201 and version
        codeSystemUpgradeService.upgrade(null, LOINC, 20250201, true);
        codeSystemService.createVersion(LOINC, 20250301, "LOINC March 2025");

        CodeSystemUpgradeJob job = new CodeSystemUpgradeJob(extension.getShortName(), 20250101);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> codeSystemUpgradeService.preUpgradeChecks(extension, 20250101, job));
        String expected = "Version 20250101 of the International release isn’t compatible with all additional dependencies." +
                " Try upgrading to 20250201, which is fully compatible.";

        assertEquals(expected, ex.getMessage());
    }

    @Test
    void upgradeAllowed_whenNoAdditionalDependencies() {
        // Version MAIN for 20241101
        codeSystemService.createVersion(MAIN, 20241101, "International 20241101");
        // Setup: Extension only depends on International (no additional dependencies)
        CodeSystem simpleExtension = new CodeSystem("SNOMEDCT-SIMPLE", "MAIN/SNOMEDCT-SIMPLE");
        simpleExtension.setDependantVersionEffectiveTime(20241101);
        codeSystemService.createCodeSystem(simpleExtension);
        createMDRS("22020000206", "MAIN/SNOMEDCT-SIMPLE", CORE_MODULE, "20241101");

        // Version MAIN
        codeSystemService.createVersion(MAIN, 20250101, "International Jan 2025");

        CodeSystemUpgradeJob job = new CodeSystemUpgradeJob(simpleExtension.getShortName(), 20250101);
        assertDoesNotThrow(() -> codeSystemUpgradeService.preUpgradeChecks(simpleExtension, 20250101, job));
    }

    @Test
    void upgradeBlocked_withMultipleAdditionalDependencies() throws ServiceException {
        setUpAdditionalDependencies();
        
        // Create a third dependency
        CodeSystem thirdDep = new CodeSystem("SNOMEDCT-THIRD", "MAIN/SNOMEDCT-THIRD");
        codeSystemService.createCodeSystem(thirdDep);
        createMDRS("33030000309", "MAIN/SNOMEDCT-THIRD", CORE_MODULE, "20241101");
        
        // Create version for third dependency that is NOT compatible with 20250101
        // This version depends on 20241201, not 20250101
        codeSystemService.createVersion(thirdDep, 20241201, "SNOMEDCT-THIRD 20241201");
        
        // Add third dependency to extension
        createMDRS("22020000206", "MAIN/SNOMEDCT-EXT", "33030000309", "20241201");

        // Version MAIN
        codeSystemService.createVersion(MAIN, 20250101, "International Jan 2025");

        // Only upgrade LOINC to 20250101, leave third dependency incompatible
        codeSystemUpgradeService.upgrade(null, LOINC, 20250101, true);
        codeSystemService.createVersion(LOINC, 20250201, "LOINC Jan 2025");

        // Now try to upgrade extension to 20250101 - should fail because third dependency is not compatible
        CodeSystemUpgradeJob job = new CodeSystemUpgradeJob(extension.getShortName(), 20250101);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> codeSystemUpgradeService.preUpgradeChecks(extension, 20250101, job));

        String expected = "No compatible release found for dependent code system SNOMEDCT-THIRD with the requested International version 20250101. " +
                "Please wait for a compatible release before proceeding.";
        assertEquals(expected, ex.getMessage());
    }

    private void setUpAdditionalDependencies() {
        createMDRS(CORE_MODULE, MAIN.getBranchPath(), MODEL_MODULE, null);
        codeSystemService.createVersion(MAIN, 20241101, "International November release 2024");

        // Create Additional code system and version
        LOINC = new CodeSystem("SNOMEDCT-LOINC", "MAIN/SNOMEDCT-LOINC");
        codeSystemService.createCodeSystem(LOINC);
        createMDRS("11010000107", "MAIN/SNOMEDCT-LOINC", CORE_MODULE, "20241101");
        codeSystemService.createVersion(LOINC, 20241201, "LOINC December 2024");

        // Create Extension code system
        extension = new CodeSystem("SNOMEDCT-EXT", "MAIN/SNOMEDCT-EXT");
        extension.setDependantVersionEffectiveTime(20241101);
        codeSystemService.createCodeSystem(extension);
        createMDRS("22020000206", "MAIN/SNOMEDCT-EXT", CORE_MODULE, "20241101");
        // Additional dependency on LOINC module
        createMDRS("22020000206", "MAIN/SNOMEDCT-EXT", "11010000107", "20241201");
    }

    private void inactivate(Concept concept, String inactivationIndicator) {
        concept.setActive(false);
        concept.setInactivationIndicator(inactivationIndicator);
        concept.updateEffectiveTime();
        for (Axiom classAxiom : concept.getClassAxioms()) {
            classAxiom.setActive(false);
            for (Relationship relationship : classAxiom.getRelationships()) {
                relationship.setActive(false);
            }
        }
    }

    private Map<String, Collection<ReferenceSetMember>> mapCNCByTerm(Concept concept) {
        Map<String, Collection<ReferenceSetMember>> cncByTerm = new HashMap<>();

        for (Description description : concept.getDescriptions()) {
            String term = description.getTerm();
            cncByTerm.put(term, description.getInactivationIndicatorMembers());
        }

        return cncByTerm;
    }

    private MergeReview getMergeReviewInCurrentState(String source, String target) throws InterruptedException {
        MergeReview review = reviewService.createMergeReview(source, target);

        long maxWait = 10;
        long cumulativeWait = 0;
        while (review.getStatus() == PENDING && cumulativeWait < maxWait) {
            //noinspection BusyWait
            Thread.sleep(1_000);
            cumulativeWait++;
        }
        return review;
    }

    private void createMDRS(String moduleId, String branchPath, String referencedComponentId, String targetEffectiveTime) {
        ReferenceSetMember mdrs = new ReferenceSetMember();
        mdrs.setModuleId(moduleId);
        mdrs.setReferencedComponentId(referencedComponentId);
        mdrs.setActive(true);
        mdrs.setRefsetId(Concepts.MODULE_DEPENDENCY_REFERENCE_SET);
        mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME, "");
        targetEffectiveTime = Objects.requireNonNullElse(targetEffectiveTime, "");
        mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME, targetEffectiveTime);
        referenceSetMemberService.createMember(branchPath, mdrs);
    }
}
