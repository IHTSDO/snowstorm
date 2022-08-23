package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.snowstorm.core.data.domain.security.PermissionRecord;
import org.snomed.snowstorm.core.data.services.PermissionService;
import org.snomed.snowstorm.rest.pojo.UserGroupsPojo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Admin - Permissions", description = "-")
@RequestMapping(value = "/admin/permissions", produces = "application/json")
public class PermissionController {

	@Autowired
	private BranchService branchService;

	@Autowired
	private PermissionService permissionService;

	@Operation(summary = "Retrieve all permissions", description = "List all roles and user groups set at the global level and set against each branch.")
	@GetMapping
	@PreAuthorize("hasPermission('ADMIN', 'global')")
	@JsonView(value = View.Component.class)
	public List<PermissionRecord> findAll() {
		return permissionService.findAll();
	}

	@Operation(summary = "Retrieve all global permissions", description = "List roles and user groups set at the global level.")
	@GetMapping(value = "/global")
	@PreAuthorize("hasPermission('ADMIN', 'global')")
	@JsonView(value = View.Component.class)
	public List<PermissionRecord> findGlobal() {
		return permissionService.findGlobal();
	}

	@Operation(summary = "Retrieve all permissions on given branch", description = "List roles and user groups for a specific branch.")
	@GetMapping(value = "/{branch}")
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	@JsonView(value = View.Component.class)
	public List<PermissionRecord> findForBranch(@PathVariable String branch) {
		branch = BranchPathUriUtil.decodePath(branch);
		verifyBranch(branch);
		return permissionService.findByBranchPath(branch);
	}

	@Operation(summary = "Set global permissions.", description = "Set which user groups have the given role globally.\n " +
			"Global permissions apply to all branches and code systems.")
	@PutMapping(value = "/global/role/{role}")
	@PreAuthorize("hasPermission('ADMIN', 'global')")
	public void setGlobalRoleGroups(@PathVariable String role, @RequestBody UserGroupsPojo userGroupsPojo) {
		permissionService.setGlobalRoleGroups(role, userGroupsPojo.getUserGroups());
	}

	@Operation(summary = "Delete a global role.")
	@DeleteMapping(value = "/global/role/{role}")
	@PreAuthorize("hasPermission('ADMIN', 'global')")
	public void deleteGlobalRole(@PathVariable String role) {
		permissionService.deleteGlobalRole(role);
	}

	@Operation(summary = "Set branch permissions.", description = "Set which user groups have the given role on the given branch.\n " +
			"These permissions will also apply to ancestor branches in the same code system.")
	@PutMapping(value = "/{branch}/role/{role}")
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	public void setBranchRoleGroups(@PathVariable String branch, @PathVariable String role, @RequestBody UserGroupsPojo userGroupsPojo) {
		branch = BranchPathUriUtil.decodePath(branch);
		verifyBranch(branch);
		permissionService.setBranchRoleGroups(branch, role, userGroupsPojo.getUserGroups());
	}

	@Operation(summary = "Delete branch role.")
	@DeleteMapping(value = "/{branch}/role/{role}")
	@PreAuthorize("hasPermission('ADMIN', #branch)")
	public void deleteBranchRole(@PathVariable String branch, @PathVariable String role) {
		branch = BranchPathUriUtil.decodePath(branch);
		verifyBranch(branch);
		permissionService.deleteBranchRole(branch, role);
	}

	@Operation(summary = "Retrieve all permissions for a provided user group",
			description = "List all permissions for a user group.")
	@GetMapping(value = "/user-group/{userGroup}")
	@PreAuthorize("hasPermission('ADMIN', 'global')")
	@JsonView(value = View.Component.class)
	public List<PermissionRecord> findUserGroupPermissions(@PathVariable String userGroup) {
		return permissionService.findUserGroupPermissions(userGroup);
	}

	private void verifyBranch (String branch) {
		if (branch !=null && !branchService.exists(branch)) {
			throw new IllegalStateException("Branch '" + branch + "' does not exist.");
		}
	}

}
