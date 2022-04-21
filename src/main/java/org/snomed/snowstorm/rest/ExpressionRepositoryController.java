package org.snomed.snowstorm.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.postcoordination.ExpressionRepositoryService;
import org.snomed.snowstorm.core.data.services.postcoordination.PostCoordinatedExpression;
import org.snomed.snowstorm.rest.pojo.CreatePostCoordinatedExpressionRequest;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Postcoordination", description = "-")
@RequestMapping(produces = "application/json")
public class ExpressionRepositoryController {

	@Autowired
	private ExpressionRepositoryService expressionRepository;

	@Operation(summary = "Retrieve all postcoordinated expressions", hidden = true)
	@GetMapping(value = "/{branch}/expressions")
	public ItemsPage<PostCoordinatedExpression> retrieveAllExpressions(
			@PathVariable String branch,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "1000") int limit) {

		branch = BranchPathUriUtil.decodePath(branch);
		return new ItemsPage<>(expressionRepository.findAll(branch, ControllerHelper.getPageRequest(offset, limit)));
	}

	@Operation(summary = "Create an expression in the repository", hidden = true)
	@PutMapping(value = "/{branch}/expressions")
	public PostCoordinatedExpression createExpression(@PathVariable String branch, @RequestBody CreatePostCoordinatedExpressionRequest request) throws ServiceException {
		branch = BranchPathUriUtil.decodePath(branch);
		return expressionRepository.createExpression(branch, request.getCloseToUserForm(), request.getModuleId());
	}

	@Operation(summary = "Validate and transform a postcoordinated expression.",
            description = "<b>Work In Progress</b>. This endpoint can be used for testing the validation of a postcoordinated expression, stated in close to user form, and " +
                    "any transformation to the classifiable form as required.")
	@PostMapping(value = "/{branch}/expressions/transform")
	public PostCoordinatedExpression transformExpression(@PathVariable String branch, @RequestBody CreatePostCoordinatedExpressionRequest request) throws ServiceException {
		branch = BranchPathUriUtil.decodePath(branch);
		return expressionRepository.parseValidateAndTransformExpression(branch, request.getCloseToUserForm());
	}

}
