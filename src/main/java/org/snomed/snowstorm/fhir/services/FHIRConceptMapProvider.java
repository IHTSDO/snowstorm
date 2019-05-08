package org.snomed.snowstorm.fhir.services;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;

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
	
	private BiMap<String, String> knownUriMap;
	String[] validMapTargets;
	
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
		validate("Target", target.asStringValue(), Validation.EQUALS, getValidMapTargets(), true);
		String refsetId = url.substring(SNOMED_CONCEPTMAP.length());
		
		//If a refset is specified does that match the target system?
		if (!refsetId.isEmpty()) {
			String expectedTargetSystem = knownUriMap.inverse().get(refsetId);
			if (expectedTargetSystem != null && !target.equals(expectedTargetSystem)) {
				throw new FHIROperationException (IssueType.CONFLICT, "Refset " + refsetId + " relates to target system '" + expectedTargetSystem + "' rather than '" + target + "'");
			}
		}
		
		Page<ReferenceSetMember> members = memberService.findMembers(
				BranchPathUriUtil.decodePath(MAIN),
				new MemberSearchRequest()
					.referenceSet(refsetId)
					.active(true)
					.referencedComponentId(code.getCode()),
				ControllerHelper.getPageRequest(0, DEFAULT_PAGESIZE));
		return mapper.mapToFHIR(members.getContent(), target, knownUriMap);
	}
	
	private String[] getValidMapTargets() {
		if (validMapTargets == null) {
			validMapTargets = new String[2];
			validMapTargets[0] = SNOMED_URI + "?fhir_vs";
			validMapTargets[1] = "ICD-10";
			
			//This hardcoding will be replaced by machine readable Refset metadata
			knownUriMap = new ImmutableBiMap.Builder<String, String>()
			.put("ICD-10", "447562003")
			.put("CTV-3","900000000000497000")
			.build();
		}
		return validMapTargets;
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
	
	private void validate(String fieldName, String actual, Validation mode, String[] permittedValues, boolean mandatory) throws FHIROperationException {
		if (!mandatory && actual == null) {
			return;
		}
		boolean matchFound = false;
		for (String permitted : permittedValues) {
			switch (mode) {
				case EQUALS : if (actual.equals(permitted)) matchFound = true;
					break;
				case STARTS_WITH : if (actual.startsWith(permitted)) matchFound = true;
					break;
			}
		}
		if (!matchFound) {
			throw new FHIROperationException (null, fieldName + " expected to contain one of " + String.join(", ", permittedValues));
		}
	}

	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return ConceptMap.class;
	}
}
