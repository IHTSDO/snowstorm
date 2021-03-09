package org.snomed.snowstorm.rest.pojo;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.util.*;

@JsonPropertyOrder({"conceptIds", "descriptionIds"})
public class ConceptBulkLoadRequest {

	private List<String> conceptIds;
	private Set<String> descriptionIds;

	public ConceptBulkLoadRequest() {
		conceptIds = new ArrayList<>();
		descriptionIds = new HashSet<>();
	}

	public ConceptBulkLoadRequest(List<String> conceptIds, Set<String> descriptionIds) {
		this.conceptIds = conceptIds;
		this.descriptionIds = descriptionIds;
	}

	public List<String> getConceptIds() {
		return conceptIds;
	}

	@JsonSetter(value = "conceptIds")
	public void setConceptIdsSafely(List<String> conceptIds) {
		conceptIds.removeIf(Objects::isNull);
		this.conceptIds = conceptIds;
	}

	public Set<String> getDescriptionIds() {
		return descriptionIds;
	}

	@JsonSetter(value = "descriptionIds")
	public void setDescriptionIdsSafely(Set<String> descriptionIds) {
		descriptionIds.removeIf(Objects::isNull);
		this.descriptionIds = descriptionIds;
	}
}
