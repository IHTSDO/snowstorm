package org.snomed.snowstorm.core.data.services.postcoordination;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.identifier.LocalRandomIdentifierSource;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;
import org.snomed.snowstorm.core.data.services.postcoordination.model.PostCoordinatedExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.core.data.services.postcoordination.ExpressionRepositoryService.*;

class ExpressionRepositoryServiceTest extends AbstractExpressionTest {

	private static final ComparableExpression MOCK_CLASSIFIED_EXPRESSION = new ComparableExpression("404684003");
	static {
		MOCK_CLASSIFIED_EXPRESSION.setExpressionId(28984902063L);
	}

	@Autowired
	private ExpressionRepositoryService expressionRepository;

	@Autowired
	private ReferenceSetMemberService memberService;

	@Autowired
	private ExpressionParser expressionParser;

	@Autowired
	private QueryService queryService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	@MockBean
	private IncrementalClassificationService mockClassificationService;

	@Test
	public void createExpressionOrThrow() throws ServiceException {
		PostCoordinatedExpression expression = createExpressionOrThrow("83152002 |Oophorectomy|");
		String expressionId = expression.getId();
		System.out.println("Expression ID is " + expressionId);
		assertEquals("16", LocalRandomIdentifierSource.POSTCOORDINATED_EXPRESSION_PARTITION_ID);
		assertEquals("16", expressionId.substring(expressionId.length() - 3, expressionId.length() - 1));

		// Single concept
		assertEquals("===83152002",
				createExpressionOrThrow("83152002 |Oophorectomy|").getClassifiableForm());

		// Single concept with explicit definition status
		assertEquals("===421720008",
				createExpressionOrThrow("===421720008 |Spray dose form|").getClassifiableForm());

		// Single concept with explicit subtype definition status
		assertEquals("<<<83152002",
				createExpressionOrThrow("<<<  83152002 |Oophorectomy|").getClassifiableForm());

		// Multiple focus concepts, ids get sorted
		PostCoordinatedExpression twoFocusConcepts = createExpressionOrThrow("7946007 |Drug suspension| + 421720008 |Spray dose form|");
		assertEquals("421720008+7946007", twoFocusConcepts.getCloseToUserForm());
		assertEquals("===421720008+7946007", twoFocusConcepts.getClassifiableForm());


		// With multiple refinements, attributes are sorted
		PostCoordinatedExpression expressionMultipleRefinements = createExpressionOrThrow("   71388002 |Procedure| :" +
				"{" +
				"       405815000 |Procedure device|  =  122456005 |Laser device| ," +
				"       260686004 |Method|  =  129304002 |Excision - action| ," +
				"       405813007 |Procedure site - direct|  =  15497006 |Ovarian structure|" +
				"}");
		assertEquals("===71388002:{260686004=129304002,405813007=15497006,405815000=122456005}", expressionMultipleRefinements.getClassifiableForm());

		Page<PostCoordinatedExpression> page = expressionRepository.findAll(branch, PageRequest.of(0, 10));
		assertEquals(5, page.getTotalElements());

		Page<PostCoordinatedExpression> results = expressionRepository.findByExpression(branch, expressionMultipleRefinements.getCloseToUserForm().replace(" ", ""),
				CANONICAL_CLOSE_TO_USER_FORM_EXPRESSION_REFERENCE_SET, PageRequest.of(0, 1));
		assertEquals(1, results.getTotalElements());

		expressionId = expressionMultipleRefinements.getId();
		MemberSearchRequest memberSearchRequest = new MemberSearchRequest()
				.referencedComponentId(expressionId)
				.referenceSet(CANONICAL_CLOSE_TO_USER_FORM_EXPRESSION_REFERENCE_SET);
		Page<ReferenceSetMember> members = memberService.findMembers(branch, memberSearchRequest, PageRequest.of(0, 10));
		assertEquals(1, members.getTotalElements());
		ReferenceSetMember member = members.get().iterator().next();
		String refsetMemberExpressionField = member.getAdditionalField(ReferenceSetMember.PostcoordinatedExpressionFields.EXPRESSION);
		assertFalse(refsetMemberExpressionField.isEmpty());
		System.out.println(refsetMemberExpressionField);
		assertTrue(refsetMemberExpressionField.contains(":"));
		assertFalse(refsetMemberExpressionField.contains(" "), () -> String.format("Expression should not contain any whitespace: '%s'", refsetMemberExpressionField));
		assertFalse(refsetMemberExpressionField.contains("|"));

		assertEquals(1, memberService.findMembers(branch, memberSearchRequest.referenceSet(CLASSIFIABLE_FORM_EXPRESSION_REFERENCE_SET), PageRequest.of(0, 10)).getTotalElements());

		String equivalentsRefset = branchService.findLatest(branch).getMetadata().getString(EXPRESSION_EQUIVALENT_CONCEPTS_ASSOCIATION_METADATA_KEY);
		assertNotNull(equivalentsRefset);
		Page<ReferenceSetMember> equivalentConceptMembers = memberService.findMembers(branch, memberSearchRequest.referenceSet(equivalentsRefset), PageRequest.of(0, 10));
		assertEquals(1, equivalentConceptMembers.getTotalElements());
		assertEquals("1234567890", equivalentConceptMembers.iterator().next().getAdditionalField(ReferenceSetMember.AssociationFields.TARGET_COMP_ID));

		Branch latestBranch = branchService.findLatest(branch);
		assertEquals(expressionModuleId, latestBranch.getMetadata().getString("defaultModuleId"));
		Concept expressionNNFConcept = conceptService.find(expressionId, branch);
		assertEquals(expressionModuleId, expressionNNFConcept.getModuleId());
		Set<Relationship> relationships = expressionNNFConcept.getRelationships();
		assertEquals(4, relationships.size());
		assertEquals(expressionModuleId, relationships.iterator().next().getModuleId());
	}

