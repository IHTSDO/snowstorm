package org.snomed.snowstorm;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import org.junit.After;
import org.junit.Before;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.PermissionService;
import org.snomed.snowstorm.core.data.services.classification.ClassificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

public abstract class AbstractTest {

	public static final String MAIN = "MAIN";

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private ClassificationService classificationService;

	@Autowired
	private PermissionService permissionService;

	@Value("${ims-security.roles.enabled}")
	private boolean rolesEnabled;

	@Before
	public void before() {
		// Setup security
		if (!rolesEnabled) {
			PreAuthenticatedAuthenticationToken authentication = new PreAuthenticatedAuthenticationToken("test-admin", "123", Sets.newHashSet(new SimpleGrantedAuthority("USER")));
			SecurityContextHolder.setContext(new SecurityContextImpl(authentication));
		} else {
			SecurityContextHolder.clearContext();
		}

		branchService.create(MAIN);
	}

	@After
	public void defaultTearDown() {
		branchService.deleteAll();
		conceptService.deleteAll();
		codeSystemService.deleteAll();
		classificationService.deleteAll();
		permissionService.deleteAll();
	}

}
