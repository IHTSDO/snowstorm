package org.ihtsdo.elasticsnomed.core.data.domain.expression;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.elasticsnomed.core.data.domain.ConceptMicro;
import org.ihtsdo.elasticsnomed.core.data.domain.ConceptMini;

public class Expression  {

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
	
}
