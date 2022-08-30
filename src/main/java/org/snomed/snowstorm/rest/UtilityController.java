package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.snomed.langauges.ecl.ECLException;
import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.snowstorm.core.data.services.RuntimeServiceException;
import org.snomed.snowstorm.ecl.deserializer.ECLModelDeserializerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@RestController
@Tag(name = "Utility Functions", description = "-")
@RequestMapping(value = "util", produces = "application/json")
public class UtilityController {

	@Autowired
	private ECLQueryBuilder eclQueryBuilder;

	@Autowired
	private ECLModelDeserializerService eclModelDeserializerService;

	@Operation(summary = "Parse ECL and convert to a model representation.",
			description = "This utility function can be used to parse Expression Constraint Language and convert to a model representation, " +
					"to support ECL builder web applications. " +
					"Please note that this function does not validate any concepts or terms within the expression.")
	@ReadOnlyApi
	@PostMapping(value = "ecl-string-to-model")
	@ResponseBody
	public ExpressionConstraint parseECL(@RequestBody String ecl) {
		try {
			ecl = URLDecoder.decode(ecl, StandardCharsets.UTF_8.toString());
			return eclQueryBuilder.createQuery(ecl);
		} catch (ECLException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeServiceException(e);
		}
	}

	@Operation(summary = "Parse ECL model representation and convert it to ECL string.",
			description = "This utility function can be used to convert an Expression Constraint Language JSON model representation to an ECL string, " +
					"to support ECL builder web application. " +
					"Please note that this function does not validate any concepts or terms within the expression.")
	@ReadOnlyApi
	@PostMapping(value = "ecl-model-to-string")
	@ResponseBody
	public EclString parseECLModel(@RequestBody String eclModel) {
		try {
			return new EclString(eclModelDeserializerService.convertECLModelToString(eclModel));
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Failed to parse ECL model.", e);
		}
	}

	public static final class EclString {

		private final String eclString;

		public EclString(String eclString) {
			this.eclString = eclString;
		}

		public String getEclString() {
			return eclString;
		}
	}

}
