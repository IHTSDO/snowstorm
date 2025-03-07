package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.ReferencedConceptsLookup;
import org.snomed.snowstorm.ecl.ReferencedConceptsLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.core.data.domain.ReferencedConceptsLookup.Type.EXCLUDE;
import static org.snomed.snowstorm.core.data.domain.ReferencedConceptsLookup.Type.INCLUDE;

@ExtendWith(SpringExtension.class)
class ReferencedConceptsLookupUpdateServiceTest extends AbstractTest {

    @Autowired
    private BranchService branchService;

    @Autowired
    private VersionControlHelper versionControlHelper;

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
    @BeforeEach
    void setUp() {
       projectA = branchService.create("MAIN/projectA");
       taskA = branchService.create("MAIN/projectA/taskA");
    }
    @Test
    void testUpdateLookupsDuringPromotion() throws ServiceException {
        // Add one refset change on a task A
        createMembers(taskA.getPath(), Concepts.REFSET_SIMPLE, Collections.singletonList("200001"));
        taskA = branchService.findLatest(taskA.getPath());
        List<ReferencedConceptsLookup> lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(taskA), false);
        // Check that the lookup is empty due to not reaching the threshold of 2
        assertEquals(0, lookups.size());
        // Add one more member
        createMembers(taskA.getPath(), Concepts.REFSET_SIMPLE, Collections.singletonList("200002"));
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(taskA.getPath())), true);
        assertEquals(1, lookups.size());
        assertEquals(2, lookups.get(0).getTotal());
        assertEquals(Concepts.REFSET_SIMPLE, lookups.get(0).getRefsetId());
        assertEquals(taskA.getPath(), lookups.get(0).getPath());
        assertEquals(Set.of(200001L, 200002L), lookups.get(0).getConceptIds());

        // Promote task A to Project A
        branchMergeService.mergeBranchSync(taskA.getPath(), projectA.getPath(), Collections.emptyList());
        projectA = branchService.findLatest(projectA.getPath());
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(projectA), true);
        assertEquals(1, lookups.size());
        assertEquals(2, lookups.get(0).getTotal());
        assertEquals(Concepts.REFSET_SIMPLE, lookups.get(0).getRefsetId());
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
        assertEquals(Concepts.REFSET_SIMPLE, lookups.get(0).getRefsetId());
        assertEquals("MAIN", lookups.get(0).getPath());
        assertEquals(Set.of(200001L, 200002L), lookups.get(0).getConceptIds());

        // Check that the lookup has been ended on project A after promotion
        lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(projectA.getPath())), false);
        assertEquals(1, lookups.size());
    }

    @Test
    void testPromotionsWithDeletion() throws ServiceException {
        // Add two refset changes on a project A
        Set<ReferenceSetMember> members = createMembers(projectA.getPath(), Concepts.REFSET_SIMPLE, List.of("200001", "200002"));
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
        Set<ReferenceSetMember> members = createMembers(projectA.getPath(), Concepts.REFSET_SIMPLE, List.of("200001", "200002"));
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
        Set<ReferenceSetMember> members = createMembers("MAIN", Concepts.REFSET_SIMPLE, List.of("200001", "200002"));
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
        createMembers(taskA.getPath(), Concepts.REFSET_SIMPLE, List.of("200001", "200002"));
        // Check lookups created
        List<ReferencedConceptsLookup> lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(taskA.getPath())));
        assertEquals(1, lookups.size());

        // Add refset changes on project A
        createMembers(projectA.getPath(), Concepts.REFSET_SIMPLE, List.of("200003", "200004"));
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
        createMembers(taskA.getPath(), Concepts.REFSET_SIMPLE, List.of("200001", "200002"));
        // Check lookups created
        List<ReferencedConceptsLookup> lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(taskA.getPath())));
        assertEquals(1, lookups.size());

        // Add a refset change on project A and no lookups created yet
        createMembers(projectA.getPath(), Concepts.REFSET_SIMPLE, List.of("200003", "200004"));
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
        createMembers(projectA.getPath(), Concepts.REFSET_SIMPLE, List.of("200005", "200006"));

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
        createMembers(MAIN, Concepts.REFSET_SIMPLE, List.of("200001", "200002"));
        List<ReferencedConceptsLookup> lookups = conceptsLookupService.getConceptsLookups(versionControlHelper.getBranchCriteria(branchService.findLatest(MAIN)));
        assertEquals(1, lookups.size());
        codeSystemService.createVersion(main, 20250101, "20250101 release");

        // Create a code system branch
        CodeSystem extension = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-AU", "MAIN/SNOMEDCT-AU"));
        createMembers(extension.getBranchPath(), Concepts.REFSET_SIMPLE, List.of("200003", "200004"));
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
        createMembers(MAIN, Concepts.REFSET_SIMPLE, List.of("200007", "200008"));
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
    void testRebuildDryRun() {
        Map<String, Integer> updateCount = conceptsLookupUpdateService.rebuild("MAIN", null, true);
        assertEquals(0, updateCount.size());
        Branch branch = branchService.findLatest("MAIN");
        assertFalse(branch.isLocked());
        assertNull(branch.getMetadata().getMap("lock"));
    }

    private Set<ReferenceSetMember> createMembers(String branchPath, String refsetId, List<String> referencedConcepts) {
        Set<ReferenceSetMember> members = new HashSet<>();
        for (String conceptId : referencedConcepts) {
            ReferenceSetMember member = new ReferenceSetMember();
            member.setRefsetId(refsetId);
            member.setReferencedComponentId(conceptId);
            member.setModuleId(Concepts.CORE_MODULE);
            member.setPath(branchPath);
            members.add(member);
        }
        try (Commit commit = branchService.openCommit(branchPath, "Testing")) {
            refsetMemberService.doSaveBatchMembers(members, commit);
            commit.markSuccessful();
        }
        refsetMemberService.createMembers(branchPath, members);
        return members;
    }
}