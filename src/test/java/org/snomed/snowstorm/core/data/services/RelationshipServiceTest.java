package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.internal.util.collections.Sets;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class RelationshipServiceTest extends AbstractTest {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private RelationshipService relationshipService;

    @Autowired
    private BranchService branchService;

    @Autowired
    private ReferenceSetMemberService referenceSetMemberService;

	private static final String MAIN = "MAIN";

	@BeforeEach
	void setup() throws ServiceException {
		conceptService.create(new Concept(Concepts.SNOMEDCT_ROOT), MAIN);
		conceptService.create(new Concept(Concepts.CORE_MODULE).addRelationship(new Relationship("200000022", Concepts.ISA, Concepts.SNOMEDCT_ROOT)), MAIN);
		conceptService.create(new Concept(Concepts.CLINICAL_FINDING).addRelationship(new Relationship("1000000022", Concepts.ISA, Concepts.SNOMEDCT_ROOT)), MAIN);
	}

    private void givenBranchExists() {
        //given
        branchService.create("MAIN/CDI-90");
    }

    private void givenConceptExistsOnBranch() throws ServiceException {
        //given
        final Relationship relationship = new Relationship("20988805027", 20210131, true, "900000000000207008", "376792007", "#2", 1, "3311482005", "900000000000011006", "900000000000451002");
        final Concept concept = new Concept("12345");
        concept.addRelationship(relationship);

        conceptService.create(concept, "MAIN/CDI-90");
    }

    private void givenReferenceSetMemberExistsOnBranchWithRangeConstraint(final String rangeConstraint) {
        //given
        final ReferenceSetMember referenceSetMember = new ReferenceSetMember();
        referenceSetMember.setRefsetId(Concepts.REFSET_MRCM_ATTRIBUTE_RANGE_INTERNATIONAL);
        referenceSetMember.setReferencedComponentId("3311482005");
        referenceSetMember.setAdditionalField("rangeConstraint", rangeConstraint);

        referenceSetMemberService.createMember("MAIN/CDI-90", referenceSetMember);
    }

	@Test
	void testDelete() {
		assertEquals(2, findAll().size());
		relationshipService.deleteRelationship("1000000022", MAIN, false);
		assertEquals(1, findAll().size());
	}

	@Test
	void testBulkDelete() {
		assertEquals(2, findAll().size());
		relationshipService.deleteRelationships(Collections.emptySet(), MAIN, false);
		assertEquals(2, findAll().size());
		relationshipService.deleteRelationships(Sets.newSet("200000022", "1000000022"), MAIN, false);
		assertEquals(0, findAll().size());
	}

    @Test
    public void findRelationship_ShouldReturnRelationshipWithConcreteDataTypeString_WhenMRCMIsString() throws ServiceException {
        //given
        givenBranchExists();
        givenConceptExistsOnBranch();
        givenReferenceSetMemberExistsOnBranchWithRangeConstraint("str(\"PANADOL\" \"TYLENOL\" \"HERRON\")");

        //when
        final Relationship result = relationshipService.findRelationship("MAIN/CDI-90", "20988805027");
        final ConcreteValue concreteValue = result.getConcreteValue();
        final ConcreteValue.DataType dataType = concreteValue.getDataType();

        //then
        assertEquals(ConcreteValue.DataType.STRING, dataType);
    }

    @Test
    public void findRelationship_ShouldReturnRelationshipWithConcreteDataTypeInteger_WhenMRCMIsInteger() throws ServiceException {
        //given
        givenBranchExists();
        givenConceptExistsOnBranch();
        givenReferenceSetMemberExistsOnBranchWithRangeConstraint("int(#5)");

        //when
        final Relationship result = relationshipService.findRelationship("MAIN/CDI-90", "20988805027");
        final ConcreteValue concreteValue = result.getConcreteValue();
        final ConcreteValue.DataType dataType = concreteValue.getDataType();

        //then
        assertEquals(ConcreteValue.DataType.INTEGER, dataType);
    }

    @Test
    public void findRelationship_ShouldReturnRelationshipWithConcreteDataTypeDecimal_WhenMRCMIsDecimal() throws ServiceException {
        //given
        givenBranchExists();
        givenConceptExistsOnBranch();
        givenReferenceSetMemberExistsOnBranchWithRangeConstraint("dec(#2)"); //purposely missing decimal point

        //when
        final Relationship result = relationshipService.findRelationship("MAIN/CDI-90", "20988805027");
        final ConcreteValue concreteValue = result.getConcreteValue();
        final ConcreteValue.DataType dataType = concreteValue.getDataType();

        //then
        assertEquals(ConcreteValue.DataType.DECIMAL, dataType);
    }

    @Test
    public void setConcreteValueFromMRCM_ShouldReturnRelationshipWithConcreteDataTypeString_WhenMRCMIsString() throws ServiceException {
        //given
        givenBranchExists();
        givenConceptExistsOnBranch();
        givenReferenceSetMemberExistsOnBranchWithRangeConstraint("str(\"PANADOL\" \"TYLENOL\" \"HERRON\")");
        final Relationship relationship = relationshipService.findRelationship("MAIN/CDI-90", "20988805027");

        //when
        relationshipService.setConcreteValueFromMRCM("MAIN/CDI-90", relationship);
        final ConcreteValue concreteValue = relationship.getConcreteValue();
        final ConcreteValue.DataType dataType = concreteValue.getDataType();

        //then
        assertEquals(ConcreteValue.DataType.STRING, dataType);
    }

    @Test
    public void setConcreteValueFromMRCM_ShouldReturnRelationshipWithConcreteDataTypeInteger_WhenMRCMIsInteger() throws ServiceException {
        //given
        givenBranchExists();
        givenConceptExistsOnBranch();
        givenReferenceSetMemberExistsOnBranchWithRangeConstraint("int(#2)");
        final Relationship relationship = relationshipService.findRelationship("MAIN/CDI-90", "20988805027");

        //when
        relationshipService.setConcreteValueFromMRCM("MAIN/CDI-90", relationship);
        final ConcreteValue concreteValue = relationship.getConcreteValue();
        final ConcreteValue.DataType dataType = concreteValue.getDataType();

        //then
        assertEquals(ConcreteValue.DataType.INTEGER, dataType);
    }

    @Test
    public void setConcreteValueFromMRCM_ShouldReturnRelationshipWithConcreteDataTypeDecimal_WhenMRCMIsDecimal() throws ServiceException {
        //given
        givenBranchExists();
        givenConceptExistsOnBranch();
        givenReferenceSetMemberExistsOnBranchWithRangeConstraint("dec(#2)"); //purposely missing decimal point
        final Relationship relationship = relationshipService.findRelationship("MAIN/CDI-90", "20988805027");

        //when
        relationshipService.setConcreteValueFromMRCM("MAIN/CDI-90", relationship);
        final ConcreteValue concreteValue = relationship.getConcreteValue();
        final ConcreteValue.DataType dataType = concreteValue.getDataType();

        //then
        assertEquals(ConcreteValue.DataType.DECIMAL, dataType);
    }


	public List<Relationship> findAll() {
		return relationshipService.findRelationships(MAIN, null, null, null, null, null, null, null, null, null, PageRequest.of(0, 10)).getContent();
	}

}
