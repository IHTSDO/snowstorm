package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.FHIRConceptMap;
import org.snomed.snowstorm.fhir.domain.FHIRMapElement;
import org.snomed.snowstorm.fhir.domain.FHIRMapTarget;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.snomed.snowstorm.fhir.services.FHIRConceptMapService.WHOLE_SYSTEM_VALUE_SET_URI_POSTFIX;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.*;

@Component
public class FHIRConceptMapProvider implements IResourceProvider, FHIRConstants {

	@Value("${snowstorm.rest-api.readonly}")
	private boolean readOnlyMode;

	@Autowired
	private FHIRConceptMapService service;

	@Read
	public ConceptMap getConceptMap(@IdParam IdType id) {
		String idPart = id.getIdPart();
		FHIRConceptMap map = service.findByIdWithGroups(idPart);
		return map != null ? map.getHapi() : null;
	}

	@Create
	public MethodOutcome createUpdateConceptMap(@IdParam IdType id, @ResourceParam ConceptMap conceptMap) {
		FHIRHelper.readOnlyCheck(readOnlyMode);
		MethodOutcome outcome = new MethodOutcome();
		FHIRConceptMap savedMap = service.createOrUpdate(new FHIRConceptMap(conceptMap));
		outcome.setId(new IdType("ConceptMap", savedMap.getId(), savedMap.getVersion()));
		return outcome;
	}

	@Update
	public MethodOutcome updateConceptMap(@IdParam IdType id, @ResourceParam ConceptMap conceptMap) {
		return createUpdateConceptMap(id, conceptMap);
	}

	//See https://www.hl7.org/fhir/conceptmap.html#search
	@Search
	public List<ConceptMap> findConceptMaps(
			HttpServletRequest theRequest,
			HttpServletResponse theResponse,
			@OptionalParam(name="url") String url) {

		List<FHIRConceptMap> page = service.findAll();

		return page.stream()
				.filter(map -> url == null || url.equals(map.getUrl()))
				.map(FHIRConceptMap::getHapi)
				.peek(map -> map.setGroup(null))// Clear groups for display listing
				.collect(Collectors.toList());
	}

	@Operation(name="$translate", idempotent=true)
	public Parameters translate(
			HttpServletRequest request,
			HttpServletResponse response,
			@OperationParam(name="url") UriType urlType,
			@OperationParam(name="conceptMap") ConceptMap conceptMap,
			@OperationParam(name="conceptMapVersion") String conceptMapVersion,
			@OperationParam(name="code") String code,
			@OperationParam(name="system") String system,
			@OperationParam(name="version") String version,
			@OperationParam(name="source") String sourceValueSet,
			@OperationParam(name="coding") Coding coding,
			@OperationParam(name="codeableConcept") CodeableConcept codeableConcept,
			@OperationParam(name="target") String targetValueSet,
			@OperationParam(name="targetsystem") String targetSystem,
			@OperationParam(name="reverse") BooleanType reverse) {

		String url = urlType != null ? urlType.getValueAsString() : null;
		notSupported("conceptMapVersion", conceptMapVersion);
		notSupported("reverse", reverse);
		List<LanguageDialect> languageDialects = ControllerHelper.parseAcceptLanguageHeader(request.getHeader(ACCEPT_LANGUAGE_HEADER));

		// Get coding to translate
		requireExactlyOneOf("code", code, "coding", coding, "codeableConcept", codeableConcept);
		mutuallyRequired("code", code, "system", system);
		if (coding == null) {
			if (code != null) {
				coding = new Coding(system, code, null).setVersion(version);
			} else {
				if (codeableConcept.getCoding().size() > 1) {
					throw exception("Translation of CodeableConcept with multiple codes is not supported.", IssueType.NOTSUPPORTED, 400);
				}
				if (codeableConcept.getCoding().isEmpty()) {
					throw exception("CodeableConcept contains no coding.", IssueType.INVARIANT, 400);
				}
				coding = codeableConcept.getCoding().get(0);
			}
		}

		if (url != null && url.startsWith("http://snomed.info") && url.contains("sct/") && coding.getVersion() == null) {
			coding.setVersion(url.substring(0, url.indexOf("?")));
		}

		// Get map
		Collection<FHIRConceptMap> maps;
		if (conceptMap != null) {
			maps = Collections.singleton(new FHIRConceptMap(conceptMap));
		} else {
			maps = service.findMaps(url, coding, targetSystem, sourceValueSet, targetValueSet);
		}
		if (maps.isEmpty()) {
			throw exception("No suitable map found.", IssueType.NOTFOUND, 404);
		}

		Map<FHIRConceptMap, Collection<FHIRMapElement>> mapElements = new HashMap<>();
		for (FHIRConceptMap map : maps) {
			Collection<FHIRMapElement> foundElements = service.findMapElements(map, coding, targetSystem, languageDialects);
			if (!foundElements.isEmpty()) {
				mapElements.put(map, foundElements);
			}
		}

		Parameters parameters = new Parameters();
		if (!mapElements.isEmpty()) {
			parameters.addParameter("result", true);
			for (Map.Entry<FHIRConceptMap, Collection<FHIRMapElement>> mapAndElements : mapElements.entrySet()) {
				FHIRConceptMap map = mapAndElements.getKey();
				for (FHIRMapElement mapElement : mapAndElements.getValue()) {
					for (FHIRMapTarget mapTarget : mapElement.getTarget()) {
						Parameters.ParametersParameterComponent matchParam = new Parameters.ParametersParameterComponent(new StringType("match"));
						if (mapTarget.getEquivalence() != null) {
							matchParam.addPart(new Parameters.ParametersParameterComponent(new StringType("equivalence"))
									.setValue(new CodeType(mapTarget.getEquivalence())));

						}
						String elementTargetSystem = map.isImplicitSnomedMap() ? map.getTargetUri().replace(WHOLE_SYSTEM_VALUE_SET_URI_POSTFIX, "") : targetSystem;
						matchParam.addPart(new Parameters.ParametersParameterComponent(new StringType("concept"))
								.setValue(new Coding(elementTargetSystem, mapTarget.getCode(), mapTarget.getDisplay())));
						if (mapElement.getMessage() != null) {
							parameters.addParameter("message", mapElement.getMessage());
						}
						matchParam.addPart(new Parameters.ParametersParameterComponent(new StringType("source"))
								.setValue(new StringType(map.getUrl())));
						parameters.addParameter(matchParam);
					}
				}
			}

			return parameters;
		}
		parameters.addParameter("result", false);
		parameters.addParameter("message", format("No mapping found for code '%s', system '%s'.", coding.getCode(), coding.getSystem()));
		return parameters;
	}

	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return ConceptMap.class;
	}

}
