package org.snomed.snowstorm.fhir.services;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;

@Component
public class FHIRCodeSystemProvider implements IResourceProvider, FHIRConstants {
	
	@Autowired
	private ConceptService conceptService;
	
	@Autowired
	private FHIRMappingService mappingService;
	
	private FHIRHelper helper = new FHIRHelper();

	@Operation(name="$lookup", idempotent=true)
	public Parameters lookup(
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="code") CodeType code,
			@OperationParam(name="system") UriType system,
			@OperationParam(name="version") StringType version,
			@OperationParam(name="coding") Coding coding,
//				@OperationParam(name="date") DateTimeType date,   // Not supported
			@OperationParam(name="property") List<CodeType> properties) throws FHIROperationException {
		
		if (system == null || system.isEmpty() || !system.equals(SNOMED_URI)) {
			//return helper.validationFailure("System must be present, and currently only " + SNOMED_URI + " is supported.");
		}
		
		String branch = helper.getBranchForVersion(version.asStringValue());
		Concept c = ControllerHelper.throwIfNotFound("Concept", conceptService.find(code.getValue(), BranchPathUriUtil.parseBranchPath(branch)));
		return null; // mappingService.mapToFHIR(c); 
	}
	
	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return CodeSystem.class;
	}
}
