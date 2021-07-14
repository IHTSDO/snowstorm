package org.snomed.snowstorm.validation;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Metadata;
import org.ihtsdo.drools.response.InvalidContent;
import org.ihtsdo.drools.response.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.BranchMetadataKeys;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class DroolsValidationServiceTest extends AbstractTest {
    private static final String DEFAULT_BRANCH = "MAIN";

    @Autowired
    private ConceptService conceptService;

    @Autowired
    private DroolsValidationService droolValidationService;

    @Autowired
    private BranchService branchService;

    @Autowired
    private ReferenceSetMemberService referenceSetMemberService;

    @BeforeEach
    void setup() throws ServiceException {
        branchService.updateMetadata(DEFAULT_BRANCH, new Metadata().putString(BranchMetadataKeys.ASSERTION_GROUP_NAMES, "common-authoring"));

        conceptService.create(new Concept(SNOMEDCT_ROOT, null, true, CORE_MODULE, PRIMITIVE), DEFAULT_BRANCH);
        conceptService.create(new Concept(ISA, null, true, CORE_MODULE, Concepts.PRIMITIVE), DEFAULT_BRANCH);
        conceptService.create(new Concept("23131313", null, true, CORE_MODULE, Concepts.PRIMITIVE), DEFAULT_BRANCH);

        String conceptId = "100001";
        Concept concept = new Concept(conceptId, null, true, CORE_MODULE, PRIMITIVE);
        concept.addDescription(new Description("12220000", null, true, CORE_MODULE, conceptId, "en", FSN, "Test (event)", Concepts.CASE_INSENSITIVE));
        concept.addDescription(new Description("12220003", null, true, CORE_MODULE, conceptId, "en", SYNONYM, "Test", CASE_INSENSITIVE));
        conceptService.create(concept, DEFAULT_BRANCH);

        Set <ReferenceSetMember> attributeRanges = new HashSet<>();
        attributeRanges.add(constructMrcmRange("23131313", "int(>#0..#20)"));
        referenceSetMemberService.createMembers(DEFAULT_BRANCH, attributeRanges);
    }

    @Test
    void testValidateRegularConcept() throws ServiceException {
        Concept foundConcept = conceptService.find("100001", DEFAULT_BRANCH);
        assertNotNull(foundConcept);

        foundConcept.getDescriptions().forEach(description -> description.setAcceptabilityMap(Collections.singletonMap(Concepts.US_EN_LANG_REFSET,
                Concepts.descriptionAcceptabilityNames.get(PREFERRED))));

        foundConcept.addRelationship(new Relationship("100002", ISA, SNOMEDCT_ROOT));
        foundConcept.addAxiom(new Relationship("100002", ISA, SNOMEDCT_ROOT));

        final Concept updatedConcept = conceptService.update(foundConcept, DEFAULT_BRANCH);
        assertEquals(1, updatedConcept.getClassAxioms().size());
		List<InvalidContent> invalidContents = droolValidationService.validateConcepts(DEFAULT_BRANCH, Collections.singleton(updatedConcept), false);
        assertEquals(3, invalidContents.size());

        int index = 0;
        assertEquals(Severity.WARNING, invalidContents.get(index).getSeverity());
        assertEquals("Test resources were not available so assertions like case significance and US specific terms checks will not have run.", invalidContents.get(index).getMessage());
        assertEquals("setup-issue", invalidContents.get(index).getRuleId());

        index++;
        assertEquals(Severity.WARNING, invalidContents.get(index).getSeverity());
        assertEquals("Active FSN should end with a valid semantic tag.", invalidContents.get(index).getMessage());
		assertEquals("011559f8-a333-4317-8764-cf0ab41e42c0", invalidContents.get(index).getRuleId());
    }

    @Test
    void testValidateConceptWithoutRelationship() throws ServiceException {
        Concept foundConcept = conceptService.find("100001", DEFAULT_BRANCH);
        assertNotNull(foundConcept);
        foundConcept.getDescriptions().forEach(description -> description.setAcceptabilityMap(Collections.singletonMap(Concepts.US_EN_LANG_REFSET,
                Concepts.descriptionAcceptabilityNames.get(PREFERRED))));

        final Concept updatedConcept = conceptService.update(foundConcept, DEFAULT_BRANCH);

        assertEquals(0, updatedConcept.getClassAxioms().size());
		List<InvalidContent> invalidContents = droolValidationService.validateConcepts(DEFAULT_BRANCH, Collections.singleton(updatedConcept), false);
        assertEquals(4, invalidContents.size());

        int index = 0;
        assertEquals(Severity.WARNING, invalidContents.get(index).getSeverity());
        assertEquals("Test resources were not available so assertions like case significance and US specific terms checks will not have run.", invalidContents.get(index).getMessage());

        index++;
        assertEquals(Severity.ERROR, invalidContents.get(index).getSeverity());
        assertEquals("Active concepts must have at least one IS A relationship.", invalidContents.get(index).getMessage());

        index++;
        assertEquals(Severity.WARNING, invalidContents.get(index).getSeverity());
        assertEquals("Active FSN should end with a valid semantic tag.", invalidContents.get(index).getMessage());
    }

    @Test
    void testValidateConceptAgainstConcreteValue() throws ServiceException {
        Concept foundConcept = conceptService.find("100001", DEFAULT_BRANCH);
        assertNotNull(foundConcept);

        foundConcept.getDescriptions().forEach(description -> description.setAcceptabilityMap(Collections.singletonMap(Concepts.US_EN_LANG_REFSET,
                Concepts.descriptionAcceptabilityNames.get(PREFERRED))));

        foundConcept.addRelationship(new Relationship("100002", ISA, SNOMEDCT_ROOT));

        ConcreteValue value = ConcreteValue.from("10", "int");
        foundConcept.addAxiom(new Relationship("100002", ISA, SNOMEDCT_ROOT), new Relationship("100003", "23131313", value));

        final Concept updatedConcept = conceptService.update(foundConcept, DEFAULT_BRANCH);
        assertEquals(1, updatedConcept.getClassAxioms().size());

        Axiom axiom = updatedConcept.getClassAxioms().iterator().next();
        assertEquals(2, axiom.getRelationships().size());

        for (Relationship relationship : axiom.getRelationships()){
            if ("100003".equals(relationship.getRelationshipId())) {
                assertTrue(value.getDataType().equals(relationship.getConcreteValue().getDataType()));
                assertTrue(value.getValue().equals(relationship.getConcreteValue().getValue()));
            }
        }

		List<InvalidContent> invalidContents = droolValidationService.validateConcepts(DEFAULT_BRANCH, Collections.singleton(updatedConcept), false);
        assertEquals(3, invalidContents.size());

        int index = 0;
        assertEquals(Severity.WARNING, invalidContents.get(index).getSeverity());
        assertEquals("Test resources were not available so assertions like case significance and US specific terms checks will not have run.", invalidContents.get(index).getMessage());

        index++;
        assertEquals(Severity.WARNING, invalidContents.get(index).getSeverity());
        assertEquals("Active FSN should end with a valid semantic tag.", invalidContents.get(index).getMessage());
    }

    private ReferenceSetMember constructMrcmRange(String referencedComponentId, String rangeConstraint) {
        ReferenceSetMember rangeMember = new ReferenceSetMember("900000000000207008", REFSET_MRCM_ATTRIBUTE_RANGE_INTERNATIONAL, referencedComponentId);
        rangeMember.setAdditionalField("rangeConstraint", rangeConstraint);
        rangeMember.setAdditionalField("attributeRule", "");
        rangeMember.setAdditionalField("ruleStrengthId", "723597001");
        rangeMember.setAdditionalField("contentTypeId", "723596005");
        return rangeMember;
    }
}
