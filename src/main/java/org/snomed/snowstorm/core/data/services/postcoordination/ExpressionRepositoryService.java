package org.snomed.snowstorm.core.data.services.postcoordination;

import org.snomed.languages.scg.SCGException;
import org.snomed.languages.scg.SCGExpressionParser;
import org.snomed.languages.scg.SCGObjectFactory;
import org.snomed.languages.scg.domain.model.Expression;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.data.services.postcoordination.model.ComparableExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ExpressionRepositoryService {

	private final SCGExpressionParser expressionParser;

	@Autowired
	private ReferenceSetMemberService memberService;

	// Temporary workaround until postcoordinated expression reference set is created.
	private static final String ANNOTATION_REFSET = "900000000000516008";
	private static final String ANNOTATION_FIELD = "annotation";

	public ExpressionRepositoryService() {
		expressionParser = new SCGExpressionParser(new SCGObjectFactory());
	}

	public Page<PostCoordinatedExpression> findAll(String branch, PageRequest pageRequest) {
		Page<ReferenceSetMember> membersPage = memberService.findMembers(branch, new MemberSearchRequest().referenceSet(ANNOTATION_REFSET), pageRequest);
		List<PostCoordinatedExpression> expressions = membersPage.getContent().stream()
				.map(this::toExpression).collect(Collectors.toList());
		return new PageImpl<>(expressions, pageRequest, membersPage.getTotalElements());
	}

	public PostCoordinatedExpression createExpression(String branch, String closeToUserForm, String moduleId) {
		try {
			Expression expression = expressionParser.parseExpression(closeToUserForm);
			// Sort contents of expression
			expression = new ComparableExpression(expression);
			ReferenceSetMember member = memberService.createMember(branch, new ReferenceSetMember(moduleId, ANNOTATION_REFSET, getFirstFocusConceptOrRoot(expression))
					.setAdditionalField(ANNOTATION_FIELD, expression.toString()));
			return toExpression(member);
		} catch (SCGException e) {
			throw new IllegalArgumentException("Failed to parse expression: " + e.getMessage(), e);
		}
	}

	private PostCoordinatedExpression toExpression(ReferenceSetMember member) {
		return new PostCoordinatedExpression(member.getAdditionalField(ANNOTATION_FIELD));
	}

	private String getFirstFocusConceptOrRoot(Expression expression) {
		List<String> focusConcepts = expression.getFocusConcepts();
		return focusConcepts != null && !focusConcepts.isEmpty() ? focusConcepts.get(0) : Concepts.SNOMEDCT_ROOT;
	}

}
