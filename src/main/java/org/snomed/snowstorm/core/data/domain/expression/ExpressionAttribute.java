package org.snomed.snowstorm.core.data.domain.expression;

import org.snomed.snowstorm.core.data.domain.ConceptMicro;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.ConcreteValue;
import org.snomed.snowstorm.core.data.domain.AttributeValue;

public class ExpressionAttribute {

	private final ConceptMicro type;
	private final AttributeValue value;
	
	public ExpressionAttribute(ConceptMini type, ConceptMini value) {
		this.type = new ConceptMicro(type);
		this.value = new ConceptMicro(value);
	}
	
	public ExpressionAttribute(ConceptMini type, AttributeValue value) {
		this.type = new ConceptMicro(type);
		this.value = value;
	}

	public ExpressionAttribute(ConceptMicro type, AttributeValue value) {
		this.type = type;
		this.value = value;
	}

	public ConceptMicro getType() {
		return type;
	}

	public AttributeValue getValue() {
		return value;
	}
	
	public String toString (boolean includeTerms) {
		return type.toString(includeTerms) + " = " + value.toString(includeTerms);
	}

}
