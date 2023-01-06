package org.snomed.snowstorm.core.data.services.postcoordination;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;
import org.snomed.snowstorm.core.data.services.postcoordination.model.PostCoordinatedExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;

class ExpressionRepositoryServiceTest extends AbstractExpressionTest {

	private static final ComparableExpression MOCK_CLASSIFIED_EXPRESSION = new ComparableExpression("404684003");
	static {
		MOCK_CLASSIFIED_EXPRESSION.setExpressionId(28984902063L);
	}

	@Autowired
	private ExpressionRepositoryService expressionRepository;

	@Autowired
	private ReferenceSetMemberService memberService;

	@MockBean
	private IncrementalClassificationService incrementalClassificationService;

	@Test
	public void createExpression() throws ServiceException {
		Mockito.when(incrementalClassificationService.classify(ArgumentMatchers.any(), eq(branch))).thenReturn(MOCK_CLASSIFIED_EXPRESSION);

		PostCoordinatedExpression expression = expressionRepository.createExpression("83152002 |Oophorectomy|", branch, moduleId);
		String expressionId = expression.getId();
		System.out.println("Expression ID is " + expressionId);
		assertEquals("16", LocalRandomIdentifierSource.POSTCOORDINATED_EXPRESSION_PARTITION_ID);
		assertEquals("16", expressionId.substring(expressionId.length() - 3, expressionId.length() - 1));

		// Single concept
		assertEquals("=== 83152002",
				expressionRepository.createExpression("83152002 |Oophorectomy|", branch, moduleId).getClassifiableForm());

		// Single concept with explicit definition status
		assertEquals("=== 83152002",
				expressionRepository.createExpression("===83152002 |Oophorectomy|", branch, moduleId).getClassifiableForm());

		// Single concept with explicit subtype definition status
		assertEquals("<<< 83152002",
				expressionRepository.createExpression("<<<  83152002 |Oophorectomy|", branch, moduleId).getClassifiableForm());

		// Multiple focus concepts
		assertEquals("=== 421720008 + 7946007",
				expressionRepository.createExpression("421720008 |Spray dose form| + 7946007 |Drug suspension|", branch, moduleId).getClassifiableForm());
		// Same concepts stated in reverse order to test concept sorting
		assertEquals("=== 421720008 + 7946007",
				expressionRepository.createExpression("7946007 |Drug suspension| + 421720008 |Spray dose form|", branch, moduleId).getClassifiableForm());


		// With single refinement
		final PostCoordinatedExpression expression1 =
				expressionRepository.createExpression("83152002 |Oophorectomy| :  405815000 |Procedure device|  =  122456005 |Laser device|", branch, moduleId);
		assertEquals("=== 83152002 : { 260686004 = 129304002, 405813007 = 15497006, 405815000 = 122456005 }",
				expression1.getClassifiableForm());
		assertEquals("83152002 : 405815000 = 122456005", expression1.getCloseToUserForm());

		// With multiple refinements, attributes are sorted
		PostCoordinatedExpression expressionMultipleRefinements = expressionRepository.createExpression("   71388002 |Procedure| :" +
				"       405815000 |Procedure device|  =  122456005 |Laser device| ," +
				"       260686004 |Method|  =  129304002 |Excision - action| ," +
				"       405813007 |Procedure site - direct|  =  15497006 |Ovarian structure|", branch, moduleId);
		assertEquals("=== 71388002 : { 260686004 = 129304002, 405813007 = 15497006, 405815000 = 122456005 }", expressionMultipleRefinements.getClassifiableForm());

		Page<PostCoordinatedExpression> page = expressionRepository.findAll(branch, PageRequest.of(0, 10));
		assertEquals(8, page.getTotalElements());

		Page<PostCoordinatedExpression> results = expressionRepository.findByCanonicalCloseToUserForm(branch, "83152002:405815000=122456005", PageRequest.of(0, 1));
		assertEquals(1, results.getTotalElements());

		Page<ReferenceSetMember> members = memberService.findMembers(branch, expressionMultipleRefinements.getId(), PageRequest.of(0, 10));
		assertEquals(2, members.getTotalElements());
		ReferenceSetMember member = members.get().iterator().next();
		String refsetMemberExpressionField = member.getAdditionalField(ReferenceSetMember.PostcoordinatedExpressionFields.EXPRESSION);
		assertFalse(refsetMemberExpressionField.isEmpty());
		System.out.println(refsetMemberExpressionField);
		assertTrue(refsetMemberExpressionField.contains(":"));
		assertFalse(refsetMemberExpressionField.contains(" "), () -> String.format("Expression should not contain any whitespace: '%s'", refsetMemberExpressionField));
		assertFalse(refsetMemberExpressionField.contains("|"));
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
		Mockito.when(incrementalClassificationService.classify(ArgumentMatchers.any(), eq(branch))).thenReturn(MOCK_CLASSIFIED_EXPRESSION);

		// All in range as per data in setup
		expressionRepository.createExpression("   71388002 |Procedure| :" +
				"       405815000 |Procedure device| = 122456005 |Laser device| ," +
				"       260686004 |Method| = 129304002 |Excision - action| ," +
				"       405813007 |Procedure site - direct| = 15497006 |Ovarian structure|", branch, moduleId);

		try {
			expressionRepository.createExpression("   71388002 |Procedure| :" +
					"       405815000 |Procedure device| = 122456005 |Laser device| ," +
					"       260686004 |Method| = 129304002 |Excision - action| ," +
					"       405813007 |Procedure site - direct| = 388441000 |Horse|", branch, moduleId);
			fail("Should have thrown exception.");
		} catch (IllegalArgumentException e) {
			assertEquals("Value 388441000 | Horse | is not within the permitted range" +
							" of attribute 405813007 | Procedure site - direct (attribute) | - (<< 442083009 |Anatomical or acquired body structure (body structure)|).",
					e.getMessage());
		}
	}

