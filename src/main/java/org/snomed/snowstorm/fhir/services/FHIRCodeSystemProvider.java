package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.QuantityParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
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
import org.snomed.snowstorm.fhir.domain.SearchFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_DIALECTS;

@Component
public class FHIRCodeSystemProvider implements IResourceProvider, FHIRConstants {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private QueryService queryService;

	@Autowired
	private HapiParametersMapper pMapper;
	
	@Autowired
	private HapiCodeSystemMapper csMapper;
	
	@Autowired
	private FHIRHelper fhirHelper;

	@Autowired
	private MultiSearchService multiSearchService;
	
	private List<LanguageDialect> defaultLanguages;
	
	FHIRCodeSystemProvider() {
		defaultLanguages = new ArrayList<>();
		defaultLanguages.addAll(DEFAULT_LANGUAGE_DIALECTS);
	}
	
	private static String[] defaultSortOrder = new String[] { "title", "-date" };
	
	//See https://stackoverflow.com/questions/61073018/streams-sorting-by-runtime-parameter
	private static Map<String, Comparator<CodeSystem>> comparatorMap = Map.ofEntries(
			new AbstractMap.SimpleEntry<>("_id", Comparator.comparing(CodeSystem::getId)),
			new AbstractMap.SimpleEntry<>("-_id", Comparator.comparing(CodeSystem::getId).reversed()),
			new AbstractMap.SimpleEntry<>("date", Comparator.comparing(CodeSystem::getDate)),
			new AbstractMap.SimpleEntry<>("-date", Comparator.comparing(CodeSystem::getDate).reversed()),
			new AbstractMap.SimpleEntry<>("description", Comparator.comparing(CodeSystem::getDescription)),
			new AbstractMap.SimpleEntry<>("-description", Comparator.comparing(CodeSystem::getDescription).reversed()),
			new AbstractMap.SimpleEntry<>("name", Comparator.comparing(CodeSystem::getName)),
			new AbstractMap.SimpleEntry<>("-name", Comparator.comparing(CodeSystem::getName).reversed()),
			new AbstractMap.SimpleEntry<>("publisher", Comparator.comparing(CodeSystem::getPublisher)),
			new AbstractMap.SimpleEntry<>("-publisher", Comparator.comparing(CodeSystem::getPublisher).reversed()),
			new AbstractMap.SimpleEntry<>("title", Comparator.comparing(CodeSystem::getTitle)),
			new AbstractMap.SimpleEntry<>("-title", Comparator.comparing(CodeSystem::getTitle).reversed()),
			new AbstractMap.SimpleEntry<>("url", Comparator.comparing(CodeSystem::getUrl)),
			new AbstractMap.SimpleEntry<>("-url", Comparator.comparing(CodeSystem::getUrl).reversed()),
			new AbstractMap.SimpleEntry<>("version", Comparator.comparing(CodeSystem::getVersion)),
			new AbstractMap.SimpleEntry<>("-version", Comparator.comparing(CodeSystem::getVersion).reversed())
		);
	
	//See https://www.hl7.org/fhir/valueset.html#search
	@Search
	public List<CodeSystem> findCodeSystems(
			RequestDetails theRequest, 
			HttpServletResponse theResponse,
			@OptionalParam(name="_id") String id,
			@OptionalParam(name="code") String code,
			@OptionalParam(name="context") TokenParam context,
			@OptionalParam(name="context-quantity") QuantityParam contextQuantity,
			@OptionalParam(name="context-type") String contextType,
			@OptionalParam(name="date") StringParam date,
			@OptionalParam(name="description") StringParam description,
			@OptionalParam(name="identifier") StringParam identifier,
			@OptionalParam(name="jurisdiction") StringParam jurisdiction,
			@OptionalParam(name="name") StringParam name,
			@OptionalParam(name="publisher") StringParam publisher,
			@OptionalParam(name="reference") StringParam reference,
			@OptionalParam(name="status") String status,
			@OptionalParam(name="title") StringParam title,
			@OptionalParam(name="url") String url,
			@OptionalParam(name="version") String version) throws FHIROperationException {
		SearchFilter csFilter = new SearchFilter()
									.withId(id)
									.withCode(code)
									.withContext(context)
									.withContextQuantity(contextQuantity)
									.withContextType(contextType)
									.withDate(date)
									.withDescription(description)
									.withIdentifier(identifier)
									.withJurisdiction(jurisdiction)
									.withName(name)
									.withPublisher(publisher)
									.withReference(reference)
									.withStatus(status)
									.withTitle(title)
									.withUrl(url)
									.withVersion(version);
		
		List<String> sortOn;
		if (theRequest.getParameters().get("_sort") != null) {
			sortOn = new ArrayList<>();
			for (String param : theRequest.getParameters().get("_sort")) {
				sortOn.addAll(Arrays.asList(param.split("\\,")));
			}
		} else {
			sortOn = Arrays.asList(defaultSortOrder);
		}
		
		for (String sortField : sortOn) {
			if (!comparatorMap.containsKey(sortField)) {
				throw new FHIROperationException(IssueType.PROCESSING, sortField + " is not supported as a field to sort on.");
			}
		}
		
		Comparator<CodeSystem> chainedComparator = sortOn.stream().map(comparatorMap::get).reduce(Comparator::thenComparing).get();
		return multiSearchService.getAllPublishedVersions().stream()
				.map(cv -> csMapper.mapToFHIR(cv))
				.filter(cs -> csFilter.apply(cs, fhirHelper))
				.sorted(chainedComparator)
				.collect(Collectors.toList());
	}
	
