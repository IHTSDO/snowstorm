package org.snomed.snowstorm.core.data.domain.security;

import java.util.Collections;
import java.util.Set;

public class UserBranchRoles {

	private final Set<String> grantedGlobalRole;
	private final Set<String> grantedBranchRole;

	public UserBranchRoles() {
		grantedGlobalRole = Collections.emptySet();
		grantedBranchRole = Collections.emptySet();
	}

	public UserBranchRoles(Set<String> grantedGlobalRole, Set<String> grantedBranchRole) {
		this.grantedGlobalRole = Collections.unmodifiableSet(grantedGlobalRole);
		this.grantedBranchRole = Collections.unmodifiableSet(grantedBranchRole);
	}

	public Set<String> getGrantedGlobalRole() {
		return grantedGlobalRole;
	}

	public Set<String> getGrantedBranchRole() {
		return grantedBranchRole;
	}
}
