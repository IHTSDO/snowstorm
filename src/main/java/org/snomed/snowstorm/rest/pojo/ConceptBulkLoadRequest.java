package org.snomed.snowstorm.rest.pojo;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@JsonPropertyOrder({"conceptIds", "descriptionIds"})
public class ConceptBulkLoadRequest {

	private List<String> conceptIds;
	private Set<String> descriptionIds;

	public ConceptBulkLoadRequest() {
		conceptIds = new ArrayList<>();
		descriptionIds = new HashSet<>();
	}

	public List<String> getConceptIds() {
		return conceptIds;
	}

	public Set<String> getDescriptionIds() {
		return descriptionIds;
	}
}
