package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.QuantityParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.FHIRValueSet;
import org.snomed.snowstorm.fhir.domain.SearchFilter;
import org.snomed.snowstorm.fhir.pojo.ValueSetExpansionParameters;
import org.snomed.snowstorm.fhir.repositories.FHIRValueSetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.snomed.snowstorm.fhir.services.FHIRHelper.exception;

@Component
public class FHIRValueSetProvider implements IResourceProvider, FHIRConstants {

	@Autowired
	private FHIRValueSetRepository valuesetRepository;

	@Autowired
	private FHIRValueSetService valueSetService;

	@Autowired
	private FhirContext fhirContext;

	@Autowired
	private FHIRHelper fhirHelper;

	public static int DEFAULT_PAGESIZE = 1_000;

	@Read()
	public ValueSet getValueSet(@IdParam IdType id) {
		Optional<FHIRValueSet> valueSetOptional = valuesetRepository.findById(id.getIdPart());
		return valueSetOptional.map(FHIRValueSet::getHapi).orElse(null);
	}

	@Create()
	public MethodOutcome createValueSet(@IdParam IdType id, @ResourceParam ValueSet vs) {
		MethodOutcome outcome = new MethodOutcome();
		FHIRValueSet savedVs = valueSetService.createOrUpdateValueset(vs);
		outcome.setId(new IdType("ValueSet", savedVs.getId(), vs.getVersion()));
		return outcome;
	}

	@Update
	public MethodOutcome updateValueSet(@IdParam IdType id, @ResourceParam ValueSet vs) {
		try {
			return createValueSet(id, vs);
		} catch (SnowstormFHIRServerResponseException e) {
			throw exception("Failed to update/create valueset '" + vs.getId() + "'", IssueType.EXCEPTION, 400, e);
		}
	}

	@Delete
	public void deleteValueSet(
			@IdParam IdType id,
			@OptionalParam(name="url") UriType url,
			@OptionalParam(name="version") String version) {

		if (id != null) {
			valuesetRepository.deleteById(id.getIdPart());
		} else {
			FHIRHelper.required("url", url);
			FHIRHelper.required("version", version);
			valueSetService.find(url.getValueAsString(), version).ifPresent(vs -> valuesetRepository.deleteById(vs.getId()));
		}
	}

	//See https://www.hl7.org/fhir/valueset.html#search
	@Search
	public Bundle findValueSets(
			@OptionalParam(name="_id") String id,
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
			@OptionalParam(name="url") UriType url,
			@OptionalParam(name="version") StringParam version,
			RequestDetails requestDetails) {

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

		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.SEARCHSET);

