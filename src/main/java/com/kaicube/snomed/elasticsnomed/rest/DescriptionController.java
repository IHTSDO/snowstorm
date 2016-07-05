package com.kaicube.snomed.elasticsnomed.rest;

import com.fasterxml.jackson.annotation.JsonView;
import com.kaicube.snomed.elasticsnomed.domain.Concept;
import com.kaicube.snomed.elasticsnomed.domain.Description;
import com.kaicube.snomed.elasticsnomed.services.ConceptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class DescriptionController {

	@Autowired
	private ConceptService conceptService;

	@RequestMapping("/descriptions")
	@ResponseBody
	@JsonView(value = View.Component.class)
	public Page<Description> findConcepts(@RequestParam String term, @RequestParam(defaultValue = "0") int number, @RequestParam(defaultValue = "100") int size) {
		return new Page<>(conceptService.findDescriptions("MAIN", term, new PageRequest(number, size)));
	}

}
