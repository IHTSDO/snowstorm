package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.server.IResourceProvider;

import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.MultiSearchService;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.data.services.pojo.ConceptCriteria;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.BranchPath;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
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

	@Autowired
	private MultiSearchService multiSearchService;

	@Operation(name="$lookup", idempotent=true)
	public Parameters lookup(
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="code") CodeType code,
			@OperationParam(name="system") UriType system,
			@OperationParam(name="version") StringType codeSystemVersionUri,
			@OperationParam(name="coding") Coding coding,
			@OperationParam(name="displayLanguage") String displayLanguage,
			@OperationParam(name="property") List<CodeType> propertiesType ) throws FHIROperationException {

		if (system == null || system.isEmpty() || !system.equals(SNOMED_URI)) {
			String detail = "  Instead received: " + (system == null ? "null" : ("'" + system.asStringValue() + "'"));
			throw new FHIROperationException(IssueType.VALUE, "'system' parameter must be present, and currently only '" + SNOMED_URI + "' is supported." + detail);
		}

		List<LanguageDialect> languageDialects = fhirHelper.getLanguageDialects(null, request);
		// Also if displayLanguage has been used, ensure that's part of our requested Language Codes
		fhirHelper.ensurePresent(displayLanguage, languageDialects);

		BranchPath branchPath = fhirHelper.getBranchPathForCodeSystemVersion(codeSystemVersionUri);
		Concept concept = ControllerHelper.throwIfNotFound("Concept", conceptService.find(code.getValue(), languageDialects, branchPath.toString()));
		Page<Long> childIds = queryService.searchForIds(queryService.createQueryBuilder(false).ecl("<!" + code.getValue()), branchPath.toString(), LARGE_PAGE);
		Set<FhirSctProperty> properties = FhirSctProperty.parse(propertiesType);
		return mapper.mapToFHIR(concept, childIds.getContent(), properties);
	}

	@Operation(name="$validate-code", idempotent=true)
	public Parameters validateCode(
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="url") UriType url,
			@OperationParam(name="codeSystem") CodeSystem codeSystem,
			@OperationParam(name="code") CodeType code,
			@OperationParam(name="version") StringType codeSystemVersionUri,
			@OperationParam(name="date") DateTimeType date,
			@OperationParam(name="coding") Coding coding,
			@OperationParam(name="displayLanguage") String displayLanguage) throws FHIROperationException {

		if (codeSystem != null) {
			throw new FHIROperationException(IssueType.NOTSUPPORTED, "'codeSystem' parameter must not be present.");
		}
		if (date != null) {
			throw new FHIROperationException(IssueType.NOTSUPPORTED, "'date' parameter is not currently supported.");
		}

		List<LanguageDialect> languageDialects = fhirHelper.getLanguageDialects(null, request);

		if (code != null && IdentifierService.isConceptId(code.getCode())) {
			String conceptId = code.getCode();
			Page<Concept> concepts = multiSearchService.findConcepts(new ConceptCriteria().conceptIds(Collections.singleton(conceptId)), PageRequest.of(0, 1));
			List<Concept> content = concepts.getContent();
			if (!content.isEmpty()) {
				Concept concept = content.get(0);
				Concept fullConcept = conceptService.find(conceptId, languageDialects, concept.getPath());
				return mapper.mapToFHIR(fullConcept);
			}
		}

		return null;
	}

	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return CodeSystem.class;
	}
}