	@Test
	public void attributeRangeMRCMValidationOfAttributeValueWithinExpression() throws ServiceException {
		Mockito.when(incrementalClassificationService.classify(ArgumentMatchers.any(), eq(branch))).thenReturn(MOCK_CLASSIFIED_EXPRESSION);

		// All in range as per data in setup
		expressionRepository.createExpression("71388002 |Procedure| :" +
				"       405815000 |Procedure device| = 122456005 |Laser device| ," +
				"       260686004 |Method| = 129304002 |Excision - action| ," +
				"       405813007 |Procedure site - direct| = ( 15497006 |Ovarian structure| : 272741003 |Laterality| = 24028007 |Right| )", branch, moduleId);

		try {
			expressionRepository.createExpression("71388002 |Procedure| :" +
					"       405815000 |Procedure device| = 122456005 |Laser device| ," +
					"       260686004 |Method| = 129304002 |Excision - action| ," +
					"       405813007 |Procedure site - direct| = ( 15497006 |Ovarian structure| : 272741003 |Laterality| = 388441000 |Horse| )", branch, moduleId);
			fail("Should have thrown exception.");
		} catch (IllegalArgumentException e) {
			assertEquals("Value 388441000 | Horse | is not within the permitted range" +
							" of attribute 272741003 | Laterality (attribute) | - (<< 182353008 |Side (qualifier value)|).",
					e.getMessage());
		}
	}

	@Test
	public void attributeRangeMRCMValidationOfAttributeWithExpressionValue() throws ServiceException {
		Mockito.when(incrementalClassificationService.classify(ArgumentMatchers.any(), eq(branch))).thenReturn(MOCK_CLASSIFIED_EXPRESSION);

		// All in range as per data in setup
		expressionRepository.createExpression("71388002 |Procedure| :" +
				"       405815000 |Procedure device| = 122456005 |Laser device| ," +
				"       260686004 |Method| = 129304002 |Excision - action| ," +
				"       405813007 |Procedure site - direct| = ( 15497006 |Ovarian structure| : 272741003 |Laterality| = 24028007 |Right| )", branch, moduleId);

		try {
			expressionRepository.createExpression("71388002 |Procedure| :" +
					"       405815000 |Procedure device| = 122456005 |Laser device| ," +
					"       260686004 |Method| = 129304002 |Excision - action| ," +
					"       405813007 |Procedure site - direct| = ( 388441000 |Horse| : 272741003 |Laterality| = 24028007 |Right| )", branch, moduleId);
			fail("Should have thrown exception.");
		} catch (IllegalArgumentException e) {
			assertEquals("Value 388441000 | Horse | is not within the permitted range" +
							" of attribute 405813007 | Procedure site - direct (attribute) | - (<< 442083009 |Anatomical or acquired body structure (body structure)|).",
					e.getMessage());
		}
	}

	private void assertIllegalArgumentParsingError(String closeToUserForm) {
		try {
			PostCoordinatedExpression expression = expressionRepository.createExpression(closeToUserForm, branch, moduleId);
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
