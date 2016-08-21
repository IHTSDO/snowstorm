package com.kaicube.snomed.elasticsnomed.rest;

import com.fasterxml.jackson.annotation.JsonView;
import com.kaicube.snomed.elasticsnomed.domain.Concept;
import com.kaicube.snomed.elasticsnomed.domain.ConceptMini;
import com.kaicube.snomed.elasticsnomed.services.ConceptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
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
		String path = ControllerHelper.extractBranchPath(request, "/concepts");
		return new Page<>(conceptService.findAll(path, new PageRequest(number, size)));
	}

	@ResponseBody
	@RequestMapping("/**/concepts/{conceptId}")
	@JsonView(value = View.Component.class)
	public Concept findConcept(@PathVariable String conceptId, HttpServletRequest request) {
		String path = ControllerHelper.extractBranchPath(request, "/concepts");
		return conceptService.find(conceptId, path);
	}

	@ResponseBody
	@RequestMapping(value = "/**/concepts/{conceptId}", method = RequestMethod.POST)
	@JsonView(value = View.Component.class)
	public Concept updateConcept(@PathVariable String conceptId, @RequestBody Concept concept, HttpServletRequest request) {
		String path = ControllerHelper.extractBranchPath(request, "/concepts");

		Assert.isTrue(concept.getConceptId() != null && conceptId != null && concept.getConceptId().equals(conceptId), "The conceptId in the " +
				"path must match the one in the request body.");
		return conceptService.update(concept, path);
	}

	@ResponseBody
	@RequestMapping("/**/concepts/{conceptId}/children")
	@JsonView(value = View.Component.class)
	public Collection<ConceptMini> findConceptChildren(@PathVariable String conceptId, HttpServletRequest request) {
		String path = ControllerHelper.extractBranchPath(request, "/concepts");
		return conceptService.findConceptChildrenInferred(conceptId, path);
	}

}
