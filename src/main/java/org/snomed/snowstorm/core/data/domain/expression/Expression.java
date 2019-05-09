package org.snomed.snowstorm.core.data.domain.expression;

import org.snomed.snowstorm.core.data.domain.ConceptMicro;
import org.snomed.snowstorm.core.data.domain.ConceptMini;

import java.util.*;
import java.util.stream.Collectors;

public class Expression {

	private List<ConceptMicro> focusConcepts;
	private List<ExpressionAttribute> attributes;
	final Map<Integer, ExpressionGroup> groupMap;
	
	public Expression() {
		focusConcepts = new ArrayList<>();
		attributes = new ArrayList<>();
		groupMap = new HashMap<>();
	}

	public void addConcept(ConceptMicro concept) {
		focusConcepts.add(concept);
	}

	public void addAttribute(ExpressionAttribute attribute) {
		attributes.add(attribute);
	}

	public void addGroup(int groupNum, ExpressionGroup group) {
		groupMap.put(groupNum, group);
	}
	
	public List<ConceptMicro> getConcepts() {
		return focusConcepts;
	}
	
	public List<ExpressionAttribute> getAttributes() {
		return attributes;
	}
	
	public Collection<ExpressionGroup> getGroups() {
		return groupMap.values();
	}
	
	public ExpressionGroup getGroup(int groupNum) {
		ExpressionGroup group = groupMap.get(groupNum);
		if (group == null) {
			group = new ExpressionGroup();
			groupMap.put(groupNum, group);
		}
		return group;
	}

	public void addConcepts(Collection<ConceptMini> concepts) {
		for (ConceptMini c : concepts) {
			focusConcepts.add(new ConceptMicro(c));
		}
	}
	
	public String toString() {
		return toString(true);
	}

	public String toString(boolean includeTerms) {
		StringBuffer sb = new StringBuffer();
		
		//First focus concepts
		sb.append(focusConcepts.stream()
				.map(fc -> fc.toString(includeTerms))
				.collect(Collectors.joining(" + ")));
		
		//Is there anything more?  Colon if so
		if (attributes.size() > 0 || groupMap.size() > 0) {
			sb.append(" : ");
		}
		
		//Next ungrouped attributes
		sb.append(attributes.stream()
				.map(a-> a.toString(includeTerms))
				.collect(Collectors.joining(", ")));
		
		if (attributes.size() > 0) {
			sb.append(", ");
		}
		
		//And finally the groups
		sb.append(groupMap.values().stream()
				.map(g-> g.toString(includeTerms))
				.collect(Collectors.joining(", ")));
		
		return sb.toString();
	}
	

	
}
