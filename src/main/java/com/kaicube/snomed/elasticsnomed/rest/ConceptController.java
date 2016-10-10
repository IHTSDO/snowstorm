package com.kaicube.snomed.elasticsnomed.rest;

import com.fasterxml.jackson.annotation.JsonView;
import com.kaicube.snomed.elasticsnomed.domain.Concept;
import com.kaicube.snomed.elasticsnomed.domain.ConceptMini;
import com.kaicube.snomed.elasticsnomed.domain.ConceptView;
import com.kaicube.snomed.elasticsnomed.services.ConceptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.validation.Valid;

@RestController
public class ConceptController {

	@Autowired
	private ConceptService conceptService;

	@RequestMapping(value = "/browser/{branch}/concepts", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	@JsonView(value = View.Component.class)
	public Page<? extends ConceptView> findConcepts(
			@PathVariable String branch,
			@RequestParam(defaultValue = "0") int number,
			@RequestParam(defaultValue = "100") int size) {
		return new Page<>(conceptService.findAll(ControllerHelper.parseBranchPath(branch), new PageRequest(number, size)));
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/concepts/{conceptId}", method = RequestMethod.GET, produces = "application/json")
	@JsonView(value = View.Component.class)
	public ConceptView findConcept(@PathVariable String branch, @PathVariable String conceptId) {
		return conceptService.find(conceptId, ControllerHelper.parseBranchPath(branch));
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/concepts/{conceptId}", method = RequestMethod.POST, produces = "application/json")
	@JsonView(value = View.Component.class)
	public ConceptView updateConcept(@PathVariable String branch, @PathVariable String conceptId, @RequestBody @Valid ConceptView concept) {
		Assert.isTrue(concept.getConceptId() != null && conceptId != null && concept.getConceptId().equals(conceptId), "The conceptId in the " +
				"path must match the one in the request body.");
		return conceptService.update((Concept) concept, ControllerHelper.parseBranchPath(branch));
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/concepts/bulk", method = RequestMethod.POST, produces = "application/json")
	@JsonView(value = View.Component.class)
	public Iterable<Concept> updateConcepts(@PathVariable String branch, @RequestBody @Valid List<ConceptView> concepts) {
		List<Concept> conceptList = new ArrayList<>();
		concepts.forEach(conceptView -> conceptList.add((Concept) conceptView));
		return conceptService.update(conceptList, ControllerHelper.parseBranchPath(branch));
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/concepts/{conceptId}/children", method = RequestMethod.GET, produces = "application/json")
	@JsonView(value = View.Component.class)
	public Collection<ConceptMini> findConceptChildren(@PathVariable String branch, @PathVariable String conceptId) {
		return conceptService.findConceptChildrenInferred(conceptId, ControllerHelper.parseBranchPath(branch));
	}

	@RequestMapping(value = "/{branch}/calculate-transitive-closure", method = RequestMethod.POST, produces = "application/json")
	public void calculateTransitiveClosure(@PathVariable String branch) {
		conceptService.createTransitiveClosureForEveryConcept(ControllerHelper.parseBranchPath(branch));
	}

}
