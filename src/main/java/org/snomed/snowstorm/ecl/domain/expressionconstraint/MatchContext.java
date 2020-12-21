package org.snomed.snowstorm.ecl.domain.expressionconstraint;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MatchContext {

	private Map<Integer, Map<String, List<Object>>> conceptAttributes;
	private boolean withinGroup;
	private MatchContext parentContext;
	private Set<Integer> matchingGroups;

	MatchContext(Map<Integer, Map<String, List<Object>>> conceptAttributes) {
		this.conceptAttributes = conceptAttributes;
	}

	public MatchContext(MatchContext parentContext, boolean withinGroup) {
		this.parentContext = parentContext;
		this.withinGroup = withinGroup;
	}

	public boolean isWithinGroup() {
		return withinGroup;
	}

	public Map<Integer, Map<String, List<Object>>> getConceptAttributes() {
		return parentContext != null ? parentContext.getConceptAttributes() : conceptAttributes;
	}

	public Set<Integer> getMatchingGroups() {
		return matchingGroups;
	}

	public void setMatchingGroups(Set<Integer> matchingGroups) {
		this.matchingGroups = matchingGroups;
	}

	public Set<Integer> getMatchingGroupsAndClear() {
		Set<Integer> toReturn = this.matchingGroups;
		matchingGroups = new HashSet<>();
		return toReturn;
	}

	public MatchContext clear() {
		matchingGroups = new HashSet<>();
		return this;
	}
}
