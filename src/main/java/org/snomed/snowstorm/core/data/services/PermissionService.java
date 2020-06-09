package org.snomed.snowstorm.core.data.services;

import org.snomed.snowstorm.core.data.domain.security.PermissionRecord;
import org.snomed.snowstorm.core.data.domain.security.Role;
import org.snomed.snowstorm.core.data.repositories.PermissionRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class PermissionService {

	private static final Sort SORT = Sort.by(
			Sort.Order.desc(PermissionRecord.Fields.GLOBAL), Sort.Order.asc(PermissionRecord.Fields.PATH), Sort.Order.asc(PermissionRecord.Fields.ROLE));

	private static final PageRequest PAGE_REQUEST = PageRequest.of(0, 10_000, SORT);

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
