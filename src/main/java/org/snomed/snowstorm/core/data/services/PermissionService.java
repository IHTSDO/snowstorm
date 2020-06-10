package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.domain.Branch;
import org.snomed.snowstorm.core.data.domain.security.PermissionRecord;
import org.snomed.snowstorm.core.data.domain.security.Role;
import org.snomed.snowstorm.core.data.repositories.PermissionRecordRepository;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

@Service
public class PermissionService {

	private static final Sort SORT = Sort.by(
			Sort.Order.desc(PermissionRecord.Fields.GLOBAL), Sort.Order.asc(PermissionRecord.Fields.PATH), Sort.Order.asc(PermissionRecord.Fields.ROLE));

	private static final PageRequest PAGE_REQUEST = PageRequest.of(0, 10_000, SORT);

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private PermissionRecordRepository repository;

	private @Value("${permission.admin.group}") String adminUserGroup;

	@PostConstruct
	public void init() {
		setGlobalRoleGroups(Role.ADMIN, Collections.singleton(adminUserGroup));
	}

	public List<PermissionRecord> findAll() {
		return repository.findAll(PAGE_REQUEST).getContent();
	}

	public List<PermissionRecord> findGlobal() {
		return repository.findByGlobal(true, PAGE_REQUEST).getContent();
	}

	public List<PermissionRecord> findByBranchPath(String branchPath) {
		return repository.findByPath(escapeBranchPath(branchPath), PAGE_REQUEST).getContent();
	}

	public Set<String> getUserRolesForBranch(String branchPath) {
		SecurityContext securityContext = SecurityContextHolder.getContext();
		if (securityContext == null) {
			return Collections.emptySet();
		}

		TimerUtil timer = new TimerUtil("PermissionLoad");
		// TODO: cache this
		List<PermissionRecord> allPermissionRecords = findAll();
		timer.checkpoint("load records");
		// TODO: cache this
		List<String> codeSystemBranches = codeSystemService.findAllCodeSystemBranches();
		timer.checkpoint("load code systems");

		Set<String> grantedBranchRole = getUserRolesForBranch(branchPath, allPermissionRecords, codeSystemBranches, securityContext.getAuthentication());

		timer.checkpoint("filter records");
		timer.finish();

		return grantedBranchRole;
	}

	Set<String> getUserRolesForBranch(String branchPath, List<PermissionRecord> allPermissionRecords, List<String> codeSystemBranches, Authentication authentication) {
		// Find closest code system
		String closestCodeSystemBranch = Branch.MAIN;
		for (String codeSystemBranch : codeSystemBranches) {
			if (branchPath.contains(codeSystemBranch)) {
				closestCodeSystemBranch = codeSystemBranch;
			}
		}

		Set<String> userGroups = new HashSet<>();
		for (GrantedAuthority grantedAuthority : authentication.getAuthorities()) {
			userGroups.add(grantedAuthority.getAuthority().replace("ROLE_", ""));
		}

		Set<String> grantedBranchRole = new HashSet<>();
		for (PermissionRecord permissionRecord : allPermissionRecords) {
			if (permissionRecord.isGlobal() || (
					// Record applies to the closest code system
					permissionRecord.getPath().contains(closestCodeSystemBranch)
					// Branch is same as or sub branch of record branch
					&& branchPath.contains(permissionRecord.getPath()))) {
				for (String requiredUserGroup : permissionRecord.getUserGroups()) {
					if (userGroups.contains(requiredUserGroup)) {
						grantedBranchRole.add(permissionRecord.getRole());
					}
				}
			}
		}
		return grantedBranchRole;
	}

	public void setGlobalRoleGroups(Role role, Set<String> userGroups) {
		setGlobalRoleGroups(role.name(), userGroups);
	}

	public void setGlobalRoleGroups(String role, Set<String> userGroups) {
		// null branch used for global roles
		setGlobalOrBranchRoleGroups(true, null, role, userGroups);
	}

	public void setBranchRoleGroups(String branch, String role, Set<String> userGroups) {
		setGlobalOrBranchRoleGroups(false, branch, role, userGroups);
	}

	private void setGlobalOrBranchRoleGroups(boolean global, String branch, String role, Set<String> userGroups) {
		if (!userGroups.isEmpty()) {
			PermissionRecord permissionRecord = findByGlobalPathAndRole(global, branch, role).orElse(new PermissionRecord(role, branch));
			permissionRecord.setUserGroups(userGroups);
			repository.save(permissionRecord);
		} else {
			// Delete any existing entry
			findByGlobalPathAndRole(global, branch, role).ifPresent(record -> repository.delete(record));
		}
	}

	private Optional<PermissionRecord> findByGlobalPathAndRole(boolean global, String branch, String role) {
		return repository.findByGlobalAndPathAndRole(global, escapeBranchPath(branch), role);
	}

	private String escapeBranchPath(String branch) {
		return branch != null ? branch.replace("/", "\\/") : null;
	}
}
