package com.kaicube.snomed.elasticsnomed.rest;

import com.fasterxml.jackson.annotation.JsonView;
import com.kaicube.snomed.elasticsnomed.domain.Concept;
import com.kaicube.snomed.elasticsnomed.services.ConceptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConceptController {

	@Autowired
	private ConceptService conceptService;

	@RequestMapping("/concepts")
	@ResponseBody
	@JsonView(value = View.Component.class)
	public Iterable<Concept> findConcepts() {
		return conceptService.findAll("MAIN", new PageRequest(0, 10));
	}

	@RequestMapping("/concepts/{conceptId}")
	@ResponseBody
	@JsonView(value = View.Component.class)
	public Concept findConcept(@PathVariable String conceptId) {
		return conceptService.find(conceptId, "MAIN");
	}

}
