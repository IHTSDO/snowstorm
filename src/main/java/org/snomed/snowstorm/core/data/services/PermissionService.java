package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.domain.Branch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.security.PermissionRecord;
import org.snomed.snowstorm.core.data.domain.security.Role;
import org.snomed.snowstorm.core.data.domain.security.UserBranchRoles;
import org.snomed.snowstorm.core.data.repositories.PermissionRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

@Service
public class PermissionService {

	private static final Sort SORT = Sort.by(
			Sort.Order.desc(PermissionRecord.Fields.GLOBAL), Sort.Order.asc(PermissionRecord.Fields.PATH), Sort.Order.asc(PermissionRecord.Fields.ROLE));

	protected static final PageRequest PAGE_REQUEST = PageRequest.of(0, 10_000, SORT);

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private PermissionRecordRepository repository;

	@Autowired
	private PermissionServiceCache permissionServiceCache;

	private @Value("${permission.admin.group}") String adminUserGroup;

	@Value("${ims-security.roles.enabled}")
	private boolean rolesEnabled;

	private final Logger logger = LoggerFactory.getLogger(getClass());

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
		return repository.findByPath(branchPath, PAGE_REQUEST).getContent();
	}

	public boolean userHasRoleOnBranch(String role, String branchPath, Authentication authentication) {
		if (!rolesEnabled) {
			return true;
		}
		Set<String> userRoleForBranch = getUserRolesForBranch(branchPath, authentication).getGrantedBranchRole();
		boolean contains = userRoleForBranch.contains(role);
		if (!contains) {
			logger.info("User '{}' does not have required role '{}' on branch '{}', on this branch they have roles:{}.", getUsername(authentication), role, branchPath, userRoleForBranch);
		}
		return contains;
	}

	public UserBranchRoles getUserRolesForBranch(String branchPath) {
		SecurityContext securityContext = SecurityContextHolder.getContext();
		if (securityContext == null || securityContext.getAuthentication() == null) {
			return new UserBranchRoles();
		}

		return getUserRolesForBranch(branchPath, securityContext.getAuthentication());
	}

	private UserBranchRoles getUserRolesForBranch(String branchPath, Authentication authentication) {
		List<PermissionRecord> allPermissionRecords = permissionServiceCache.findAllUsingCache();
		List<String> codeSystemBranches = codeSystemService.findAllCodeSystemBranchesUsingCache();
		return getUserRolesForBranch(branchPath, allPermissionRecords, codeSystemBranches, authentication);
	}

	UserBranchRoles getUserRolesForBranch(String branchPath, List<PermissionRecord> allPermissionRecords, List<String> codeSystemBranches, Authentication authentication) {
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
		logger.debug("Authorities:{}, userGroups:{}", authentication.getAuthorities(), userGroups);

		Set<String> grantedBranchRole = new HashSet<>();
		Set<String> grantedGlobalRole = new HashSet<>();
		for (PermissionRecord permissionRecord : allPermissionRecords) {
			if (permissionRecord.isGlobal() || (
					// Record applies to the closest code system
					permissionRecord.getPath().contains(closestCodeSystemBranch)
					// Branch is same as or sub branch of record branch
					&& branchPath.contains(permissionRecord.getPath()))) {
				for (String requiredUserGroup : permissionRecord.getUserGroups()) {
					if (userGroups.contains(requiredUserGroup)) {
						grantedBranchRole.add(permissionRecord.getRole());
						if (permissionRecord.isGlobal()) {
							grantedGlobalRole.add(permissionRecord.getRole());
						}
					}
				}
			}
		}
		return new UserBranchRoles(grantedGlobalRole, grantedBranchRole);
	}

	public void setGlobalRoleGroups(Role role, Set<String> userGroups) {
		setGlobalRoleGroups(role.name(), userGroups);
	}

	public void setGlobalRoleGroups(String role, Set<String> userGroups) {
		// null branch used for global roles
		setGlobalOrBranchRoleGroups(true, null, role, userGroups);
	}

	public void deleteGlobalRole(String role) {
		setGlobalOrBranchRoleGroups(true, null, role, Collections.emptySet());
	}

	public void setBranchRoleGroups(String branch, Role role, Set<String> userGroups) {
		setBranchRoleGroups(branch, role.toString(), userGroups);
	}

	public void setBranchRoleGroups(String branch, String role, Set<String> userGroups) {
		setGlobalOrBranchRoleGroups(false, branch, role, userGroups);
	}

	public void deleteBranchRole(String branch, String role) {
		setGlobalOrBranchRoleGroups(false, branch, role, Collections.emptySet());
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
		permissionServiceCache.clearCache();
	}

	private Optional<PermissionRecord> findByGlobalPathAndRole(boolean global, String branch, String role) {
		return repository.findByGlobalAndPathAndRole(global, branch, role);
	}

	private String getUsername(Authentication authentication) {
		if (authentication != null) {
			Object principal = authentication.getPrincipal();
			if (principal != null) {
				return principal.toString();
			}
		}
		return null;
	}

	public void deleteAll() {
		repository.deleteAll();
	}
}
