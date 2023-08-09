package org.snomed.snowstorm.core.data.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.pojo.BranchTimepoint;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.snomed.snowstorm.TestConcepts.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutoMergerTest {
    private static final String MODULE_A = "module-a";
    private static final String MODULE_B = "module-b";
    private static final String MODULE_C = "module-c";
    private static final String US_PREFERRED_MEMBER_ID = "ca4fc507-c3ab-4b81-a730-e484e59f94ba";
    private static final String GB_PREFERRED_MEMBER_ID = "70102c0a-4595-4427-a906-398d30d59ebc";
    private static final String AXIOM_ID = "0beed5e0-4681-43a1-b2c3-058e3a733d04";
    private static final Map<String, String> PREFERRED = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(Concepts.PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(Concepts.PREFERRED));
    private static final Map<String, String> ACCEPTABLE = Map.of(US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(Concepts.PREFERRED), GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(Concepts.ACCEPTABLE));

    @Mock
    private ReferenceSetMemberService referenceSetMemberService;

    @Mock
    private ConceptService conceptService;

    @InjectMocks
    private AutoMerger autoMerger;

    private final Concept concept = buildConcept();
    private final Concept sourceConcept = clone(concept); // The LHS Concept of the merge screen
    private final Concept targetConceptBefore = clone(concept); // The RHS Concept of the merge screen, before any authoring changes
    private final Concept targetConceptAfter = clone(concept); // The RHS Concept of the merge screen, after any authoring changes

    @BeforeEach
    public void beforeEach() {
        givenTargetConceptBeforeAuthoringChanges();
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetConceptChangesNothing() {
        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        // Concept
        assertEquals("100", result.getConceptId());
        assertEquals(20220131, result.getEffectiveTimeI());
        assertEquals(20220131, result.getReleasedEffectiveTime());
        assertTrue(result.isReleased());
        assertTrue(result.isActive());
        assertEquals(MODULE_A, result.getModuleId());
        assertEquals(FULLY_DEFINED, result.getDefinitionStatusId());
        assertEquals(sourceConcept.getReleaseHash(), result.getReleaseHash());

        // Description
        assertEquals(1, result.getDescriptions().size());
        assertEquals(20220131, result.getDescription("101").getEffectiveTimeI());
        assertEquals(20220131, result.getDescription("101").getReleasedEffectiveTime());
        assertTrue(result.getDescription("101").isReleased());
        assertTrue(result.getDescription("101").isActive());
        assertEquals(MODULE_A, result.getDescription("101").getModuleId());
        assertEquals("100", result.getDescription("101").getConceptId());
        assertEquals("Vehicle (physical object)", result.getDescription("101").getTerm());
        assertEquals("en", result.getDescription("101").getLanguageCode());
        assertEquals(FSN, result.getDescription("101").getTypeId());
        assertEquals(CASE_INSENSITIVE, result.getDescription("101").getCaseSignificanceId());

        // Relationship
        assertEquals(1, result.getRelationships().size());
        assertEquals(20220131, result.getRelationship("102").getEffectiveTimeI());
        assertEquals(20220131, result.getRelationship("102").getReleasedEffectiveTime());
        assertTrue(result.getRelationship("102").isReleased());
        assertTrue(result.getRelationship("102").isActive());
        assertEquals(0, result.getRelationship("102").getRelationshipGroup());
        assertEquals(MODULE_A, result.getRelationship("102").getModuleId());
        assertEquals("100", result.getRelationship("102").getSourceId());
        assertEquals(PHYSICAL_OBJECT, result.getRelationship("102").getDestinationId());
        assertEquals(ISA, result.getRelationship("102").getTypeId());
        assertEquals(INFERRED_RELATIONSHIP, result.getRelationship("102").getCharacteristicTypeId());
        assertEquals(EXISTENTIAL, result.getRelationship("102").getModifierId());

        // Language Reference Set Member (US Preferred)
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getEffectiveTimeI());
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleasedEffectiveTime());
        assertEquals(
                sourceConcept.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleaseHash(),
                result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleaseHash()
        );
        assertTrue(result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).isReleased());
        assertTrue(result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).isActive());
        assertEquals(MODULE_A, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getModuleId());
        assertEquals(US_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReferencedComponentId());
        assertEquals(Concepts.PREFERRED, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));

        // Language Reference Set Member (GB Preferred)
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getEffectiveTimeI());
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleasedEffectiveTime());
        assertEquals(
                sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleaseHash(),
                result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleaseHash()
        );
        assertTrue(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).isReleased());
        assertTrue(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).isActive());
        assertEquals(MODULE_A, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getModuleId());
        assertEquals(GB_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReferencedComponentId());
        assertEquals(Concepts.PREFERRED, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));

        // Class Axioms
        assertEquals(AXIOM_ID, result.getClassAxioms().iterator().next().getAxiomId());
        assertTrue(result.getClassAxioms().iterator().next().isActive());
        assertTrue(result.getClassAxioms().iterator().next().isReleased());
        assertEquals(FULLY_DEFINED, result.getClassAxioms().iterator().next().getDefinitionStatusId());
        assertEquals(MODULE_A, result.getClassAxioms().iterator().next().getModuleId());
        assertEquals(1, result.getClassAxioms().iterator().next().getRelationships().size());

        assertEquals(AXIOM_ID, result.getClassAxioms().iterator().next().getReferenceSetMember().getMemberId());
        assertTrue(result.getClassAxioms().iterator().next().getReferenceSetMember().isActive());
        assertEquals(MODULE_A, result.getClassAxioms().iterator().next().getReferenceSetMember().getModuleId());
        assertEquals(20220131, result.getClassAxioms().iterator().next().getReferenceSetMember().getReleasedEffectiveTime());
        assertEquals(20220131, result.getClassAxioms().iterator().next().getReferenceSetMember().getEffectiveTimeI());
        assertTrue(result.getClassAxioms().iterator().next().getReferenceSetMember().isReleased());
        assertEquals(
                sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().getReleaseHash(),
                result.getClassAxioms().iterator().next().getReferenceSetMember().getReleaseHash()
        );
        assertEquals(Concepts.OWL_AXIOM_REFERENCE_SET, result.getClassAxioms().iterator().next().getReferenceSetMember().getRefsetId());
        assertEquals("101", result.getClassAxioms().iterator().next().getReferenceSetMember().getReferencedComponentId());
        assertEquals(
                sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().getAdditionalFields(),
                result.getClassAxioms().iterator().next().getReferenceSetMember().getAdditionalFields()
        );
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetConceptChangesActive() {
        // Create conflicting authoring changes
        sourceConcept.setModuleId(MODULE_B);
        sourceConcept.updateEffectiveTime();

        targetConceptAfter.setActive(false);
        targetConceptAfter.updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals("100", result.getConceptId());
        assertNull(result.getEffectiveTimeI());
        assertEquals(20220131, result.getReleasedEffectiveTime());
        assertTrue(result.isReleased());
        assertFalse(result.isActive());
        assertEquals(MODULE_B, result.getModuleId());
        assertEquals(FULLY_DEFINED, result.getDefinitionStatusId());
        assertEquals(sourceConcept.getReleaseHash(), result.getReleaseHash());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetConceptChangesModule() {
        // Create conflicting authoring changes
        sourceConcept.setActive(false);
        sourceConcept.updateEffectiveTime();

        targetConceptAfter.setModuleId(MODULE_B);
        targetConceptAfter.updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals("100", result.getConceptId());
        assertNull(result.getEffectiveTimeI());
        assertEquals(20220131, result.getReleasedEffectiveTime());
        assertTrue(result.isReleased());
        assertFalse(result.isActive());
        assertEquals(MODULE_B, result.getModuleId());
        assertEquals(FULLY_DEFINED, result.getDefinitionStatusId());
        assertEquals(sourceConcept.getReleaseHash(), result.getReleaseHash());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetConceptChangesDefinitionStatus() {
        // Create conflicting authoring changes
        sourceConcept.setModuleId(MODULE_B);
        sourceConcept.updateEffectiveTime();

        targetConceptAfter.setDefinitionStatusId(PRIMITIVE);
        targetConceptAfter.updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals("100", result.getConceptId());
        assertNull(result.getEffectiveTimeI());
        assertEquals(20220131, result.getReleasedEffectiveTime());
        assertTrue(result.isReleased());
        assertTrue(result.isActive());
        assertEquals(MODULE_B, result.getModuleId());
        assertEquals(PRIMITIVE, result.getDefinitionStatusId());
        assertEquals(sourceConcept.getReleaseHash(), result.getReleaseHash());
    }

    @Test
    void autoMerge_ShouldFavourTargetConceptChanges() {
        // Create conflicting authoring changes
        sourceConcept.setModuleId(MODULE_B);
        sourceConcept.updateEffectiveTime();

        sourceConcept.setModuleId(MODULE_C);
        targetConceptAfter.updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals("100", result.getConceptId());
        assertNull(result.getEffectiveTimeI());
        assertEquals(20220131, result.getReleasedEffectiveTime());
        assertTrue(result.isReleased());
        assertTrue(result.isActive());
        assertEquals(MODULE_C, result.getModuleId());
        assertEquals(FULLY_DEFINED, result.getDefinitionStatusId());
        assertEquals(sourceConcept.getReleaseHash(), result.getReleaseHash());
    }

    @Test
    void autoMerge_ShouldUpgradeAndReapplyConcept() {
        // Create conflicting authoring changes
        sourceConcept.setModuleId(MODULE_B);
        setReleaseDetails(sourceConcept, 20220228); // Version ahead of target

        targetConceptAfter.setDefinitionStatusId(PRIMITIVE);
        targetConceptAfter.updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals("100", result.getConceptId());
        assertNull(result.getEffectiveTimeI());
        assertEquals(20220228, result.getReleasedEffectiveTime());
        assertTrue(result.isReleased());
        assertTrue(result.isActive());
        assertEquals(MODULE_B, result.getModuleId());
        assertEquals(PRIMITIVE, result.getDefinitionStatusId());
        assertEquals(sourceConcept.getReleaseHash(), result.getReleaseHash());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetDescriptionChangesActive() {
        // Create conflicting authoring changes
        sourceConcept.getDescription("101").setModuleId(MODULE_B);
        sourceConcept.getDescription("101").updateEffectiveTime();

        targetConceptAfter.getDescription("101").setActive(false);
        targetConceptAfter.getDescription("101").updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(1, result.getDescriptions().size());
        assertNull(result.getDescription("101").getEffectiveTimeI());
        assertNotNull(result.getDescription("101").getReleasedEffectiveTime());
        assertTrue(result.getDescription("101").isReleased());
        assertFalse(result.getDescription("101").isActive());
        assertEquals(MODULE_B, result.getDescription("101").getModuleId());
        assertEquals("100", result.getDescription("101").getConceptId());
        assertEquals("en", result.getDescription("101").getLanguageCode());
        assertEquals(FSN, result.getDescription("101").getTypeId());
        assertEquals(CASE_INSENSITIVE, result.getDescription("101").getCaseSignificanceId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetDescriptionChangesModule() {
        // Create conflicting authoring changes
        sourceConcept.getDescription("101").setActive(false);
        sourceConcept.getDescription("101").updateEffectiveTime();

        targetConceptAfter.getDescription("101").setModuleId(MODULE_B);
        targetConceptAfter.getDescription("101").updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(1, result.getDescriptions().size());
        assertFalse(result.getDescription("101").isActive());
        assertNull(result.getDescription("101").getEffectiveTimeI());
        assertNotNull(result.getDescription("101").getReleasedEffectiveTime());
        assertTrue(result.getDescription("101").isReleased());
        assertEquals(MODULE_B, result.getDescription("101").getModuleId());
        assertEquals("100", result.getDescription("101").getConceptId());
        assertEquals("en", result.getDescription("101").getLanguageCode());
        assertEquals(FSN, result.getDescription("101").getTypeId());
        assertEquals(CASE_INSENSITIVE, result.getDescription("101").getCaseSignificanceId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetDescriptionChangesLanguageCode() {
        // Create conflicting authoring changes
        sourceConcept.getDescription("101").setActive(false);
        sourceConcept.getDescription("101").updateEffectiveTime();

        targetConceptAfter.getDescription("101").setLanguageCode("sv");
        targetConceptAfter.getDescription("101").updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(1, result.getDescriptions().size());
        assertFalse(result.getDescription("101").isActive());
        assertNull(result.getDescription("101").getEffectiveTimeI());
        assertNotNull(result.getDescription("101").getReleasedEffectiveTime());
        assertTrue(result.getDescription("101").isReleased());
        assertEquals(MODULE_A, result.getDescription("101").getModuleId());
        assertEquals("100", result.getDescription("101").getConceptId());
        // Description is released and field cannot be changed
        assertEquals("en", result.getDescription("101").getLanguageCode());
        assertEquals(FSN, result.getDescription("101").getTypeId());
        assertEquals(CASE_INSENSITIVE, result.getDescription("101").getCaseSignificanceId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetDescriptionChangesTypeId() {
        // Create conflicting authoring changes
        sourceConcept.getDescription("101").setActive(false);
        sourceConcept.getDescription("101").updateEffectiveTime();

        targetConceptAfter.getDescription("101").setTypeId(SYNONYM);
        targetConceptAfter.getDescription("101").updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(1, result.getDescriptions().size());
        assertNull(result.getDescription("101").getEffectiveTimeI());
        assertNotNull(result.getDescription("101").getReleasedEffectiveTime());
        assertTrue(result.getDescription("101").isReleased());
        assertFalse(result.getDescription("101").isActive());
        assertEquals(MODULE_A, result.getDescription("101").getModuleId());
        assertEquals("100", result.getDescription("101").getConceptId());
        assertEquals("en", result.getDescription("101").getLanguageCode());
        // Description is released and field cannot be changed
        assertEquals(FSN, result.getDescription("101").getTypeId());
        assertEquals(CASE_INSENSITIVE, result.getDescription("101").getCaseSignificanceId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetDescriptionChangesCaseSignificance() {
        // Create conflicting authoring changes
        sourceConcept.getDescription("101").setActive(false);
        sourceConcept.getDescription("101").updateEffectiveTime();

        targetConceptAfter.getDescription("101").setCaseSignificanceId(Concepts.ENTIRE_TERM_CASE_SENSITIVE);
        targetConceptAfter.getDescription("101").updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(1, result.getDescriptions().size());
        assertNull(result.getDescription("101").getEffectiveTimeI());
        assertNotNull(result.getDescription("101").getReleasedEffectiveTime());
        assertTrue(result.getDescription("101").isReleased());
        assertFalse(result.getDescription("101").isActive());
        assertEquals(MODULE_A, result.getDescription("101").getModuleId());
        assertEquals("100", result.getDescription("101").getConceptId());
        assertEquals("en", result.getDescription("101").getLanguageCode());
        assertEquals(FSN, result.getDescription("101").getTypeId());
        assertEquals(ENTIRE_TERM_CASE_SENSITIVE, result.getDescription("101").getCaseSignificanceId());
    }

    @Test
    void autoMerge_ShouldFavourTargetDescriptionChanges() {
        // Create conflicting authoring changes
        sourceConcept.getDescription("101").setModuleId(MODULE_B);
        sourceConcept.getDescription("101").updateEffectiveTime();

        targetConceptAfter.getDescription("101").setModuleId(MODULE_C);
        targetConceptAfter.getDescription("101").updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(1, result.getDescriptions().size());
        assertNull(result.getDescription("101").getEffectiveTimeI());
        assertNotNull(result.getDescription("101").getReleasedEffectiveTime());
        assertTrue(result.getDescription("101").isReleased());
        assertTrue(result.getDescription("101").isActive());
        assertEquals(MODULE_C, result.getDescription("101").getModuleId());
        assertEquals("100", result.getDescription("101").getConceptId());
        assertEquals("en", result.getDescription("101").getLanguageCode());
        assertEquals(FSN, result.getDescription("101").getTypeId());
        assertEquals(CASE_INSENSITIVE, result.getDescription("101").getCaseSignificanceId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetDescriptionChangesNothing() {
        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(1, result.getDescriptions().size());
        assertNotNull(result.getDescription("101").getEffectiveTimeI());
        assertNotNull(result.getDescription("101").getReleasedEffectiveTime());
        assertTrue(result.getDescription("101").isReleased());
        assertTrue(result.getDescription("101").isActive());
        assertEquals(MODULE_A, result.getDescription("101").getModuleId());
        assertEquals("100", result.getDescription("101").getConceptId());
        assertEquals("en", result.getDescription("101").getLanguageCode());
        assertEquals(FSN, result.getDescription("101").getTypeId());
        assertEquals(CASE_INSENSITIVE, result.getDescription("101").getCaseSignificanceId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetDescriptionIsVersionAhead() {
        // Create conflicting authoring changes
        sourceConcept.getDescription("101").setCaseSignificanceId(ENTIRE_TERM_CASE_SENSITIVE);
        setReleaseDetails(sourceConcept.getDescription("101"), 20220228);

        targetConceptAfter.getDescription("101").setModuleId(MODULE_C);
        targetConceptAfter.getDescription("101").updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(1, result.getDescriptions().size());
        assertNull(result.getDescription("101").getEffectiveTimeI());
        assertEquals(20220228, result.getDescription("101").getReleasedEffectiveTime());
        assertTrue(result.getDescription("101").isReleased());
        assertTrue(result.getDescription("101").isActive());
        assertTrue(result.getDescription("101").isReleased());
        assertEquals(MODULE_C, result.getDescription("101").getModuleId());
        assertEquals("100", result.getDescription("101").getConceptId());
        assertEquals("en", result.getDescription("101").getLanguageCode());
        assertEquals(FSN, result.getDescription("101").getTypeId());
        assertEquals(ENTIRE_TERM_CASE_SENSITIVE, result.getDescription("101").getCaseSignificanceId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenSourceHasNewDescription() {
        // Create conflicting authoring changes
        Description vehicle = new Description("201", "Vehicle")
                .setActive(true)
                .setModuleId(MODULE_A)
                .setConceptId("100")
                .setLanguageCode("en")
                .setTypeId(SYNONYM)
                .setCaseSignificanceId(CASE_INSENSITIVE)
                .setAcceptabilityMap(PREFERRED);

        sourceConcept.addDescription(vehicle);

        targetConceptAfter.getDescription("101").setModuleId(MODULE_C);
        targetConceptAfter.getDescription("101").updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals("100", result.getConceptId());
        assertEquals(20220131, result.getEffectiveTimeI());
        assertEquals(20220131, result.getReleasedEffectiveTime());
        assertTrue(result.isReleased());
        assertTrue(result.isActive());
        assertEquals(MODULE_A, result.getModuleId());
        assertEquals(FULLY_DEFINED, result.getDefinitionStatusId());
        assertEquals(sourceConcept.getReleaseHash(), result.getReleaseHash());

        assertEquals(2, result.getDescriptions().size());

        // 101
        assertTrue(result.getDescription("101").isActive());
        assertNull(result.getDescription("101").getEffectiveTimeI());
        assertNotNull(result.getDescription("101").getReleasedEffectiveTime());
        assertEquals(20220131, result.getDescription("101").getReleasedEffectiveTime());
        assertTrue(result.getDescription("101").isReleased());
        assertEquals(MODULE_C, result.getDescription("101").getModuleId());
        assertEquals("100", result.getDescription("101").getConceptId());
        assertEquals("en", result.getDescription("101").getLanguageCode());
        assertEquals(FSN, result.getDescription("101").getTypeId());
        assertEquals(CASE_INSENSITIVE, result.getDescription("101").getCaseSignificanceId());

        // 201
        assertTrue(result.getDescription("201").isActive());
        assertNull(result.getDescription("201").getEffectiveTimeI());
        assertNull(result.getDescription("201").getReleasedEffectiveTime());
        assertFalse(result.getDescription("201").isReleased());
        assertEquals(MODULE_A, result.getDescription("201").getModuleId());
        assertEquals("100", result.getDescription("201").getConceptId());
        assertEquals("en", result.getDescription("201").getLanguageCode());
        assertEquals(SYNONYM, result.getDescription("201").getTypeId());
        assertEquals(CASE_INSENSITIVE, result.getDescription("201").getCaseSignificanceId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetHasNewDescription() {
        // Create conflicting authoring changes
        Description vehicle = new Description("201", "Vehicle")
                .setActive(true)
                .setModuleId(MODULE_A)
                .setConceptId("100")
                .setLanguageCode("en")
                .setTypeId(SYNONYM)
                .setCaseSignificanceId(CASE_INSENSITIVE)
                .setAcceptabilityMap(PREFERRED);

        sourceConcept.getDescription("101").setModuleId(MODULE_C);
        sourceConcept.getDescription("101").updateEffectiveTime();

        targetConceptAfter.addDescription(vehicle);

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(2, result.getDescriptions().size());

        // 101
        assertTrue(result.getDescription("101").isActive());
        assertNull(result.getDescription("101").getEffectiveTimeI());
        assertNotNull(result.getDescription("101").getReleasedEffectiveTime());
        assertEquals(20220131, result.getDescription("101").getReleasedEffectiveTime());
        assertTrue(result.getDescription("101").isReleased());
        assertEquals(MODULE_C, result.getDescription("101").getModuleId());
        assertEquals("100", result.getDescription("101").getConceptId());
        assertEquals("en", result.getDescription("101").getLanguageCode());
        assertEquals(FSN, result.getDescription("101").getTypeId());
        assertEquals(CASE_INSENSITIVE, result.getDescription("101").getCaseSignificanceId());

        // 201
        assertTrue(result.getDescription("201").isActive());
        assertNull(result.getDescription("201").getEffectiveTimeI());
        assertNull(result.getDescription("201").getReleasedEffectiveTime());
        assertFalse(result.getDescription("201").isReleased());
        assertEquals(MODULE_A, result.getDescription("201").getModuleId());
        assertEquals("100", result.getDescription("201").getConceptId());
        assertEquals("en", result.getDescription("201").getLanguageCode());
        assertEquals(SYNONYM, result.getDescription("201").getTypeId());
        assertEquals(CASE_INSENSITIVE, result.getDescription("201").getCaseSignificanceId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenSourceAndTargetHaveNewDescriptions() {
        // Create conflicting authoring changes
        Description vehicle = new Description("201", "Vehicle")
                .setActive(true)
                .setModuleId(MODULE_A)
                .setConceptId("100")
                .setLanguageCode("en")
                .setTypeId(SYNONYM)
                .setCaseSignificanceId(CASE_INSENSITIVE)
                .setAcceptabilityMap(PREFERRED);

        Description car = new Description("301", "Car")
                .setActive(true)
                .setModuleId(MODULE_A)
                .setConceptId("100")
                .setLanguageCode("en")
                .setTypeId(SYNONYM)
                .setCaseSignificanceId(CASE_INSENSITIVE)
                .setAcceptabilityMap(PREFERRED);

        sourceConcept.addDescription(car);
        targetConceptAfter.addDescription(vehicle);

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(3, result.getDescriptions().size());

        // 101
        assertTrue(result.getDescription("101").isActive());
        assertEquals(20220131, result.getDescription("101").getEffectiveTimeI());
        assertEquals(20220131, result.getDescription("101").getReleasedEffectiveTime());
        assertTrue(result.getDescription("101").isReleased());
        assertEquals(MODULE_A, result.getDescription("101").getModuleId());
        assertEquals("100", result.getDescription("101").getConceptId());
        assertEquals("en", result.getDescription("101").getLanguageCode());
        assertEquals(FSN, result.getDescription("101").getTypeId());
        assertEquals(CASE_INSENSITIVE, result.getDescription("101").getCaseSignificanceId());

        // 201
        assertTrue(result.getDescription("201").isActive());
        assertNull(result.getDescription("201").getEffectiveTimeI());
        assertNull(result.getDescription("201").getReleasedEffectiveTime());
        assertFalse(result.getDescription("201").isReleased());
        assertEquals(MODULE_A, result.getDescription("201").getModuleId());
        assertEquals("100", result.getDescription("201").getConceptId());
        assertEquals("en", result.getDescription("201").getLanguageCode());
        assertEquals(SYNONYM, result.getDescription("201").getTypeId());
        assertEquals(CASE_INSENSITIVE, result.getDescription("201").getCaseSignificanceId());

        // 301
        assertTrue(result.getDescription("301").isActive());
        assertNull(result.getDescription("301").getEffectiveTimeI());
        assertNull(result.getDescription("301").getReleasedEffectiveTime());
        assertFalse(result.getDescription("301").isReleased());
        assertEquals(MODULE_A, result.getDescription("301").getModuleId());
        assertEquals("100", result.getDescription("301").getConceptId());
        assertEquals("en", result.getDescription("301").getLanguageCode());
        assertEquals(SYNONYM, result.getDescription("301").getTypeId());
        assertEquals(CASE_INSENSITIVE, result.getDescription("301").getCaseSignificanceId());

    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetDescriptionHasChangedTerm() {
        // Create conflicting authoring changes
        clearReleaseDetails(sourceConcept);
        for (Description description : sourceConcept.getDescriptions()) {
            clearReleaseDetails(description);
        }

        clearReleaseDetails(targetConceptBefore);
        for (Description description : targetConceptBefore.getDescriptions()) {
            clearReleaseDetails(description);
        }

        clearReleaseDetails(targetConceptAfter);
        for (Description description : targetConceptAfter.getDescriptions()) {
            clearReleaseDetails(description);
        }

        sourceConcept.getDescription("101").setModuleId(MODULE_B);
        targetConceptAfter.getDescription("101").setTerm("Vehicles (vehicles)");

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(1, result.getDescriptions().size());
        assertTrue(result.getDescription("101").isActive());
        assertNull(result.getDescription("101").getEffectiveTimeI());
        assertNull(result.getDescription("101").getReleasedEffectiveTime());
        assertFalse(result.getDescription("101").isReleased());
        assertEquals("Vehicles (vehicles)", result.getDescription("101").getTerm());
        assertEquals(MODULE_B, result.getDescription("101").getModuleId());
        assertEquals("100", result.getDescription("101").getConceptId());
        assertEquals("en", result.getDescription("101").getLanguageCode());
        assertEquals(FSN, result.getDescription("101").getTypeId());
        assertEquals(CASE_INSENSITIVE, result.getDescription("101").getCaseSignificanceId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetRelationshipHasChangedActive() {
        // Create conflicting authoring changes
        sourceConcept.getRelationship("102").setModuleId(MODULE_B);
        sourceConcept.getRelationship("102").updateEffectiveTime();

        targetConceptAfter.getRelationship("102").setActive(false);
        targetConceptAfter.getRelationship("102").updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(1, result.getRelationships().size());
        assertNull(result.getRelationship("102").getEffectiveTimeI());
        assertEquals(20220131, result.getRelationship("102").getReleasedEffectiveTime());
        assertTrue(result.getRelationship("102").isReleased());
        assertFalse(result.getRelationship("102").isActive());
        assertEquals(0, result.getRelationship("102").getRelationshipGroup());
        assertEquals(MODULE_B, result.getRelationship("102").getModuleId());
        assertEquals("100", result.getRelationship("102").getSourceId());
        assertEquals(PHYSICAL_OBJECT, result.getRelationship("102").getDestinationId());
        assertEquals(0, result.getRelationship("102").getRelationshipGroup());
        assertEquals(ISA, result.getRelationship("102").getTypeId());
        assertEquals(INFERRED_RELATIONSHIP, result.getRelationship("102").getCharacteristicTypeId());
        assertEquals(EXISTENTIAL, result.getRelationship("102").getModifierId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetRelationshipHasChangedModule() {
        // Create conflicting authoring changes
        sourceConcept.getRelationship("102").setActive(false);
        sourceConcept.getRelationship("102").updateEffectiveTime();

        targetConceptAfter.getRelationship("102").setModuleId(MODULE_B);
        targetConceptAfter.getRelationship("102").updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(1, result.getRelationships().size());
        assertNull(result.getRelationship("102").getEffectiveTimeI());
        assertEquals(20220131, result.getRelationship("102").getReleasedEffectiveTime());
        assertTrue(result.getRelationship("102").isReleased());
        assertFalse(result.getRelationship("102").isActive());
        assertEquals(0, result.getRelationship("102").getRelationshipGroup());
        assertEquals(MODULE_B, result.getRelationship("102").getModuleId());
        assertEquals("100", result.getRelationship("102").getSourceId());
        assertEquals(PHYSICAL_OBJECT, result.getRelationship("102").getDestinationId());
        assertEquals(0, result.getRelationship("102").getRelationshipGroup());
        assertEquals(ISA, result.getRelationship("102").getTypeId());
        assertEquals(INFERRED_RELATIONSHIP, result.getRelationship("102").getCharacteristicTypeId());
        assertEquals(EXISTENTIAL, result.getRelationship("102").getModifierId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetRelationshipHasChangedSource() {
        // Create conflicting authoring changes
        sourceConcept.getRelationship("102").setModuleId(MODULE_B);
        sourceConcept.getRelationship("102").updateEffectiveTime();

        targetConceptAfter.getRelationship("102").setSourceId(SNOMEDCT_ROOT);
        targetConceptAfter.getRelationship("102").updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(1, result.getRelationships().size());
        assertNull(result.getRelationship("102").getEffectiveTimeI());
        assertEquals(20220131, result.getRelationship("102").getReleasedEffectiveTime());
        assertTrue(result.getRelationship("102").isReleased());
        assertTrue(result.getRelationship("102").isActive());
        assertEquals(0, result.getRelationship("102").getRelationshipGroup());
        assertEquals(MODULE_B, result.getRelationship("102").getModuleId());
        // Relationship has been released and field cannot be changed
        assertEquals("100", result.getRelationship("102").getSourceId());
        assertEquals(PHYSICAL_OBJECT, result.getRelationship("102").getDestinationId());
        assertEquals(0, result.getRelationship("102").getRelationshipGroup());
        assertEquals(ISA, result.getRelationship("102").getTypeId());
        assertEquals(INFERRED_RELATIONSHIP, result.getRelationship("102").getCharacteristicTypeId());
        assertEquals(EXISTENTIAL, result.getRelationship("102").getModifierId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetRelationshipHasChangedDestination() {
        // Create conflicting authoring changes
        sourceConcept.getRelationship("102").setModuleId(MODULE_B);
        sourceConcept.getRelationship("102").updateEffectiveTime();

        targetConceptAfter.getRelationship("102").setDestinationId(CLINICAL_FINDING);
        targetConceptAfter.getRelationship("102").updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(1, result.getRelationships().size());
        assertNull(result.getRelationship("102").getEffectiveTimeI());
        assertEquals(20220131, result.getRelationship("102").getReleasedEffectiveTime());
        assertTrue(result.getRelationship("102").isReleased());
        assertTrue(result.getRelationship("102").isActive());
        assertEquals(0, result.getRelationship("102").getRelationshipGroup());
        assertEquals(MODULE_B, result.getRelationship("102").getModuleId());
        assertEquals("100", result.getRelationship("102").getSourceId());
        // Relationship has been released and field cannot be changed
        assertEquals(PHYSICAL_OBJECT, result.getRelationship("102").getDestinationId());
        assertEquals(0, result.getRelationship("102").getRelationshipGroup());
        assertEquals(ISA, result.getRelationship("102").getTypeId());
        assertEquals(INFERRED_RELATIONSHIP, result.getRelationship("102").getCharacteristicTypeId());
        assertEquals(EXISTENTIAL, result.getRelationship("102").getModifierId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetRelationshipHasChangedRelationshipGroup() {
        // Create conflicting authoring changes
        sourceConcept.getRelationship("102").setModuleId(MODULE_B);
        sourceConcept.getRelationship("102").updateEffectiveTime();

        targetConceptAfter.getRelationship("102").setRelationshipGroup(1);
        targetConceptAfter.getRelationship("102").updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(1, result.getRelationships().size());
        assertNull(result.getRelationship("102").getEffectiveTimeI());
        assertEquals(20220131, result.getRelationship("102").getReleasedEffectiveTime());
        assertTrue(result.getRelationship("102").isReleased());
        assertTrue(result.getRelationship("102").isActive());
        assertEquals(1, result.getRelationship("102").getRelationshipGroup());
        assertEquals(MODULE_B, result.getRelationship("102").getModuleId());
        assertEquals("100", result.getRelationship("102").getSourceId());
        assertEquals(PHYSICAL_OBJECT, result.getRelationship("102").getDestinationId());
        assertEquals(1, result.getRelationship("102").getRelationshipGroup());
        assertEquals(ISA, result.getRelationship("102").getTypeId());
        assertEquals(INFERRED_RELATIONSHIP, result.getRelationship("102").getCharacteristicTypeId());
        assertEquals(EXISTENTIAL, result.getRelationship("102").getModifierId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetRelationshipHasChangedTypeId() {
        // Create conflicting authoring changes
        sourceConcept.getRelationship("102").setModuleId(MODULE_B);
        sourceConcept.getRelationship("102").updateEffectiveTime();

        targetConceptAfter.getRelationship("102").setTypeId(HAS_FILLING);
        targetConceptAfter.getRelationship("102").updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(1, result.getRelationships().size());
        assertNull(result.getRelationship("102").getEffectiveTimeI());
        assertEquals(20220131, result.getRelationship("102").getReleasedEffectiveTime());
        assertTrue(result.getRelationship("102").isReleased());
        assertTrue(result.getRelationship("102").isActive());
        assertEquals(0, result.getRelationship("102").getRelationshipGroup());
        assertEquals(MODULE_B, result.getRelationship("102").getModuleId());
        assertEquals("100", result.getRelationship("102").getSourceId());
        assertEquals(PHYSICAL_OBJECT, result.getRelationship("102").getDestinationId());
        // Relationship has been published and field cannot be changed
        assertEquals(ISA, result.getRelationship("102").getTypeId());
        assertEquals(INFERRED_RELATIONSHIP, result.getRelationship("102").getCharacteristicTypeId());
        assertEquals(EXISTENTIAL, result.getRelationship("102").getModifierId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetRelationshipHasChangedCharacteristicTypeId() {
        // Create conflicting authoring changes
        sourceConcept.getRelationship("102").setModuleId(MODULE_B);
        sourceConcept.getRelationship("102").updateEffectiveTime();

        targetConceptAfter.getRelationship("102").setCharacteristicTypeId(ADDITIONAL_RELATIONSHIP);
        targetConceptAfter.getRelationship("102").updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(1, result.getRelationships().size());
        assertNull(result.getRelationship("102").getEffectiveTimeI());
        assertEquals(20220131, result.getRelationship("102").getReleasedEffectiveTime());
        assertTrue(result.getRelationship("102").isReleased());
        assertTrue(result.getRelationship("102").isActive());
        assertEquals(0, result.getRelationship("102").getRelationshipGroup());
        assertEquals(MODULE_B, result.getRelationship("102").getModuleId());
        assertEquals("100", result.getRelationship("102").getSourceId());
        assertEquals(PHYSICAL_OBJECT, result.getRelationship("102").getDestinationId());
        assertEquals(ISA, result.getRelationship("102").getTypeId());
        assertEquals(ADDITIONAL_RELATIONSHIP, result.getRelationship("102").getCharacteristicTypeId());
        assertEquals(EXISTENTIAL, result.getRelationship("102").getModifierId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetRelationshipHasChangedModifierId() {
        // Create conflicting authoring changes
        sourceConcept.getRelationship("102").setModuleId(MODULE_B);
        sourceConcept.getRelationship("102").updateEffectiveTime();

        targetConceptAfter.getRelationship("102").setModifier("UNIVERSAL");
        targetConceptAfter.getRelationship("102").updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(1, result.getRelationships().size());
        assertNull(result.getRelationship("102").getEffectiveTimeI());
        assertEquals(20220131, result.getRelationship("102").getReleasedEffectiveTime());
        assertTrue(result.getRelationship("102").isReleased());
        assertTrue(result.getRelationship("102").isActive());
        assertEquals(0, result.getRelationship("102").getRelationshipGroup());
        assertEquals(MODULE_B, result.getRelationship("102").getModuleId());
        assertEquals("100", result.getRelationship("102").getSourceId());
        assertEquals(PHYSICAL_OBJECT, result.getRelationship("102").getDestinationId());
        assertEquals(ISA, result.getRelationship("102").getTypeId());
        assertEquals(INFERRED_RELATIONSHIP, result.getRelationship("102").getCharacteristicTypeId());
        assertEquals(UNIVERSAL, result.getRelationship("102").getModifierId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenSourceHasExtraRelationship() {
        // Create conflicting authoring changes
        Relationship isADevice = new Relationship("202");
        isADevice.setModuleId(MODULE_A);
        isADevice.setActive(true);
        isADevice.setSourceId("100");
        isADevice.setDestinationId(DEVICE);
        isADevice.setRelationshipGroup(0);
        isADevice.setTypeId(ISA);
        isADevice.setCharacteristicTypeId(INFERRED_RELATIONSHIP);
        isADevice.setModifier("EXISTENTIAL");
        sourceConcept.addRelationship(isADevice);

        targetConceptAfter.getRelationship("102").setModuleId(MODULE_B);
        targetConceptAfter.getRelationship("102").updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(2, result.getRelationships().size());

        // 102
        assertNull(result.getRelationship("102").getEffectiveTimeI());
        assertEquals(20220131, result.getRelationship("102").getReleasedEffectiveTime());
        assertTrue(result.getRelationship("102").isReleased());
        assertTrue(result.getRelationship("102").isActive());
        assertEquals(0, result.getRelationship("102").getRelationshipGroup());
        assertEquals(MODULE_B, result.getRelationship("102").getModuleId());
        assertEquals("100", result.getRelationship("102").getSourceId());
        assertEquals(PHYSICAL_OBJECT, result.getRelationship("102").getDestinationId());
        assertEquals(ISA, result.getRelationship("102").getTypeId());
        assertEquals(INFERRED_RELATIONSHIP, result.getRelationship("102").getCharacteristicTypeId());
        assertEquals(EXISTENTIAL, result.getRelationship("102").getModifierId());

        // 202
        assertNull(result.getRelationship("202").getEffectiveTimeI());
        assertNull(result.getRelationship("202").getReleasedEffectiveTime());
        assertFalse(result.getRelationship("202").isReleased());
        assertTrue(result.getRelationship("202").isActive());
        assertEquals(0, result.getRelationship("202").getRelationshipGroup());
        assertEquals(MODULE_A, result.getRelationship("202").getModuleId());
        assertEquals("100", result.getRelationship("202").getSourceId());
        assertEquals(DEVICE, result.getRelationship("202").getDestinationId());
        assertEquals(ISA, result.getRelationship("202").getTypeId());
        assertEquals(INFERRED_RELATIONSHIP, result.getRelationship("202").getCharacteristicTypeId());
        assertEquals(EXISTENTIAL, result.getRelationship("202").getModifierId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetHasExtraRelationship() {
        // Create conflicting authoring changes
        Relationship isADevice = new Relationship("202");
        isADevice.setModuleId(MODULE_A);
        isADevice.setActive(true);
        isADevice.setSourceId("100");
        isADevice.setDestinationId(DEVICE);
        isADevice.setRelationshipGroup(0);
        isADevice.setTypeId(ISA);
        isADevice.setCharacteristicTypeId(INFERRED_RELATIONSHIP);
        isADevice.setModifier("EXISTENTIAL");

        sourceConcept.getRelationship("102").setModuleId(MODULE_B);
        sourceConcept.getRelationship("102").updateEffectiveTime();

        targetConceptAfter.addRelationship(isADevice);

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(2, result.getRelationships().size());

        // 102
        assertNull(result.getRelationship("102").getEffectiveTimeI());
        assertEquals(20220131, result.getRelationship("102").getReleasedEffectiveTime());
        assertTrue(result.getRelationship("102").isReleased());
        assertTrue(result.getRelationship("102").isActive());
        assertEquals(0, result.getRelationship("102").getRelationshipGroup());
        assertEquals(MODULE_B, result.getRelationship("102").getModuleId());
        assertEquals("100", result.getRelationship("102").getSourceId());
        assertEquals(PHYSICAL_OBJECT, result.getRelationship("102").getDestinationId());
        assertEquals(ISA, result.getRelationship("102").getTypeId());
        assertEquals(INFERRED_RELATIONSHIP, result.getRelationship("102").getCharacteristicTypeId());
        assertEquals(EXISTENTIAL, result.getRelationship("102").getModifierId());

        // 202
        assertNull(result.getRelationship("202").getEffectiveTimeI());
        assertNull(result.getRelationship("202").getReleasedEffectiveTime());
        assertFalse(result.getRelationship("202").isReleased());
        assertTrue(result.getRelationship("202").isActive());
        assertEquals(0, result.getRelationship("202").getRelationshipGroup());
        assertEquals(MODULE_A, result.getRelationship("202").getModuleId());
        assertEquals("100", result.getRelationship("202").getSourceId());
        assertEquals(DEVICE, result.getRelationship("202").getDestinationId());
        assertEquals(ISA, result.getRelationship("202").getTypeId());
        assertEquals(INFERRED_RELATIONSHIP, result.getRelationship("202").getCharacteristicTypeId());
        assertEquals(EXISTENTIAL, result.getRelationship("202").getModifierId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenSourceAndTargetHaveExtraRelationships() {
        // Create conflicting authoring changes
        Relationship isADevice = new Relationship("202");
        isADevice.setModuleId(MODULE_A);
        isADevice.setActive(true);
        isADevice.setSourceId("100");
        isADevice.setDestinationId(DEVICE);
        isADevice.setRelationshipGroup(0);
        isADevice.setTypeId(ISA);
        isADevice.setCharacteristicTypeId(INFERRED_RELATIONSHIP);
        isADevice.setModifier("EXISTENTIAL");

        Relationship isADomestic = new Relationship("302");
        isADomestic.setModuleId(MODULE_A);
        isADomestic.setActive(true);
        isADomestic.setSourceId("100");
        isADomestic.setDestinationId(DOMESTIC);
        isADomestic.setRelationshipGroup(0);
        isADomestic.setTypeId(ISA);
        isADomestic.setCharacteristicTypeId(INFERRED_RELATIONSHIP);
        isADomestic.setModifier("EXISTENTIAL");

        sourceConcept.addRelationship(isADomestic);
        targetConceptAfter.addRelationship(isADevice);

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(3, result.getRelationships().size());

        // 102
        assertEquals(20220131, result.getRelationship("102").getEffectiveTimeI());
        assertEquals(20220131, result.getRelationship("102").getReleasedEffectiveTime());
        assertTrue(result.getRelationship("102").isReleased());
        assertTrue(result.getRelationship("102").isActive());
        assertEquals(0, result.getRelationship("102").getRelationshipGroup());
        assertEquals(MODULE_A, result.getRelationship("102").getModuleId());
        assertEquals("100", result.getRelationship("102").getSourceId());
        assertEquals(PHYSICAL_OBJECT, result.getRelationship("102").getDestinationId());
        assertEquals(ISA, result.getRelationship("102").getTypeId());
        assertEquals(INFERRED_RELATIONSHIP, result.getRelationship("102").getCharacteristicTypeId());
        assertEquals(EXISTENTIAL, result.getRelationship("102").getModifierId());

        // 202
        assertNull(result.getRelationship("202").getEffectiveTimeI());
        assertNull(result.getRelationship("202").getReleasedEffectiveTime());
        assertFalse(result.getRelationship("202").isReleased());
        assertTrue(result.getRelationship("202").isActive());
        assertEquals(0, result.getRelationship("202").getRelationshipGroup());
        assertEquals(MODULE_A, result.getRelationship("202").getModuleId());
        assertEquals("100", result.getRelationship("202").getSourceId());
        assertEquals(DEVICE, result.getRelationship("202").getDestinationId());
        assertEquals(ISA, result.getRelationship("202").getTypeId());
        assertEquals(INFERRED_RELATIONSHIP, result.getRelationship("202").getCharacteristicTypeId());
        assertEquals(EXISTENTIAL, result.getRelationship("202").getModifierId());

        // 302
        assertNull(result.getRelationship("302").getEffectiveTimeI());
        assertNull(result.getRelationship("302").getReleasedEffectiveTime());
        assertFalse(result.getRelationship("302").isReleased());
        assertTrue(result.getRelationship("302").isActive());
        assertEquals(0, result.getRelationship("302").getRelationshipGroup());
        assertEquals(MODULE_A, result.getRelationship("302").getModuleId());
        assertEquals("100", result.getRelationship("302").getSourceId());
        assertEquals(DOMESTIC, result.getRelationship("302").getDestinationId());
        assertEquals(ISA, result.getRelationship("302").getTypeId());
        assertEquals(INFERRED_RELATIONSHIP, result.getRelationship("302").getCharacteristicTypeId());
        assertEquals(EXISTENTIAL, result.getRelationship("302").getModifierId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenSourceRelationshipIsVersionAhead() {
        // Create conflicting authoring changes
        sourceConcept.getRelationship("102").setModuleId(MODULE_B);
        setReleaseDetails(sourceConcept.getRelationship("102"), 20220228);

        targetConceptAfter.getRelationship("102").setActive(false);
        targetConceptAfter.getRelationship("102").updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(1, result.getRelationships().size());
        assertNull(result.getRelationship("102").getEffectiveTimeI());
        // Component has been upgraded
        assertEquals(20220228, result.getRelationship("102").getReleasedEffectiveTime());
        assertTrue(result.getRelationship("102").isReleased());
        // Target changes have been re-applied
        assertFalse(result.getRelationship("102").isActive());
        assertEquals(0, result.getRelationship("102").getRelationshipGroup());
        // Component has been upgraded
        assertEquals(MODULE_B, result.getRelationship("102").getModuleId());
        assertEquals("100", result.getRelationship("102").getSourceId());
        assertEquals(PHYSICAL_OBJECT, result.getRelationship("102").getDestinationId());
        assertEquals(ISA, result.getRelationship("102").getTypeId());
        assertEquals(INFERRED_RELATIONSHIP, result.getRelationship("102").getCharacteristicTypeId());
        assertEquals(EXISTENTIAL, result.getRelationship("102").getModifierId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetClassAxiomChangesActive() {
        // Create conflicting authoring changes
        sourceConcept.getClassAxioms().iterator().next().setModuleId(MODULE_B);
        sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().setModuleId(MODULE_B);
        sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().updateEffectiveTime();

        targetConceptAfter.getClassAxioms().iterator().next().setActive(false);
        targetConceptAfter.getClassAxioms().iterator().next().getReferenceSetMember().setActive(false);
        targetConceptAfter.getClassAxioms().iterator().next().getReferenceSetMember().updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(AXIOM_ID, result.getClassAxioms().iterator().next().getAxiomId());
        assertFalse(result.getClassAxioms().iterator().next().isActive());
        assertTrue(result.getClassAxioms().iterator().next().isReleased());
        assertEquals(FULLY_DEFINED, result.getClassAxioms().iterator().next().getDefinitionStatusId());
        assertEquals(MODULE_B, result.getClassAxioms().iterator().next().getModuleId());
        assertEquals(1, result.getClassAxioms().iterator().next().getRelationships().size());

        assertEquals(AXIOM_ID, result.getClassAxioms().iterator().next().getReferenceSetMember().getMemberId());
        assertFalse(result.getClassAxioms().iterator().next().getReferenceSetMember().isActive());
        assertEquals(MODULE_B, result.getClassAxioms().iterator().next().getReferenceSetMember().getModuleId());
        assertEquals(20220131, result.getClassAxioms().iterator().next().getReferenceSetMember().getReleasedEffectiveTime());
        assertNull(result.getClassAxioms().iterator().next().getReferenceSetMember().getEffectiveTimeI());
        assertTrue(result.getClassAxioms().iterator().next().getReferenceSetMember().isReleased());
        assertEquals(
                sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().getReleaseHash(),
                result.getClassAxioms().iterator().next().getReferenceSetMember().getReleaseHash()
        );
        assertEquals(Concepts.OWL_AXIOM_REFERENCE_SET, result.getClassAxioms().iterator().next().getReferenceSetMember().getRefsetId());
        assertEquals("101", result.getClassAxioms().iterator().next().getReferenceSetMember().getReferencedComponentId());
        assertEquals(
                sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().getAdditionalFields(),
                result.getClassAxioms().iterator().next().getReferenceSetMember().getAdditionalFields()
        );
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetClassAxiomChangesModule() {
        // Create conflicting authoring changes
        sourceConcept.getClassAxioms().iterator().next().setActive(false);
        sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().setActive(false);
        sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().updateEffectiveTime();

        targetConceptAfter.getClassAxioms().iterator().next().setModuleId(MODULE_B);
        targetConceptAfter.getClassAxioms().iterator().next().getReferenceSetMember().setModuleId(MODULE_B);
        targetConceptAfter.getClassAxioms().iterator().next().getReferenceSetMember().updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(AXIOM_ID, result.getClassAxioms().iterator().next().getAxiomId());
        assertFalse(result.getClassAxioms().iterator().next().isActive());
        assertTrue(result.getClassAxioms().iterator().next().isReleased());
        assertEquals(FULLY_DEFINED, result.getClassAxioms().iterator().next().getDefinitionStatusId());
        assertEquals(MODULE_B, result.getClassAxioms().iterator().next().getModuleId());
        assertEquals(1, result.getClassAxioms().iterator().next().getRelationships().size());

        assertEquals(AXIOM_ID, result.getClassAxioms().iterator().next().getReferenceSetMember().getMemberId());
        assertFalse(result.getClassAxioms().iterator().next().getReferenceSetMember().isActive());
        assertEquals(MODULE_B, result.getClassAxioms().iterator().next().getReferenceSetMember().getModuleId());
        assertEquals(20220131, result.getClassAxioms().iterator().next().getReferenceSetMember().getReleasedEffectiveTime());
        assertNull(result.getClassAxioms().iterator().next().getReferenceSetMember().getEffectiveTimeI());
        assertTrue(result.getClassAxioms().iterator().next().getReferenceSetMember().isReleased());
        assertEquals(
                sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().getReleaseHash(),
                result.getClassAxioms().iterator().next().getReferenceSetMember().getReleaseHash()
        );
        assertEquals(Concepts.OWL_AXIOM_REFERENCE_SET, result.getClassAxioms().iterator().next().getReferenceSetMember().getRefsetId());
        assertEquals("101", result.getClassAxioms().iterator().next().getReferenceSetMember().getReferencedComponentId());
        assertEquals(
                sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().getAdditionalFields(),
                result.getClassAxioms().iterator().next().getReferenceSetMember().getAdditionalFields()
        );
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetClassAxiomChangesOwlExpression() {
        // Create conflicting authoring changes
        sourceConcept.getClassAxioms().iterator().next().setModuleId(MODULE_B);
        sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().setModuleId(MODULE_B);
        sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().updateEffectiveTime();

        targetConceptAfter.getClassAxioms().iterator().next().getReferenceSetMember().setAdditionalField(
                ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION,
                "SubClassOf(:101 :38019009)"
        );
        targetConceptAfter.getClassAxioms().iterator().next().getReferenceSetMember().updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(AXIOM_ID, result.getClassAxioms().iterator().next().getAxiomId());
        assertTrue(result.getClassAxioms().iterator().next().isActive());
        assertTrue(result.getClassAxioms().iterator().next().isReleased());
        assertEquals(FULLY_DEFINED, result.getClassAxioms().iterator().next().getDefinitionStatusId());
        assertEquals(MODULE_B, result.getClassAxioms().iterator().next().getModuleId());
        assertEquals(1, result.getClassAxioms().iterator().next().getRelationships().size());

        assertEquals(AXIOM_ID, result.getClassAxioms().iterator().next().getReferenceSetMember().getMemberId());
        assertTrue(result.getClassAxioms().iterator().next().getReferenceSetMember().isActive());
        assertEquals(MODULE_B, result.getClassAxioms().iterator().next().getReferenceSetMember().getModuleId());
        assertEquals(20220131, result.getClassAxioms().iterator().next().getReferenceSetMember().getReleasedEffectiveTime());
        assertNull(result.getClassAxioms().iterator().next().getReferenceSetMember().getEffectiveTimeI());
        assertTrue(result.getClassAxioms().iterator().next().getReferenceSetMember().isReleased());
        assertEquals(
                sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().getReleaseHash(),
                result.getClassAxioms().iterator().next().getReferenceSetMember().getReleaseHash()
        );
        assertEquals(Concepts.OWL_AXIOM_REFERENCE_SET, result.getClassAxioms().iterator().next().getReferenceSetMember().getRefsetId());
        assertEquals("101", result.getClassAxioms().iterator().next().getReferenceSetMember().getReferencedComponentId());
        assertNotEquals(
                sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().getAdditionalFields(),
                result.getClassAxioms().iterator().next().getReferenceSetMember().getAdditionalFields()
        );
        assertEquals(
                "SubClassOf(:101 :38019009)",
                result.getClassAxioms().iterator().next().getReferenceSetMember().getAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION)
        );
    }

    @Test
    void autoMerge_ShouldFavourTargetClassAxiomChanges() {
        // Create conflicting authoring changes
        sourceConcept.getClassAxioms().iterator().next().setModuleId(MODULE_B);
        sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().setModuleId(MODULE_B);
        sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().updateEffectiveTime();

        sourceConcept.getClassAxioms().iterator().next().setModuleId(MODULE_C);
        sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().setModuleId(MODULE_C);
        sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(AXIOM_ID, result.getClassAxioms().iterator().next().getAxiomId());
        assertTrue(result.getClassAxioms().iterator().next().isActive());
        assertTrue(result.getClassAxioms().iterator().next().isReleased());
        assertEquals(FULLY_DEFINED, result.getClassAxioms().iterator().next().getDefinitionStatusId());
        assertEquals(MODULE_C, result.getClassAxioms().iterator().next().getModuleId());
        assertEquals(1, result.getClassAxioms().iterator().next().getRelationships().size());

        assertEquals(AXIOM_ID, result.getClassAxioms().iterator().next().getReferenceSetMember().getMemberId());
        assertTrue(result.getClassAxioms().iterator().next().getReferenceSetMember().isActive());
        assertEquals(MODULE_C, result.getClassAxioms().iterator().next().getReferenceSetMember().getModuleId());
        assertEquals(20220131, result.getClassAxioms().iterator().next().getReferenceSetMember().getReleasedEffectiveTime());
        assertNull(result.getClassAxioms().iterator().next().getReferenceSetMember().getEffectiveTimeI());
        assertTrue(result.getClassAxioms().iterator().next().getReferenceSetMember().isReleased());
        assertEquals(
                sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().getReleaseHash(),
                result.getClassAxioms().iterator().next().getReferenceSetMember().getReleaseHash()
        );
        assertEquals(Concepts.OWL_AXIOM_REFERENCE_SET, result.getClassAxioms().iterator().next().getReferenceSetMember().getRefsetId());
        assertEquals("101", result.getClassAxioms().iterator().next().getReferenceSetMember().getReferencedComponentId());
        assertEquals(
                sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().getAdditionalFields(),
                result.getClassAxioms().iterator().next().getReferenceSetMember().getAdditionalFields()
        );
        assertEquals(
                "SubClassOf(:101 :260787004)",
                result.getClassAxioms().iterator().next().getReferenceSetMember().getAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION)
        );
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetClassAxiomIsVersionAhead() {
        // Create conflicting authoring changes
        sourceConcept.getClassAxioms().iterator().next().setModuleId(MODULE_B);
        sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().setModuleId(MODULE_B);
        setReleaseDetails(sourceConcept.getClassAxioms().iterator().next(), 20220228);
        setReleaseDetails(sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember(), 20220228);

        targetConceptAfter.getClassAxioms().iterator().next().setActive(false);
        targetConceptAfter.getClassAxioms().iterator().next().getReferenceSetMember().setActive(false);
        targetConceptAfter.getClassAxioms().iterator().next().getReferenceSetMember().updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(AXIOM_ID, result.getClassAxioms().iterator().next().getAxiomId());
        assertFalse(result.getClassAxioms().iterator().next().isActive());
        assertTrue(result.getClassAxioms().iterator().next().isReleased());
        assertEquals(FULLY_DEFINED, result.getClassAxioms().iterator().next().getDefinitionStatusId());
        assertEquals(MODULE_B, result.getClassAxioms().iterator().next().getModuleId());
        assertEquals(1, result.getClassAxioms().iterator().next().getRelationships().size());

        assertEquals(AXIOM_ID, result.getClassAxioms().iterator().next().getReferenceSetMember().getMemberId());
        assertFalse(result.getClassAxioms().iterator().next().getReferenceSetMember().isActive());
        assertEquals(MODULE_B, result.getClassAxioms().iterator().next().getReferenceSetMember().getModuleId());
        assertEquals(20220228, result.getClassAxioms().iterator().next().getReferenceSetMember().getReleasedEffectiveTime());
        assertNull(result.getClassAxioms().iterator().next().getReferenceSetMember().getEffectiveTimeI());
        assertTrue(result.getClassAxioms().iterator().next().getReferenceSetMember().isReleased());
        assertEquals(
                sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().getReleaseHash(),
                result.getClassAxioms().iterator().next().getReferenceSetMember().getReleaseHash()
        );
        assertEquals(Concepts.OWL_AXIOM_REFERENCE_SET, result.getClassAxioms().iterator().next().getReferenceSetMember().getRefsetId());
        assertEquals("101", result.getClassAxioms().iterator().next().getReferenceSetMember().getReferencedComponentId());
        assertEquals(
                sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().getAdditionalFields(),
                result.getClassAxioms().iterator().next().getReferenceSetMember().getAdditionalFields()
        );
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenSourceHasExtraClassAxiom() {
        // Create conflicting authoring changes
        String newAxiomOnSourceId = UUID.randomUUID().toString();
        Axiom newAxiomOnSource = new Axiom();
        newAxiomOnSource.setAxiomId(newAxiomOnSourceId);
        newAxiomOnSource.setActive(true);
        newAxiomOnSource.setDefinitionStatusId(PRIMITIVE);
        newAxiomOnSource.setModuleId(MODULE_A);
        newAxiomOnSource.setReferenceSetMember(new ReferenceSetMember());
        sourceConcept.getClassAxioms().add(newAxiomOnSource);

        targetConceptAfter.getClassAxioms().iterator().next().setModuleId(MODULE_B);
        targetConceptAfter.getClassAxioms().iterator().next().getReferenceSetMember().setModuleId(MODULE_B);
        targetConceptAfter.getClassAxioms().iterator().next().getReferenceSetMember().updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(2, result.getClassAxioms().size());
        Axiom axiom;

        // Original axiom
        axiom = getAxiomById(result.getClassAxioms(), AXIOM_ID);
        assertTrue(axiom.isActive());
        assertTrue(axiom.isReleased());
        assertEquals(FULLY_DEFINED, axiom.getDefinitionStatusId());
        assertEquals(MODULE_B, axiom.getModuleId());

        // Axiom from Source
        axiom = getAxiomById(result.getClassAxioms(), newAxiomOnSourceId);
        assertTrue(axiom.isActive());
        assertFalse(axiom.isReleased());
        assertEquals(PRIMITIVE, axiom.getDefinitionStatusId());
        assertEquals(MODULE_A, axiom.getModuleId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetHasExtraClassAxiom() {
        // Create conflicting authoring changes
        sourceConcept.getClassAxioms().iterator().next().setModuleId(MODULE_B);
        sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().setModuleId(MODULE_B);
        sourceConcept.getClassAxioms().iterator().next().getReferenceSetMember().updateEffectiveTime();

        String newAxiomOnTargetId = UUID.randomUUID().toString();
        Axiom newAxiomOnTarget = new Axiom();
        newAxiomOnTarget.setAxiomId(newAxiomOnTargetId);
        newAxiomOnTarget.setActive(true);
        newAxiomOnTarget.setDefinitionStatusId(PRIMITIVE);
        newAxiomOnTarget.setModuleId(MODULE_A);
        newAxiomOnTarget.setReferenceSetMember(new ReferenceSetMember());
        targetConceptAfter.getClassAxioms().add(newAxiomOnTarget);

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(2, result.getClassAxioms().size());
        Axiom axiom;

        // Original axiom
        axiom = getAxiomById(result.getClassAxioms(), AXIOM_ID);
        assertTrue(axiom.isActive());
        assertTrue(axiom.isReleased());
        assertEquals(FULLY_DEFINED, axiom.getDefinitionStatusId());
        assertEquals(MODULE_B, axiom.getModuleId());

        // Axiom from Target
        axiom = getAxiomById(result.getClassAxioms(), newAxiomOnTargetId);
        assertTrue(axiom.isActive());
        assertFalse(axiom.isReleased());
        assertEquals(PRIMITIVE, axiom.getDefinitionStatusId());
        assertEquals(MODULE_A, axiom.getModuleId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenSourceAndTargetHasExtraClassAxiom() {
        // Create conflicting authoring changes
        String newAxiomOnSourceId = UUID.randomUUID().toString();
        Axiom newAxiomOnSource = new Axiom();
        newAxiomOnSource.setAxiomId(newAxiomOnSourceId);
        newAxiomOnSource.setActive(true);
        newAxiomOnSource.setDefinitionStatusId(PRIMITIVE);
        newAxiomOnSource.setModuleId(MODULE_A);
        newAxiomOnSource.setReferenceSetMember(new ReferenceSetMember());
        sourceConcept.getClassAxioms().add(newAxiomOnSource);

        String newAxiomOnTargetId = UUID.randomUUID().toString();
        Axiom newAxiomOnTarget = new Axiom();
        newAxiomOnTarget.setAxiomId(newAxiomOnTargetId);
        newAxiomOnTarget.setActive(true);
        newAxiomOnTarget.setDefinitionStatusId(PRIMITIVE);
        newAxiomOnTarget.setModuleId(MODULE_A);
        newAxiomOnTarget.setReferenceSetMember(new ReferenceSetMember());
        targetConceptAfter.getClassAxioms().add(newAxiomOnTarget);

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(3, result.getClassAxioms().size());
        Axiom axiom;

        // Original axiom
        axiom = getAxiomById(result.getClassAxioms(), AXIOM_ID);
        assertTrue(axiom.isActive());
        assertTrue(axiom.isReleased());
        assertEquals(FULLY_DEFINED, axiom.getDefinitionStatusId());
        assertEquals(MODULE_A, axiom.getModuleId());

        // Axiom from Target
        axiom = getAxiomById(result.getClassAxioms(), newAxiomOnSourceId);
        assertTrue(axiom.isActive());
        assertFalse(axiom.isReleased());
        assertEquals(PRIMITIVE, axiom.getDefinitionStatusId());
        assertEquals(MODULE_A, axiom.getModuleId());

        // Axiom from Target
        axiom = getAxiomById(result.getClassAxioms(), newAxiomOnTargetId);
        assertTrue(axiom.isActive());
        assertFalse(axiom.isReleased());
        assertEquals(PRIMITIVE, axiom.getDefinitionStatusId());
        assertEquals(MODULE_A, axiom.getModuleId());
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetLangMemberHaveNotChanged() {
        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(2, result.getDescription("101").getLangRefsetMembers().size());

        // US Preferred
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getEffectiveTimeI());
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleasedEffectiveTime());
        assertEquals(
                sourceConcept.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleaseHash(),
                result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleaseHash()
        );
        assertTrue(result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).isReleased());
        assertTrue(result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).isActive());
        assertEquals(MODULE_A, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getModuleId());
        assertEquals(US_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReferencedComponentId());
        assertEquals(Concepts.PREFERRED, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));

        // GB Preferred
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getEffectiveTimeI());
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleasedEffectiveTime());
        assertEquals(
                sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleaseHash(),
                result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleaseHash()
        );
        assertTrue(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).isReleased());
        assertTrue(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).isActive());
        assertEquals(MODULE_A, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getModuleId());
        assertEquals(GB_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReferencedComponentId());
        assertEquals(Concepts.PREFERRED, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetLangMemberHasChangedActive() {
        // Create conflicting authoring changes
        sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).setModuleId(MODULE_B);
        sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).updateEffectiveTime();

        targetConceptAfter.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).setActive(false);
        targetConceptAfter.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(2, result.getDescription("101").getLangRefsetMembers().size());

        // US Preferred
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getEffectiveTimeI());
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleasedEffectiveTime());
        assertEquals(
                sourceConcept.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleaseHash(),
                result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleaseHash()
        );
        assertTrue(result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).isReleased());
        assertTrue(result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).isActive());
        assertEquals(MODULE_A, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getModuleId());
        assertEquals(US_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReferencedComponentId());
        assertEquals(Concepts.PREFERRED, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));

        // GB Preferred
        assertNull(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getEffectiveTimeI());
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleasedEffectiveTime());
        assertEquals(
                sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleaseHash(),
                result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleaseHash()
        );
        assertTrue(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).isReleased());
        assertFalse(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).isActive());
        assertEquals(MODULE_B, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getModuleId());
        assertEquals(GB_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReferencedComponentId());
        assertEquals(Concepts.PREFERRED, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetLangMemberHasChangedModule() {
        // Create conflicting authoring changes
        sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).setActive(false);
        sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).updateEffectiveTime();

        targetConceptAfter.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).setModuleId(MODULE_B);
        targetConceptAfter.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(2, result.getDescription("101").getLangRefsetMembers().size());

        // US Preferred
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getEffectiveTimeI());
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleasedEffectiveTime());
        assertEquals(
                sourceConcept.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleaseHash(),
                result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleaseHash()
        );
        assertTrue(result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).isReleased());
        assertTrue(result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).isActive());
        assertEquals(MODULE_A, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getModuleId());
        assertEquals(US_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReferencedComponentId());
        assertEquals(Concepts.PREFERRED, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));

        // GB Preferred
        assertNull(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getEffectiveTimeI());
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleasedEffectiveTime());
        assertEquals(
                sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleaseHash(),
                result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleaseHash()
        );
        assertTrue(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).isReleased());
        assertFalse(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).isActive());
        assertEquals(MODULE_B, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getModuleId());
        assertEquals(GB_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReferencedComponentId());
        assertEquals(Concepts.PREFERRED, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetLangMemberHasChangedRefsetId() {
        // Create conflicting authoring changes
        sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).setActive(false);
        sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).updateEffectiveTime();

        targetConceptAfter.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).setRefsetId(US_EN_LANG_REFSET);
        targetConceptAfter.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(2, result.getDescription("101").getLangRefsetMembers().size());

        // US Preferred
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getEffectiveTimeI());
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleasedEffectiveTime());
        assertEquals(
                sourceConcept.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleaseHash(),
                result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleaseHash()
        );
        assertTrue(result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).isReleased());
        assertTrue(result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).isActive());
        assertEquals(MODULE_A, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getModuleId());
        assertEquals(US_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReferencedComponentId());
        assertEquals(Concepts.PREFERRED, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));

        // GB Preferred
        assertNull(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getEffectiveTimeI());
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleasedEffectiveTime());
        assertEquals(
                sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleaseHash(),
                result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleaseHash()
        );
        assertTrue(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).isReleased());
        assertFalse(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).isActive());
        assertEquals(MODULE_A, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getModuleId());
        // Can't change as member has been published
        assertEquals(GB_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReferencedComponentId());
        assertEquals(Concepts.PREFERRED, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetLangMemberHasChangedAcceptabilityId() {
        // Create conflicting authoring changes
        sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).setActive(false);
        sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).updateEffectiveTime();

        targetConceptAfter.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).setAdditionalField(
                ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID,
                Concepts.ACCEPTABLE
        );
        targetConceptAfter.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(2, result.getDescription("101").getLangRefsetMembers().size());

        // US Preferred
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getEffectiveTimeI());
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleasedEffectiveTime());
        assertEquals(
                sourceConcept.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleaseHash(),
                result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleaseHash()
        );
        assertTrue(result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).isReleased());
        assertTrue(result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).isActive());
        assertEquals(MODULE_A, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getModuleId());
        assertEquals(US_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReferencedComponentId());
        assertEquals(Concepts.PREFERRED, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));

        // GB Preferred
        assertNull(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getEffectiveTimeI());
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleasedEffectiveTime());
        assertEquals(
                sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleaseHash(),
                result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleaseHash()
        );
        assertTrue(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).isReleased());
        assertFalse(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).isActive());
        assertEquals(MODULE_A, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getModuleId());
        assertEquals(GB_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReferencedComponentId());
        assertEquals(Concepts.PREFERRED, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));
    }

    @Test
    void autoMerge_ShouldFavourTargetLangMemberChanges() {
        // Create conflicting authoring changes
        sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).setModuleId(MODULE_B);
        sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).updateEffectiveTime();

        targetConceptAfter.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).setModuleId(MODULE_C);
        targetConceptAfter.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(2, result.getDescription("101").getLangRefsetMembers().size());

        // US Preferred
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getEffectiveTimeI());
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleasedEffectiveTime());
        assertEquals(
                sourceConcept.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleaseHash(),
                result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleaseHash()
        );
        assertTrue(result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).isReleased());
        assertTrue(result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).isActive());
        assertEquals(MODULE_A, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getModuleId());
        assertEquals(US_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReferencedComponentId());
        assertEquals(Concepts.PREFERRED, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));

        // GB Preferred
        assertNull(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getEffectiveTimeI());
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleasedEffectiveTime());
        assertEquals(
                sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleaseHash(),
                result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleaseHash()
        );
        assertTrue(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).isReleased());
        assertTrue(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).isActive());
        assertEquals(MODULE_C, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getModuleId());
        assertEquals(GB_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReferencedComponentId());
        assertEquals(Concepts.PREFERRED, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetLangMemberIsVersionAhead() {
        // Create conflicting authoring changes
        sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).setModuleId(MODULE_B);
        setReleaseDetails(sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID), 20220228);

        targetConceptAfter.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).setActive(false);
        targetConceptAfter.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(2, result.getDescription("101").getLangRefsetMembers().size());

        // US Preferred
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getEffectiveTimeI());
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleasedEffectiveTime());
        assertEquals(
                sourceConcept.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleaseHash(),
                result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleaseHash()
        );
        assertTrue(result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).isReleased());
        assertTrue(result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).isActive());
        assertEquals(MODULE_A, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getModuleId());
        assertEquals(US_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReferencedComponentId());
        assertEquals(Concepts.PREFERRED, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));

        // GB Preferred
        assertNull(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getEffectiveTimeI());
        assertEquals(20220228, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleasedEffectiveTime());
        assertEquals(
                sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleaseHash(),
                result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleaseHash()
        );
        assertTrue(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).isReleased());
        assertFalse(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).isActive());
        assertEquals(MODULE_B, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getModuleId());
        assertEquals(GB_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReferencedComponentId());
        assertEquals(Concepts.PREFERRED, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenSourceHasExtraLangMember() {
        // Create conflicting authoring changes
        String newSourceLangMemberId = UUID.randomUUID().toString();
        sourceConcept.getDescription("101").addLanguageRefsetMember(
                new ReferenceSetMember()
                        .setMemberId(newSourceLangMemberId)
                        .setActive(true)
                        .setModuleId(MODULE_A)
                        .setRefsetId(GB_EN_LANG_REFSET)
                        .setReferencedComponentId("101")
                        .setConceptId("100")
                        .setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, Concepts.ACCEPTABLE)
        );

        targetConceptAfter.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).setActive(false);
        targetConceptAfter.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(3, result.getDescription("101").getLangRefsetMembers().size());

        // US Preferred
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getEffectiveTimeI());
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleasedEffectiveTime());
        assertEquals(
                sourceConcept.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleaseHash(),
                result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleaseHash()
        );
        assertTrue(result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).isReleased());
        assertTrue(result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).isActive());
        assertEquals(MODULE_A, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getModuleId());
        assertEquals(US_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReferencedComponentId());
        assertEquals(Concepts.PREFERRED, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));

        // GB Preferred
        assertNull(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getEffectiveTimeI());
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleasedEffectiveTime());
        assertEquals(
                sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleaseHash(),
                result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleaseHash()
        );
        assertTrue(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).isReleased());
        assertFalse(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).isActive());
        assertEquals(MODULE_A, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getModuleId());
        assertEquals(GB_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReferencedComponentId());
        assertEquals(Concepts.PREFERRED, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));

        // New Source Lang Member
        assertNull(result.getDescription("101").getLangRefsetMember(newSourceLangMemberId).getEffectiveTimeI());
        assertNull(result.getDescription("101").getLangRefsetMember(newSourceLangMemberId).getReleasedEffectiveTime());
        assertNull(result.getDescription("101").getLangRefsetMember(newSourceLangMemberId).getReleaseHash());
        assertFalse(result.getDescription("101").getLangRefsetMember(newSourceLangMemberId).isReleased());
        assertTrue(result.getDescription("101").getLangRefsetMember(newSourceLangMemberId).isActive());
        assertEquals(MODULE_A, result.getDescription("101").getLangRefsetMember(newSourceLangMemberId).getModuleId());
        assertEquals(GB_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(newSourceLangMemberId).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(newSourceLangMemberId).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(newSourceLangMemberId).getReferencedComponentId());
        assertEquals(Concepts.ACCEPTABLE, result.getDescription("101").getLangRefsetMember(newSourceLangMemberId).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenTargetHasExtraLangMember() {
        // Create conflicting authoring changes
        sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).setActive(false);
        sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).updateEffectiveTime();

        String newTargetLangMemberId = UUID.randomUUID().toString();
        targetConceptAfter.getDescription("101").addLanguageRefsetMember(
                new ReferenceSetMember()
                        .setMemberId(newTargetLangMemberId)
                        .setActive(true)
                        .setModuleId(MODULE_A)
                        .setRefsetId(GB_EN_LANG_REFSET)
                        .setReferencedComponentId("101")
                        .setConceptId("100")
                        .setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, Concepts.ACCEPTABLE)
        );

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(3, result.getDescription("101").getLangRefsetMembers().size());

        // US Preferred
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getEffectiveTimeI());
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleasedEffectiveTime());
        assertEquals(
                sourceConcept.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleaseHash(),
                result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleaseHash()
        );
        assertTrue(result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).isReleased());
        assertTrue(result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).isActive());
        assertEquals(MODULE_A, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getModuleId());
        assertEquals(US_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReferencedComponentId());
        assertEquals(Concepts.PREFERRED, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));

        // GB Preferred
        assertNull(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getEffectiveTimeI());
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleasedEffectiveTime());
        assertEquals(
                sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleaseHash(),
                result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleaseHash()
        );
        assertTrue(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).isReleased());
        assertFalse(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).isActive());
        assertEquals(MODULE_A, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getModuleId());
        assertEquals(GB_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReferencedComponentId());
        assertEquals(Concepts.PREFERRED, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));

        // New Target Lang Member
        assertNull(result.getDescription("101").getLangRefsetMember(newTargetLangMemberId).getEffectiveTimeI());
        assertNull(result.getDescription("101").getLangRefsetMember(newTargetLangMemberId).getReleasedEffectiveTime());
        assertNull(result.getDescription("101").getLangRefsetMember(newTargetLangMemberId).getReleaseHash());
        assertFalse(result.getDescription("101").getLangRefsetMember(newTargetLangMemberId).isReleased());
        assertTrue(result.getDescription("101").getLangRefsetMember(newTargetLangMemberId).isActive());
        assertEquals(MODULE_A, result.getDescription("101").getLangRefsetMember(newTargetLangMemberId).getModuleId());
        assertEquals(GB_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(newTargetLangMemberId).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(newTargetLangMemberId).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(newTargetLangMemberId).getReferencedComponentId());
        assertEquals(Concepts.ACCEPTABLE, result.getDescription("101").getLangRefsetMember(newTargetLangMemberId).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenSourceAndTargetHaveExtraLangMembers() {
        // Create conflicting authoring changes
        String newSourceLangMemberId = UUID.randomUUID().toString();
        sourceConcept.getDescription("101").addLanguageRefsetMember(
                new ReferenceSetMember()
                        .setMemberId(newSourceLangMemberId)
                        .setActive(true)
                        .setModuleId(MODULE_A)
                        .setRefsetId(GB_EN_LANG_REFSET)
                        .setReferencedComponentId("101")
                        .setConceptId("100")
                        .setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, Concepts.ACCEPTABLE)
        );

        String newTargetLangMemberId = UUID.randomUUID().toString();
        targetConceptAfter.getDescription("101").addLanguageRefsetMember(
                new ReferenceSetMember()
                        .setMemberId(newTargetLangMemberId)
                        .setActive(true)
                        .setModuleId(MODULE_A)
                        .setRefsetId(GB_EN_LANG_REFSET)
                        .setReferencedComponentId("101")
                        .setConceptId("100")
                        .setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, Concepts.ACCEPTABLE)
        );

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        assertEquals(4, result.getDescription("101").getLangRefsetMembers().size());

        // US Preferred
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getEffectiveTimeI());
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleasedEffectiveTime());
        assertEquals(
                sourceConcept.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleaseHash(),
                result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReleaseHash()
        );
        assertTrue(result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).isReleased());
        assertTrue(result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).isActive());
        assertEquals(MODULE_A, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getModuleId());
        assertEquals(US_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getReferencedComponentId());
        assertEquals(Concepts.PREFERRED, result.getDescription("101").getLangRefsetMember(US_PREFERRED_MEMBER_ID).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));

        // GB Preferred
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getEffectiveTimeI());
        assertEquals(20220131, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleasedEffectiveTime());
        assertEquals(
                sourceConcept.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleaseHash(),
                result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReleaseHash()
        );
        assertTrue(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).isReleased());
        assertTrue(result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).isActive());
        assertEquals(MODULE_A, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getModuleId());
        assertEquals(GB_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getReferencedComponentId());
        assertEquals(Concepts.PREFERRED, result.getDescription("101").getLangRefsetMember(GB_PREFERRED_MEMBER_ID).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));

        // New Source Lang Member
        assertNull(result.getDescription("101").getLangRefsetMember(newSourceLangMemberId).getEffectiveTimeI());
        assertNull(result.getDescription("101").getLangRefsetMember(newSourceLangMemberId).getReleasedEffectiveTime());
        assertNull(result.getDescription("101").getLangRefsetMember(newSourceLangMemberId).getReleaseHash());
        assertFalse(result.getDescription("101").getLangRefsetMember(newSourceLangMemberId).isReleased());
        assertTrue(result.getDescription("101").getLangRefsetMember(newSourceLangMemberId).isActive());
        assertEquals(MODULE_A, result.getDescription("101").getLangRefsetMember(newSourceLangMemberId).getModuleId());
        assertEquals(GB_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(newSourceLangMemberId).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(newSourceLangMemberId).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(newSourceLangMemberId).getReferencedComponentId());
        assertEquals(Concepts.ACCEPTABLE, result.getDescription("101").getLangRefsetMember(newSourceLangMemberId).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));

        // New Target Lang Member
        assertNull(result.getDescription("101").getLangRefsetMember(newTargetLangMemberId).getEffectiveTimeI());
        assertNull(result.getDescription("101").getLangRefsetMember(newTargetLangMemberId).getReleasedEffectiveTime());
        assertNull(result.getDescription("101").getLangRefsetMember(newTargetLangMemberId).getReleaseHash());
        assertFalse(result.getDescription("101").getLangRefsetMember(newTargetLangMemberId).isReleased());
        assertTrue(result.getDescription("101").getLangRefsetMember(newTargetLangMemberId).isActive());
        assertEquals(MODULE_A, result.getDescription("101").getLangRefsetMember(newTargetLangMemberId).getModuleId());
        assertEquals(GB_EN_LANG_REFSET, result.getDescription("101").getLangRefsetMember(newTargetLangMemberId).getRefsetId());
        assertEquals("100", result.getDescription("101").getLangRefsetMember(newTargetLangMemberId).getConceptId());
        assertEquals("101", result.getDescription("101").getLangRefsetMember(newTargetLangMemberId).getReferencedComponentId());
        assertEquals(Concepts.ACCEPTABLE, result.getDescription("101").getLangRefsetMember(newTargetLangMemberId).getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));
    }

    @Test
    void autoMerge_ShouldReturnExpected_WhenSourceHasVersionedAndTargetHasInactivated() {
        // Create conflicting authoring changes
        sourceConcept.setDefinitionStatusId(PRIMITIVE);
        sourceConcept.getClassAxioms().iterator().next().setDefinitionStatusId(PRIMITIVE);
        setReleaseDetails(sourceConcept, 20220228);
        setReleaseDetails(sourceConcept.getClassAxioms().iterator().next(), 20220228);

        targetConceptAfter.setActive(false);
        targetConceptAfter.updateEffectiveTime();
        targetConceptAfter.getClassAxioms().iterator().next().setActive(false);
        targetConceptAfter.getClassAxioms().iterator().next().getReferenceSetMember().setActive(false);
        targetConceptAfter.getClassAxioms().iterator().next().getReferenceSetMember().updateEffectiveTime();

        // Auto-merge Concepts
        Concept result = autoMerger.autoMerge(sourceConcept, targetConceptAfter, "MAIN/project");

        // Assert components
        // Concept
        assertFalse(result.isActive());
        assertNull(result.getEffectiveTimeI());
        assertEquals(20220228, result.getReleasedEffectiveTime());
        assertEquals(PRIMITIVE, result.getDefinitionStatusId());

        // Axiom
        assertFalse(result.getClassAxioms().iterator().next().isActive());
        assertNull(result.getClassAxioms().iterator().next().getReferenceSetMember().getEffectiveTimeI());
        assertEquals(20220228, result.getClassAxioms().iterator().next().getReferenceSetMember().getReleasedEffectiveTime());
        assertEquals(PRIMITIVE, result.getClassAxioms().iterator().next().getDefinitionStatusId());
    }

    private Concept buildConcept() {
        Relationship isAInferred = new Relationship("102");
        isAInferred.setActive(true);
        isAInferred.setModuleId(MODULE_A);
        isAInferred.setSourceId("100");
        isAInferred.setDestinationId(PHYSICAL_OBJECT);
        isAInferred.setRelationshipGroup(0);
        isAInferred.setTypeId(ISA);
        isAInferred.setCharacteristicTypeId(INFERRED_RELATIONSHIP);
        isAInferred.setModifier("EXISTENTIAL");

        Relationship isAStated = new Relationship("102");
        isAStated.setActive(true);
        isAStated.setModuleId(MODULE_A);
        isAStated.setSourceId("100");
        isAStated.setDestinationId(PHYSICAL_OBJECT);
        isAStated.setRelationshipGroup(0);
        isAStated.setTypeId(ISA);
        isAStated.setCharacteristicTypeId(INFERRED_RELATIONSHIP);
        isAStated.setModifier("EXISTENTIAL");

        ReferenceSetMember classAxiomMember = new ReferenceSetMember();
        classAxiomMember.setAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION, "SubClassOf(:101 :260787004)");
        classAxiomMember.setMemberId(AXIOM_ID);
        classAxiomMember.setActive(true);
        classAxiomMember.setModuleId(MODULE_A);
        classAxiomMember.setReferencedComponentId("101");
        classAxiomMember.setRefsetId(OWL_AXIOM_REFERENCE_SET);

        Axiom classAxiom = new Axiom();
        classAxiom.setActive(true);
        classAxiom.setAxiomId(AXIOM_ID);
        classAxiom.setModuleId(MODULE_A);
        classAxiom.setDefinitionStatusId(FULLY_DEFINED);
        classAxiom.setRelationships(Set.of(
                isAStated
        ));
        classAxiom.setReferenceSetMember(classAxiomMember);

        ReferenceSetMember usPreferred = new ReferenceSetMember();
        usPreferred.setMemberId(US_PREFERRED_MEMBER_ID);
        usPreferred.setActive(true);
        usPreferred.setModuleId(MODULE_A);
        usPreferred.setRefsetId(US_EN_LANG_REFSET);
        usPreferred.setReferencedComponentId("101");
        usPreferred.setConceptId("100");
        usPreferred.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, Concepts.PREFERRED);

        ReferenceSetMember gbPreferred = new ReferenceSetMember();
        gbPreferred.setMemberId(GB_PREFERRED_MEMBER_ID);
        gbPreferred.setActive(true);
        gbPreferred.setModuleId(MODULE_A);
        gbPreferred.setRefsetId(GB_EN_LANG_REFSET);
        gbPreferred.setReferencedComponentId("101");
        gbPreferred.setConceptId("100");
        gbPreferred.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, Concepts.PREFERRED);

        Set<ReferenceSetMember> languageReferenceSetMembers = Set.of(usPreferred, gbPreferred);

        Description vehicleFsn = new Description("101", "Vehicle (physical object)")
                .setActive(true)
                .setModuleId(MODULE_A)
                .setConceptId("100")
                .setLanguageCode("en")
                .setTypeId(FSN)
                .setCaseSignificanceId(CASE_INSENSITIVE)
                .setAcceptabilityMap(PREFERRED)
                .setLanguageRefsetMembers(languageReferenceSetMembers);

        Concept concept = new Concept("100")
                .setActive(true)
                .setModuleId(MODULE_A)
                .setDefinitionStatusId(FULLY_DEFINED);

        concept.addDescription(vehicleFsn);
        concept.addRelationship(isAInferred);
        concept.addAxiom(classAxiom);

        setReleaseDetails(concept, 20220131);
        setReleaseDetails(vehicleFsn, 20220131);
        setReleaseDetails(isAInferred, 20220131);
        setReleaseDetails(usPreferred, 20220131);
        setReleaseDetails(gbPreferred, 20220131);
        setReleaseDetails(classAxiom, 20220131);
        setReleaseDetails(classAxiomMember, 20220131);

        return concept;
    }

    private void setReleaseDetails(SnomedComponent<?> snomedComponent, Integer effectiveTime) {
        snomedComponent.setEffectiveTimeI(effectiveTime);
        snomedComponent.setReleasedEffectiveTime(effectiveTime);
        snomedComponent.setReleased(true);
        snomedComponent.setReleaseHash(snomedComponent.buildReleaseHash());
    }

    private void setReleaseDetails(Axiom axiom, Integer effectiveTime) {
        axiom.setReleased(true);
        setReleaseDetails(axiom.getReferenceSetMember(), effectiveTime);
    }

    private void clearReleaseDetails(SnomedComponent<?> snomedComponent) {
        snomedComponent.setEffectiveTimeI(null);
        snomedComponent.setReleasedEffectiveTime(null);
        snomedComponent.setReleased(false);
        snomedComponent.setReleaseHash(null);
    }

    private Concept clone(Concept originalConcept) {
        Concept clone = new Concept();
        clone.clone(originalConcept);

        return clone;
    }

    private Axiom getAxiomById(Set<Axiom> axioms, String axiomId) {
        for (Axiom axiom : axioms) {
            if (axiomId.equals(axiom.getAxiomId())) {
                return axiom;
            }
        }

        return null;
    }

    private void givenTargetConceptBeforeAuthoringChanges() {
        Mockito.when(conceptService.find(anyString(), anyList(), Mockito.any(BranchTimepoint.class))).thenReturn(targetConceptBefore);
    }
}
