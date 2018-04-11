package org.snomed.snowstorm.fhir.services;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.instance.model.api.IBaseResource;
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
	
	private FHIRHelper helper = new FHIRHelper();

	@Operation(name="$expand", idempotent=true)
	public ValueSet expand(
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="url") String url) throws FHIROperationException {
		
		String branch = helper.getBranchForVersion(null);
		QueryService.ConceptQueryBuilder queryBuilder = queryService.createQueryBuilder(false);  //Inferred view only for now
		String ecl = url.substring(url.indexOf("fhir_vs=ecl/") + 12);
		queryBuilder.ecl(ecl);
		Page<ConceptMini> conceptMiniPage = queryService.search(queryBuilder, BranchPathUriUtil.parseBranchPath(branch), PageRequest.of(1, 1000));
		return mapper.mapToFHIR(conceptMiniPage.getContent(), url); 
	}
	
	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return ValueSet.class;
	}
}
