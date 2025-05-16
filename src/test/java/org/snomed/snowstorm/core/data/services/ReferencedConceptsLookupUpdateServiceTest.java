package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.repositories.ReferencedConceptsLookupRepository;
import org.snomed.snowstorm.ecl.ReferencedConceptsLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;
import static org.snomed.snowstorm.core.data.domain.ReferencedConceptsLookup.Type.EXCLUDE;
import static org.snomed.snowstorm.core.data.domain.ReferencedConceptsLookup.Type.INCLUDE;

@ExtendWith(SpringExtension.class)
class ReferencedConceptsLookupUpdateServiceTest extends AbstractTest {

    @Autowired
    private BranchService branchService;

    @Autowired
    private VersionControlHelper versionControlHelper;

    @Autowired
    private SBranchService sBranchService;

    @Autowired
    private ReferenceSetMemberService refsetMemberService;

    @Autowired
    private ReferencedConceptsLookupService conceptsLookupService;

    @Autowired
    private  BranchMergeService branchMergeService;

    @Autowired
    private CodeSystemService codeSystemService;

    @Autowired
    private CodeSystemUpgradeService codeSystemUpgradeService;

    @Autowired
    private ReferencedConceptsLookupUpdateService conceptsLookupUpdateService;

    private Branch projectA;
    private Branch taskA;
    @Autowired
    private ReferencedConceptsLookupService referencedConceptsLookupService;
    @Autowired
    private ReferencedConceptsLookupRepository referencedConceptsLookupRepository;
    @Autowired
    private ConceptService conceptService;

    @BeforeEach
    void setUp() throws ServiceException {
        // Create concepts
        List<Concept> allConcepts = new ArrayList<>();
        allConcepts.add(new Concept(SNOMEDCT_ROOT));
        allConcepts.add(new Concept(ISA).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
        allConcepts.add(new Concept(REFSET_SIMPLE).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
        allConcepts.add(new Concept(LATERALIZABLE_BODY_STRUCTURE_REFERENCE_SET).addRelationship(Concepts.ISA, REFSET_SIMPLE));
        conceptService.batchCreate(allConcepts, MAIN);
       projectA = branchService.create("MAIN/projectA");
       taskA = branchService.create("MAIN/projectA/taskA");
    }
    @Test
    void testUpdateLookupsDuringPromotion() throws ServiceException {
        // Add one refset change on a task A
        createMembers(taskA.getPath(), REFSET_SIMPLE, Collections.singletonList("200001"));
        taskA = branchService.findLatest(taskA.getPath());
        List<ReferencedConceptsLookup> lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(taskA), false);
        // Check that the lookup is empty due to not reaching the threshold of 2
        assertEquals(0, lookups.size());
        // Add one more member
        createMembers(taskA.getPath(), REFSET_SIMPLE, Collections.singletonList("200002"));
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(taskA.getPath())), true);
        assertEquals(1, lookups.size());
        assertEquals(2, lookups.get(0).getTotal());
        assertEquals(REFSET_SIMPLE, lookups.get(0).getRefsetId());
        assertEquals(taskA.getPath(), lookups.get(0).getPath());
        assertEquals(Set.of(200001L, 200002L), lookups.get(0).getConceptIds());

