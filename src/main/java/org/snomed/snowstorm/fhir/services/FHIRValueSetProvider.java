package org.snomed.snowstorm.fhir.services;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.ValueSet.*;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregations;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.*;
import org.snomed.snowstorm.fhir.repositories.FHIRValuesetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.QuantityParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;

import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_DIALECTS;
import static org.snomed.snowstorm.core.data.services.ReferenceSetMemberService.AGGREGATION_MEMBER_COUNTS_BY_REFERENCE_SET;

@Component
public class FHIRValueSetProvider implements IResourceProvider, FHIRConstants {
	
	@Autowired
	private FHIRValuesetRepository valuesetRepository;
	
	@Autowired
	private QueryService queryService;
	
	@Autowired
	private ConceptService conceptService;
	
	@Autowired
	private ReferenceSetMemberService refsetService;
	
	@Autowired
	private HapiValueSetMapper mapper;
	
	@Autowired
	private HapiParametersMapper paramMapper;
	
	@Autowired
	private DialectConfigurationService dialectService;
	
	@Autowired
	private FHIRHelper fhirHelper;
	
	private static int DEFAULT_PAGESIZE = 1000;
	
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	@Read()
	public ValueSet getValueSet(@IdParam IdType id) {
		Optional<ValueSetWrapper> vsOpt = valuesetRepository.findById(id.getIdPart());
		if (vsOpt.isPresent()) {
			ValueSet vs = vsOpt.get().getValueset();
			//If we're not calling the expansion operation, don't include that element
			vs.setExpansion(null);
			return vs;
		}
		return null;
	}
	
	@Create()
	public MethodOutcome createValueset(@IdParam IdType id, @ResourceParam ValueSet vs) throws FHIROperationException {
		MethodOutcome outcome = new MethodOutcome();
		validateId(id, vs);
		
		//Attempt to expand the valueset in lieu of full validation
		if (vs != null && vs.getCompose() != null && !vs.getCompose().isEmpty()) {
			obtainConsistentCodeSystemVersionFromCompose(vs.getCompose(), new BranchPath("MAIN"));
			covertComposeToEcl(vs.getCompose());
		}
		
		ValueSetWrapper savedVs = valuesetRepository.save(new ValueSetWrapper(id, vs));
		int version = 1;
		if (id.hasVersionIdPart()) {
			version += id.getVersionIdPartAsLong().intValue();
		}
		outcome.setId(new IdType("ValueSet", savedVs.getId(), Long.toString(version)));
		return outcome;
	}

	@Update
	public MethodOutcome updateValueset(@IdParam IdType id, @ResourceParam ValueSet vs) throws FHIROperationException {
		try {
			return createValueset(id, vs);
		} catch (Exception e) {
			throw new FHIROperationException(IssueType.EXCEPTION, "Failed to update/create valueset '" + vs.getId(),e);
		}
	}
	
	@Delete
	public void deleteValueset(@IdParam IdType id) {
		valuesetRepository.deleteById(id.getIdPart());
	}
	
	
	//See https://www.hl7.org/fhir/valueset.html#search
	@Search
	public List<ValueSet> findValuesets(
			HttpServletRequest theRequest, 
			HttpServletResponse theResponse,
			@OptionalParam(name="_id") String id,
			@OptionalParam(name="code") String code,
			@OptionalParam(name="context") TokenParam context,
			@OptionalParam(name="context-quantity") QuantityParam contextQuantity,
			@OptionalParam(name="context-type") String contextType,
			@OptionalParam(name="date") String date,
			@OptionalParam(name="description") StringParam description,
			@OptionalParam(name="expansion") String expansion,
			@OptionalParam(name="identifier") StringParam identifier,
			@OptionalParam(name="jurisdiction") StringParam jurisdiction,
			@OptionalParam(name="name") StringParam name,
			@OptionalParam(name="publisher") StringParam publisher,
			@OptionalParam(name="reference") StringParam reference,
			@OptionalParam(name="status") String status,
			@OptionalParam(name="title") StringParam title,
			@OptionalParam(name="url") String url,
			@OptionalParam(name="version") String version) throws FHIROperationException {
		SearchFilter vsFilter = new SearchFilter()
									.withId(id)
									.withCode(code)
									.withContext(context)
									.withContextQuantity(contextQuantity)
									.withContextType(contextType)
									.withDate(date)
									.withDescription(description)
									.withExpansion(expansion)
									.withIdentifier(identifier)
									.withJurisdiction(jurisdiction)
									.withName(name)
									.withPublisher(publisher)
									.withReference(reference)
									.withStatus(status)
									.withTitle(title)
									.withUrl(url)
									.withVersion(version);
		return StreamSupport.stream(valuesetRepository.findAll().spliterator(), false)
				.map(vs -> vs.getValueset())
				.filter(vs -> vsFilter.apply(vs, queryService, fhirHelper))
				.collect(Collectors.toList());
	}
	
