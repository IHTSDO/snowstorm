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
import org.snomed.snowstorm.fhir.domain.BranchPath;
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
	
	@Autowired
	private FHIRHelper fhirHelper;
	
	private static int DEFAULT_PAGESIZE = 1000;
	
	private BiMap<String, String> knownUriMap;
	String[] validMapTargets;
	String[] validMapSources;
	
	@Operation(name="$translate", idempotent=true)
	public Parameters translate(
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="url") String url,
			@OperationParam(name="system") UriType system,
			@OperationParam(name="code") CodeType code,
			@OperationParam(name="version") StringType version,
			@OperationParam(name="source") UriType source,
			@OperationParam(name="target") UriType target) throws FHIROperationException {
		fhirHelper.required("source", source);
		fhirHelper.required("target", target);
		validate("System", system.asStringValue(), Validation.EQUALS, getValidMapSources(), true);
		validate("Source", source.asStringValue(), Validation.STARTS_WITH, getValidMapSources(), true);
		validate("Target", target.asStringValue(), Validation.EQUALS, getValidMapTargets(), true);
		fhirHelper.notSupported("version", version);

		if (!source.asStringValue().startsWith(SNOMED_URI) && source.asStringValue().equals(target.asStringValue())) {
			throw new FHIROperationException (null, "Source and target cannot be the same: '" + source.asStringValue() + "'");
		}
		
		String refsetId = "";
		if (url != null) {
			validate("Url", url, Validation.STARTS_WITH, new String[] {SNOMED_URI, SNOMED_URI_UNVERSIONED}, true);
			int idx = url.indexOf(MAP_INDICATOR);
			if (idx == NOT_SET) {
				throw new FHIROperationException (IssueType.INCOMPLETE, "url parameter is expected to contain '"+ MAP_INDICATOR +"' indicating the refset sctid of the map to be used.");
			}
			refsetId = url.substring(idx + MAP_INDICATOR.length());
		}
		
		//If a refset is specified does that match the target system?
		if (!refsetId.isEmpty()) {
			String expectedTargetSystem = knownUriMap.inverse().get(refsetId);
			if (expectedTargetSystem != null && !target.equals(expectedTargetSystem)) {
				throw new FHIROperationException (IssueType.CONFLICT, "Refset " + refsetId + " relates to target system '" + expectedTargetSystem + "' rather than '" + target + "'");
			}
		}
		
		MemberSearchRequest memberSearchRequest = new MemberSearchRequest()
				.referenceSet(refsetId)
				.active(true);
		
		//Are we going from SNOMED to other, or other to SNOMED?
		if (target.asStringValue().startsWith(SNOMED_URI) && !source.asStringValue().startsWith(SNOMED_URI)) {
			memberSearchRequest.mapTarget(code.getCode());
		} else {
			memberSearchRequest.referencedComponentId(code.getCode());
		}
		
		//The code system is the URL up to where the parameters start eg http://snomed.info/sct?fhir_cm=447562003
		//These calls will also set the branchPath
		BranchPath branchPath = new BranchPath();
		int cutPoint = url == null ? -1 : url.indexOf("?");
		if (cutPoint == NOT_SET) {
			if (url == null) {
				branchPath.set(MAIN);
			} else {
				throw new FHIROperationException (IssueType.INCOMPLETE, "url parameter is expected to contain a parameter indicating the refset id of the map to be used");
			}
		} else {
			StringType codeSystemVersionUri = new StringType(url.substring(0, cutPoint));
			branchPath.set(fhirHelper.getBranchPathFromURI(codeSystemVersionUri));
		}

		Page<ReferenceSetMember> members = memberService.findMembers(
				branchPath.toString(),
				memberSearchRequest,
				ControllerHelper.getPageRequest(0, DEFAULT_PAGESIZE));
		return mapper.mapToFHIR(members.getContent(), target, knownUriMap);

	}
	
	private String[] getValidMapTargets() {
		if (validMapTargets == null) {
			validMapTargets = new String[6];
			validMapTargets[0] = SNOMED_URI + "?fhir_vs";
			validMapTargets[1] = ICD10;
			validMapTargets[2] = ICD10_URI;
			validMapTargets[3] = SNOMED_URI;
			validMapTargets[4] = ICDO;
			validMapTargets[5] = ICDO_URI;
			
			//This hardcoding will be replaced by machine readable Refset metadata
			knownUriMap = new ImmutableBiMap.Builder<String, String>()
			.put(ICD10_URI, "447562003")
			.put(ICDO_URI, "446608001")
			.put("CTV-3","900000000000497000")
			.build();
		}
		return validMapTargets;
	}
	
	private String[] getValidMapSources() {
		if (validMapSources == null) {
			validMapSources = new String[3];
			validMapSources[0] = SNOMED_URI;
			validMapSources[1] = ICD10;
			validMapSources[2] = ICD10_URI;
		}
		return validMapSources;
	}

	private void validate(String fieldName, String actual, Validation mode, String expected, boolean mandatory) throws FHIROperationException {
		if (!mandatory && actual == null) {
			return;
		}
		switch (mode) {
			case EQUALS:
				if (actual == null || !actual.equals(expected)) {
					throw new FHIROperationException(null, fieldName + " must be exactly equal to '" + expected + "'.  Received '" + actual + "'.");
				}
				break;
			case STARTS_WITH:
				if (actual == null || !actual.startsWith(expected)) {
					throw new FHIROperationException(null, fieldName + " must start with '" + expected + "'.  Received '" + actual + "'.");
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
				case EQUALS : if (actual != null && actual.equals(permitted)) matchFound = true;
					break;
				case STARTS_WITH : if (actual != null && actual.startsWith(permitted)) matchFound = true;
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