	private PostCoordinatedExpression createExpressionOrThrow(String expression) throws ServiceException {
		// For unit testing we are mocking out the classification step
		// The expressions returned are not actually classified, but it's enough to support expression handling and ECL testing.

		Mockito.doAnswer(invocation -> {
			Object[] args = invocation.getArguments();
			@SuppressWarnings("unchecked")
			Set<PostCoordinatedExpression> expressions = ((Map) args[0]).keySet();
			for (PostCoordinatedExpression ex : expressions) {
				try {
					ComparableExpression returnExpression = expressionParser.parseExpression(ex.getClassifiableForm());
					returnExpression.setEquivalentConcepts(Set.of(1234567890L));
					ex.setNecessaryNormalForm(returnExpression);
				} catch (ServiceException e) {
					throw new RuntimeException(e);
				}
			}
			return null;
		}).when(mockClassificationService).classify(ArgumentMatchers.any(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.any());

		PostCoordinatedExpression postCoordinatedExpression = expressionRepository.createExpression(expression, expressionCodeSystem);
		if (postCoordinatedExpression.getException() != null) {
			throw postCoordinatedExpression.getException();
		}
		return postCoordinatedExpression;
	}

	@Test
	public void handleExpressionWithBadSyntax() throws ServiceException {
		// Missing colon
		assertIllegalArgumentParsingError("373873005 |Pharmaceutical / biologic product|" +
				"\t\t411116001 |Has dose form|  = 421720008 |Spray dose form|");

		// Missing equals
		assertIllegalArgumentParsingError("373873005 |Pharmaceutical / biologic product| :" +
				"\t\t411116001 |Has dose form| 421720008 |Spray dose form|");

		// Double equals
		assertIllegalArgumentParsingError("373873005 |Pharmaceutical / biologic product| :" +
				"\t\t411116001 |Has dose form| == 421720008 |Spray dose form|");

		assertEquals(0, expressionRepository.findAll("MAIN", PageRequest.of(0, 5)).getTotalElements(), "No invalid expressions should have been saved.");
	}

	@Test
	public void attributeRangeMRCMValidation() throws ServiceException {
		// All in range as per data in setup
		createExpressionOrThrow("   71388002 |Procedure| :" +
				"{" +
				"       405815000 |Procedure device| = 122456005 |Laser device| ," +
				"       260686004 |Method| = 129304002 |Excision - action| ," +
				"       405813007 |Procedure site - direct| = 15497006 |Ovarian structure|" +
				"}");

		try {
			createExpressionOrThrow("   71388002 |Procedure| :" +
					"{" +
					"       405815000 |Procedure device| = 122456005 |Laser device| ," +
					"       260686004 |Method| = 129304002 |Excision - action| ," +
					"       405813007 |Procedure site - direct| = 388441000 |Horse|" +
					"}");
			fail("Should have thrown exception.");
		} catch (ExpressionValidationException e) {
			assertEquals("Value 388441000 |Horse| is not within the permitted range" +
							" of attribute 405813007 |Procedure site - direct (attribute)| - (<< 442083009 |Anatomical or acquired body structure (body structure)|).",
					e.getMessage());
		}
	}

	@Test
	public void attributeRangeMRCMValidationOfAttributeValueWithinExpression() throws ServiceException {
		assertEquals(0, queryService.eclSearch("<!15497006 |Ovarian structure|", false, branch, PageRequest.of(0, 10)).getTotalElements());

		// All in range as per data in setup
		createExpressionOrThrow("71388002 |Procedure| : " +
				"{" +
				"       405815000 |Procedure device| = 122456005 |Laser device| ," +
				"       260686004 |Method| = 129304002 |Excision - action| ," +
				"       405813007 |Procedure site - direct| = ( 15497006 |Ovarian structure| : 272741003 |Laterality| = 24028007 |Right| ) " +
				"}");

		assertEquals(1, queryService.eclSearch("<!15497006 |Ovarian structure|", false, branch, PageRequest.of(0, 10)).getTotalElements(),
				"Nested concept should be created.");

		createExpressionOrThrow("71388002 |Procedure| : " +
				"{" +
				"       405815000 |Procedure device| = 118295004 |Gas laser device| ," +
				"       260686004 |Method| = 129304002 |Excision - action| ," +
				"       405813007 |Procedure site - direct| = ( 15497006 |Ovarian structure| : 272741003 |Laterality| = 24028007 |Right| ) " +
				"}");

		assertEquals(1, queryService.eclSearch("<!15497006 |Ovarian structure|", false, branch, PageRequest.of(0, 10)).getTotalElements(),
				"Nested concept should be reused.");

		try {
			createExpressionOrThrow("71388002 |Procedure| :" +
					"{" +
					"       405815000 |Procedure device| = 122456005 |Laser device| ," +
					"       260686004 |Method| = 129304002 |Excision - action| ," +
					"       405813007 |Procedure site - direct| = ( 15497006 |Ovarian structure| : 272741003 |Laterality| = 388441000 |Horse| )" +
					"}");
			fail("Should have thrown exception.");
		} catch (ExpressionValidationException e) {
			assertEquals("Value 388441000 |Horse| is not within the permitted range" +
							" of attribute 272741003 |Laterality (attribute)| - (<< 182353008 |Side (qualifier value)|).",
					e.getMessage());
		}
	}

	@Test
	public void expressionECL() throws ServiceException {
		String dummyExpressionString = "71388002 |Procedure| :" +
				"{" +
				"       405815000 |Procedure device| = 122456005 |Laser device| ," +
				"       260686004 |Method| = 129304002 |Excision - action| ," +
				"       405813007 |Procedure site - direct| = 15497006 |Ovarian structure|" +
				"}";

		// Assert state before
		assertEquals(3, queryService.eclSearch("<!71388002 |Procedure|", false, branch, PageRequest.of(0, 10)).getTotalElements());

		// Test
		PostCoordinatedExpression expression = createExpressionOrThrow(dummyExpressionString);
		assertNull(expression.getException());

		// Assert state after
		assertEquals(4, queryService.eclSearch("<!71388002 |Procedure|", false, branch, PageRequest.of(0, 10)).getTotalElements());
	}

	@Test
	public void expressionECLWithNesting() throws ServiceException {
		String dummyExpressionString = "71388002 |Procedure| :" +
				"{" +
				"       405815000 |Procedure device| = 122456005 |Laser device| ," +
				"       260686004 |Method| = 129304002 |Excision - action| ," +
				"       405813007 |Procedure site - direct| = ( 15497006 |Ovarian structure| : 272741003 |Laterality| = 24028007 |Right| )" +
				"}";

		// Assert state before
		assertEquals(3, queryService.eclSearch("<!71388002 |Procedure|", false, branch, PageRequest.of(0, 10)).getTotalElements());

		// Test
		PostCoordinatedExpression expression = createExpressionOrThrow(dummyExpressionString);
		assertNull(expression.getException());

		// Assert state after
		assertEquals(4, queryService.eclSearch("<!71388002 |Procedure|", false, branch, PageRequest.of(0, 10)).getTotalElements());
	}

	private void assertIllegalArgumentParsingError(String closeToUserForm) {
		try {
			PostCoordinatedExpression expression = expressionRepository.createExpression(closeToUserForm, expressionCodeSystem);
			ServiceException exception = expression.getException();
			if (exception != null) {
				throw exception;
			}
			fail();
		} catch (ServiceException e) {
			// Good
			assertTrue(e.getMessage().startsWith("Failed to parse expression"), "Message: " + e.getMessage());
		}
	}
}
