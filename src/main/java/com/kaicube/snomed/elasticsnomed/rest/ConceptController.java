package com.kaicube.snomed.elasticsnomed.rest;

import com.fasterxml.jackson.annotation.JsonView;
import com.kaicube.snomed.elasticsnomed.domain.Concept;
import com.kaicube.snomed.elasticsnomed.services.ConceptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class ConceptController {

	@Autowired
	private ConceptService conceptService;

	@RequestMapping("/concepts")
	@ResponseBody
	@JsonView(value = View.Component.class)
	public Page<Concept> findConcepts(@RequestParam(defaultValue = "0") int number, @RequestParam(defaultValue = "100") int size) {
		return new Page<>(conceptService.findAll("MAIN", new PageRequest(number, size)));
	}

	@RequestMapping("/concepts/{conceptId}")
	@ResponseBody
	@JsonView(value = View.Component.class)
	public Concept findConcept(@PathVariable String conceptId) {
		return conceptService.find(conceptId, "MAIN");
	}

}
