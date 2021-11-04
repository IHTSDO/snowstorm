package org.snomed.snowstorm.core.data.domain.expression;

import org.snomed.snowstorm.core.data.domain.*;

public class ExpressionAttribute {

	private final ConceptMicro type;
	private final ConceptMicro target;
	private final ConcreteValue value;
	
	public ExpressionAttribute(ConceptMini type, ConceptMini target) {
		this.type = new ConceptMicro(type);
		this.target = new ConceptMicro(target);
		this.value = null;
	}

	public ExpressionAttribute(ConceptMicro type, ConcreteValue value) {
		this.type = type;
		this.target = null;
		this.value = value;
	}

	public ExpressionAttribute(ConceptMini type, ConcreteValue value) {
		this(new ConceptMicro(type), value);
	}

	public ConceptMicro getType() {
		return type;
	}
	
	public ConceptMicro getTarget() {
		return target;
	}

	public ConcreteValue getValue() {
		return value;
	}
	
	public boolean isConcrete() {
		return value != null;
	}
	
	public String toString (boolean includeTerms) {
		if (isConcrete()) {
			return type.toString(includeTerms) + " = " + value.toString();
		} else {
			return type.toString(includeTerms) + " = " + target.toString(includeTerms);
		}
	}

}
