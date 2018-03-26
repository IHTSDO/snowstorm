package org.snomed.snowstorm.fhir.services;

import java.util.List;

import org.hl7.fhir.dstu3.model.CodeSystem;
import org.modelmapper.ModelMapper;
import org.modelmapper.PropertyMap;
import org.modelmapper.TypeToken;
import org.snomed.snowstorm.fhir.config.FHIRConstants;

import java.lang.reflect.Type;
import java.time.Year;


public class FHIRMappingService implements FHIRConstants {

	ModelMapper modelMapper = new ModelMapper();

	public FHIRMappingService init() {
		PropertyMap<org.snomed.snowstorm.core.data.domain.CodeSystem, CodeSystem> codeSystemMap = new PropertyMap<org.snomed.snowstorm.core.data.domain.CodeSystem, CodeSystem>() {
			protected void configure() {
				String copyrightStr = copyright.replace("YEAR", Integer.toString(Year.now().getValue()));
				map().setCopyright(copyrightStr);
				map().setName(source.getShortName());
			}
		};
		modelMapper.addMappings(codeSystemMap);
		return this;
	}

	public List<CodeSystem> mapToFHIR(List<org.snomed.snowstorm.core.data.domain.CodeSystem> codeSystems) {
		Type listType = new TypeToken<List<CodeSystem>>() {}.getType();
		List<CodeSystem> fhirCodeSystems = modelMapper.map(codeSystems, listType);
		return fhirCodeSystems;
	}

}
