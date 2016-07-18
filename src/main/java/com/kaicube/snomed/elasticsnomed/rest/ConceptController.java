package com.kaicube.snomed.elasticsnomed.rest;

import com.fasterxml.jackson.annotation.JsonView;
import com.kaicube.snomed.elasticsnomed.domain.Concept;
import com.kaicube.snomed.elasticsnomed.services.ConceptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;

@RestController
public class ConceptController {

	@Autowired
	private ConceptService conceptService;

	@RequestMapping("/**/concepts")
	@ResponseBody
	@JsonView(value = View.Component.class)
	public Page<Concept> findConcepts(
			@RequestParam(defaultValue = "0") int number,
			@RequestParam(defaultValue = "100") int size, HttpServletRequest request) {
		String path = extractBranchPath(request, "/concepts");
		return new Page<>(conceptService.findAll(path, new PageRequest(number, size)));
	}

	@ResponseBody
	@RequestMapping("/**/concepts/{conceptId}")
	@JsonView(value = View.Component.class)
	public Concept findConcept(@PathVariable String conceptId, HttpServletRequest request) {
		String path = extractBranchPath(request, "/concepts");
		return conceptService.find(conceptId, path);
	}

	private String extractBranchPath(HttpServletRequest request, String end) {
		String mappingPath = (String) request.getAttribute(
				HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
		String path = mappingPath.substring(0, mappingPath.lastIndexOf(end));
		Assert.isTrue(!path.isEmpty(), "A branch path is required.");
		path = path.substring(1);
		return path;
	}

}
