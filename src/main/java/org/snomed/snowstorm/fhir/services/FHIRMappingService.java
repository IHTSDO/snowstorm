package org.snomed.snowstorm.fhir.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.modelmapper.ModelMapper;
import org.modelmapper.PropertyMap;
import org.modelmapper.TypeToken;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.element.FHIRCoding;
import org.snomed.snowstorm.fhir.domain.element.FHIRParameter;
import org.snomed.snowstorm.fhir.domain.resource.FHIRCodeSystem;
import org.snomed.snowstorm.fhir.domain.resource.FHIRParameters;

import java.lang.reflect.Type;
import java.time.Year;

public class FHIRMappingService implements FHIRConstants {

	ModelMapper modelMapper = new ModelMapper();

	public FHIRMappingService init() {
		PropertyMap<CodeSystem, FHIRCodeSystem> codeSystemMap = new PropertyMap<CodeSystem, FHIRCodeSystem>() {
			protected void configure() {
				String copyrightStr = COPYRIGHT.replace("YEAR", Integer.toString(Year.now().getValue()));
				map().setCopyright(copyrightStr);
				map().setName(source.getShortName());
				map().setPublisher(SNOMED_INTERNATIONAL);
			}
		};
		modelMapper.addMappings(codeSystemMap);
		return this;
	}

	public List<FHIRCodeSystem> mapToFHIR(List<CodeSystem> codeSystems) {
		Type listType = new TypeToken<List<FHIRCodeSystem>>() {}.getType();
		List<FHIRCodeSystem> fhirCodeSystems = modelMapper.map(codeSystems, listType);
		return fhirCodeSystems;
	}
	
	public FHIRParameters mapToFHIR(Concept c) {
		List<FHIRParameter> parameters = new ArrayList<>();
		FHIRParameter preferredTerm = new FHIRParameter(DISPLAY);
		parameters.add(preferredTerm);
		parameters.addAll(getDesignations(c, preferredTerm));
		//TODO 
		parameters.addAll(getProperties(c));
		return new FHIRParameters(parameters);
	}

	private List<FHIRParameter> getDesignations(Concept c, FHIRParameter preferredTerm) {
		List<FHIRParameter> designations = new ArrayList<>();
		for (Description d : c.getDescriptions(true, null, null, null)) {
			designations.add( new FHIRParameter(DESIGNATION)
							.addPart(new FHIRParameter(LANGUAGE, d.getLang(), true))
							.addPart(getUse(d))
							.addPart(new FHIRParameter(VALUE, d.getTerm(), false))
			);
			//Is this the US Preferred term?
			//TODO Obtain the desired lang/dialect from request headers and lookup refsetid to use
			if (d.hasAcceptability(Concepts.PREFERRED, Concepts.US_EN_LANG_REFSET)) {
				preferredTerm.setValue(d.getTerm());
			}
		}
		return designations;
	}

	private List<FHIRParameter> getProperties(Concept c) {
		List<FHIRParameter> properties = new ArrayList<>();
		properties.add(createProperty(EFFECTIVE_TIME, c.getEffectiveTime(), false));
		properties.add(createProperty(MODULE_ID, c.getModuleId(), true));
		Boolean sufficientlyDefined = c.getDefinitionStatusId().equals(Concepts.SUFFICIENTLY_DEFINED);
		properties.add(createProperty(SUFFICIENTLY_DEFINED, sufficientlyDefined, false));
		return properties;
	}

	private FHIRParameter createProperty(String propertyName, Object propertyValue, boolean isCode) {
		FHIRParameter property = new FHIRParameter(PROPERTY);
		property.addPart(new FHIRParameter(CODE, propertyName, true));
		if (isCode) {
			property.addPart(new FHIRParameter(VALUE, propertyValue.toString(), true));
		} else {
			String typeName = getTypeName(propertyValue);
			property.addPart(new FHIRParameter(typeName, propertyValue));
		}
		return property;
	}

	private String getTypeName(Object obj) {
		if (obj instanceof String) {
			return VALUE_STRING;
		} else if (obj instanceof Boolean) {
			return VALUE_BOOLEAN;
		}
		return "UNKNOWN_TYPE";
	}

	private FHIRParameter getUse(Description d) {
		FHIRCoding coding = new FHIRCoding();
		coding.setCode(d.getType());
		coding.setDisplay(FHIRHelper.translateDescType(d.getType()));
		return new FHIRParameter(USE, coding);
	}

}
