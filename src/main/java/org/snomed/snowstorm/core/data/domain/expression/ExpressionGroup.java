package org.snomed.snowstorm.core.data.domain.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
	
	public String toString(boolean includeTerms) {
		StringBuffer sb = new StringBuffer();
		sb.append("{ ");
		sb.append(attributes.stream()
			.map(a -> a.toString(includeTerms))
			.collect(Collectors.joining(", ")));
		sb.append(" }");
		return sb.toString();
	}

}
