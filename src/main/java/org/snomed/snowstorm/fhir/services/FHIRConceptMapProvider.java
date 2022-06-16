package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.BranchPath;
import org.snomed.snowstorm.fhir.domain.FHIRConceptMap;
import org.snomed.snowstorm.fhir.domain.FHIRConceptMapGroup;
import org.snomed.snowstorm.fhir.repositories.FHIRConceptMapRepository;
import org.snomed.snowstorm.fhir.repositories.FHIRMapElementRepository;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class FHIRConceptMapProvider implements IResourceProvider, FHIRConstants {

	@Autowired
	// For maps within SNOMED CT releases
	private ReferenceSetMemberService memberService;

	@Autowired
	private FHIRConceptMapRepository conceptMapRepository;

	@Autowired
	private FHIRMapElementRepository mapElementRepository;

	@Autowired
	private HapiParametersMapper mapper;
	
	@Autowired
	private FHIRHelper fhirHelper;
	
	private static final int DEFAULT_PAGESIZE = 1000;
	
	private BiMap<String, String> knownUriMap;
	String[] validMapTargets;
	String[] validMapSources;

	//See https://www.hl7.org/fhir/valueset.html#search
	@Search
	public List<ConceptMap> findConceptMaps(
			HttpServletRequest theRequest,
			HttpServletResponse theResponse) {

		PageRequest pageRequest = PageRequest.of(0, 100);
		Page<FHIRConceptMap> page = conceptMapRepository.findAll(pageRequest);

		return page.getContent().stream()
				.map(FHIRConceptMap::getHapi)
				.peek(map -> map.setGroup(null))// Clear groups for display listing
				.collect(Collectors.toList());
	}

	@Read
	public ConceptMap getConceptMap(@IdParam IdType id) {
		String idPart = id.getIdPart();
		Optional<FHIRConceptMap> conceptMap = conceptMapRepository.findById(idPart);
		if (conceptMap.isPresent()) {
			FHIRConceptMap map = conceptMap.get();
			for (FHIRConceptMapGroup group : orEmpty(map.getGroup())) {
				List<FHIRMapElement> elements = mapElementRepository.findAllByGroupId(group.getGroupId());
				group.setElement(elements);
			}
			return map.getHapi();
		}
		return null;
	}

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
		normaliseURIs(source, target, ICD10, ICD10_URI);
		normaliseURIs(source, target, ICDO, ICDO_URI);
		
		if (!source.asStringValue().startsWith(SNOMED_URI) && source.asStringValue().equals(target.asStringValue())) {
			throw new FHIROperationException ("Source and target cannot be the same: '" + source.asStringValue() + "'", null, 400);
		}
		
		String refsetId = "";
		if (url != null) {
			validate("Url", url, Validation.STARTS_WITH, new String[] {SNOMED_URI, SNOMED_URI_UNVERSIONED}, true);
			int idx = url.indexOf(MAP_INDICATOR);
			if (idx == NOT_SET) {
				throw new FHIROperationException ("url parameter is expected to contain '"+ MAP_INDICATOR +"' indicating the refset sctid of the map to be used.", IssueType.INCOMPLETE, 400);
			}
			refsetId = url.substring(idx + MAP_INDICATOR.length());
		}
		
		//If a refset is specified does that match the target system?
		if (!refsetId.isEmpty()) {
			String expectedTargetSystem = knownUriMap.inverse().get(refsetId);
			if (expectedTargetSystem != null && !target.equals(expectedTargetSystem)) {
				throw new FHIROperationException ("Refset " + refsetId + " relates to target system '" + expectedTargetSystem + "' rather than '" + target + "'", IssueType.CONFLICT, 400);
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
				throw new FHIROperationException ("url parameter is expected to contain a parameter indicating the refset id of the map to be used", IssueType.INCOMPLETE, 400);
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

	private void normaliseURIs(UriType source, UriType target, String shortName, String uri) {
		//Allow shortNames to be input, but swap with the real URI
		if (target.asStringValue().equals(shortName)) {
			target = new UriType(uri);
		}
		if (source.asStringValue().equals(shortName)) {
			source = new UriType(uri);
		}

	}

	public void createMap(FHIRConceptMap conceptMap) {
		// TODO: Delete existing map?
		// Save concept map and groups
		conceptMapRepository.save(conceptMap);
		for (FHIRConceptMapGroup mapGroup : conceptMap.getGroup()) {
			// Save elements within each group
			mapElementRepository.saveAll(mapGroup.getElement());
		}
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
					throw new FHIROperationException(fieldName + " must be exactly equal to '" + expected + "'.  Received '" + actual + "'.", null, 400);
				}
				break;
			case STARTS_WITH:
				if (actual == null || !actual.startsWith(expected)) {
					throw new FHIROperationException(fieldName + " must start with '" + expected + "'.  Received '" + actual + "'.", null, 400);
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
			throw new FHIROperationException (fieldName + " expected to contain one of " + String.join(", ", permittedValues), null, 400);
		}
	}

	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return ConceptMap.class;
	}

	public void deleteAll() {
		conceptMapRepository.deleteAll();
		mapElementRepository.deleteAll();
	}
}
