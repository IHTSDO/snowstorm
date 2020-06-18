package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.snomed.snowstorm.core.data.domain.security.PermissionRecord;
import org.snomed.snowstorm.core.data.services.PermissionService;
import org.snomed.snowstorm.rest.pojo.UserGroupsPojo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Api(tags = "Admin - Permissions", description = "-")
@RequestMapping(value = "/admin/permissions", produces = "application/json")
public class PermissionController {

	@Autowired
	private PermissionService permissionService;

	@ApiOperation(value = "Retrieve all permissions", notes = "List all roles and user groups set at the global level and set against each branch.")
	@RequestMapping(method = RequestMethod.GET)
	@PreAuthorize("hasPermission('ADMIN', 'global')")
	@JsonView(value = View.Component.class)
	public List<PermissionRecord> findAll() {
		return permissionService.findAll();
	}

	@ApiOperation(value = "Retrieve all global permissions", notes = "List roles and user groups set at the global level.")
	@RequestMapping(value = "/global", method = RequestMethod.GET)
	@PreAuthorize("hasPermission('ADMIN', 'global')")
	@JsonView(value = View.Component.class)
	public List<PermissionRecord> findGlobal() {
		return permissionService.findGlobal();
	}

	@ApiOperation(value = "Retrieve all permissions on given branch", notes = "List roles and user groups for a specific branch.")
	@RequestMapping(value = "/{branch}", method = RequestMethod.GET)
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	@JsonView(value = View.Component.class)
	public List<PermissionRecord> findForBranch(@PathVariable String branch) {
		branch = BranchPathUriUtil.decodePath(branch);
		return permissionService.findByBranchPath(branch);
	}

	@ApiOperation(value = "Set global permissions.", notes = "Set which user groups have the given role globally.\n " +
			"Global permissions apply to all branches and code systems.")
	@RequestMapping(value = "/global/role/{role}", method = RequestMethod.PUT)
	@PreAuthorize("hasPermission('ADMIN', 'global')")
	public void setGlobalRoleGroups(@PathVariable String role, @RequestBody UserGroupsPojo userGroupsPojo) {
		permissionService.setGlobalRoleGroups(role, userGroupsPojo.getUserGroups());
	}

	@ApiOperation(value = "Set branch permissions.", notes = "Set which user groups have the given role on the given branch.\n " +
			"These permissions will also apply to ancestor branches in the same code system.")
	@RequestMapping(value = "/{branch}/role/{role}", method = RequestMethod.PUT)
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	public void setBranchRoleGroups(@PathVariable String branch, @PathVariable String role, @RequestBody UserGroupsPojo userGroupsPojo) {
		branch = BranchPathUriUtil.decodePath(branch);
		permissionService.setBranchRoleGroups(branch, role, userGroupsPojo.getUserGroups());
	}

}
