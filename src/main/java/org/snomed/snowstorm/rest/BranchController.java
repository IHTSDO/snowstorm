package org.snomed.snowstorm.rest;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.snomed.snowstorm.core.data.domain.BranchMergeJob;
import org.snomed.snowstorm.core.data.services.BranchMergeService;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.rest.pojo.CreateBranchRequest;
import org.snomed.snowstorm.rest.pojo.MergeRequest;
import org.snomed.snowstorm.rest.pojo.UpdateBranchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping(produces = "application/json")
public class BranchController {

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMergeService branchMergeService;

	@Autowired
	private QueryService queryService;

	@ApiOperation("Retrieve all branches")
	@RequestMapping(value = "/branches", method = RequestMethod.GET)
	@ResponseBody
	public List<Branch> retrieveAllBranches() {
		return branchService.findAll();
	}

	@RequestMapping(value = "/branches", method = RequestMethod.POST)
	@ResponseBody
	public Branch createBranch(@RequestBody CreateBranchRequest request) {
		return branchService.create(request.getParent() + "/" + request.getName(), request.getMetadata());
	}

	@ApiOperation("Update branch metadata")
	@RequestMapping(value = "/branches/{path}", method = RequestMethod.PUT)
	@ResponseBody
	public Branch updateBranch(@PathVariable String path, @RequestBody UpdateBranchRequest request) {
		return branchService.updateMetadata(path, request.getMetadata());
	}

	@ApiOperation("Retrieve a single branch")
	@RequestMapping(value = "/branches/{path}", method = RequestMethod.GET)
	@ResponseBody
	public Branch retrieveBranch(@PathVariable String path, @RequestParam(required = false, defaultValue = "false") boolean includeInheritedMetadata) {
		return branchService.findBranchOrThrow(BranchPathUriUtil.decodePath(path), includeInheritedMetadata);
	}

	@RequestMapping(value = "/branches/{path}/actions/unlock", method = RequestMethod.POST)
	@ResponseBody
	public void unlockBranch(@PathVariable String path) {
		branchService.unlock(BranchPathUriUtil.decodePath(path));
	}

	@RequestMapping(value = "/merges", method = RequestMethod.POST)
	public ResponseEntity<Void> mergeBranch(@RequestBody MergeRequest mergeRequest) {
		BranchMergeJob mergeJob = branchMergeService.mergeBranchAsync(mergeRequest);
		return ControllerHelper.getCreatedResponse(mergeJob.getId());
	}

	@RequestMapping(value = "/merges/{mergeId}", method = RequestMethod.GET)
	public BranchMergeJob retrieveMerge(@PathVariable String mergeId) {
		return branchMergeService.getBranchMergeJobOrThrow(mergeId);
	}

	@ResponseBody
	@RequestMapping(value = "/{branch}/integrity-check", method = RequestMethod.GET)
	@ApiOperation(value = "Perform integrity check against this branch.",
			notes = "Returns a map of problem components. " +
					"Each map key is the id of problem component. The corresponding map value is a set of ids of concepts which are referred to by the component but which are missing or inactive.")
	public Map<Long, Set<Long>> integrityCheck(@ApiParam(value="The branch path") @PathVariable(value="branch") @NotNull final String branchPath) {
		return queryService.findActiveRelationshipsReferencingNotActiveConcepts(BranchPathUriUtil.decodePath(branchPath), true);
	}

}
