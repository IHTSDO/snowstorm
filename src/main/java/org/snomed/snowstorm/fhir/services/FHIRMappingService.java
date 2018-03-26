package org.snomed.snowstorm.fhir.services;

import java.util.List;

import org.modelmapper.ModelMapper;
import org.modelmapper.PropertyMap;
import org.modelmapper.TypeToken;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystem;

import java.lang.reflect.Type;
import java.time.Year;


public class FHIRMappingService implements FHIRConstants {

	ModelMapper modelMapper = new ModelMapper();

	public FHIRMappingService init() {
		PropertyMap<CodeSystem, FHIRCodeSystem> codeSystemMap = new PropertyMap<CodeSystem, FHIRCodeSystem>() {
			protected void configure() {
				String copyrightStr = copyright.replace("YEAR", Integer.toString(Year.now().getValue()));
				map().setCopyright(copyrightStr);
				map().setName(source.getShortName());
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

}
