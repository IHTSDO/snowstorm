package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.server.IResourceProvider;

import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.NotFoundException;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;

@Component
public class FHIRCodeSystemProvider implements IResourceProvider, FHIRConstants {

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private QueryService queryService;

	@Autowired
	private HapiCodeSystemMapper mapper;
	
	private FHIRHelper helper = new FHIRHelper();

	@Operation(name="$lookup", idempotent=true)
	public Parameters lookup(
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="code") CodeType code,
			@OperationParam(name="system") UriType system,
			@OperationParam(name="version") StringType codeSystemUri,
			@OperationParam(name="coding") Coding coding,
//				@OperationParam(name="date") DateTimeType date,   // Not supported
			@OperationParam(name="property") List<CodeType> properties
			/*@RequestHeader(value = "Accept-Language", defaultValue = ControllerHelper.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader*/) throws FHIROperationException {

		if (system == null || system.isEmpty() || !system.equals(SNOMED_URI)) {
			throw new FHIROperationException(IssueType.VALUE, "System must be present, and currently only " + SNOMED_URI + " is supported.");
		}

		String defaultModule = helper.getSnomedEditionModule(codeSystemUri);
		Integer editionVersionString = null;
		if (codeSystemUri != null) {
			editionVersionString = helper.getSnomedVersion(codeSystemUri.toString());
		}

		org.snomed.snowstorm.core.data.domain.CodeSystem codeSystem = codeSystemService.findByDefaultModule(defaultModule);
		if (codeSystem == null) {
			throw new NotFoundException(String.format("No code system with default module %s.", defaultModule));
		}

		CodeSystemVersion codeSystemVersion;
		String shortName = codeSystem.getShortName();
		if (editionVersionString != null) {
			// Lookup specific version
			codeSystemVersion = codeSystemService.findVersion(shortName, editionVersionString);
		} else {
			// Lookup latest
			codeSystemVersion = codeSystemService.findLatestVersion(shortName);
		}
		if (codeSystemVersion == null) {
			throw new NotFoundException(String.format("No version found for Code system %s with default module %s.", shortName, defaultModule));
		}
		
		List<String> languageCodes = ControllerHelper.getLanguageCodes(ControllerHelper.DEFAULT_ACCEPT_LANG_HEADER);
		String branchPath = codeSystemVersion.getBranchPath();
		Concept concept = ControllerHelper.throwIfNotFound("Concept", conceptService.find(code.getValue(), languageCodes, branchPath));
		Page<Long> childIds = queryService.searchForIds(queryService.createQueryBuilder(false).ecl("<!" + code.getValue()), branchPath, LARGE_PAGE);
		return mapper.mapToFHIR(concept, childIds.getContent());
	}
	
	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return CodeSystem.class;
	}
}
