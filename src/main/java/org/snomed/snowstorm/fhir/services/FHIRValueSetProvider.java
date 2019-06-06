package org.snomed.snowstorm.fhir.services;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregations;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.ValueSetWrapper;
import org.snomed.snowstorm.fhir.repositories.FHIRValuesetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;

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
	private FHIRHelper fhirHelper;
	
	private static int DEFAULT_PAGESIZE = 1000;
	
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	@Read()
	public ValueSet getValueSet(@IdParam IdType id) {
		Optional<ValueSetWrapper> vs = valuesetRepository.findById(id.getIdPart());
		if (vs.isPresent()) {
			return vs.get().getValueset();
		}
		return null;
	}
	
	@Create()
	public MethodOutcome createValueset(@IdParam IdType id, @ResourceParam ValueSet vs) throws FHIROperationException {
		MethodOutcome outcome = new MethodOutcome();
		validateId(id, vs);
		ValueSetWrapper savedVs = valuesetRepository.save(new ValueSetWrapper(id, vs));
		int version = 1;
		if (id.hasVersionIdPart()) {
			version += id.getVersionIdPartAsLong();
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
	
	@Search
	public List<ValueSet> findValuesets(
			HttpServletRequest theRequest, 
			HttpServletResponse theResponse) {
		return StreamSupport.stream(valuesetRepository.findAll().spliterator(), false)
				.map(vs -> vs.getValueset())
				.collect(Collectors.toList());
	}
	
	@Operation(name="$expand", idempotent=true)
	public ValueSet expandInstance(@IdParam IdType id,
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="url") String url,
			@OperationParam(name="filter") String filter,
			@OperationParam(name="activeOnly") BooleanType activeType,
			@OperationParam(name="includeDesignations") BooleanType includeDesignationsType,
			@OperationParam(name="designation") List<String> designations,
			@OperationParam(name="displayLanguage") String displayLanguage,
			@OperationParam(name="offset") String offsetStr,
			@OperationParam(name="count") String countStr) throws FHIROperationException {
		return expand (id, request, response, url, filter, activeType, includeDesignationsType,
				designations, displayLanguage, offsetStr, countStr);
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
			@OperationParam(name="count") String countStr) throws FHIROperationException {
		return expand (null, request, response, url, filter, activeType, includeDesignationsType,
				designations, displayLanguage, offsetStr, countStr);
	}
	
	private ValueSet expand (@IdParam IdType id,
			HttpServletRequest request,
			HttpServletResponse response,
			String url,
			String filter,
			BooleanType activeType,
			BooleanType includeDesignationsType,
			List<String> designations,
			String displayLanguage,
			String offsetStr,
			String countStr) throws FHIROperationException {
		//Are we expanding a specific named Valueset?
		ValueSet vs = null;
		if (id != null) {
			logger.info("Expanding '{}'",id.getIdPart());
			vs = getValueSet(id);
			//Are we expanding based on the URL of the named ValueSet?  Can't do both!
			if (url != null && vs.getUrl() != null) {
				throw new FHIROperationException(IssueType.VALUE, "Cannot expand both '" + vs.getUrl() + "' in " + id.getIdPart() + "' and '" + url + "' in request.");
			}
			url = vs.getUrl();
		}
		//The code system is the URL up to where the parameters start eg http://snomed.info/sct?fhir_vs=ecl/ or http://snomed.info/sct/45991000052106?fhir_vs=ecl/
		int cutPoint = url == null ? -1 : url.indexOf("?");
		if (cutPoint == -1) {
			throw new FHIROperationException(IssueType.VALUE, "'url' parameter is expected to be present for an expansion, containing eg http://snomed.info/sct?fhir_vs=ecl/ or http://snomed.info/sct/45991000052106?fhir_vs=ecl/ ");
		}
		StringType codeSystemVersionUri = new StringType(url.substring(0, cutPoint));
		String branchPath = fhirHelper.getBranchPathForCodeSystemVersion(codeSystemVersionUri);
		int offset = (offsetStr == null || offsetStr.isEmpty()) ? 0 : Integer.parseInt(offsetStr);
		int pageSize = (countStr == null || countStr.isEmpty()) ? DEFAULT_PAGESIZE : Integer.parseInt(countStr);
		Boolean active = activeType == null ? null : activeType.booleanValue();
		List<String> languageCodes = fhirHelper.getLanguageCodes(designations, request);
		
		//Also if displayLanguage has been used, ensure that's part of our requested Language Codes
		if (displayLanguage != null && !languageCodes.contains(displayLanguage)) {
			languageCodes.add(displayLanguage);
		}
		
		//If someone specified designations, then include them in any event
		Boolean includeDesignations = includeDesignationsType == null ? false : includeDesignationsType.booleanValue();
		if (designations != null) {
			includeDesignations = true;
		}
		
		Page<ConceptMini> conceptMiniPage;
		boolean missingFsns = false;
		//Are we looking for all known refsets?  Special case.
		if (url.endsWith("?fhir_vs=refset")) {
			conceptMiniPage = findAllRefsets(branchPath, PageRequest.of(offset, pageSize));
			missingFsns = true;
		} else {
			String ecl = determineEcl(url);
			QueryService.ConceptQueryBuilder queryBuilder = queryService.createQueryBuilder(false);  //Inferred view only for now
			queryBuilder.ecl(ecl)
						.termMatch(filter)
						.languageCodes(languageCodes)
						.activeFilter(active);
	
			conceptMiniPage = queryService.search(queryBuilder, BranchPathUriUtil.decodePath(branchPath), PageRequest.of(offset, pageSize));
			logger.info("Recovered: {} concepts from branch: {} with ecl: '{}'", conceptMiniPage.getContent().size(), branchPath, ecl);
		}
		
		//Do we need further detail on each concept?
		Map<String, Concept> conceptDetails = null;
		if (includeDesignations || missingFsns || displayLanguage != null || designations != null) {
			conceptDetails = getConceptDetailsMap(branchPath, conceptMiniPage, languageCodes);
		}
		
		ValueSet valueSet = mapper.mapToFHIR(vs, conceptMiniPage.getContent(), url, conceptDetails, languageCodes, displayLanguage, includeDesignations); 
		valueSet.getExpansion().setTotal((int)conceptMiniPage.getTotalElements());
		valueSet.getExpansion().setOffset(offset);
		return valueSet;
	}
	
	private void validateId(IdType id, ValueSet vs) throws FHIROperationException {
		if (vs == null || id == null) {
			throw new FHIROperationException(IssueType.EXCEPTION, "Both ID and ValueSet object must be supplied");
		}
		if (vs.getId() == null || !id.asStringValue().equals(vs.getId())) {
			throw new FHIROperationException(IssueType.EXCEPTION, "ID in request must match that in ValueSet object");
		}
	}
	
	private Page<ConceptMini> findAllRefsets(String branchPath, PageRequest pageRequest) {
		PageWithBucketAggregations<ReferenceSetMember> bucketPage = refsetService.findReferenceSetMembersWithAggregations(branchPath, pageRequest, true);
		List<ConceptMini> refsets = new ArrayList<>();
		if (bucketPage.getBuckets() != null && bucketPage.getBuckets().containsKey("referenceSetIds")) {
			refsets = bucketPage.getBuckets().get("referenceSetIds").keySet().stream()
					.map(s -> new ConceptMini(s, null))
					.collect(Collectors.toList());
		}
		return new PageImpl<ConceptMini>(refsets, pageRequest, refsets.size());
	}

	private Map<String, Concept> getConceptDetailsMap(String branchPath, Page<ConceptMini> page, List<String> languageCodes) {
		Map<String, Concept> conceptDetails = null;
		if (page.hasContent()) {
			conceptDetails = new HashMap<>();
			List<String> ids = page.getContent().stream()
					.map(c -> c.getConceptId())
					.collect(Collectors.toList());
			conceptDetails = conceptService.find(branchPath, ids, languageCodes).stream()
				.collect(Collectors.toMap(Concept::getConceptId, c-> c));
		}
		return conceptDetails;
	}

	/**
	 * See https://www.hl7.org/fhir/snomedct.html#implicit 
	 * @param url
	 * @return
	 * @throws FHIROperationException 
	 */
	private String determineEcl(String url) throws FHIROperationException {
		String ecl;
		if (url.endsWith("?fhir_vs")) {
			//Return all of SNOMED CT in this situation
			ecl = "*";
		} else if (url.contains(IMPLICIT_ISA)) {
			String sctId = url.substring(url.indexOf(IMPLICIT_ISA) + IMPLICIT_ISA.length());
			ecl = "<<" + sctId;
		} else if (url.contains(IMPLICIT_REFSET)) {
			String sctId = url.substring(url.indexOf(IMPLICIT_REFSET) + IMPLICIT_REFSET.length());
			ecl = "^" + sctId;
		} else if (url.contains(IMPLICIT_ECL)) {
			ecl = url.substring(url.indexOf(IMPLICIT_ECL) + IMPLICIT_ECL.length());
		} else {
			throw new FHIROperationException(IssueType.VALUE, "url is expected to include parameter with value: 'fhir_vs=ecl/'");
		}
		return ecl;
	}

	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return ValueSet.class;
	}
}
