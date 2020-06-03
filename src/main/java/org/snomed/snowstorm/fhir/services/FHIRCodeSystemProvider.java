package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.server.IResourceProvider;

import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.MultiSearchService;
import org.snomed.snowstorm.core.data.services.NotFoundException;
import org.snomed.snowstorm.core.data.services.QueryService;
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
import java.util.*;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_DIALECTS;

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
	
	List<LanguageDialect> defaultLanguages;
	
	FHIRCodeSystemProvider() {
		defaultLanguages = new ArrayList<>();
		defaultLanguages.addAll(DEFAULT_LANGUAGE_DIALECTS);
	}

	@Operation(name="$lookup", idempotent=true)
	public Parameters lookup(
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="code") CodeType code,
			@OperationParam(name="system") StringType system,
			@OperationParam(name="version") StringType version,
			@OperationParam(name="coding") Coding coding,
			@OperationParam(name="date") StringType date,
			@OperationParam(name="displayLanguage") String displayLanguage,
			@OperationParam(name="property") List<CodeType> propertiesType ) throws FHIROperationException {
		fhirHelper.mutuallyExclusive("code", code, "coding", coding);
		fhirHelper.notSupported("date", date);
		system = fhirHelper.enhanceCodeSystem(system, version, coding);
		String conceptId = fhirHelper.recoverConceptId(code, coding);
		List<LanguageDialect> languageDialects = fhirHelper.getLanguageDialects(null, request);
		// Also if displayLanguage has been used, ensure that's part of our requested Language Codes
		fhirHelper.ensurePresent(displayLanguage, languageDialects);

		BranchPath branchPath = fhirHelper.getBranchPathFromURI(system);
		Concept concept = ControllerHelper.throwIfNotFound("Concept", conceptService.find(conceptId, languageDialects, branchPath.toString()));
		Page<Long> childIds = queryService.searchForIds(queryService.createQueryBuilder(false).ecl("<!" + conceptId), branchPath.toString(), LARGE_PAGE);
		Set<FhirSctProperty> properties = FhirSctProperty.parse(propertiesType);
		return mapper.mapToFHIR(system, concept, childIds.getContent(), properties);
	}

	@Operation(name="$validate-code", idempotent=true)
	public Parameters validateCode(
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="url") UriType url,
			@OperationParam(name="codeSystem") StringType codeSystem,
			@OperationParam(name="code") CodeType code,
			@OperationParam(name="display") String display,
			@OperationParam(name="version") StringType version,
			@OperationParam(name="date") DateTimeType date,
			@OperationParam(name="coding") Coding coding,
			@OperationParam(name="displayLanguage") String displayLanguage) throws FHIROperationException {
		fhirHelper.mutuallyExclusive("url", url, "codeSystem", codeSystem);
		fhirHelper.mutuallyExclusive("code", code, "coding", coding);
		fhirHelper.mutuallyRequired("display", display, "code", code, "coding", coding);
		fhirHelper.notSupported("date", date);
		codeSystem = fhirHelper.enhanceCodeSystem(codeSystem, version, coding);
		List<LanguageDialect> languageDialects = fhirHelper.getLanguageDialects(null, request);
		String conceptId = fhirHelper.recoverConceptId(code, coding);
		ConceptCriteria criteria = new ConceptCriteria().conceptIds(Collections.singleton(conceptId));
		Concept fullConcept = null;
		if (codeSystem == null || codeSystem.toString().equals(SNOMED_URI)) {
			Page<Concept> concepts = multiSearchService.findConcepts(criteria, PageRequest.of(0, 1));
			List<Concept> content = concepts.getContent();
			if (!content.isEmpty()) {
				Concept concept = content.get(0);
				fullConcept = conceptService.find(conceptId, languageDialects, concept.getPath());
			}
		} else {
			BranchPath branchPath = fhirHelper.getBranchPathFromURI(codeSystem);
			fullConcept = conceptService.find(conceptId, languageDialects, branchPath.toString());
		}
		
		if (fullConcept == null) {
			return mapper.conceptNotFound();
		} else {
			return mapper.mapToFHIR(fullConcept, display);
		}
	}
	
	@Operation(name="$subsumes", idempotent=true)
	public Parameters subsumesInstance(
			@IdParam IdType id,
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="codeA") CodeType codeA,
			@OperationParam(name="codeB") CodeType codeB,
			@OperationParam(name="systen") StringType system,
			@OperationParam(name="version") StringType version,
			@OperationParam(name="codingA") Coding codingA,
			@OperationParam(name="codingB") Coding codingB)
			throws FHIROperationException {
		
		//doSubsumptionParameterValidation(codeA, codeB, system, version, codingA, codingB);
		//system = fhirHelper.enhanceCodeSystem(system, version, codingA);
		throw new FHIROperationException(IssueType.NOTSUPPORTED, "Subsumption testing on codeSystem instances not yet supported.  Specify codeSystem in parameters instead");
	}

	@Operation(name="$subsumes", idempotent=true)
	public Parameters subsumes(
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="codeA") CodeType codeA,
			@OperationParam(name="codeB") CodeType codeB,
			@OperationParam(name="system") StringType system,
			@OperationParam(name="version") StringType version,
			@OperationParam(name="codingA") Coding codingA,
			@OperationParam(name="codingB") Coding codingB)
			throws FHIROperationException {
		
		doSubsumptionParameterValidation(codeA, codeB, system, version, codingA, codingB);
		Coding commonCoding = validateCodings(codingA, codingB);
		system = fhirHelper.enhanceCodeSystem(system, version, commonCoding);
		String conceptAId = fhirHelper.recoverConceptId(codeA, codingA);
		String conceptBId = fhirHelper.recoverConceptId(codeB, codingB);
		if (conceptAId.equals(conceptBId)) {
			return mapper.singleOutValue("outcome", "equivalent");
		}
		//Test for A subsumes B, then B subsumes A
		String eclAsubsumesB = conceptAId + " AND > " + conceptBId;
		String eclBsubsumesA = conceptBId + " AND > " + conceptAId;
		BranchPath branchPath = fhirHelper.getBranchPathFromURI(system);
		if (matchesConcept(eclAsubsumesB, branchPath)) {
			return mapper.singleOutValue("outcome", "subsumes");
		} else if (matchesConcept(eclBsubsumesA, branchPath)) {
			return mapper.singleOutValue("outcome", "subsumed-by");
		}
		//TODO First check for the concept in all known codesystemversions
		//Secondly, should we return an Outcome object if the concept is not found?
		ensureConceptExists(conceptAId, branchPath);
		ensureConceptExists(conceptBId, branchPath);
		return mapper.singleOutValue("outcome", "not-subsumed");
	}
	
	private void ensureConceptExists(String sctId, BranchPath branchPath) {
		if (!matchesConcept(sctId, branchPath)) {
			throw new NotFoundException(sctId + " not found in " + branchPath); 
		}
	}

	private void doSubsumptionParameterValidation(CodeType codeA, CodeType codeB, StringType system, StringType version,
			Coding codingA, Coding codingB) throws FHIROperationException {
		fhirHelper.mutuallyExclusive("codeA", codeA, "codingA", codingA);
		fhirHelper.mutuallyExclusive("codeB", codeB, "codingB", codingB);
		fhirHelper.mutuallyExclusive("codingA", codingA, "system", system);
		fhirHelper.mutuallyExclusive("codingA", codingA, "version", version);
		fhirHelper.mutuallyRequired("codeA", codeA, "codeB", codeB);
		fhirHelper.mutuallyRequired("codingA", codingA, "codingB", codingB);
		fhirHelper.mutuallyRequired("system", system, "codeA", codeA);
		fhirHelper.mutuallyRequired("version", version, "codeA", codeA);
	}

	private Coding validateCodings(Coding codingA, Coding codingB) throws FHIROperationException {
		//Return whatever coding has a system, but if they both have one, ensure it's the same
		if (codingA == null && codingB == null ) {
			return null;
		} else if (codingA != null && codingB != null && codingB.getSystem() == null) {
			return codingA;
		} else if (codingB != null && codingA != null && codingA.getSystem() == null) {
			return codingB;
		} else if (!codingA.getSystem().equals(codingB.getSystem())) {
			throw new FHIROperationException(IssueType.CONFLICT, "CodeSystem defined in codingA must match that in codingB");
		}
		//Here both are present and they're the same system, so return either
		return codingA;
	}

	private boolean matchesConcept(String ecl, BranchPath branchPath) {
		//We don't care about language, use defaults
		Page<ConceptMini> result = fhirHelper.eclSearch(queryService, ecl, (Boolean)null, 
				(String)null, defaultLanguages, branchPath, 0, 1);
		return (result != null && result.hasContent() && result.getContent().size() == 1);
	}

	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return CodeSystem.class;
	}
}
