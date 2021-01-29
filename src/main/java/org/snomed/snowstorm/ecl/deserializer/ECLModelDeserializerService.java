package org.snomed.snowstorm.ecl.deserializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.refinement.*;
import org.snomed.snowstorm.ecl.domain.refinement.*;
import org.springframework.stereotype.Service;

@Service
public class ECLModelDeserializerService {

	private final ObjectMapper mapper;

	public ECLModelDeserializerService() {
		mapper = new ObjectMapper();

		SimpleModule module = new SimpleModule();
		final ECLModelDeserializer deserializer = new ECLModelDeserializer(mapper, null);
		module.addDeserializer(ExpressionConstraint.class, deserializer);
		module.addDeserializer(SubExpressionConstraint.class, new SubExpressionDeserializer(deserializer));

		module.addDeserializer(EclAttribute.class, new GenericJsonDeserializer<>(SEclAttribute.class));
		module.addDeserializer(EclAttributeGroup.class, new GenericJsonDeserializer<>(SEclAttributeGroup.class));
		module.addDeserializer(EclAttributeSet.class, new GenericJsonDeserializer<>(SEclAttributeSet.class));
		module.addDeserializer(EclRefinement.class, new GenericJsonDeserializer<>(SEclRefinement.class));
		module.addDeserializer(SubAttributeSet.class, new GenericJsonDeserializer<>(SSubAttributeSet.class));
		module.addDeserializer(SubRefinement.class, new GenericJsonDeserializer<>(SSubRefinement.class));

		mapper.registerModule(module);
	}

	public String convertECLModelToString(String eclModelJsonString) throws JsonProcessingException {
		final ExpressionConstraint expressionConstraint = mapper.readValue(eclModelJsonString, ExpressionConstraint.class);

		StringBuffer buffer = new StringBuffer();
		ECLModelDeserializer.expressionConstraintToString(expressionConstraint, buffer);
		return buffer.toString();
	}

}
