package org.snomed.snowstorm.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.ExpressionRepositoryService;
import org.snomed.snowstorm.core.data.services.postcoordination.PostCoordinatedExpression;
import org.snomed.snowstorm.rest.pojo.CreatePostCoordinatedExpressionRequest;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Api(tags = "Expression Repository", description = "-")
@RequestMapping(produces = "application/json")
public class ExpressionRepositoryController {

	@Autowired
	private ExpressionRepositoryService expressionRepository;

	@ApiOperation("Retrieve all postcoordinated expressions")
	@RequestMapping(value = "/{branch}/expressions", method = RequestMethod.GET)
	public ItemsPage<PostCoordinatedExpression> retrieveAllExpressions(
			@PathVariable String branch,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "1000") int limit) {

		branch = BranchPathUriUtil.decodePath(branch);
		return new ItemsPage<>(expressionRepository.findAll(branch, ControllerHelper.getPageRequest(offset, limit)));
	}

	@RequestMapping(value = "/{branch}/expressions", method = RequestMethod.PUT)
	public PostCoordinatedExpression createExpression(@PathVariable String branch, @RequestBody CreatePostCoordinatedExpressionRequest request) throws ServiceException {
		branch = BranchPathUriUtil.decodePath(branch);
		return expressionRepository.createExpression(branch, request.getCloseToUserForm(), request.getModuleId());
	}

	@RequestMapping(value = "/{branch}/expressions/transform", method = RequestMethod.POST)
	public PostCoordinatedExpression transformExpression(@PathVariable String branch, @RequestBody CreatePostCoordinatedExpressionRequest request) throws ServiceException {
		branch = BranchPathUriUtil.decodePath(branch);
		return expressionRepository.transformExpression(branch, request.getCloseToUserForm());
	}

}
