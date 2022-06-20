package org.snomed.snowstorm.fhir.services;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.*;
import org.snomed.snowstorm.fhir.repositories.FHIRStructureDefinitionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.QuantityParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;

import static org.snomed.snowstorm.fhir.services.FHIRHelper.exception;

@Component
public class FHIRStructureDefinitionProvider implements IResourceProvider, FHIRConstants {
	
	@Autowired
	private FHIRStructureDefinitionRepository structureDefinitionRepository;
	
	@Autowired
	private FHIRHelper fhirHelper;
	
	@Read
	public StructureDefinition getStructureDefinition(@IdParam IdType id) {
		Optional<StructureDefinitionWrapper> sdOpt = structureDefinitionRepository.findById(id.getIdPart());
		return sdOpt.map(StructureDefinitionWrapper::getStructureDefinition).orElse(null);
	}
	
	@Create
	public MethodOutcome createStructureDefinition(@IdParam IdType id, @ResourceParam StructureDefinition sd) {
		MethodOutcome outcome = new MethodOutcome();
		validateId(id, sd);
		
		StructureDefinitionWrapper savedVs = structureDefinitionRepository.save(new StructureDefinitionWrapper(id, sd));
		int version = 1;
		if (id.hasVersionIdPart()) {
			version += id.getVersionIdPartAsLong().intValue();
		}
		outcome.setId(new IdType("StructureDefinition", savedVs.getId(), Long.toString(version)));
		return outcome;
	}

	@Update
	public MethodOutcome updateStructureDefinition(@IdParam IdType id, @ResourceParam StructureDefinition sd) {
		try {
			return createStructureDefinition(id, sd);
		} catch (SnowstormFHIRServerResponseException e) {
			throw exception("Failed to update/create structureDefinition '" + sd.getId(), IssueType.EXCEPTION, 400, e);
		}
	}
	
	@Delete
	public void deleteStructureDefinition(@IdParam IdType id) {
		structureDefinitionRepository.deleteById(id.getIdPart());
	}
	
	//See https://build.fhir.org/structuredefinition.html#search
	@Search
	public List<StructureDefinition> findStructureDefinitions(
			HttpServletRequest theRequest, 
			HttpServletResponse theResponse,
			@OptionalParam(name="code") String code,
			@OptionalParam(name="context") TokenParam context,
			@OptionalParam(name="context-quantity") QuantityParam contextQuantity,
			@OptionalParam(name="context-type") String contextType,
			@OptionalParam(name="date") StringParam date,
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
			@OptionalParam(name="version") String version) {
		SearchFilter sdFilter = new SearchFilter()
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
		return StreamSupport.stream(structureDefinitionRepository.findAll().spliterator(), false)
				.map(StructureDefinitionWrapper::getStructureDefinition)
				.filter(sd -> sdFilter.apply(sd, fhirHelper))
				.collect(Collectors.toList());
	}

	private void validateId(IdType id, StructureDefinition sd) {
		if (sd == null || id == null) {
			throw exception("Both ID and StructureDefinition object must be supplied", IssueType.EXCEPTION, 400);
		}
		if (sd.getId() == null || !id.asStringValue().equals(sd.getId())) {
			throw exception("ID in request must match that in StructureDefinition object", IssueType.EXCEPTION, 400);
		}
	}
	
	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return StructureDefinition.class;
	}
}
