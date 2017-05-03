package org.ihtsdo.elasticsnomed.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import org.ihtsdo.elasticsnomed.domain.*;
import org.ihtsdo.elasticsnomed.rest.pojo.ConceptDescriptionsResult;
import org.ihtsdo.elasticsnomed.rest.pojo.InboundRelationshipsResult;
import org.ihtsdo.elasticsnomed.services.ConceptService;
import org.ihtsdo.elasticsnomed.services.QueryIndexService;
import org.ihtsdo.elasticsnomed.services.RelationshipService;
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

	@Autowired
	private RelationshipService relationshipService;

	@Autowired
	private QueryIndexService queryIndexService;

	@RequestMapping(value = "/browser/{branch}/concepts", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	@JsonView(value = View.Component.class)
	public Page<? extends ConceptView> findConcepts(
			@PathVariable String branch,
			@RequestParam(defaultValue = "0") int number,
			@RequestParam(defaultValue = "100") int size) {
		return new Page<>(conceptService.findAll(BranchPathUriUtil.parseBranchPath(branch), new PageRequest(number, size)));
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/concepts/{conceptId}", method = RequestMethod.GET, produces = "application/json")
	@JsonView(value = View.Component.class)
	public ConceptView findConcept(@PathVariable String branch, @PathVariable String conceptId) {
		return conceptService.find(conceptId, BranchPathUriUtil.parseBranchPath(branch));
	}

	@ResponseBody
	@RequestMapping(value = "/{branch}/concepts/{conceptId}/descriptions", method = RequestMethod.GET, produces = "application/json")
	@JsonView(value = View.Component.class)
	public ConceptDescriptionsResult findConceptDescriptions(@PathVariable String branch, @PathVariable String conceptId) {
		return new ConceptDescriptionsResult(conceptService.find(conceptId, BranchPathUriUtil.parseBranchPath(branch)).getDescriptions());
	}

	@ResponseBody
	@RequestMapping(value = "/{branch}/concepts/{conceptId}/descendants", method = RequestMethod.GET, produces = "application/json")
	@JsonView(value = View.Component.class)
	public ItemsPage<ConceptMiniNestedFsn> findConceptDescendants(@PathVariable String branch, @PathVariable String conceptId) {
		Collection<ConceptMini> descendants = conceptService.findConceptDescendants(conceptId, BranchPathUriUtil.parseBranchPath(branch), Relationship.CharacteristicType.stated);
		return new ItemsPage<>(ControllerHelper.nestConceptMiniFsn(descendants));
	}

	@ResponseBody
	@RequestMapping(value = "/{branch}/concepts/{conceptId}/inbound-relationships", method = RequestMethod.GET, produces = "application/json")
	@JsonView(value = View.Component.class)
	public InboundRelationshipsResult findConceptInboundRelationships(@PathVariable String branch, @PathVariable String conceptId) {
		List<Relationship> inboundRelationships = relationshipService.findInboundRelationships(conceptId, BranchPathUriUtil.parseBranchPath(branch), null);
		return new InboundRelationshipsResult(inboundRelationships);
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/concepts/{conceptId}", method = RequestMethod.PUT, produces = "application/json")
	@JsonView(value = View.Component.class)
	public ConceptView updateConcept(@PathVariable String branch, @PathVariable String conceptId, @RequestBody @Valid ConceptView concept) {
		Assert.isTrue(concept.getConceptId() != null && conceptId != null && concept.getConceptId().equals(conceptId), "The conceptId in the " +
				"path must match the one in the request body.");
		return conceptService.update((Concept) concept, BranchPathUriUtil.parseBranchPath(branch));
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/concepts/bulk", method = RequestMethod.POST, produces = "application/json")
	@JsonView(value = View.Component.class)
	public Iterable<Concept> updateConcepts(@PathVariable String branch, @RequestBody @Valid List<ConceptView> concepts) {
		List<Concept> conceptList = new ArrayList<>();
		concepts.forEach(conceptView -> conceptList.add((Concept) conceptView));
		return conceptService.createUpdate(conceptList, BranchPathUriUtil.parseBranchPath(branch));
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/concepts/{conceptId}/children", method = RequestMethod.GET, produces = "application/json")
	@JsonView(value = View.Component.class)
	public Collection<ConceptMini> findConceptChildren(@PathVariable String branch,
													   @PathVariable String conceptId,
													   @RequestParam(defaultValue = "inferred") Relationship.CharacteristicType form) {

		return conceptService.findConceptChildren(conceptId, BranchPathUriUtil.parseBranchPath(branch), form);
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/concepts/{conceptId}/parents", method = RequestMethod.GET, produces = "application/json")
	@JsonView(value = View.Component.class)
	public Collection<ConceptMini> findConceptParents(@PathVariable String branch,
													   @PathVariable String conceptId,
													   @RequestParam(defaultValue = "inferred") Relationship.CharacteristicType form) {

		return conceptService.findConceptParents(conceptId, BranchPathUriUtil.parseBranchPath(branch), form);
	}

	@RequestMapping(value = "/rebuild/{branch}", method = RequestMethod.POST)
	public void rebuildBranchTransitiveClosure(@PathVariable String branch) {
		queryIndexService.rebuildStatedAndInferredTransitiveClosures(branch);
	}

}
