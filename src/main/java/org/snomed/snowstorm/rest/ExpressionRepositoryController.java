package org.snomed.snowstorm.rest;

import io.kaicode.elasticvc.api.BranchService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.snomed.snowstorm.core.data.services.postcoordination.ExpressionRepositoryService;
import org.snomed.snowstorm.core.data.services.postcoordination.PostCoordinatedExpression;
import org.snomed.snowstorm.rest.pojo.CreatePostCoordinatedExpressionRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@Api(tags = "Expression Repository", description = "-")
@RequestMapping(value = "expressions", produces = "application/json")
public class ExpressionRepositoryController {

	@Autowired
	private BranchService branchService;

	@Autowired
	private ExpressionRepositoryService expressionRepository;

	@ApiOperation("Retrieve all postcoordinated expressions")
	@RequestMapping(value = "/", method = RequestMethod.GET)
	public Collection<PostCoordinatedExpression> retrieveAllExpressions() {
		return expressionRepository.findAll();
	}

	@RequestMapping(value = "/", method = RequestMethod.POST)
	public PostCoordinatedExpression createExpression(@RequestBody CreatePostCoordinatedExpressionRequest request) {
		String closeToUserForm = request.getCloseToUserForm();
		return expressionRepository.createExpression(closeToUserForm);
	}

}
