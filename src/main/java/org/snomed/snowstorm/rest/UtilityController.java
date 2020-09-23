package org.snomed.snowstorm.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.snomed.langauges.ecl.ECLException;
import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.snowstorm.core.data.services.TooCostlyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

@RestController
@Api(tags = "Utility Functions", description = "-")
@RequestMapping(value = "util", produces = "application/json")
public class UtilityController {

	@Autowired
	private ECLQueryBuilder eclQueryBuilder;

	@ApiOperation(value = "Parse ECL and convert to a model representation.",
			notes = "This utility function can be used to parse Expression Constraint Language and convert to a model representation to support ECL builder web applications.")
	@RequestMapping(value = "parse-ecl", method = RequestMethod.GET)
	public ExpressionConstraint parseECL(@RequestParam String ecl) {
		try {
			return eclQueryBuilder.createQuery(ecl);
		} catch (ECLException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}
	}
	
}
