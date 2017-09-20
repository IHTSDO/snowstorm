package org.ihtsdo.elasticsnomed.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import org.ihtsdo.elasticsnomed.core.data.domain.Concept;
import org.ihtsdo.elasticsnomed.core.data.domain.ConceptMini;
import org.ihtsdo.elasticsnomed.core.data.domain.ConceptView;
import org.ihtsdo.elasticsnomed.core.data.domain.Relationship;
import org.ihtsdo.elasticsnomed.core.data.services.*;
import org.ihtsdo.elasticsnomed.core.data.services.pojo.ResultMapPage;
import org.ihtsdo.elasticsnomed.rest.pojo.ConceptDescriptionsResult;
import org.ihtsdo.elasticsnomed.rest.pojo.ConceptSearchRequest;
import org.ihtsdo.elasticsnomed.rest.pojo.InboundRelationshipsResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping(produces = "application/json")
public class ConceptController {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private RelationshipService relationshipService;

	@Autowired
	private QueryService queryService;

	@Autowired
	private QueryConceptUpdateService queryConceptUpdateService;

	@RequestMapping(value = "/{branch}/concepts", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public ItemsPage<ConceptMiniNestedFsn> findConcepts(
			@PathVariable String branch,
			@RequestParam(defaultValue = "false") boolean stated,
			@RequestParam(required = false) String term,
			@RequestParam(required = false) String ecl,
			@RequestParam(required = false) String escg,
			@RequestParam(required = false, defaultValue = "0") int page,
			@RequestParam(required = false, defaultValue = "50") int size) {

		// TODO: Remove this partial ESCG support
		if (ecl == null && escg != null && !escg.isEmpty()) {
			ecl = escg.replace("UNION", "OR");
		}

		QueryService.ConceptQueryBuilder queryBuilder = queryService.createQueryBuilder(stated);
		queryBuilder.ecl(ecl);
		queryBuilder.termPrefix(term);
		org.springframework.data.domain.Page<ConceptMini> conceptMiniPage = queryService.search(queryBuilder, BranchPathUriUtil.parseBranchPath(branch), new PageRequest(page, size));
		return new ItemsPage<>(ControllerHelper.nestConceptMiniFsn(conceptMiniPage.getContent()), conceptMiniPage.getTotalElements());
	}

	@RequestMapping(value = "/{branch}/concepts/search", method = RequestMethod.POST)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public ItemsPage<ConceptMiniNestedFsn> search(@PathVariable String branch, @RequestBody ConceptSearchRequest searchRequest) {
		return findConcepts(BranchPathUriUtil.parseBranchPath(branch),
				searchRequest.isStated(),
				searchRequest.getTermFilter(),
				searchRequest.getEclFilter(),
				null,
				searchRequest.getPage(),
				searchRequest.getSize());
	}

	@RequestMapping(value = "/browser/{branch}/concepts", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public Page<? extends ConceptView> getBrowserConcepts(
			@PathVariable String branch,
			@RequestParam(defaultValue = "0") int number,
			@RequestParam(defaultValue = "100") int size) {
		return new Page<>(conceptService.findAll(BranchPathUriUtil.parseBranchPath(branch), new PageRequest(number, size)));
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/concepts/{conceptId}", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public ConceptView findConcept(@PathVariable String branch, @PathVariable String conceptId) {
		return ControllerHelper.throwIfNotFound("Concept", conceptService.find(conceptId, BranchPathUriUtil.parseBranchPath(branch)));
	}

	@ResponseBody
	@RequestMapping(value = "/{branch}/concepts/{conceptId}/descriptions", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public ConceptDescriptionsResult findConceptDescriptions(@PathVariable String branch, @PathVariable String conceptId) {
		Concept concept = ControllerHelper.throwIfNotFound("Concept", conceptService.find(conceptId, BranchPathUriUtil.parseBranchPath(branch)));
		return new ConceptDescriptionsResult(concept.getDescriptions());
	}

	@ResponseBody
	@RequestMapping(value = "/{branch}/concepts/{conceptId}/descendants", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public ItemsPage<ConceptMiniNestedFsn> findConceptDescendants(@PathVariable String branch, @PathVariable String conceptId,
																  @RequestParam(required = false, defaultValue = "0") int page,
																  @RequestParam(required = false, defaultValue = "50") int size) {
		ResultMapPage<String, ConceptMini> descendants = conceptService.findConceptDescendants(conceptId, BranchPathUriUtil.parseBranchPath(branch),
				Relationship.CharacteristicType.stated, new PageRequest(page, size));
		return new ItemsPage<>(ControllerHelper.nestConceptMiniFsn(descendants.getResultsMap().values()), descendants.getTotalElements());
	}

	@ResponseBody
	@RequestMapping(value = "/{branch}/concepts/{conceptId}/inbound-relationships", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public InboundRelationshipsResult findConceptInboundRelationships(@PathVariable String branch, @PathVariable String conceptId) {
		List<Relationship> inboundRelationships = relationshipService.findInboundRelationships(conceptId, BranchPathUriUtil.parseBranchPath(branch), null);
		return new InboundRelationshipsResult(inboundRelationships);
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/concepts/{conceptId}", method = RequestMethod.PUT)
	@JsonView(value = View.Component.class)
	public ConceptView updateConcept(@PathVariable String branch, @PathVariable String conceptId, @RequestBody @Valid ConceptView concept) throws ServiceException {
		Assert.isTrue(concept.getConceptId() != null && conceptId != null && concept.getConceptId().equals(conceptId), "The conceptId in the " +
				"path must match the one in the request body.");
		return conceptService.update((Concept) concept, BranchPathUriUtil.parseBranchPath(branch));
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/concepts", method = RequestMethod.POST)
	@JsonView(value = View.Component.class)
	public ConceptView createConcept(@PathVariable String branch, @RequestBody @Valid ConceptView concept) throws ServiceException {
		return conceptService.create((Concept) concept, BranchPathUriUtil.parseBranchPath(branch));
	}

	@RequestMapping(value = "/{branch}/concepts/{conceptId}", method = RequestMethod.DELETE)
	public void deleteConcept(@PathVariable String branch, @PathVariable String conceptId) {
		conceptService.deleteConceptAndComponents(conceptId, BranchPathUriUtil.parseBranchPath(branch), false);
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/concepts/bulk", method = RequestMethod.POST)
	@JsonView(value = View.Component.class)
	public Iterable<Concept> updateConcepts(@PathVariable String branch, @RequestBody @Valid List<ConceptView> concepts) throws ServiceException {
		List<Concept> conceptList = new ArrayList<>();
		concepts.forEach(conceptView -> conceptList.add((Concept) conceptView));
		return conceptService.createUpdate(conceptList, BranchPathUriUtil.parseBranchPath(branch));
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/concepts/{conceptId}/children", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public Collection<ConceptMini> findConceptChildren(@PathVariable String branch,
													   @PathVariable String conceptId,
													   @RequestParam(defaultValue = "inferred") Relationship.CharacteristicType form) {

		return conceptService.findConceptChildren(conceptId, BranchPathUriUtil.parseBranchPath(branch), form);
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/concepts/{conceptId}/parents", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public Collection<ConceptMini> findConceptParents(@PathVariable String branch,
													  @PathVariable String conceptId,
													  @RequestParam(defaultValue = "inferred") Relationship.CharacteristicType form) {

		return conceptService.findConceptParents(conceptId, BranchPathUriUtil.parseBranchPath(branch), form);
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/concepts/{conceptId}/ancestors", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public Collection<ConceptMini> findConceptAncestors(@PathVariable String branch,
													  @PathVariable String conceptId,
													  @RequestParam(defaultValue = "inferred") Relationship.CharacteristicType form) {

		String branchPath = BranchPathUriUtil.parseBranchPath(branch);
		Set<Long> ancestorIds = queryService.retrieveAncestors(conceptId, branchPath, form == Relationship.CharacteristicType.stated);
		return conceptService.findConceptMinis(branchPath, ancestorIds).getResultsMap().values();
	}

	@RequestMapping(value = "/rebuild/{branch}", method = RequestMethod.POST)
	public void rebuildBranchTransitiveClosure(@PathVariable String branch) {
		queryConceptUpdateService.rebuildStatedAndInferredTransitiveClosures(branch);
	}

}
