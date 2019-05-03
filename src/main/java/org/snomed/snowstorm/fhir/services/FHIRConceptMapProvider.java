package org.snomed.snowstorm.fhir.services;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;

@Component
public class FHIRConceptMapProvider implements IResourceProvider, FHIRConstants {
	
	@Autowired
	private ReferenceSetMemberService memberService;
	
	@Autowired
	private HapiParametersMapper mapper;
	
	private static int DEFAULT_PAGESIZE = 1000;
	
	@Operation(name="$translate", idempotent=true)
	public Parameters expand(
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="url") String url,
			@OperationParam(name="system") UriType system,
			@OperationParam(name="code") CodeType code,
			@OperationParam(name="source") UriType source,
			@OperationParam(name="target") UriType target) throws FHIROperationException {

		validate("System", system.asStringValue(), Validation.EQUALS, SNOMED_URI, true);
		validate("Source", source.asStringValue(), Validation.STARTS_WITH, SNOMED_URI, true);
		validate("Url", url, Validation.STARTS_WITH, SNOMED_CONCEPTMAP, true);
		validate("Target", target.asStringValue(), Validation.EQUALS, SNOMED_URI + "?fhir_vs", true);
		String refset = url.substring(SNOMED_CONCEPTMAP.length());
		
		Page<ReferenceSetMember> members = memberService.findMembers(
				BranchPathUriUtil.decodePath(MAIN),
				new MemberSearchRequest()
					.referenceSet(refset)
					.referencedComponentId(code.getCode()),
				ControllerHelper.getPageRequest(0, DEFAULT_PAGESIZE));
		return mapper.mapToFHIR(members.getContent(), target);
	}
	
	private void validate(String fieldName, String actual, Validation mode, String expected, boolean mandatory) throws FHIROperationException {
		if (!mandatory && actual == null) {
			return;
		}
		switch (mode) {
			case EQUALS : if (!actual.equals(expected)) {
					throw new FHIROperationException (null, fieldName + " must be exactly equal to '" + expected + "'.  Received '" + actual + "'.");
				}
				break;
			case STARTS_WITH : if (!actual.startsWith(expected)) {
					throw new FHIROperationException (null, fieldName + " must start with '" + expected + "'.  Received '" + actual + "'.");
				}
				break;
		}
	}

	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return ConceptMap.class;
	}
}