        // Promote task A to Project A
        branchMergeService.mergeBranchSync(taskA.getPath(), projectA.getPath(), Collections.emptyList());
        projectA = branchService.findLatest(projectA.getPath());
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(projectA), true);
        assertEquals(1, lookups.size());
        assertEquals(2, lookups.get(0).getTotal());
        assertEquals(REFSET_SIMPLE, lookups.get(0).getRefsetId());
        assertEquals(projectA.getPath(), lookups.get(0).getPath());
        assertEquals(Set.of(200001L, 200002L), lookups.get(0).getConceptIds());
        // Check that the lookup has been ended on task A after promotion
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(taskA.getPath())), false);
        assertEquals(1, lookups.size());

        // Promote Project A to MAIN
        branchMergeService.mergeBranchSync(projectA.getPath(), "MAIN", Collections.emptyList());
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest("MAIN")));
        assertEquals(1, lookups.size());
        assertEquals(2, lookups.get(0).getTotal());
        assertEquals(REFSET_SIMPLE, lookups.get(0).getRefsetId());
        assertEquals("MAIN", lookups.get(0).getPath());
        assertEquals(Set.of(200001L, 200002L), lookups.get(0).getConceptIds());

        // Check that the lookup has been ended on project A after promotion
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(projectA.getPath())), false);
        assertEquals(1, lookups.size());
    }

    @Test
    void testPromotionsWithDeletion() throws ServiceException {
        // Add two refset changes on a project A
        Set<ReferenceSetMember> members = createMembers(projectA.getPath(), REFSET_SIMPLE, List.of("200001", "200002"));
        List<ReferencedConceptsLookup> lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(projectA.getPath())));
        assertEquals(1, lookups.size());
        // Add deletion on task B for one member
        Branch taskB = branchService.create("MAIN/projectA/taskB");
        String uuid = members.stream().filter(member -> member.getReferencedComponentId().equals("200001")).findFirst().orElseThrow().getMemberId();
        refsetMemberService.deleteMember(taskB.getPath(), uuid);
        ReferenceSetMember member = refsetMemberService.findMember(taskB.getPath(), uuid);
        assertNull(member);
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(taskB.getPath())), true);
        // one include from project A and one exclude from task B
        assertEquals(2, lookups.size());
        lookups.forEach(lookup -> {
            if (lookup.getPath().equals(projectA.getPath())) {
                assertEquals(2, lookup.getTotal());
                assertEquals(Set.of(200001L, 200002L), lookup.getConceptIds());
            } else {
                assertEquals(1, lookup.getTotal());
                assertEquals(Set.of(200001L), lookup.getConceptIds());
            }
        });
        // Promote task B to project A
        branchMergeService.mergeBranchSync(taskB.getPath(), projectA.getPath(), Collections.emptyList());
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(projectA.getPath())));
        // one for include because the deletion change is originally from project A
        assertEquals(1, lookups.size());
        lookups.forEach(lookup -> {
            assertEquals(1, lookup.getTotal());
            assertEquals(Set.of(200002L), lookup.getConceptIds());
            assertEquals(projectA.getPath(), lookup.getPath());
            assertEquals(INCLUDE, lookup.getType());
        });

        // Promote project A to MAIN
        branchMergeService.mergeBranchSync(projectA.getPath(), "MAIN", Collections.emptyList());
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest("MAIN")));
        // one for include on code system branch
        assertEquals(1, lookups.size());
    }


    @Test
    void testPromotionsWhenDeleteAll() throws ServiceException {
        // Add two refset changes on a project A
        Set<ReferenceSetMember> members = createMembers(projectA.getPath(), REFSET_SIMPLE, List.of("200001", "200002"));
        List<ReferencedConceptsLookup> lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(projectA.getPath())));
        assertEquals(1, lookups.size());
        // Add deletion on task B for both
        Branch taskB = branchService.create("MAIN/projectA/taskB");
        members.forEach(member -> refsetMemberService.deleteMember(taskB.getPath(), member.getMemberId()));
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(taskB.getPath())), true);
        assertEquals(2, lookups.size());
        lookups.forEach(lookup -> {
            assertEquals(2, lookup.getTotal());
            assertEquals(Set.of(200001L, 200002L), lookup.getConceptIds());
            if (lookup.getPath().equals(projectA.getPath())) {
                assertEquals(INCLUDE, lookup.getType());
            } else {
                assertEquals(EXCLUDE, lookup.getType());
                assertEquals("MAIN/projectA/taskB", lookup.getPath());
            }
        });
        // Promote task B to project A
        branchMergeService.mergeBranchSync(taskB.getPath(), projectA.getPath(), Collections.emptyList());
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(projectA.getPath())));
        assertEquals(0, lookups.size());
    }

    @Test
    void testRebaseWithDeletion() throws ServiceException {
        // Add two refset changes on MAIN
        Set<ReferenceSetMember> members = createMembers("MAIN", REFSET_SIMPLE, List.of("200001", "200002"));
        // Rebase project A and task A
        branchMergeService.mergeBranchSync("MAIN", projectA.getPath(), Collections.emptyList());
        branchMergeService.mergeBranchSync(projectA.getPath(), taskA.getPath(), Collections.emptyList());
        List<ReferencedConceptsLookup> lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(taskA.getPath())));
        assertEquals(1, lookups.size());

        // Delete one from task A
        String uuid = members.stream().filter(member -> member.getReferencedComponentId().equals("200001")).findFirst().orElseThrow().getMemberId();
        refsetMemberService.deleteMember(taskA.getPath(), uuid);
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(taskA.getPath())));
        // one from MAIN and one from task A
        assertEquals(2, lookups.size());
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getChangesOnBranchCriteria(branchService.findLatest(taskA.getPath())));
        assertEquals(1, lookups.size());
        lookups.forEach(lookup -> {
            assertEquals(1, lookup.getTotal());
            assertEquals(Set.of(200001L), lookup.getConceptIds());
            assertEquals(taskA.getPath(), lookup.getPath());
            assertEquals(EXCLUDE, lookup.getType());
        });

        // Delete the other member from task B
        Branch taskB = branchService.create("MAIN/projectA/taskB");
        String another = members.stream().filter(member -> member.getReferencedComponentId().equals("200002")).findFirst().orElseThrow().getMemberId();
        refsetMemberService.deleteMember(taskB.getPath(), another);
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getChangesOnBranchCriteria(branchService.findLatest(taskB.getPath())));
        assertEquals(1, lookups.size());

        // Promote task A to project A
        branchMergeService.mergeBranchSync(taskA.getPath(), projectA.getPath(), Collections.emptyList());
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(projectA.getPath())));
        assertEquals(2, lookups.size());
        // EXCLUDE on project A as change made on MAIN originally
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getChangesOnBranchCriteria(branchService.findLatest(projectA.getPath())));
        assertEquals(1, lookups.size());
        lookups.forEach(lookup -> {
            assertEquals(1, lookup.getTotal());
            assertEquals(Set.of(200001L), lookup.getConceptIds());
            assertEquals(EXCLUDE, lookup.getType());
        });
        // Rebase task B from project A
        branchMergeService.mergeBranchSync(projectA.getPath(), taskB.getPath(), Collections.emptyList());
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(taskB.getPath())));
        assertEquals(3, lookups.size());
        lookups.forEach(lookup -> {
            if (lookup.getPath().equals(projectA.getPath())) {
                assertEquals(1, lookup.getTotal());
                assertEquals(Set.of(200001L), lookup.getConceptIds());
                assertEquals(EXCLUDE, lookup.getType());
            } else if (lookup.getPath().equals(taskB.getPath())) {
                assertEquals(1, lookup.getTotal());
                assertEquals(Set.of(200002L), lookup.getConceptIds());
                assertEquals(EXCLUDE, lookup.getType());
            } else {
                assertEquals("MAIN", lookup.getPath());
                assertEquals(2, lookup.getTotal());
                assertEquals(Set.of(200001L, 200002L), lookup.getConceptIds());
                assertEquals(INCLUDE, lookup.getType());
            }
        });
    }

    @Test
    void testUpdateLookupsDuringRebase() throws ServiceException {
        // Add refset changes on a task A to create lookups first
        createMembers(taskA.getPath(), REFSET_SIMPLE, List.of("200001", "200002"));
        // Check lookups created
        List<ReferencedConceptsLookup> lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(taskA.getPath())));
        assertEquals(1, lookups.size());

        // Add refset changes on project A
        createMembers(projectA.getPath(), REFSET_SIMPLE, List.of("200003", "200004"));
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(projectA.getPath())));
        assertEquals(1, lookups.size());

        // Rebase task A from project A
        branchMergeService.mergeBranchSync(projectA.getPath(), taskA.getPath(), Collections.emptyList());
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(taskA.getPath())));
        assertEquals(2, lookups.size());
        lookups.forEach(lookup -> {
            if (lookup.getPath().equals(taskA.getPath())) {
                assertEquals(2, lookup.getTotal());
                assertEquals(Set.of(200001L, 200002L), lookup.getConceptIds());
            } else {
                assertEquals(2, lookup.getTotal());
                assertEquals(Set.of(200003L, 200004L), lookup.getConceptIds());
            }
        });
    }


    @Test
    void testUpdateLookupsDuringPromotionAndRebase() throws ServiceException {
        // Add refset changes on a task A to create lookups first
        createMembers(taskA.getPath(), REFSET_SIMPLE, List.of("200001", "200002"));
        // Check lookups created
        List<ReferencedConceptsLookup> lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(taskA.getPath())));
        assertEquals(1, lookups.size());

        // Add a refset change on project A and no lookups created yet
        createMembers(projectA.getPath(), REFSET_SIMPLE, List.of("200003", "200004"));
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(projectA.getPath())));
        assertEquals(1, lookups.size());

        // Rebase task A from project A
        branchMergeService.mergeBranchSync(projectA.getPath(), taskA.getPath(), Collections.emptyList());
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(taskA.getPath())));
        assertEquals(2, lookups.size());

        // Promote task A to Project A
        branchMergeService.mergeBranchSync(taskA.getPath(), projectA.getPath(), Collections.emptyList());
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(projectA.getPath())));
        lookups.forEach(System.out::println);
        assertEquals(1, lookups.size());

        // Add two more refset changes on project A
        createMembers(projectA.getPath(), REFSET_SIMPLE, List.of("200005", "200006"));

        // Rebase task A from project A
        branchMergeService.mergeBranchSync(projectA.getPath(), taskA.getPath(), Collections.emptyList());
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(taskA.getPath())));
        assertEquals(1, lookups.size());
        assertEquals(6, lookups.get(0).getTotal());
        assertEquals(Set.of(200001L, 200002L, 200003L, 200004L, 200005L, 200006L), lookups.get(0).getConceptIds());
        assertEquals(projectA.getPath(), lookups.get(0).getPath());
    }
    @Test
    void testUpdateLookupsOnCodeSystemBranch() throws ServiceException {
        // Add refset changes on MAIN
        CodeSystem main = new CodeSystem("SNOMEDCT", "MAIN");
        codeSystemService.createCodeSystem(main);
        createMembers(MAIN, REFSET_SIMPLE, List.of("200001", "200002"));
        List<ReferencedConceptsLookup> lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(MAIN)));
        assertEquals(1, lookups.size());
        codeSystemService.createVersion(main, 20250101, "20250101 release");

        // Create a code system branch
        CodeSystem extension = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-AU", "MAIN/SNOMEDCT-AU"));
        conceptService.create(new Concept("609331003").addRelationship(Concepts.ISA, REFSET_SIMPLE), extension.getBranchPath());
        createMembers(extension.getBranchPath(), REFSET_SIMPLE, List.of("200003", "200004"));
        createMembers(extension.getBranchPath(), "609331003", List.of("200005", "200006"));
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(extension.getBranchPath())));
        // 1 for MAIN and 2 for AU
        assertEquals(3, lookups.size());
        assertEquals(Set.of("446609009", "609331003"), lookups.stream().map(ReferencedConceptsLookup::getRefsetId).collect(Collectors.toSet()));
        // Check lookups for AU only for changes in AU code system branch only
        lookups.stream().filter(lookup -> lookup.getPath().equals("MAIN/SNOMEDCT-AU")).forEach(lookup -> {
            if (lookup.getRefsetId().equals("446609009")) {
                assertEquals(2, lookup.getTotal());
                assertEquals(Set.of(200003L, 200004L), lookup.getConceptIds());
            } else {
                assertEquals(2, lookup.getTotal());
                assertEquals(Set.of(200005L, 200006L), lookup.getConceptIds());
            }
        });

        // Add more members and version MAIN
        createMembers(MAIN, REFSET_SIMPLE, List.of("200007", "200008"));
        codeSystemService.createVersion(main, 20250201, "20250201 release");
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(MAIN)));
        assertEquals(1, lookups.size());
        assertEquals(4, lookups.get(0).getTotal());
        assertEquals(Set.of(200001L, 200002L, 200007L, 200008L), lookups.get(0).getConceptIds());

        // Upgrade extension from MAIN
        codeSystemUpgradeService.upgrade("upgrade_testing", extension,20250201, true);

        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(extension.getBranchPath())));
        assertEquals(3, lookups.size());
        assertEquals(Set.of("446609009", "609331003"), lookups.stream().map(ReferencedConceptsLookup::getRefsetId).collect(Collectors.toSet()));
        lookups.forEach(lookup -> {
            if (lookup.getRefsetId().equals("446609009")) {
                if (lookup.getPath().equals(MAIN)) {
                    assertEquals(4, lookup.getTotal());
                    assertEquals(Set.of(200001L, 200002L, 200007L, 200008L), lookup.getConceptIds());
                } else {
                    assertEquals(2, lookup.getTotal());
                    assertEquals(Set.of(200003L, 200004L), lookup.getConceptIds());
                }
            } else {
                if (lookup.getPath().equals(MAIN)) {
                    assertEquals(0, lookup.getTotal());
                } else {
                    assertEquals(2, lookup.getTotal());
                    assertEquals(Set.of(200005L, 200006L), lookup.getConceptIds());
                }
            }
        });
    }

    @Test
    void testExtensionUpgrade() throws ServiceException {
        // Create an extension code system branch
        CodeSystem main = new CodeSystem("SNOMEDCT", "MAIN");
        codeSystemService.createCodeSystem(main);

        CodeSystem extension = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-US", "MAIN/SNOMEDCT-US"));
        createMembers(extension.getBranchPath(), LATERALIZABLE_BODY_STRUCTURE_REFERENCE_SET, List.of("200003"));
        // Version extension
        codeSystemService.createVersion(extension, 20241201, "20241201 release");

        // Add members to MAIN to generate concept lookup and version
        createMembers(MAIN, LATERALIZABLE_BODY_STRUCTURE_REFERENCE_SET, List.of("200001", "200002"));
        List<ReferencedConceptsLookup> lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(MAIN)));
        assertEquals(1, lookups.size());
        codeSystemService.createVersion(main, 20250101, "20250101 release");

        // Check lookups before upgrade
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(extension.getBranchPath())));
        assertEquals(0, lookups.size());
        // Upgrade extension to 20250101 release
        codeSystemUpgradeService.upgrade("upgrade_testing", extension,20250101, true);

        // Check lookups after upgrade
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(extension.getBranchPath())));
        assertEquals(2, lookups.size());
        assertEquals(Set.of(LATERALIZABLE_BODY_STRUCTURE_REFERENCE_SET), lookups.stream().map(ReferencedConceptsLookup::getRefsetId).collect(Collectors.toSet()));
        lookups.forEach(lookup -> {
            if (lookup.getPath().equals(MAIN)) {
                assertEquals(2, lookup.getTotal());
                assertEquals(Set.of(200001L, 200002L), lookup.getConceptIds());
            } else {
                assertEquals(1, lookup.getTotal());
                assertEquals(Set.of(200003L), lookup.getConceptIds());
            }
        });
    }

    @Test
    void testRebuildDryRun() {
        Map<String, Integer> updateCount = conceptsLookupUpdateService.rebuild("MAIN", null, false, true);
        assertEquals(0, updateCount.size());
        Branch branch = branchService.findLatest("MAIN");
        assertFalse(branch.isLocked());
        assertNull(branch.getMetadata().getMap("lock"));
    }

    @Test
    void testRebuildWhenThresholdCheckingIsDisabled() {
        // Add one refset member on MAIN
        createMembers("MAIN", REFSET_SIMPLE, List.of("200001"));
       // Check no concepts lookup created
       List<ReferencedConceptsLookup> conceptsLookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest("MAIN")));
       assertTrue(conceptsLookups.isEmpty());

        Map<String, Integer> updateCount = conceptsLookupUpdateService.rebuild("MAIN", null, true, false);
       assertEquals(1, updateCount.size());
    }

    @Test
    void testMultiplePromotions() throws ServiceException {
        // Add new refset changes on task A and promote to project A
        Set<ReferenceSetMember> members = createMembers(taskA.getPath(), REFSET_SIMPLE, List.of("200001", "200002"));
        branchMergeService.mergeBranchSync(taskA.getPath(), projectA.getPath(), Collections.emptyList());
        // Promote project A to MAIN
        branchMergeService.mergeBranchSync(projectA.getPath(), "MAIN", Collections.emptyList());
        CodeSystem main = new CodeSystem("SNOMEDCT", "MAIN");
        codeSystemService.createVersion(main, 20250201, "20250201 release");
        // Rebase project A
        branchMergeService.mergeBranchSync("MAIN", projectA.getPath(), Collections.emptyList());

        // Add new refset changes on task B and promote to project A
        Branch taskB = branchService.create("MAIN/projectA/taskB");
        createMembers(taskB.getPath(), REFSET_SIMPLE, List.of("200003", "200004"));
        branchMergeService.mergeBranchSync(taskB.getPath(), projectA.getPath(), Collections.emptyList());

        // Add inactivation refset changes on task C and promote to project A
        Branch taskC = branchService.create("MAIN/projectA/taskC");
        members.forEach(member -> {
            member.setActive(false);
            member.setPath("MAIN/projectA/taskC");
        });


        try (Commit commit = branchService.openCommit(taskC.getPath(), "Inactivate members")) {
            refsetMemberService.doSaveBatchMembers(members, commit);
            commit.markSuccessful();
        }
        taskC = branchService.findLatest(taskC.getPath());
        branchMergeService.mergeBranchSync(taskC.getPath(), projectA.getPath(), Collections.emptyList());

        // Check lookups on project A
        List<ReferencedConceptsLookup> lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(projectA.getPath())));
        projectA = branchService.findLatest(projectA.getPath());
        assertEquals(3, lookups.size());
        lookups.forEach(lookup -> {
            if (lookup.getPath().equals("MAIN")) {
                assertEquals(2, lookup.getTotal());
                assertEquals(Set.of(200001L, 200002L), lookup.getConceptIds());
            } else {
                if (lookup.getType() == EXCLUDE) {
                    // Check the start date is the same as the latest from project A
                    assertEquals(lookup.getStart(), projectA.getStart());
                    assertEquals(2, lookup.getTotal());
                    assertEquals(Set.of(200001L, 200002L), lookup.getConceptIds());
                } else {
                    // check the start date hasn't been updated
                    assertNotEquals(lookup.getStart(), projectA.getStart());
                    assertEquals(2, lookup.getTotal());
                    assertEquals(Set.of(200003L, 200004L), lookup.getConceptIds());
                }
            }
        });
    }

    @Test
    void testPromotionWithBothTypesForSameConcepts() throws ServiceException {
        // Add new refset changes on task A and promote to project A
        Set<ReferenceSetMember> members = createMembers(taskA.getPath(), REFSET_SIMPLE, List.of("200001", "200002"));
        branchMergeService.mergeBranchSync(taskA.getPath(), projectA.getPath(), Collections.emptyList());
        // Promote project A to MAIN
        branchMergeService.mergeBranchSync(projectA.getPath(), "MAIN", Collections.emptyList());
        CodeSystem main = new CodeSystem("SNOMEDCT", "MAIN");
        codeSystemService.createVersion(main, 20250201, "20250201 release");
        // Rebase project A
        branchMergeService.mergeBranchSync("MAIN", projectA.getPath(), Collections.emptyList());

        // Add new refset changes on task B and promote to project A
        Branch taskB = branchService.create("MAIN/projectA/taskB");
        Set<ReferenceSetMember> unpublished = createMembers(taskB.getPath(), REFSET_SIMPLE, List.of("200003", "200004"));
        branchMergeService.mergeBranchSync(taskB.getPath(), projectA.getPath(), Collections.emptyList());

        // Add inactivation refset changes on task C and promote to project A
        Branch taskC = branchService.create("MAIN/projectA/taskC");
        members.forEach(member -> {
            member.setActive(false);
            member.setPath("MAIN/projectA/taskC");
        });
        try (Commit commit = branchService.openCommit(taskC.getPath(), "Inactivate members")) {
            refsetMemberService.doSaveBatchMembers(members, commit);
            commit.markSuccessful();
        }
        taskC = branchService.findLatest(taskC.getPath());
        branchMergeService.mergeBranchSync(taskC.getPath(), projectA.getPath(), Collections.emptyList());

        // Add inactivation refset changes on task D and promote to project A
        Branch taskD = branchService.create("MAIN/projectA/taskD");
        unpublished.forEach(member -> {
            member.setActive(false);
            member.setPath("MAIN/projectA/taskD");
        });
        try (Commit commit = branchService.openCommit(taskD.getPath(), "Delete members")) {
            refsetMemberService.doSaveBatchMembers(unpublished, commit);
            commit.markSuccessful();
        }
        // Promote task D to project A
        branchMergeService.mergeBranchSync(taskD.getPath(), projectA.getPath(), Collections.emptyList());

        // Check lookups on project A
        List<ReferencedConceptsLookup> lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(projectA.getPath())));
        assertEquals(2, lookups.size());
        lookups.forEach(lookup -> {
            if (lookup.getPath().equals("MAIN")) {
                assertEquals(2, lookup.getTotal());
                assertEquals(Set.of(200001L, 200002L), lookup.getConceptIds());
                assertEquals(INCLUDE, lookup.getType());
            } else {
                assertEquals(2, lookup.getTotal());
                assertEquals(Set.of(200001L, 200002L), lookup.getConceptIds());
                assertEquals(EXCLUDE, lookup.getType());
            }
        });

        // Promote project A to MAIN
        branchMergeService.mergeBranchSync(projectA.getPath(), "MAIN", Collections.emptyList());
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest("MAIN")));
        // There shouldn't be any lookups on MAIN as the changes are inactivated
        assertEquals(0, lookups.size());
    }

    @Test
    void testPromotionSameChangesFromDifferentTasks() throws ServiceException {
        // Add new refset changes on task A and promote to project A
        Set<ReferenceSetMember> members = createMembers(taskA.getPath(), REFSET_SIMPLE, List.of("200001", "200002"));
        branchMergeService.mergeBranchSync(taskA.getPath(), projectA.getPath(), Collections.emptyList());
        // Promote project A to MAIN
        branchMergeService.mergeBranchSync(projectA.getPath(), "MAIN", Collections.emptyList());
        CodeSystem main = new CodeSystem("SNOMEDCT", "MAIN");
        codeSystemService.createVersion(main, 20250201, "20250201 release");

        // Add same inactivation changes on task B and task C
        Branch taskB = branchService.create("MAIN/projectA/taskB");

        members.forEach(member -> {
            member.setActive(false);
            member.setPath("MAIN/projectA/taskB");
        });

        Branch taskC = branchService.create("MAIN/projectA/taskC");
        Set<ReferenceSetMember> taskCMembers = new HashSet<>(members);
        taskCMembers.forEach(member -> {
            member.setActive(false);
            member.setPath("MAIN/projectA/taskC");
        });

        try (Commit commit = branchService.openCommit(taskB.getPath(), "Inactivate members")) {
            refsetMemberService.doSaveBatchMembers(members, commit);
            commit.markSuccessful();
        }
        branchMergeService.mergeBranchSync(taskB.getPath(), projectA.getPath(), Collections.emptyList());

        // Check lookups on project A
        List<ReferencedConceptsLookup> lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(projectA.getPath())));
        assertEquals(2, lookups.size());
        lookups.forEach(lookup -> {
            if (lookup.getPath().equals("MAIN")) {
                assertEquals(INCLUDE, lookup.getType());
                assertEquals(2, lookup.getTotal());
                assertEquals(Set.of(200001L, 200002L), lookup.getConceptIds());
            } else {
                assertEquals(EXCLUDE, lookup.getType());
                assertEquals(2, lookup.getTotal());
                assertEquals(Set.of(200001L, 200002L), lookup.getConceptIds());
            }
        });

        // Rebase TaskC from project A
        branchMergeService.mergeBranchSync(projectA.getPath(), taskC.getPath(), Collections.emptyList());
        // Check lookups on task C
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(taskC.getPath())));
        assertEquals(2, lookups.size());
        try (Commit commit = branchService.openCommit(taskB.getPath(), "Inactivate members")) {
            refsetMemberService.doSaveBatchMembers(members, commit);
            commit.markSuccessful();
        }
        branchMergeService.mergeBranchSync(taskB.getPath(), projectA.getPath(), Collections.emptyList());
        // Check lookups on project A
        assertEquals(2, lookups.size());
        lookups.forEach(lookup -> {
            if (lookup.getPath().equals("MAIN")) {
                assertEquals(INCLUDE, lookup.getType());
                assertEquals(2, lookup.getTotal());
                assertEquals(Set.of(200001L, 200002L), lookup.getConceptIds());
            } else {
                assertEquals(EXCLUDE, lookup.getType());
                assertEquals(2, lookup.getTotal());
                assertEquals(Set.of(200001L, 200002L), lookup.getConceptIds());
            }
        });
    }

    @Test
    void testPromotionWithChangesForBothTypes() throws ServiceException {
        // Add new refset changes on task A and promote to project A
        Set<ReferenceSetMember> members = createMembers(taskA.getPath(), REFSET_SIMPLE, List.of("200001", "200002"));
        // Promote task A to Project A
        branchMergeService.mergeBranchSync(taskA.getPath(), projectA.getPath(), Collections.emptyList());
        // Add two more and two deletion changes in task B
        Branch taskB = branchService.create("MAIN/projectA/taskB");
        createMembers(taskB.getPath(), REFSET_SIMPLE, List.of("200003", "200004"));
        taskB = branchService.findLatest(taskB.getPath());
        members.forEach(member -> {
            member.setActive(false);
            member.setPath("MAIN/projectA/taskB");
        });
        try (Commit commit = branchService.openCommit(taskB.getPath(), "Inactivate members")) {
            refsetMemberService.doSaveBatchMembers(members, commit);
            commit.markSuccessful();
        }
        List<ReferencedConceptsLookup> lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(taskB.getPath())));
        assertEquals(3, lookups.size());

        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(projectA.getPath())));
        assertEquals(1, lookups.size());
        // Promote task B to Project A
        taskB = branchService.findLatest(taskB.getPath());
        branchMergeService.mergeBranchSync(taskB.getPath(), projectA.getPath(), Collections.emptyList());
        // Check lookups on project A
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(projectA.getPath())));
        assertEquals(1, lookups.size());
        lookups.forEach(lookup -> {
            assertEquals(INCLUDE, lookup.getType());
            assertEquals(2, lookup.getTotal());
            assertEquals(Set.of(200003L, 200004L), lookup.getConceptIds());
        });
    }

    @Test
    void testRollback() {
        // Add new refset changes on task A
        createMembers(taskA.getPath(), REFSET_SIMPLE, List.of("200001", "200002"));
        taskA = branchService.findLatest(taskA.getPath());
        sBranchService.rollbackCommit(taskA.getPath(),  taskA.getHeadTimestamp());
        List<ReferencedConceptsLookup> lookups = referencedConceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(taskA.getPath()));
        assertEquals(0, lookups.size());
        Iterable<ReferencedConceptsLookup> results = referencedConceptsLookupRepository.findAll();
        assertFalse(results.iterator().hasNext());
    }

    private Set<ReferenceSetMember> createMembers(String branchPath, String refsetId, List<String> referencedConcepts) {
        Set<ReferenceSetMember> members = new HashSet<>();
        for (String conceptId : referencedConcepts) {
            ReferenceSetMember member = new ReferenceSetMember();
            member.setMemberId(UUID.randomUUID().toString());
            member.setRefsetId(refsetId);
            member.setReferencedComponentId(conceptId);
            member.setModuleId(Concepts.CORE_MODULE);
            member.setPath(branchPath);
            member.markChanged();
            members.add(member);
        }
        refsetMemberService.createMembers(branchPath, members);
        return members;
    }
    @Test
    void testBypassThresholdCheckingForRefsetIdHavingExistingLookups() {
        // Create a parent code system and add lookups
        CodeSystem main = new CodeSystem("SNOMEDCT", "MAIN");
        codeSystemService.createCodeSystem(main);
        createMembers(MAIN, REFSET_SIMPLE, List.of("200001", "200002"));
        List<ReferencedConceptsLookup> conceptsLookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(MAIN)));
        assertEquals(1, conceptsLookups.size());

        // Create an extension branch
        CodeSystem extension = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-AU", "MAIN/SNOMEDCT-AU"));
        // Threshold is set to 2 in application-test.properties
        createMembers(extension.getBranchPath(), REFSET_SIMPLE, List.of("200003"));

        // Verify that the threshold check was bypassed and the lookup was built
        List<ReferencedConceptsLookup> extensionLookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getChangesOnBranchCriteria(branchService.findLatest(extension.getBranchPath())));
        assertEquals(1, extensionLookups.size());
        assertEquals(1, extensionLookups.get(0).getTotal());
    }
    @Test
    void testSkipRebuildingWhenNoLookupsFoundInParentBranch() {
        // Create a parent code system without lookups for the refset ID
        CodeSystem main = new CodeSystem("SNOMEDCT", "MAIN");
        codeSystemService.createCodeSystem(main);
        // Add one member below threshold of 2 and no concepts look will be created
        createMembers(main.getBranchPath(), REFSET_SIMPLE, List.of("200001"));

        // Create an extension branch
        CodeSystem extension = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-AU", "MAIN/SNOMEDCT-AU"));
        // Even it is over the threshold of 2 but due to parent code system hasn't generated lookups, it should not be rebuilt on extension branch either
        createMembers(extension.getBranchPath(), REFSET_SIMPLE, List.of("200003","200004"));

        // Verify that the lookup was not built
        List<ReferencedConceptsLookup> extensionLookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(extension.getBranchPath())));
        assertEquals(0, extensionLookups.size());
    }

    @Test
    void testRebuildWhenAddingRefsetMembersForTheFirstTime() {
        // Create a parent code system without lookups for the refset ID
        CodeSystem main = new CodeSystem("SNOMEDCT", "MAIN");
        codeSystemService.createCodeSystem(main);

        // Create an extension branch
        codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-AU", "MAIN/SNOMEDCT-AU"));
        Branch extensionProject = branchService.create("MAIN/SNOMEDCT-AU/ProjectA");
        createMembers(extensionProject.getPath(), REFSET_SIMPLE, List.of("200003","200004"));

        // Verify that the lookup was not built
        List<ReferencedConceptsLookup> extensionLookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(extensionProject.getPath())));
        assertEquals(1, extensionLookups.size());
    }

    @Test
    void testRebuildingInExtensionForExistingRefsetMembers() throws ServiceException {
        // Create a parent code system without lookups for the refset ID
        CodeSystem main = new CodeSystem("SNOMEDCT", "MAIN");
        codeSystemService.createCodeSystem(main);
        // Add one member below threshold of 2 and no concepts look will be created
        createMembers(main.getBranchPath(), REFSET_SIMPLE, List.of("200001"));

        // Create an extension branch
        CodeSystem extension = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-AU", "MAIN/SNOMEDCT-AU"));
        // Even it is over the threshold of 2 but due to parent code system hasn't generated lookups, it should not be rebuilt on extension branch either
        createMembers(extension.getBranchPath(), REFSET_SIMPLE, List.of("200003", "200004"));

        // Verify that the lookup was not built
        List<ReferencedConceptsLookup> conceptsLookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(extension.getBranchPath())));
        assertEquals(0, conceptsLookups.size());

        // Rebuild on MAIN to skip threshold checking
        conceptsLookupUpdateService.rebuild(main.getBranchPath(), List.of(Long.valueOf(REFSET_SIMPLE)), true, false);
        conceptsLookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(main.getBranchPath())));
        assertEquals(1, conceptsLookups.size());

        // Rebase on extension branch to verify that the lookup is rebuilt again
        branchMergeService.mergeBranchSync(main.getBranchPath(), extension.getBranchPath(), Collections.emptyList());
        conceptsLookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(extension.getBranchPath())));
        assertEquals(2, conceptsLookups.size());
    }

    @Test
    void testDuplicateRefsetMembersInactivation() throws ServiceException {
        // Create two duplicate refset members with different member IDs
        CodeSystem main = new CodeSystem("SNOMEDCT", "MAIN");
        codeSystemService.createCodeSystem(main);
        createMembers(MAIN, REFSET_SIMPLE, List.of("200001", "200001", "200002"));
        long total = refsetMemberService.findMembers(MAIN, "200001", PageRequest.ofSize(10)).getTotalElements();
        assertEquals(2L, total);

        // Verify that the lookup remains accurate
        List<ReferencedConceptsLookup>  lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getChangesOnBranchCriteria(branchService.findLatest(MAIN)));
        assertEquals(1, lookups.size());
        assertEquals(2, lookups.get(0).getTotal());
        assertEquals(Set.of(200001L, 200002L), lookups.get(0).getConceptIds());

        // Publish
        codeSystemService.createVersion(main,20250401, "20250401 release");

        // Rebase project and task
        branchMergeService.mergeBranchSync("MAIN", projectA.getPath(), Collections.emptyList());
        branchMergeService.mergeBranchSync(projectA.getPath(), taskA.getPath(), Collections.emptyList());

        // Inactivate one of the members
        List<ReferenceSetMember> members = refsetMemberService.findMembers(taskA.getPath(), "200001", PageRequest.ofSize(10)).stream().toList();
        assertEquals(2, members.size());
        ReferenceSetMember duplicate = members.stream().filter(m -> "200001".equals(m.getReferencedComponentId())).findFirst().orElseThrow();
        duplicate.setActive(false);
        duplicate.setPath(taskA.getPath());
        refsetMemberService.updateMember(taskA.getPath(), duplicate);

        members = refsetMemberService.findMembers(taskA.getPath(), "200001", PageRequest.ofSize(10)).stream().toList();
        assertEquals(2, members.size());

        // Verify that no lookup is generated
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getChangesOnBranchCriteria(branchService.findLatest(taskA.getPath())));
        assertEquals(0, lookups.size());

        // Promote task to project
        branchMergeService.mergeBranchSync(taskA.getPath(), projectA.getPath(), Collections.emptyList());
        // Promote project to MAIN
        branchMergeService.mergeBranchSync(projectA.getPath(), main.getBranchPath(), Collections.emptyList());

        // Verify that the lookup remains accurate
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(MAIN)));
        assertEquals(1, lookups.size());
        assertEquals(2, lookups.get(0).getTotal());
        assertEquals(Set.of(200001L, 200002L), lookups.get(0).getConceptIds());
    }


    @Test
    void testDuplicateRefsetMembersDeletion() throws ServiceException {
        // Create two duplicate refset members with different member IDs
        CodeSystem main = new CodeSystem("SNOMEDCT", "MAIN");
        codeSystemService.createCodeSystem(main);
        createMembers(MAIN, REFSET_SIMPLE, List.of("200001", "200001", "200002"));
        long total = refsetMemberService.findMembers(MAIN, "200001", PageRequest.ofSize(10)).getTotalElements();
        assertEquals(2L, total);

        // Verify that the lookup remains accurate
        List<ReferencedConceptsLookup>  lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getChangesOnBranchCriteria(branchService.findLatest(MAIN)));
        assertEquals(1, lookups.size());
        assertEquals(2, lookups.get(0).getTotal());
        assertEquals(Set.of(200001L, 200002L), lookups.get(0).getConceptIds());

        // Rebase project and task
        branchMergeService.mergeBranchSync("MAIN", projectA.getPath(), Collections.emptyList());
        branchMergeService.mergeBranchSync(projectA.getPath(), taskA.getPath(), Collections.emptyList());

        // Delete one of the members
        List<ReferenceSetMember> members = refsetMemberService.findMembers(taskA.getPath(), "200001", PageRequest.ofSize(10)).stream().toList();
        assertEquals(2, members.size());
        ReferenceSetMember duplicate = members.stream().filter(m -> "200001".equals(m.getReferencedComponentId())).findFirst().orElseThrow();
        refsetMemberService.deleteMember(taskA.getPath(), duplicate.getMemberId());

        members = refsetMemberService.findMembers(taskA.getPath(), "200001", PageRequest.ofSize(10)).stream().toList();
        assertEquals(1, members.size());

        // Verify that the exclude lookup is not generated
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getChangesOnBranchCriteria(branchService.findLatest(taskA.getPath())));
        assertEquals(0, lookups.size());

        // Promote task to project
        branchMergeService.mergeBranchSync(taskA.getPath(), projectA.getPath(), Collections.emptyList());
        // Promote project to MAIN
        branchMergeService.mergeBranchSync(projectA.getPath(), main.getBranchPath(), Collections.emptyList());

        // Verify that the lookup remains accurate
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(MAIN)));
        assertEquals(1, lookups.size());
        assertEquals(2, lookups.get(0).getTotal());
        assertEquals(Set.of(200001L, 200002L), lookups.get(0).getConceptIds());
    }
}