	@Operation(name="$expand", idempotent=true)
	public ValueSet expandInstance(
			@IdParam IdType id,
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="url") String url,  //TODO Check how URL gets used with an Id
			@OperationParam(name="filter") String filter,
			@OperationParam(name="activeOnly") BooleanType activeType,
			@OperationParam(name="includeDesignations") BooleanType includeDesignationsType,
			@OperationParam(name="designation") List<String> designations,
			@OperationParam(name="displayLanguage") String displayLanguage,
			@OperationParam(name="offset") String offsetStr,
			@OperationParam(name="count") String countStr,
			@OperationParam(name="version") StringType version,
			@OperationParam(name="system-version") StringType systemVersion,
			@OperationParam(name="force-system-version") StringType forceSystemVersion) throws FHIROperationException {
		fhirHelper.notSupported("version", version, "ValueSet $expand operation.  Use system-version or force-system-version parameters instead.");
		return expand (id, request, response, url, filter, activeType, includeDesignationsType,
				designations, displayLanguage, offsetStr, countStr, systemVersion, forceSystemVersion);
	}
	
	@Operation(name="$expand", idempotent=true)
	public ValueSet expandType(
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="url") String url,
			@OperationParam(name="filter") String filter,
			@OperationParam(name="activeOnly") BooleanType activeType,
			@OperationParam(name="includeDesignations") BooleanType includeDesignationsType,
			@OperationParam(name="designation") List<String> designations,
			@OperationParam(name="displayLanguage") String displayLanguage,
			@OperationParam(name="offset") String offsetStr,
			@OperationParam(name="count") String countStr,
			@OperationParam(name="version") StringType version,
			@OperationParam(name="system-version") StringType systemVersion,
			@OperationParam(name="force-system-version") StringType forceSystemVersion) throws FHIROperationException {
		fhirHelper.notSupported("version", version, "ValueSet $expand operation.  Use system-version or force-system-version parameters instead.");
		return expand(null, request, response, url, filter, activeType, includeDesignationsType,
				designations, displayLanguage, offsetStr, countStr, systemVersion, forceSystemVersion);
	}
	
	@Operation(name="$validate-code", idempotent=true)
	public Parameters validateCodeExplicit(
			@IdParam IdType id,
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="url") UriType url,
			@OperationParam(name="codeSystem") StringType codeSystem,
			@OperationParam(name="code") CodeType code,
			@OperationParam(name="codeableConcept") Coding codeableConcept,
			@OperationParam(name="coding") Coding coding,
			@OperationParam(name="display") String display,
			@OperationParam(name="version") StringType version,
			@OperationParam(name="date") DateTimeType date,
			@OperationParam(name="abstract") BooleanType abstractBool,
			@OperationParam(name="context") String context,
			@OperationParam(name="displayLanguage") String displayLanguage) throws FHIROperationException {
		List<LanguageDialect> languageDialects = fhirHelper.getLanguageDialects(null, request);
		return validateCode(id, url, codeSystem, code, display, version, date, coding, codeableConcept, context, abstractBool, displayLanguage, languageDialects);
	}

	@Operation(name="$validate-code", idempotent=true)
	public Parameters validateCodeImplicit(
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="url") UriType url,
			@OperationParam(name="codeSystem") StringType codeSystem,
			@OperationParam(name="code") CodeType code,
			@OperationParam(name="codeableConcept") Coding codeableConcept,
			@OperationParam(name="coding") Coding coding,
			@OperationParam(name="display") String display,
			@OperationParam(name="version") StringType version,
			@OperationParam(name="date") DateTimeType date,
			@OperationParam(name="abstract") BooleanType abstractBool,
			@OperationParam(name="context") String context,
			@OperationParam(name="displayLanguage") String displayLanguage) throws FHIROperationException {
		List<LanguageDialect> languageDialects = fhirHelper.getLanguageDialects(null, request);
		return validateCode(null, url, codeSystem, code, display, version, date, coding, codeableConcept, context, abstractBool, displayLanguage, languageDialects);
	}
	
	private Parameters validateCode(IdType id, UriType urlType, StringType codeSystem, CodeType code, String display,
			StringType version, DateTimeType date, Coding coding, Coding codeableConcept, String context, 
			BooleanType abstractBool, String displayLanguage, 
			List<LanguageDialect> languageDialects) throws FHIROperationException {
		String url = urlType == null ? null : urlType.primitiveValue();
		doParameterValidation (url, codeSystem, code, coding, codeableConcept, context, display, date, version, abstractBool);
		//Can we get a codeSystem from the URL?
		if (url != null && url.startsWith(SNOMED_URI) && 
				url.indexOf("?") > SNOMED_URI.length()) {
			if (codeSystem != null) {
				throw new FHIROperationException (IssueType.INVARIANT, "Cannot handle CodeSystem defined via both url and codeSystem parameter");
			}
			codeSystem = new StringType(url.substring(0, url.indexOf("?")));
		}
		
		if (version != null) {
			if (codeSystem == null) {
				codeSystem = new StringType(SNOMED_URI + "/version/" + version.toString());
			} else {
				fhirHelper.append(codeSystem, "/version/" + version.toString());
			}
		}
		//From either a saved VS instance or some implcit url, can we recover some ECL?
		String ecl = getECL(id, url == null? null : url);
		if (ecl != null) { 
			String conceptId = fhirHelper.recoverConceptId(code, coding);
			BranchPath branchPath = fhirHelper.getBranchPathFromURI(codeSystem);
			//Construct ECL to find the intersection of these two sets
			String intersectionEcl = conceptId + " AND (" + ecl + ")";
			Page<ConceptMini> result = fhirHelper.eclSearch(queryService, intersectionEcl, null, null, languageDialects, branchPath, 0, 1);
			if (result.getContent().size() == 1) {
				ConceptMini concept = result.getContent().get(0);
				if (!concept.getConceptId().equals(conceptId)) {
					throw new FHIROperationException (IssueType.PROCESSING, "ECL recovered an unexpected concept id (" + concept.getConceptId() + ") using " + intersectionEcl);
				}
				Concept fullConcept = conceptService.find(conceptId, languageDialects, branchPath.toString());
				return paramMapper.mapToFHIR(fullConcept, display);
			} else {
				//Now it might be that in this case we do not have this ValueSet loaded at all - or it's been 
				//defined or the substrate has changed such that it has no members.   MAINT-1261 refers
				result = fhirHelper.eclSearch(queryService, ecl, null, null, languageDialects, branchPath, 0, 1);
				if (result.getContent().size() == 0) {
					throw new FHIROperationException (IssueType.PROCESSING, "Concept not found and additionally the Valueset contains no members when expanded against the specified substrate. Check any relevant reference set is actually loaded.  ECL = " + ecl + ", branch path = " + branchPath);
				}
				return paramMapper.conceptNotFound();
			}
		} else {
			//TODO We have some sort of enumerated valueset saved, we need to just search through the members
			throw new FHIROperationException (IssueType.NOTSUPPORTED, "Validating code against enumerated ValueSets has still to be implemented");
		}
	}
	
	private String getECL(IdType id, String url) throws FHIROperationException {
		ValueSet vs = null;
		if (id != null) {
			logger.info("Expanding '{}'",id.getIdPart());
			vs = getValueSet(id);
			if (vs == null) {
				return null; // Will be translated into a 404
			}
			// Are we expanding based on the URL of the named ValueSet?  Can't do both!
			if (url != null && vs.getUrl() != null) {
				throw new FHIROperationException(IssueType.VALUE, "Cannot expand both '" + vs.getUrl() + "' in " + id.getIdPart() + "' and '" + url + "' in request.");
			}
			url = vs.getUrl();
			String ecl = determineEcl(url, false);
			if (ecl == null) {
				ecl = covertComposeToEcl(vs.getCompose());
			}
			return ecl;
		} else {
			int cutPoint = url == null ? -1 : url.indexOf("?");
			if (cutPoint == NOT_SET) {
				throw new FHIROperationException(IssueType.INCOMPLETE, "Require a ValueSet instance, or an implicit ValueSet defined in the 'url' parameter to validate code against");
			}
		}
		return determineEcl(url, false);  //Don't throw exception if we don't find ECL in URL.
	}

	private void doParameterValidation(String url, StringType codeSystem, CodeType code, Coding coding, Coding codeableConcept, String display, String display2, DateTimeType date, StringType version, BooleanType abstractBool) throws FHIROperationException {
		fhirHelper.mutuallyExclusive("code", code, "coding", coding);
		fhirHelper.mutuallyRequired("display", display, "code", code, "coding", coding);
		if (coding != null && coding.getSystem() != null) {
			fhirHelper.mutuallyExclusive("version", version, "coding|codeSystem", coding.getSystem());
			fhirHelper.mutuallyExclusive("codeSystem", codeSystem, "coding|codeSystem", coding.getSystem());
			codeSystem = new StringType (coding.getSystem());
		} 
		
		fhirHelper.notSupported("date", date);
		fhirHelper.notSupported("abstract", abstractBool);
		fhirHelper.notSupported("context", abstractBool);
		fhirHelper.notSupported("codeableConcept", abstractBool);
	}

	
	private ValueSet expand(@IdParam IdType id,
			HttpServletRequest request,
			HttpServletResponse response,
			String url,
			String filter,
			BooleanType activeType,
			BooleanType includeDesignationsType,
			List<String> designationsStr,
			String displayLanguageStr,
			String offsetStr,
			String countStr,
			StringType systemVersion, StringType forceSystemVersion) throws FHIROperationException {
		// Are we expanding a specific named Valueset?
		ValueSet vs = null;
		if (id != null) {
			logger.info("Expanding '{}'",id.getIdPart());
			vs = getValueSet(id);
			if (vs == null) {
				return null; // Will be translated into a 404
			}
			// Are we expanding based on the URL of the named ValueSet?  Can't do both!
			if (url != null && vs.getUrl() != null) {
				throw new FHIROperationException(IssueType.VALUE, "Cannot expand both '" + vs.getUrl() + "' in " + id.getIdPart() + "' and '" + url + "' in request.");
			}
			url = vs.getUrl();
		}
		
		int offset = (offsetStr == null || offsetStr.isEmpty()) ? 0 : Integer.parseInt(offsetStr);
		int pageSize = (countStr == null || countStr.isEmpty()) ? DEFAULT_PAGESIZE : Integer.parseInt(countStr);
		Boolean active = activeType == null ? null : activeType.booleanValue();
		BranchPath branchPath = new BranchPath();
		Page<ConceptMini> conceptMiniPage;
		List<LanguageDialect> designations = new ArrayList<>();
		boolean includeDesignations = setLanguageOptions(designations, designationsStr, displayLanguageStr, includeDesignationsType, request);

		//If we've specified a system version as part of the call, then that overrides whatever is in the compose element or URL
		//TODO In fact this behaviour needs to be a little more subtle.  The total override is what forceSystemVersion does
		//What this parameter needs to do is only specify the version when it is not otherwise specified in the ValueSet
		//TODO pass this value or forceSystemVersion through to the implicit/explicit expansion methods so they can decide
		//if they need to use it or not.   It fails too early here if the branch does not exist.
		if (systemVersion != null && !systemVersion.asStringValue().isEmpty()) {
			branchPath.set(fhirHelper.getBranchPathFromURI(systemVersion));
		}
		
		boolean branchPathForced = false;
		if (forceSystemVersion != null && !forceSystemVersion.asStringValue().isEmpty()) {
			branchPathForced = true;
			branchPath.set(fhirHelper.getBranchPathFromURI(forceSystemVersion));
			logger.warn("ValueSet expansion system version being forced to " + forceSystemVersion + " which evaluated to branch path " + branchPath);
		}
		
		//The code system is the URL up to where the parameters start eg http://snomed.info/sct?fhir_vs=ecl/ or http://snomed.info/sct/45991000052106?fhir_vs=ecl/
		//These calls will also set the branchPath
		int cutPoint = url == null ? -1 : url.indexOf("?");
		if (cutPoint == NOT_SET) {
			conceptMiniPage = doExplicitExpansion(vs, active, filter, branchPath, designations, offset, pageSize, branchPathForced);
		} else {
			if (!branchPathForced) {
				StringType codeSystemVersionUri = new StringType(url.substring(0, cutPoint));
				//If we've no branch path, or the systemVersion wasn't specified, or the implicit URL is more specific than the systemVersion
				//the use the implicit URL's idea of what the code system version should be
				if (branchPath.isEmpty() || systemVersion == null || codeSystemVersionUri.toString().length() >= systemVersion.toString().length()) {
					branchPath.set(fhirHelper.getBranchPathFromURI(codeSystemVersionUri));
				}
			}
			conceptMiniPage = doImplcitExpansion(cutPoint, url, active, filter, branchPath, designations, offset, pageSize, branchPathForced);
		}
		
		//We will always need the PT, so recover further details
		Map<String, Concept> conceptDetails = getConceptDetailsMap(branchPath, conceptMiniPage, designations);
		ValueSet valueSet = mapper.mapToFHIR(vs, conceptMiniPage.getContent(), url, conceptDetails, designations, includeDesignations); 
		valueSet.getExpansion().setTotal((int)conceptMiniPage.getTotalElements());
		valueSet.getExpansion().setOffset(offset);
		return valueSet;
	}

	private boolean setLanguageOptions(List<LanguageDialect> designations, List<String> designationsStr,
			String displayLanguageStr, BooleanType includeDesignationsType, HttpServletRequest request) throws FHIROperationException {
		boolean includeDesignations = false;
		designations.addAll(fhirHelper.getLanguageDialects(designationsStr, request));
		// Also if displayLanguage has been used, ensure that's part of our requested Language Codes
		if (displayLanguageStr != null) {
			LanguageDialect displayDialect = dialectService.getLanguageDialect(displayLanguageStr);
			//Ensure the display language is first in our list
			if (contains(designations, displayLanguageStr)) {
				designations.remove(displayDialect);
			}
			designations.add(0, displayDialect);
		} 

		//If someone specified designations, then include them unless specified not to, in which 
		//case use only for the displayLanguage because that's the only way to get a langRefsetId specified
		if (includeDesignationsType != null) {
			includeDesignations = includeDesignationsType.booleanValue();
			//If we're including designations but not specified which ones, use the default
			if (includeDesignations && designations.isEmpty()) {
				designations.addAll(DEFAULT_LANGUAGE_DIALECTS);
			}
		} else {
			//Otherwise include designations if we've specified one or more
			includeDesignations = designationsStr != null;
		}
		return includeDesignations;
	}

	/**
	 * An implicit ValueSet is one that hasn't been saved on the server, but is being 
	 * defined at expansion time by use of a URL containing a definition of the content
	 * @param branchPathForced 
	 */
	private Page<ConceptMini> doImplcitExpansion(int cutPoint, String url, Boolean active, String filter,
			BranchPath branchPath, List<LanguageDialect> designations, int offset, int pageSize, boolean branchPathForced) throws FHIROperationException {
		//Are we looking for all known refsets?  Special case.
		if (url.endsWith("?fhir_vs=refset")) {
			return findAllRefsets(branchPath, PageRequest.of(offset, pageSize));
		} else {
			String ecl = determineEcl(url, true);
			Page<ConceptMini> conceptMiniPage = fhirHelper.eclSearch(queryService, ecl, active, filter, designations, branchPath, offset, pageSize);
			logger.info("Recovered: {} concepts from branch: {} with ecl: '{}'", conceptMiniPage.getContent().size(), branchPath, ecl);
			return conceptMiniPage;
		}
	}

	/**
	 * An explicit ValueSet has been saved on the server with a name and id, and 
	 * is defined by use of the "compose" element within the valueset resource.
	 * @param branchPathForced 
	 */
	private Page<ConceptMini> doExplicitExpansion(ValueSet vs, Boolean active, String filter,
			BranchPath branchPath, List<LanguageDialect> designations, int offset, int pageSize, boolean branchPathForced) throws FHIROperationException {
		Page<ConceptMini> conceptMiniPage = new PageImpl<>(new ArrayList<>());
		if (vs != null && vs.getCompose() != null && !vs.getCompose().isEmpty()) {
			if (!branchPathForced) {
				branchPath.set(obtainConsistentCodeSystemVersionFromCompose(vs.getCompose(), branchPath));
			}
			String ecl = covertComposeToEcl(vs.getCompose());
			conceptMiniPage = fhirHelper.eclSearch(queryService, ecl, active, filter, designations, branchPath, offset, pageSize);
			logger.info("Recovered: {} concepts from branch: {} with ecl from compose: '{}'", conceptMiniPage.getContent().size(), branchPath, ecl);
		} else {
			String msg = "Compose element(s) or 'url' parameter is expected to be present for an expansion, containing eg http://snomed.info/sct?fhir_vs=ecl/ or http://snomed.info/sct/45991000052106?fhir_vs=ecl/ ";
			//We don't need ECL if we're expanding a named valueset
			if (vs != null) {
				logger.warn(msg + " when expanding " + vs.getId());
			} else {
				throw new FHIROperationException(IssueType.VALUE, msg);
			}
		}
		return conceptMiniPage;
	}

	private boolean contains(List<LanguageDialect> languageDialects, String displayLanguage) {
		return languageDialects.stream()
		.anyMatch(ld -> ld.getLanguageCode().equals(displayLanguage));
	}

	private BranchPath obtainConsistentCodeSystemVersionFromCompose(ValueSetComposeComponent compose, BranchPath branchPath) throws FHIROperationException {
		String system = null;
		String version = null;
		
		//Check all include and exclude elements to ensure they have a consistent snomed URI
		List<ConceptSetComponent> allIncludeExcludes = new ArrayList<>(compose.getInclude());
		allIncludeExcludes.addAll(compose.getExclude());
		for (ConceptSetComponent thisIncludeExclude : allIncludeExcludes) {
			if (thisIncludeExclude.getSystem() != null && !thisIncludeExclude.getSystem().contains(SNOMED_URI)) {
				throw new FHIROperationException (IssueType.NOTSUPPORTED , "Server currently limited to compose elements using SNOMED CT code system");
			}
			if (thisIncludeExclude.getSystem() != null && system == null) {
				system = thisIncludeExclude.getSystem();
			}
			if (thisIncludeExclude.getVersion() != null && version == null) {
				version = thisIncludeExclude.getVersion();
			}
			if (system != null && thisIncludeExclude.getSystem() != null && !system.equals(thisIncludeExclude.getSystem())) {
				String msg = "Server currently requires consistency in ValueSet compose element code systems.";
				msg += " Encoundered both '" + system + "' and '" + thisIncludeExclude.getSystem() + "'."; 
				throw new FHIROperationException (IssueType.NOTSUPPORTED , msg);
			}
			if (version != null && thisIncludeExclude.getVersion() != null && !version.equals(thisIncludeExclude.getVersion())) {
				throw new FHIROperationException (IssueType.NOTSUPPORTED , "Server currently requires consistency in ValueSet compose element code system versions");
			}
		}
		
		StringType codeSystemVersionUri;
		if (version == null) {
			if (system == null) {
				return branchPath;
			} else {
				codeSystemVersionUri = new StringType(system);
			}
		} else {
			codeSystemVersionUri = new StringType(system + "/" + version);
		}
		
		return fhirHelper.getBranchPathFromURI(codeSystemVersionUri);
	}
	
	
	public String covertComposeToEcl(ValueSetComposeComponent compose) throws FHIROperationException {
		//Successive include elements will be added using 'OR'
		//While the excludes will be added using 'MINUS'
		String ecl = "";
		boolean isFirstInclude = true;
		for (ConceptSetComponent include : compose.getInclude()) {
			if (isFirstInclude) {
				isFirstInclude = false;
			} else {
				ecl += " OR ";
			}
			ecl += "( " + fhirHelper.convertToECL(include) + " )";
		}
		
		//We need something to minus!
		if (isFirstInclude) {
			throw new FHIROperationException (IssueType.VALUE , "Invalid use of exclude without include in ValueSet compose element.");
		}
		
		for (ConceptSetComponent exclude : compose.getExclude()) {
			ecl += " MINUS ( " + fhirHelper.convertToECL(exclude) + " )";
		}
		
		return ecl;
	}

	private void validateId(IdType id, ValueSet vs) throws FHIROperationException {
		if (vs == null || id == null) {
			throw new FHIROperationException(IssueType.EXCEPTION, "Both ID and ValueSet object must be supplied");
		}
		if (vs.getId() == null || !id.asStringValue().equals(vs.getId())) {
			throw new FHIROperationException(IssueType.EXCEPTION, "ID in request must match that in ValueSet object");
		}
	}
	
	private Page<ConceptMini> findAllRefsets(BranchPath branchPath, PageRequest pageRequest) {
		PageWithBucketAggregations<ReferenceSetMember> bucketPage = refsetService.findReferenceSetMembersWithAggregations(branchPath.toString(), pageRequest, new MemberSearchRequest().active(true));
		List<ConceptMini> refsets = new ArrayList<>();
		if (bucketPage.getBuckets() != null && bucketPage.getBuckets().containsKey(AGGREGATION_MEMBER_COUNTS_BY_REFERENCE_SET)) {
			refsets = bucketPage.getBuckets().get(AGGREGATION_MEMBER_COUNTS_BY_REFERENCE_SET).keySet().stream()
					.map(s -> new ConceptMini(s, null))
					.collect(Collectors.toList());
		}
		return new PageImpl<>(refsets, pageRequest, refsets.size());
	}

	private Map<String, Concept> getConceptDetailsMap(BranchPath branchPath, Page<ConceptMini> page, List<LanguageDialect> languageDialects) {
		if (!page.hasContent()) {
			return null;
		}
		List<String> ids = page.getContent().stream()
				.map(ConceptMini::getConceptId)
				.collect(Collectors.toList());
		return conceptService.find(branchPath.toString(), ids, languageDialects).stream()
			.collect(Collectors.toMap(Concept::getConceptId, c -> c));
	}

	/**
	 * See https://www.hl7.org/fhir/snomedct.html#implicit 
	 * @param url
	 * @return
	 * @throws FHIROperationException 
	 */
	private String determineEcl(String url, boolean validate) throws FHIROperationException {
		String ecl = null;
		if (url.endsWith("?fhir_vs")) {
			// Return all of SNOMED CT in this situation
			ecl = "*";
		} else if (url.contains(IMPLICIT_ISA)) {
			String sctId = url.substring(url.indexOf(IMPLICIT_ISA) + IMPLICIT_ISA.length());
			ecl = "<<" + sctId;
		} else if (url.contains(IMPLICIT_REFSET)) {
			String sctId = url.substring(url.indexOf(IMPLICIT_REFSET) + IMPLICIT_REFSET.length());
			ecl = "^" + sctId;
		} else if (url.contains(IMPLICIT_ECL)) {
			ecl = url.substring(url.indexOf(IMPLICIT_ECL) + IMPLICIT_ECL.length());
		} else if (validate) {
			throw new FHIROperationException(IssueType.VALUE, "url is expected to include parameter with value: 'fhir_vs=ecl/'");
		}
		return ecl;
	}

	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return ValueSet.class;
	}
}