	@Read()
	public CodeSystem getCodeSystem(@IdParam IdType id) {
		//TODO If we cache these we could use a map to find just the one we want.
		//Would be nice to also populate the count on each module/version
		Optional<CodeSystem> o = multiSearchService.getAllPublishedVersions().stream()
				.map(cv -> csMapper.mapToFHIR(cv))
				.filter(cs -> cs.getId().equals(id.getIdPart()))
				.findAny();
		
		if (o.isPresent()) {
			return o.get();
		}
		throw new NotFoundException("Code System " + id + " not found"); 
	}

	@Operation(name="$lookup", idempotent=true)
	public Parameters lookupImplicit(
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
		return lookup(request, response, system, code, coding, displayLanguage, propertiesType);
	}
	
	@Operation(name="$lookup", idempotent=true)
	public Parameters lookupInstance(
			@IdParam IdType id,
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="system") StringType system,
			@OperationParam(name="version") StringType version,
			@OperationParam(name="code") CodeType code,
			@OperationParam(name="coding") Coding coding,
			@OperationParam(name="date") StringType date,
			@OperationParam(name="displayLanguage") String displayLanguage,
			@OperationParam(name="property") List<CodeType> propertiesType ) throws FHIROperationException {
		fhirHelper.mutuallyExclusive("code", code, "coding", coding);
		fhirHelper.notSupported("date", date);
		fhirHelper.notSupported("system", system, "CodeSystem where instance id is already specified in URL");
		fhirHelper.notSupported("version", version, "CodeSystem where instance id is already specified in URL");
		StringType systemURI = new StringType(getCodeSystem(id).getUrl());
		return lookup(request, response, systemURI, code, coding, displayLanguage, propertiesType);
	}
	
	//Method common to both implicit and instance lookups
	private Parameters lookup(
			HttpServletRequest request,
			HttpServletResponse response,
			StringType system,
			CodeType code,
			Coding coding,
			String displayLanguage,
			List<CodeType> propertiesType) throws FHIROperationException {
		String conceptId = fhirHelper.recoverConceptId(code, coding);
		List<LanguageDialect> designations = new ArrayList<>();
		// Also if displayLanguage has been used, ensure that's part of our requested Language Codes
		// And make it the first in the list so we pick it up for the display element
		fhirHelper.setLanguageOptions (designations, displayLanguage, request);
		Concept fullConcept = null;
		BranchPath branchPath = null;
		if (system == null || system.toString().equals(SNOMED_URI)) {
			//Multisearch is expensive, so we'll try on default branch first 
			branchPath = fhirHelper.getBranchPathFromURI(system);
			fullConcept = conceptService.find(conceptId, designations, branchPath.toString());
			if (fullConcept == null) {
				ConceptCriteria criteria = new ConceptCriteria().conceptIds(Collections.singleton(conceptId));
				Page<Concept> concepts = multiSearchService.findConcepts(criteria, PageRequest.of(0, 1));
				List<Concept> content = concepts.getContent();
				if (!content.isEmpty()) {
					Concept concept = content.get(0);
					branchPath = new BranchPath(concept.getPath());
					fullConcept = conceptService.find(conceptId, designations, concept.getPath());
				} else {
					throw new NotFoundException(conceptId + " not found on any code system version");
				}
			}
		} else {
			branchPath = fhirHelper.getBranchPathFromURI(system);
			fullConcept = conceptService.find(conceptId, designations, branchPath.toString());
			if (fullConcept == null) {
				throw new NotFoundException("Concept " + conceptId + " was not found on branch " + branchPath);
			}
		}
		Page<Long> childIds = queryService.searchForIds(queryService.createQueryBuilder(false).ecl("<!" + conceptId), branchPath.toString(), LARGE_PAGE);
		Set<FhirSctProperty> properties = FhirSctProperty.parse(propertiesType);
		return pMapper.mapToFHIR(system, fullConcept, childIds.getContent(), properties, designations);
	}

	@Operation(name="$validate-code", idempotent=true)
	public Parameters validateCodeImplicit(
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
		return validateCode(request, response, url, codeSystem, code, display, version, date, coding, displayLanguage);
	}
	
	@Operation(name="$validate-code", idempotent=true)
	public Parameters validateCodeInstance(
			@IdParam IdType id,
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
		fhirHelper.notSupported("codeSystem", codeSystem, "CodeSystem where instance id is already specified in URL");
		fhirHelper.notSupported("version", version, "CodeSystem where instance id is already specified in URL");
		StringType systemURI = new StringType(getCodeSystem(id).getUrl());
		return validateCode(request, response, url, systemURI, code, display, version, date, coding, displayLanguage);
	}
	
	private Parameters validateCode(
			HttpServletRequest request,
			HttpServletResponse response,
			UriType url,
			StringType codeSystem,
			CodeType code,
			String display,
			StringType version,
			DateTimeType date,
			Coding coding,
			String displayLanguage) throws FHIROperationException {
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
			return pMapper.conceptNotFound();
		} else {
			return pMapper.mapToFHIR(fullConcept, display);
		}
	}
	
	@Operation(name="$subsumes", idempotent=true)
	public Parameters subsumesInstance(
			@IdParam IdType id,
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="codeA") CodeType codeA,
			@OperationParam(name="codeB") CodeType codeB,
			@OperationParam(name="system") StringType system,
			@OperationParam(name="version") StringType version,
			@OperationParam(name="codingA") Coding codingA,
			@OperationParam(name="codingB") Coding codingB)
			throws FHIROperationException {
		fhirHelper.notSupported("system", system, "CodeSystem where instance id is already specified in URL");
		fhirHelper.notSupported("version", version, "CodeSystem where instance id is already specified in URL");
			doSubsumptionParameterValidation(codeA, codeB, system, version, codingA, codingB);
		StringType systemURI = new StringType(getCodeSystem(id).getUrl());
		return subsumes(request, response, codeA, codeB, systemURI, version, codingA, codingB);
	}

	@Operation(name="$subsumes", idempotent=true)
	public Parameters subsumesImplicit(
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
		return subsumes(request, response, codeA, codeB, system, version, codingA, codingB);
	}
	
	private Parameters subsumes(
			HttpServletRequest request,
			HttpServletResponse response,
			CodeType codeA,
			CodeType codeB,
			StringType system,
			StringType version,
			Coding codingA,
			Coding codingB)
			throws FHIROperationException {
		String conceptAId = fhirHelper.recoverConceptId(codeA, codingA);
		String conceptBId = fhirHelper.recoverConceptId(codeB, codingB);
		if (conceptAId.equals(conceptBId)) {
			return pMapper.singleOutValue("outcome", "equivalent");
		}
		//Test for A subsumes B, then B subsumes A
		String eclAsubsumesB = conceptAId + " AND > " + conceptBId;
		String eclBsubsumesA = conceptBId + " AND > " + conceptAId;
		BranchPath branchPath = fhirHelper.getBranchPathFromURI(system);
		if (matchesConcept(eclAsubsumesB, branchPath)) {
			return pMapper.singleOutValue("outcome", "subsumes");
		} else if (matchesConcept(eclBsubsumesA, branchPath)) {
			return pMapper.singleOutValue("outcome", "subsumed-by");
		}
		//TODO First check for the concept in all known codesystemversions
		//Secondly, should we return an Outcome object if the concept is not found?
		ensureConceptExists(conceptAId, branchPath);
		ensureConceptExists(conceptBId, branchPath);
		return pMapper.singleOutValue("outcome", "not-subsumed");
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
		fhirHelper.mutuallyRequired("codeA", codeA, "codeB", codeB);
		fhirHelper.mutuallyRequired("codingA", codingA, "codingB", codingB);
		fhirHelper.mutuallyRequired("system", system, "codeA", codeA);
	}

	private Coding validateCodings(Coding codingA, Coding codingB) throws FHIROperationException {
		//Return whatever coding has a system, but if they both have one, ensure it's the same
		if (codingA == null && codingB == null ) {
			return null;
		} else if (codingA != null && codingB != null && codingB.getSystem() == null) {
			return codingA;
		} else if (codingB != null && codingA != null && codingA.getSystem() == null) {
			return codingB;
		} else if (codingA != null && codingB != null && !codingA.getSystem().equals(codingB.getSystem())) {
			throw new FHIROperationException(IssueType.CONFLICT, "CodeSystem defined in codingA must match that in codingB");
		}
		//Here both are present and they're the same system, so return either
		return codingA;
	}

	private boolean matchesConcept(String ecl, BranchPath branchPath) {
		//We don't care about language, use defaults
		Page<ConceptMini> result = fhirHelper.eclSearch(ecl, (Boolean)null, 
				(String)null, defaultLanguages, branchPath, FHIRHelper.SINGLE_ITEM_PAGE);
		return (result != null && result.hasContent() && result.getContent().size() == 1);
	}

	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return CodeSystem.class;
	}
	
}
