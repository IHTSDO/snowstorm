package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.server.IResourceProvider;

import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Set;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;

@Component
public class FHIRCodeSystemProvider implements IResourceProvider, FHIRConstants {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private QueryService queryService;

	@Autowired
	private HapiParametersMapper mapper;
	
	@Autowired
	private FHIRHelper fhirHelper;

	@Operation(name="$lookup", idempotent=true)
	public Parameters lookup(
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="code") CodeType code,
			@OperationParam(name="system") UriType system,
			@OperationParam(name="version") StringType codeSystemVersionUri,
			@OperationParam(name="coding") Coding coding,
			@OperationParam(name="property") List<CodeType> propertiesType ) throws FHIROperationException {

		if (system == null || system.isEmpty() || !system.equals(SNOMED_URI)) {
			String detail = "  Instead received: " + system==null?"null": ("'" + system.asStringValue() + "'");
			throw new FHIROperationException(IssueType.VALUE, "'system' parameter must be present, and currently only '" + SNOMED_URI + "' is supported." + detail);
		}

		List<String> languageCodes = fhirHelper.getLanguageCodes(request);
		String branchPath = fhirHelper.getBranchPathForCodeSystemVersion(codeSystemVersionUri);
		Concept concept = ControllerHelper.throwIfNotFound("Concept", conceptService.find(code.getValue(), languageCodes, branchPath));
		Page<Long> childIds = queryService.searchForIds(queryService.createQueryBuilder(false).ecl("<!" + code.getValue()), branchPath, LARGE_PAGE);
		Set<FhirSctProperty> properties = FhirSctProperty.parse(propertiesType);
		return mapper.mapToFHIR(concept, childIds.getContent(), properties);
	}
	
	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return CodeSystem.class;
	}
}
