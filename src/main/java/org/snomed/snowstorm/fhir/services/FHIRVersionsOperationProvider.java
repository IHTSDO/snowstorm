package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.springframework.stereotype.Component;

@Component
public class FHIRVersionsOperationProvider implements FHIRConstants {

	@Operation(name = "$versions", idempotent = true, returnParameters = {
			@OperationParam(name = "version", type = StringType.class, min = 1, max = Integer.MAX_VALUE),
			@OperationParam(name = "default", type = StringType.class, min = 1, max = Integer.MAX_VALUE)
	})
	public Parameters versions(RequestDetails requestDetails) {
		Parameters parameters = new Parameters();

		// List all supported FHIR versions
		parameters.addParameter().setName("version").setValue(new StringType("4.0.1"));
		parameters.addParameter().setName("default").setValue(new StringType("4.0.1"));
		// Add more as needed

		return parameters;
	}
}
