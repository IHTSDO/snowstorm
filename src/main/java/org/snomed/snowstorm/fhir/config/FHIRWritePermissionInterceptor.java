package org.snomed.snowstorm.fhir.config;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.snomed.snowstorm.core.data.domain.security.Role;
import org.snomed.snowstorm.core.data.services.PermissionService;
import org.snomed.snowstorm.fhir.services.FHIRHelper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Requires the global ADMIN role for FHIR operations that change stored resources, matching the native API.
 */
@Component
@Interceptor
public class FHIRWritePermissionInterceptor {

	private static final Set<RestOperationTypeEnum> WRITE_OPERATION_TYPES = Set.of(
			RestOperationTypeEnum.CREATE,
			RestOperationTypeEnum.UPDATE,
			RestOperationTypeEnum.PATCH,
			RestOperationTypeEnum.DELETE,
			RestOperationTypeEnum.META_ADD,
			RestOperationTypeEnum.META_DELETE);

	// Listed explicitly rather than inferred from idempotent=false, because $batch-validate-code is non-idempotent but only reads.
	private static final Set<String> WRITE_EXTENDED_OPERATIONS = Set.of(
			"$upload-external-code-system");

	private final PermissionService permissionService;

	public FHIRWritePermissionInterceptor(PermissionService permissionService) {
		this.permissionService = permissionService;
	}

	@Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLED)
	public void incomingRequestPreHandled(RequestDetails requestDetails, RestOperationTypeEnum operationType) {
		if (isWriteOperation(requestDetails, operationType)) {
			checkGlobalAdmin();
		}
	}

	public void checkGlobalAdmin() {
		if (!permissionService.userHasRoleOnBranch(Role.ADMIN.name(), "global", SecurityContextHolder.getContext().getAuthentication())) {
			throw FHIRHelper.exception("The global ADMIN role is required for this operation.", IssueType.FORBIDDEN, 403);
		}
	}

	private static boolean isWriteOperation(RequestDetails requestDetails, RestOperationTypeEnum operationType) {
		String operation = requestDetails.getOperation();
		return WRITE_OPERATION_TYPES.contains(operationType) || (operation != null && WRITE_EXTENDED_OPERATIONS.contains(operation));
	}

}
