package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.review.MergeReview;
import org.snomed.snowstorm.core.data.domain.review.MergeReviewConceptVersions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;
import static org.snomed.snowstorm.core.data.domain.review.ReviewStatus.PENDING;
import static org.snomed.snowstorm.core.data.services.CodeSystemService.SNOMEDCT;

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
    private CodeSystemService codeSystemService;

    @Test
    void testNoDuplicateCNCMembersCreatedAfterUpgrade() throws ServiceException, InterruptedException {
        String intMain = "MAIN";
        String extMain = "MAIN/SNOMEDCT-XX";
        Map<String, String> intPreferred = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED));
        Map<String, String> intAcceptable = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(ACCEPTABLE));
        String ci = "CASE_INSENSITIVE";
        Concept concept;
        CodeSystem codeSystem;

        // Create International CodeSystem
        codeSystemService.createCodeSystem(new CodeSystem(SNOMEDCT, Branch.MAIN));

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
        codeSystem = codeSystemService.find("SNOMEDCT");
        codeSystemService.createVersion(codeSystem, 20220131, "20220131");

        // Create Extension
        codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-XX", extMain));
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
        codeSystem = codeSystemService.find("SNOMEDCT");
        codeSystemService.createVersion(codeSystem, 20220228, "20220228");

        // Upgrade Extension to use February
        codeSystem = codeSystemService.find("SNOMEDCT-XX");
        codeSystemUpgradeService.upgrade(null, codeSystem, 20220228, true);

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
        codeSystem = codeSystemService.find("SNOMEDCT");
        codeSystemService.createVersion(codeSystem, 20220331, "20220331");

        // Upgrade Extension to use March
        codeSystem = codeSystemService.find("SNOMEDCT-XX");
        codeSystemUpgradeService.upgrade(null, codeSystem, 20220331, true);

        // Assert CNC on Extension
        concept = conceptService.find(vehicleId, extMain);
        mapCNCByTerm = mapCNCByTerm(concept);
        assertEquals(4, mapCNCByTerm.size());
        assertEquals(1, mapCNCByTerm.get("Bil").size());
        assertEquals(1, mapCNCByTerm.get("Vehicle").size());
        assertEquals(1, mapCNCByTerm.get("Vehicle (vehicle)").size());
        assertEquals(1, mapCNCByTerm.get("Car").size());
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
}
