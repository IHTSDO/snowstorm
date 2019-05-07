package org.snomed.snowstorm.fhir.services;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;

@Component
public class FHIRValueSetProvider implements IResourceProvider, FHIRConstants {
	
	@Autowired
	private QueryService queryService;
	
	@Autowired
	private HapiValueSetMapper mapper;
	
	@Autowired
	private FHIRHelper fhirHelper;
	
	private static int DEFAULT_PAGESIZE = 1000;
	
	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Operation(name="$expand", idempotent=true)
	public ValueSet expand(
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="url") String url,
			@OperationParam(name="filter") String filter,
			@OperationParam(name="activeOnly") BooleanType activeType,
			@OperationParam(name="offset") String offsetStr,
			@OperationParam(name="count") String countStr) throws FHIROperationException {

		//The code system is the URL up to where the parameters start eg http://snomed.info/sct?fhir_vs=ecl/ or http://snomed.info/sct/45991000052106?fhir_vs=ecl/
		int cutPoint = url.indexOf("?");
		if (cutPoint == -1) {
			throw new FHIROperationException(IssueType.VALUE, "url is expected to contain one or more parameters eg http://snomed.info/sct?fhir_vs=ecl/ or http://snomed.info/sct/45991000052106?fhir_vs=ecl/ ");
		}
		StringType codeSystemStr = new StringType(url.substring(0, cutPoint));
		CodeSystemVersion codeSystemVersion = fhirHelper.getCodeSystemVersion(codeSystemStr);
		String branchPath = codeSystemVersion.getBranchPath();
		QueryService.ConceptQueryBuilder queryBuilder = queryService.createQueryBuilder(false);  //Inferred view only for now
		
		cutPoint = url.indexOf("fhir_vs=ecl/");
		if (cutPoint == -1) {
			throw new FHIROperationException(IssueType.VALUE, "url is expected to include parameter with value: 'fhir_vs=ecl/'");
		}
		String ecl = url.substring(cutPoint + 12);
		Boolean active = activeType == null ? null : activeType.booleanValue();
		queryBuilder.ecl(ecl)
					.termMatch(filter)
					.activeFilter(active);

		int offset = (offsetStr == null || offsetStr.isEmpty()) ? 0 : Integer.parseInt(offsetStr);
		int pageSize = (countStr == null || countStr.isEmpty()) ? DEFAULT_PAGESIZE : Integer.parseInt(countStr);
		Page<ConceptMini> conceptMiniPage = queryService.search(queryBuilder, BranchPathUriUtil.decodePath(branchPath), PageRequest.of(offset, pageSize));
		logger.info("Recovered: {} concepts from branch: {} with ecl: '{}'", conceptMiniPage.getContent().size(), branchPath, ecl);
		ValueSet valueSet = mapper.mapToFHIR(conceptMiniPage.getContent(), url); 
		valueSet.getExpansion().setTotal((int)conceptMiniPage.getTotalElements());
		valueSet.getExpansion().setOffset(offset);
		return valueSet;
	}
	
	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return ValueSet.class;
	}
}
