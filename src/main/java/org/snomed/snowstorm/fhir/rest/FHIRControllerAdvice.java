package org.snomed.snowstorm.fhir.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.snomed.snowstorm.fhir.domain.element.FHIRIssue;
import org.snomed.snowstorm.fhir.domain.resource.FHIROperationOutcome;
import org.snomed.snowstorm.fhir.services.FHIROperationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@org.springframework.web.bind.annotation.ControllerAdvice
@RequestMapping(value = FHIRConstants.FHIR_DTSU3_ROOT )
public class FHIRControllerAdvice {

	private final Logger logger = LoggerFactory.getLogger(FHIRControllerAdvice.class);

	@ExceptionHandler({FHIROperationException.class})
	@ResponseStatus(HttpStatus.NOT_FOUND)
	@ResponseBody
	public FHIROperationOutcome handleOperationException(FHIROperationException oe) {
		FHIRIssue issue = new FHIRIssue(oe.getIssueType(), oe.getMessage());
		FHIROperationOutcome outcome = new FHIROperationOutcome(issue);
		logger.warn("Operation Exception : " + oe.getMessage());
		return outcome;
	}
}
