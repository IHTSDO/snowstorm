package org.snomed.snowstorm.ecl.deserializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.filter.*;
import org.snomed.langauges.ecl.domain.refinement.*;
import org.snomed.snowstorm.ecl.domain.filter.*;
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

		module.addDeserializer(ConceptFilterConstraint.class, new GenericJsonDeserializer<>(SConceptFilterConstraint.class));
		module.addDeserializer(FieldFilter.class, new GenericJsonDeserializer<>(SFieldFilter.class));
		module.addDeserializer(EffectiveTimeFilter.class, new GenericJsonDeserializer<>(SEffectiveTimeFilter.class));
		module.addDeserializer(DescriptionFilterConstraint.class, new GenericJsonDeserializer<>(SDescriptionFilterConstraint.class));
		module.addDeserializer(TypedSearchTerm.class, new GenericJsonDeserializer<>(STypedSearchTerm.class));
		module.addDeserializer(DialectFilter.class, new GenericJsonDeserializer<>(SDialectFilter.class));
		module.addDeserializer(ActiveFilter.class, new GenericJsonDeserializer<>(SActiveFilter.class));
		module.addDeserializer(TermFilter.class, new GenericJsonDeserializer<>(STermFilter.class));
		module.addDeserializer(DescriptionTypeFilter.class, new GenericJsonDeserializer<>(SDescriptionTypeFilter.class));
		module.addDeserializer(LanguageFilter.class, new GenericJsonDeserializer<>(SLanguageFilter.class));
		module.addDeserializer(DialectAcceptability.class, new GenericJsonDeserializer<>(SDialectAcceptability.class));
		module.addDeserializer(MemberFilterConstraint.class, new GenericJsonDeserializer<>(SMemberFilterConstraint.class));
		module.addDeserializer(MemberFieldFilter.class, new GenericJsonDeserializer<>(SMemberFieldFilter.class));
		module.addDeserializer(HistorySupplement.class, new GenericJsonDeserializer<>(SHistorySupplement.class));

		mapper.registerModule(module);
	}

	public String convertECLModelToString(String eclModelJsonString) throws JsonProcessingException {
		final ExpressionConstraint expressionConstraint = mapper.readValue(eclModelJsonString, ExpressionConstraint.class);

		StringBuffer buffer = new StringBuffer();
		ECLModelDeserializer.expressionConstraintToString(expressionConstraint, buffer);
		return buffer.toString();
	}

}