		Stream<ValueSet> stream;
		if (url != null) {
			List<FHIRValueSet> allByUrl = valuesetRepository.findAllByUrl(url.getValueAsString());
			stream = allByUrl.stream()
					.map(FHIRValueSet::getHapi)
					.filter(vs -> vsFilter.apply(vs, fhirHelper));
			bundle.setTotal(allByUrl.size());

		} else if (vsFilter.anySearchParams()) {
			Page<FHIRValueSet> all = valueSetService.findAll(PageRequest.of(0, 10_000));
			stream = StreamSupport.stream(all.spliterator(), false)
					.map(FHIRValueSet::getHapi)
					.filter(vs -> vsFilter.apply(vs, fhirHelper));
			bundle.setTotal((int) all.getTotalElements());

		} else {
			Page<FHIRValueSet> all = valueSetService.findAll(PageRequest.of(0, 1_000));
			stream = all.stream()
					.map(FHIRValueSet::getHapi);
			bundle.setTotal((int) all.getTotalElements());
		}
		String fhirServerBase = requestDetails.getFhirServerBase();
		bundle.setEntry(stream
				.map(vs -> {
					vs.setCompose(null);// Remove compose element from ValueSet search/listing
					Bundle.BundleEntryComponent component = new Bundle.BundleEntryComponent();
					component.setFullUrl(vs.getIdElement().withServerBase(fhirServerBase, "ValueSet").getValue());
					component.setResource(vs);
					return component;
				})
				.collect(Collectors.toList()));
		return bundle;
	}

	@Operation(name = "$expand", idempotent = true)
	public ValueSet expandInstance(
			@IdParam IdType id,
			HttpServletRequest request,
			HttpServletResponse response,
			@ResourceParam String rawBody,
			@OperationParam(name="url") UriType url,
			@OperationParam(name="valueSetVersion") String valueSetVersion,
			@OperationParam(name="context") String context,
			@OperationParam(name="contextDirection") String contextDirection,
			@OperationParam(name="filter") String filter,
			@OperationParam(name="date") String date,
			@OperationParam(name="offset") IntegerType offset,
			@OperationParam(name="count") IntegerType count,
			@OperationParam(name="includeDesignations") BooleanType includeDesignationsType,
			@OperationParam(name="designation") List<String> designations,
			@OperationParam(name="includeDefinition") BooleanType includeDefinition,
			@OperationParam(name="activeOnly") BooleanType activeType,
			@OperationParam(name="excludeNested") BooleanType excludeNested,
			@OperationParam(name="excludeNotForUI") BooleanType excludeNotForUI,
			@OperationParam(name="excludePostCoordinated") BooleanType excludePostCoordinated,
			@OperationParam(name="displayLanguage") String displayLanguage,
			@OperationParam(name="exclude-system") StringType excludeSystem,
			@OperationParam(name="system-version") StringType systemVersion,
			@OperationParam(name="check-system-version") StringType checkSystemVersion,
			@OperationParam(name="force-system-version") StringType forceSystemVersion,
			@OperationParam(name="version") StringType version)// Invalid parameter
			{

		ValueSetExpansionParameters params;
		if (request.getMethod().equals(RequestMethod.POST.name())) {
			// HAPI doesn't populate the OperationParam values for POST, we parse the body instead.
			params = FHIRValueSetProviderHelper.getValueSetExpansionParameters(id, fhirContext.newJsonParser().parseResource(Parameters.class, rawBody).getParameter());
		} else {
			params = FHIRValueSetProviderHelper.getValueSetExpansionParameters(id, url, valueSetVersion, context, contextDirection, filter, date, offset, count,
					includeDesignationsType, designations, includeDefinition, activeType, excludeNested, excludeNotForUI, excludePostCoordinated, displayLanguage,
					excludeSystem, systemVersion, checkSystemVersion, forceSystemVersion, version);
		}
		return valueSetService.expand(params, FHIRHelper.getDisplayLanguage(params.getDisplayLanguage(), request.getHeader(ACCEPT_LANGUAGE_HEADER)));
	}

	@Operation(name = "$expand", idempotent = true)
	public ValueSet expandType(
			HttpServletRequest request,
			HttpServletResponse response,
			@ResourceParam String rawBody,
			@OperationParam(name="url") UriType url,
			@OperationParam(name="valueSetVersion") String valueSetVersion,
			@OperationParam(name="context") String context,
			@OperationParam(name="contextDirection") String contextDirection,
			@OperationParam(name="filter") String filter,
			@OperationParam(name="date") String date,
			@OperationParam(name="offset") IntegerType offset,
			@OperationParam(name="count") IntegerType count,
			@OperationParam(name="includeDesignations") BooleanType includeDesignationsType,
			@OperationParam(name="designation") List<String> designations,
			@OperationParam(name="includeDefinition") BooleanType includeDefinition,
			@OperationParam(name="activeOnly") BooleanType activeType,
			@OperationParam(name="excludeNested") BooleanType excludeNested,
			@OperationParam(name="excludeNotForUI") BooleanType excludeNotForUI,
			@OperationParam(name="excludePostCoordinated") BooleanType excludePostCoordinated,
			@OperationParam(name="displayLanguage") String displayLanguage,
			@OperationParam(name="exclude-system") StringType excludeSystem,
			@OperationParam(name="system-version") StringType systemVersion,
			@OperationParam(name="check-system-version") StringType checkSystemVersion,
			@OperationParam(name="force-system-version") StringType forceSystemVersion,
			@OperationParam(name="version") StringType version)// Invalid parameter
			{

		ValueSetExpansionParameters params;
		if (request.getMethod().equals(RequestMethod.POST.name())) {
			// HAPI doesn't populate the OperationParam values for POST, we parse the body instead.
			params = FHIRValueSetProviderHelper.getValueSetExpansionParameters(null, fhirContext.newJsonParser().parseResource(Parameters.class, rawBody).getParameter());
		} else {
			params = FHIRValueSetProviderHelper.getValueSetExpansionParameters(null, url, valueSetVersion, context, contextDirection, filter, date, offset, count,
					includeDesignationsType, designations, includeDefinition, activeType, excludeNested, excludeNotForUI, excludePostCoordinated, displayLanguage,
					excludeSystem, systemVersion, checkSystemVersion, forceSystemVersion, version);
		}

		return valueSetService.expand(params, FHIRHelper.getDisplayLanguage(params.getDisplayLanguage(), request.getHeader(ACCEPT_LANGUAGE_HEADER)));
	}

	@Operation(name="$validate-code", idempotent=true)
	public Parameters validateCodeExplicit(
			@IdParam IdType id,
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="url") UriType url,
			@OperationParam(name="context") UriType context,
			@OperationParam(name="valueSet") ValueSet valueSet,
			@OperationParam(name="valueSetVersion") String valueSetVersion,
			@OperationParam(name="code") String code,
			@OperationParam(name="system") UriType system,
			@OperationParam(name="systemVersion") String systemVersion,
			@OperationParam(name="display") String display,
			@OperationParam(name="coding") Coding coding,
			@OperationParam(name="codeableConcept") CodeableConcept codeableConcept,
			@OperationParam(name="date") DateTimeType date,
			@OperationParam(name="abstract") BooleanType abstractBool,
			@OperationParam(name="displayLanguage") String displayLanguage,
			@OperationParam(name="system-version") String incorrectParamSystemVersion) {

		validateCodeParamHints(incorrectParamSystemVersion);
		return valueSetService.validateCode(id.getIdPart(), url, context, valueSet, valueSetVersion, code, system, systemVersion, display, coding, codeableConcept, date, abstractBool,
				FHIRHelper.getDisplayLanguage(displayLanguage, request.getHeader(ACCEPT_LANGUAGE_HEADER)));
	}

	@Operation(name="$validate-code", idempotent=true)
	public Parameters validateCodeImplicit(
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="url") UriType url,
			@OperationParam(name="context") UriType context,
			@OperationParam(name="valueSet") ValueSet valueSet,
			@OperationParam(name="valueSetVersion") String valueSetVersion,
			@OperationParam(name="code") String code,
			@OperationParam(name="system") UriType system,
			@OperationParam(name="systemVersion") String systemVersion,
			@OperationParam(name="display") String display,
			@OperationParam(name="coding") Coding coding,
			@OperationParam(name="codeableConcept") CodeableConcept codeableConcept,
			@OperationParam(name="date") DateTimeType date,
			@OperationParam(name="abstract") BooleanType abstractBool,
			@OperationParam(name="displayLanguage") String displayLanguage,
			@OperationParam(name="system-version") String incorrectParamSystemVersion) {

		validateCodeParamHints(incorrectParamSystemVersion);
		return valueSetService.validateCode(null, url, context, valueSet, valueSetVersion, code, system, systemVersion, display, coding, codeableConcept, date, abstractBool,
				FHIRHelper.getDisplayLanguage(displayLanguage, request.getHeader(ACCEPT_LANGUAGE_HEADER)));
	}

	private void validateCodeParamHints(String incorrectParamSystemVersion) {
		FHIRHelper.parameterNamingHint("system-version", incorrectParamSystemVersion, "systemVersion");
	}

	private void validateId(IdType id, ValueSet vs) {
		if (vs.getId() == null || !id.asStringValue().equals(vs.getId())) {
			throw exception("ID in request must match that in ValueSet object", IssueType.EXCEPTION, 400);
		}
	}

	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return ValueSet.class;
	}
}
