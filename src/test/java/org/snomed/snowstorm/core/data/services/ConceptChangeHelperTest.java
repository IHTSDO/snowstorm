package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.Collectors;

@ExtendWith(SpringExtension.class)
class ConceptChangeHelperTest extends AbstractTest {
    private static final Long[] EMPTY_ARRAY = new Long[]{};

    @Autowired
    private ConceptChangeHelper conceptChangeHelper;
    @Autowired
    private ConceptService conceptService;
    @Autowired
    private BranchService branchService;
    @Autowired
    private BranchMergeService mergeService;
    private Date setupStartTime;
    private Date setupEndTime;

    @BeforeEach
    void setUp() throws Exception {
        branchService.deleteAll();
        conceptService.deleteAll();

        branchService.create("MAIN");
        setupStartTime = now();
        createConcept("10000100", "MAIN");
        branchService.create("MAIN/A");
        setupEndTime = now();
    }

    private Date now() {
        return new Date();
    }

    private void createConcept(String conceptId, String path) throws ServiceException {
        conceptService.create(
                new Concept(conceptId)
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

    @Test
    void testCreateConceptChangeReportOnBranchSinceTimepoint() throws Exception {
        // Assert report contains one new concept on MAIN since start of setup
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange("MAIN", setupStartTime, now(), true), new Long[]{10000100L});

        // Assert report contains no concepts on MAIN/A since setup
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange("MAIN/A", setupEndTime, now(), false), EMPTY_ARRAY);

        final Date beforeSecondCreation = now();
        createConcept("10000200", "MAIN/A");

        // Assert report contains no new concepts on MAIN/A since start of setup
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange("MAIN/A", setupEndTime, now(), false), new Long[]{10000200L});

