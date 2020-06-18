package org.snomed.snowstorm.config;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import org.snomed.snowstorm.core.data.services.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration;
import org.springframework.security.core.Authentication;

import java.io.Serializable;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class MethodSecurityConfig extends GlobalMethodSecurityConfiguration {

	@Autowired
	private PermissionEvaluator permissionEvaluator;

	@Override
	protected MethodSecurityExpressionHandler createExpressionHandler() {
		DefaultMethodSecurityExpressionHandler expressionHandler =
				new DefaultMethodSecurityExpressionHandler();
		expressionHandler.setPermissionEvaluator(permissionEvaluator);
		return expressionHandler;
	}

	@Bean
	public PermissionEvaluator permissionEvaluator(@Lazy PermissionService permissionService) {
		return new PermissionEvaluator() {
			@Override
			public boolean hasPermission(Authentication authentication, Object role, Object branchObject) {
				return permissionService.userHasRoleOnBranch((String) role, BranchPathUriUtil.decodePath((String) branchObject), authentication);
			}

			@Override
			public boolean hasPermission(Authentication authentication, Serializable serializable, String s, Object o) {
				return false;
			}
		};
	}
}

