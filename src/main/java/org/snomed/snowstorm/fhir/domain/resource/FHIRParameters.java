package org.snomed.snowstorm.fhir.domain.resource;

import java.util.ArrayList;
import java.util.List;

import org.snomed.snowstorm.fhir.domain.element.FHIRParameter;
import org.snomed.snowstorm.rest.View;

import com.fasterxml.jackson.annotation.JsonView;

public class FHIRParameters extends FHIRResource {
	
	static List<FHIRParameter> STANDARD_PARAMETERS;
	static {
		STANDARD_PARAMETERS = new ArrayList<>();
		STANDARD_PARAMETERS.add(new FHIRParameter(NAME, SNOMED_EDITION, false));
	}
	
	@JsonView(value = View.Component.class)
	List<FHIRParameter> parameter;
	
	public FHIRParameters (List<FHIRParameter> parameters) {
		parameter = new ArrayList<>(STANDARD_PARAMETERS);
		parameter.addAll(parameters);
	}
}