        // Assert report contains one new concept on MAIN/A since timeA
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange("MAIN/A", beforeSecondCreation, now(), false), new Long[]{10000200L});

        final Date beforeDeletion = now();

        // Delete concept 100 from MAIN
        conceptService.deleteConceptAndComponents("10000100", "MAIN", false);

        final Date afterDeletion = now();

        // Assert report contains one deleted concept on MAIN
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange("MAIN", beforeDeletion, now(), true), new Long[]{10000100L});


        // Assert report contains no deleted concepts on MAIN before the deletion
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange("MAIN", beforeSecondCreation, beforeDeletion, true), EMPTY_ARRAY);

        // Assert report contains no deleted concepts on MAIN after the deletion
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange("MAIN", afterDeletion, now(), true), EMPTY_ARRAY);
    }

    private void assertReportEquals(Set<Long> changedConcepts, Long[] expectedConceptsChanged) {
        List<Long> ids = new ArrayList<>(changedConcepts);
        ids.sort(null);
        System.out.println("changed " + Arrays.toString(ids.toArray()));
        Assertions.assertArrayEquals(expectedConceptsChanged, ids.toArray(), "Concepts Changed");
    }

    @Test
    void testDescriptionUpdateOnSameBranchInChangeReport() throws Exception {
        final String path = "MAIN";
        createConcept("10000200", path);
        createConcept("10000300", path);

        Date start = now();

        // Nothing changed since start
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange(path, start, now(), true), EMPTY_ARRAY);

        final Concept concept = conceptService.find("10000100", path);
        getDescription(concept, true).setCaseSignificanceId(Concepts.ENTIRE_TERM_CASE_SENSITIVE);
        conceptService.update(concept, path);

        // Concept updated from description change
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange(path, start, now(), true), new Long[]{10000100L});
    }

    private Description getDescription(Concept concept, boolean fetchFSN) {
        if (concept == null || concept.getDescriptions() == null || concept.getDescriptions().isEmpty()) {
            return null;
        }
        if (fetchFSN) {
            List<Description> descriptions = concept.getDescriptions().stream().filter(d -> Concepts.FSN.equals(d.getTypeId())).collect(Collectors.toList());
            if (descriptions.iterator().hasNext()) {
                return descriptions.iterator().next();
            }
        } else {
            List<Description> descriptions = concept.getDescriptions().stream().filter(d -> !Concepts.FSN.equals(d.getTypeId())).collect(Collectors.toList());
            if (descriptions.iterator().hasNext()) {
                return descriptions.iterator().next();
            }
        }
        return null;
    }

    @Test
    void testDescriptionUpdateOnChildBranchInChangeReport() throws Exception {
        final String path = "MAIN/A";
        createConcept("10000200", path);
        createConcept("10000300", path);

        Date start = now();

        // Nothing changed since start
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange(path, start, now(), false), EMPTY_ARRAY);

        final Concept concept = conceptService.find("10000100", path);
        final Description description = getDescription(concept, true);
        description.setCaseSignificanceId(Concepts.ENTIRE_TERM_CASE_SENSITIVE);
        conceptService.update(concept, path);

        // Concept updated from description change
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange(path, start, now(), false), new Long[]{10000100L});
    }

    @Test
    void testAxiomUpdateOnSameBranchInChangeReport() throws Exception {
        final String path = "MAIN";
        createConcept("10000200", path);
        createConcept("10000300", path);

        Date start = now();

        // Nothing changed since start
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange(path, start, now(), true), EMPTY_ARRAY);

        final Concept concept = conceptService.find("10000100", path);
        concept.getClassAxioms().iterator().next().setDefinitionStatusId(Concepts.FULLY_DEFINED);
        conceptService.update(concept, path);

        // Concept updated from axiom change
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange(path, start, now(), true), new Long[]{10000100L});
    }

    @Test
    void testAxiomUpdateOnSameBranchNotMAINInChangeReport() throws Exception {
        final String path = "MAIN/A";
        createConcept("10000200", path);
        createConcept("10000300", path);

        Date start = now();

        // Nothing changed since start
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange(path, start, now(), true), EMPTY_ARRAY);

        final Concept concept = conceptService.find("10000100", path);
        concept.getClassAxioms().iterator().next().setDefinitionStatusId(Concepts.FULLY_DEFINED);
        conceptService.update(concept, path);

        // Concept updated from axiom change
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange(path, start, now(), true), new Long[]{10000100L});
    }

    @Test
    void testAxiomUpdateOnGrandfatherBranchInChangeReport() throws Exception {
        branchService.create("MAIN/A/B");

        Date start = now();

        // Nothing changed since start
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange("MAIN/A/B", start, now(), true), EMPTY_ARRAY);

        final Concept concept = conceptService.find("10000100", "MAIN");
        concept.getClassAxioms().iterator().next().setDefinitionStatusId(Concepts.FULLY_DEFINED);
        conceptService.update(concept, "MAIN");

        mergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());

        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange("MAIN/A", start, now(), true), new Long[]{10000100L});
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange("MAIN/A/B", start, now(), true), EMPTY_ARRAY);

        mergeService.mergeBranchSync("MAIN/A", "MAIN/A/B", Collections.emptySet());

        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange("MAIN/A", start, now(), true), new Long[]{10000100L});
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange("MAIN/A/B", start, now(), true), new Long[]{10000100L});
    }

    @Test
    void testAxiomUpdateOnChildBranchInChangeReport() throws Exception {
        final String path = "MAIN/A";
        createConcept("10000200", path);
        createConcept("10000300", path);

        Date start = now();

        // Nothing changed since start
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange(path, start, now(), false), EMPTY_ARRAY);

        final Concept concept = conceptService.find("10000100", path);
        concept.getClassAxioms().iterator().next().setDefinitionStatusId(Concepts.FULLY_DEFINED);
        conceptService.update(concept, path);

        // Concept updated from axiom change
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange(path, start, now(), false), new Long[]{10000100L});
    }

    @Test
    void testLangRefsetUpdateOnChildBranchInChangeReport() throws Exception {
        final String path = "MAIN/A";

        Date start = now();

        // Nothing changed since start
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange(path, start, now(), false), EMPTY_ARRAY);

        final Concept concept = conceptService.find("10000100", path);
        final Description description = getDescription(concept, true);
        description.clearLanguageRefsetMembers();
        description.setAcceptabilityMap(Collections.singletonMap(Concepts.US_EN_LANG_REFSET,
                Concepts.descriptionAcceptabilityNames.get(Concepts.PREFERRED)));
        conceptService.update(concept, path);

        // Concept updated from lang refset change
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange(path, start, now(), false), new Long[]{10000100L});
    }

    @Test
    void testLangRefsetDeletionOnChildBranchInChangeReport() throws Exception {
        final String path = "MAIN/A";

        Date start = now();

        // Nothing changed since start
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange(path, start, now(), false), EMPTY_ARRAY);

        final Concept concept = conceptService.find("10000100", path);
        final Description description = getDescription(concept, true);
        description.clearLanguageRefsetMembers();
        description.setAcceptabilityMap(new HashMap<>());
        conceptService.update(concept, path);

        // Concept updated from lang refset change
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange(path, start, now(), false), new Long[]{10000100L});
    }

    @Test
    void testLangRefsetDeletionOnBranchInChangeReport() throws Exception {
        final String path = "MAIN";

        Date start = now();

        // Nothing changed since start
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange(path, start, now(), true), EMPTY_ARRAY);

        final Concept concept = conceptService.find("10000100", path);
        final Description next = getDescription(concept, true);
        next.clearLanguageRefsetMembers();
        next.setAcceptabilityMap(new HashMap<>());
        conceptService.update(concept, path);

        // Concept updated from lang refset change
        assertReportEquals(conceptChangeHelper.getConceptsChangedBetweenTimeRange(path, start, now(), true), new Long[]{10000100L});
    }
}
