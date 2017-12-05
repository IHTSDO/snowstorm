package org.snomed.snowstorm.core.data.domain.expression;

import java.util.ArrayList;
import java.util.List;

public class ExpressionGroup {

	private List<ExpressionAttribute> attributes;

	public ExpressionGroup() {
		attributes = new ArrayList<>();
	}

	public void addAttribute(ExpressionAttribute attribute) {
		attributes.add(attribute);
	}
	
	public List<ExpressionAttribute> getAttributes() {
		return attributes;
	}

}
