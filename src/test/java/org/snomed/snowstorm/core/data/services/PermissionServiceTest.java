package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.security.PermissionRecord;
import org.snomed.snowstorm.core.data.repositories.PermissionRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.collect.Sets.newHashSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PermissionServiceTest extends AbstractTest {

	@Autowired
	private PermissionService permissionService;

	@Autowired
	private CodeSystemService codeSystemService;

	@BeforeEach
	void setup() {
		List<PermissionRecord> allPermissionRecords = new ArrayList<>();

		// Global roles
		allPermissionRecords.add(new PermissionRecord("ADMIN").withUserGroups("snowstorm-admin"));
		allPermissionRecords.add(new PermissionRecord("VIEW").withUserGroups("global-view"));

		// International roles
		allPermissionRecords.add(new PermissionRecord("VIEW", "MAIN").withUserGroups("int-author", "int-reviewer"));
		allPermissionRecords.add(new PermissionRecord("AUTHOR", "MAIN").withUserGroups("int-author", "int-reviewer"));
		allPermissionRecords.add(new PermissionRecord("REVIEW", "MAIN").withUserGroups("int-reviewer"));
		allPermissionRecords.add(new PermissionRecord("AUTHOR", "MAIN/PROJECTA").withUserGroups("int-projectA-author"));

		// Extension code system roles
		allPermissionRecords.add(new PermissionRecord("AUTHOR", "MAIN/SNOMEDCT-ABC").withUserGroups("abc-author"));
		permissionService.saveAll(allPermissionRecords);

		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-ABC", "MAIN/SNOMEDCT-ABC"));
	}

	@Test
	void testRoles() {
		// Test what roles a user has on each branch if they are in the following user groups.

		// Global roles work on any branch or code system
		assertEquals(newHashSet("ADMIN"), getRolesForBranch("MAIN", "ROLE_snowstorm-admin"));
		assertEquals(newHashSet("ADMIN", "VIEW"), getRolesForBranch("MAIN", "ROLE_snowstorm-admin", "ROLE_global-view"));
		assertEquals(newHashSet("ADMIN"), getRolesForBranch("MAIN/PROJECTA", "ROLE_snowstorm-admin"));
		assertEquals(newHashSet("ADMIN"), getRolesForBranch("MAIN/SNOMEDCT-ABC", "ROLE_snowstorm-admin"));
		assertEquals(newHashSet("ADMIN"), getRolesForBranch("MAIN/SNOMEDCT-ABC/something/task-123", "ROLE_snowstorm-admin"));

		// International roles work on MAIN or any sub branch but not other code systems
		assertEquals(newHashSet("VIEW", "AUTHOR"), getRolesForBranch("MAIN", "int-author"));
		assertEquals(newHashSet("VIEW", "AUTHOR", "REVIEW"), getRolesForBranch("MAIN", "int-reviewer"));
		assertEquals(newHashSet("VIEW", "AUTHOR"), getRolesForBranch("MAIN/PROJECTA", "int-author"));
		assertEquals(newHashSet("VIEW", "AUTHOR", "REVIEW"), getRolesForBranch("MAIN/PROJECTA", "int-reviewer"));
		assertEquals(newHashSet(), getRolesForBranch("MAIN/SNOMEDCT-ABC", "int-author"));
		assertEquals(newHashSet(), getRolesForBranch("MAIN/SNOMEDCT-ABC", "int-reviewer"));

		// Roles can be set on a single project
		assertEquals(newHashSet(), getRolesForBranch("MAIN", "int-projectA-author"));
		assertEquals(newHashSet("AUTHOR"), getRolesForBranch("MAIN/PROJECTA", "int-projectA-author"));
		assertEquals(newHashSet("AUTHOR"), getRolesForBranch("MAIN/PROJECTA/task-123", "int-projectA-author"));
		assertEquals(newHashSet(), getRolesForBranch("MAIN/SNOMEDCT-ABC/PROJECTA", "int-projectA-author"));

		// Extension roles
		assertEquals(newHashSet(), getRolesForBranch("MAIN", "abc-author"));
		assertEquals(newHashSet("AUTHOR"), getRolesForBranch("MAIN/SNOMEDCT-ABC", "abc-author"));
		assertEquals(newHashSet("AUTHOR"), getRolesForBranch("MAIN/SNOMEDCT-ABC/PROJECTZ/task-99", "abc-author"));
	}

	@Test
	public void testUpdateGlobalRoles() {
		assertEquals(newHashSet(), getRolesForBranch("MAIN", "ROLE_global-view2"));
		permissionService.setGlobalRoleGroups("VIEW", Sets.newHashSet("global-view", "global-view2"));
		assertEquals(newHashSet("VIEW"), getRolesForBranch("MAIN", "ROLE_global-view2"));
	}

	@Test
	public void testDeleteGlobalRoles() {
		List<PermissionRecord> globalRoles = permissionService.findGlobal();
		assertEquals(2, globalRoles.size());
		assertEquals(newHashSet("ADMIN", "VIEW"), getRolesForBranch("MAIN", "ROLE_snowstorm-admin", "ROLE_global-view"));

		permissionService.deleteGlobalRole("VIEW");

		globalRoles = permissionService.findGlobal();
		assertEquals(1, globalRoles.size());
		assertEquals(newHashSet("ADMIN"), getRolesForBranch("MAIN", "ROLE_snowstorm-admin", "ROLE_global-view"));
	}

	@Test
	void testFindByBranchPath() {
		List<PermissionRecord> results = permissionService.findByBranchPath("MAIN/SNOMEDCT-ABC");
		assertNotNull(results);
		assertEquals(1, results.size());
		assertEquals("abc-author", results.get(0).getUserGroups().iterator().next());
	}

	private Set<String> getRolesForBranch(String branchPath, String... userGroups) {
		// Create fake user and log them in for testing
		final PreAuthenticatedAuthenticationToken user = createUserWithUserGroups(newHashSet(userGroups));
		SecurityContext securityContext = SecurityContextHolder.getContext();
		securityContext.setAuthentication(user);

		return permissionService.getUserRolesForBranch(branchPath).getGrantedBranchRole();
	}

	private PreAuthenticatedAuthenticationToken createUserWithUserGroups(Set<String> userGroups) {
		return new PreAuthenticatedAuthenticationToken("userA", "123", userGroups.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet()));
	}

}
