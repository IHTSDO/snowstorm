package org.snomed.snowstorm.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.ConceptView;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.domain.expression.Expression;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.rest.pojo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.*;

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
	private ExpressionService expressionService;
	
	@Autowired
	private QueryConceptUpdateService queryConceptUpdateService;

	@RequestMapping(value = "/{branch}/concepts", method = RequestMethod.GET, produces = {"application/json", "text/csv"})
	@ResponseBody
	public ItemsPage<ConceptMini> findConcepts(
			@PathVariable String branch,
			@RequestParam(defaultValue = "false") boolean stated,
			@RequestParam(required = false) Boolean activeFilter,
			@RequestParam(required = false) String term,
			@RequestParam(required = false) String ecl,
			@RequestParam(required = false) String escg,
			@RequestParam(required = false) Set<String> conceptIds,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "50") int limit) {

		// TODO: Remove this partial ESCG support
		if (ecl == null && escg != null && !escg.isEmpty()) {
			conceptIds = new HashSet<>();
			String[] ids = escg.split("UNION");
			for (String id : ids) {
				conceptIds.add(id.trim());
			}
		}

		QueryService.ConceptQueryBuilder queryBuilder = queryService.createQueryBuilder(stated)
				.activeFilter(activeFilter)
				.ecl(ecl)
				.termPrefix(term)
				.conceptIds(conceptIds);

		Page<ConceptMini> conceptMiniPage = queryService.search(queryBuilder, BranchPathUriUtil.decodePath(branch), ControllerHelper.getPageRequest(offset, limit));
		conceptMiniPage.getContent().forEach(ConceptMini::nestFsn);
		return new ItemsPage<>(conceptMiniPage);
	}

	@RequestMapping(value = "/{branch}/concepts/search", method = RequestMethod.POST, produces = {"application/json", "text/csv"})
	@ResponseBody
	public ItemsPage<ConceptMini> search(@PathVariable String branch, @RequestBody ConceptSearchRequest searchRequest) {
		ItemsPage<ConceptMini> concepts = findConcepts(BranchPathUriUtil.decodePath(branch),
				searchRequest.isStated(),
				searchRequest.getActiveFilter(),
				searchRequest.getTermFilter(),
				searchRequest.getEclFilter(),
				null,
				searchRequest.getConceptIds(),
				searchRequest.getOffset(),
				searchRequest.getLimit());
		concepts.getItems().forEach(ConceptMini::nestFsn);
		return concepts;
	}

	@RequestMapping(value = "/browser/{branch}/concepts", method = RequestMethod.GET)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public Page<? extends ConceptView> getBrowserConcepts(
			@PathVariable String branch,
			@RequestParam(defaultValue = "0") int number,
			@RequestParam(defaultValue = "100") int size) {
		return conceptService.findAll(BranchPathUriUtil.decodePath(branch), PageRequest.of(number, size));
	}

	@RequestMapping(value = "/browser/{branch}/concepts/bulk-load", method = RequestMethod.POST)
	@ResponseBody
	@JsonView(value = View.Component.class)
	public Collection<Concept> getBrowserConcepts(
			@PathVariable String branch,
			@RequestBody ConceptIdsPojo request) {
		return conceptService.find(BranchPathUriUtil.decodePath(branch), request.getConceptIds());
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/concepts/{conceptId}", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public ConceptView findConcept(@PathVariable String branch, @PathVariable String conceptId) {
		return ControllerHelper.throwIfNotFound("Concept", conceptService.find(conceptId, BranchPathUriUtil.decodePath(branch)));
	}

	@ResponseBody
	@RequestMapping(value = "/{branch}/concepts/{conceptId}/descriptions", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public ConceptDescriptionsResult findConceptDescriptions(@PathVariable String branch, @PathVariable String conceptId) {
		Concept concept = ControllerHelper.throwIfNotFound("Concept", conceptService.find(conceptId, BranchPathUriUtil.decodePath(branch)));
		return new ConceptDescriptionsResult(concept.getDescriptions());
	}

	@ResponseBody
	@RequestMapping(value = "/{branch}/concepts/{conceptId}/descendants", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public ItemsPage<ConceptMini> findConceptDescendants(@PathVariable String branch,
													@PathVariable String conceptId,
													@PathVariable(value = "false", required = false) boolean stated,
													@RequestParam(required = false, defaultValue = "0") int offset,
													@RequestParam(required = false, defaultValue = "50") int limit) {
		return findConcepts(branch, stated, null, null, "<" + conceptId, null, null, offset, limit);
	}

	@ResponseBody
	@RequestMapping(value = "/{branch}/concepts/{conceptId}/inbound-relationships", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public InboundRelationshipsResult findConceptInboundRelationships(@PathVariable String branch, @PathVariable String conceptId) {
		List<Relationship> inboundRelationships = relationshipService.findInboundRelationships(conceptId, BranchPathUriUtil.decodePath(branch), null).getContent();
		return new InboundRelationshipsResult(inboundRelationships);
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/concepts/{conceptId}", method = RequestMethod.PUT)
	@JsonView(value = View.Component.class)
	public ConceptView updateConcept(@PathVariable String branch, @PathVariable String conceptId, @RequestBody @Valid ConceptView concept) throws ServiceException {
		Assert.isTrue(concept.getConceptId() != null && conceptId != null && concept.getConceptId().equals(conceptId), "The conceptId in the " +
				"path must match the one in the request body.");
		return conceptService.update((Concept) concept, BranchPathUriUtil.decodePath(branch));
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/concepts", method = RequestMethod.POST)
	@JsonView(value = View.Component.class)
	public ConceptView createConcept(@PathVariable String branch, @RequestBody @Valid ConceptView concept) throws ServiceException {
		return conceptService.create((Concept) concept, BranchPathUriUtil.decodePath(branch));
	}

	@RequestMapping(value = "/{branch}/concepts/{conceptId}", method = RequestMethod.DELETE)
	public void deleteConcept(@PathVariable String branch, @PathVariable String conceptId) {
		conceptService.deleteConceptAndComponents(conceptId, BranchPathUriUtil.decodePath(branch), false);
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/concepts/bulk", method = RequestMethod.POST)
	@JsonView(value = View.Component.class)
	public Iterable<Concept> updateConcepts(@PathVariable String branch, @RequestBody @Valid List<ConceptView> concepts) throws ServiceException {
		List<Concept> conceptList = new ArrayList<>();
		concepts.forEach(conceptView -> conceptList.add((Concept) conceptView));
		return conceptService.createUpdate(conceptList, BranchPathUriUtil.decodePath(branch));
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/concepts/{conceptId}/children", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public Collection<ConceptMini> findConceptChildren(@PathVariable String branch,
													   @PathVariable String conceptId,
													   @RequestParam(defaultValue = "inferred") Relationship.CharacteristicType form) {

		return conceptService.findConceptChildren(conceptId, BranchPathUriUtil.decodePath(branch), form);
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/concepts/{conceptId}/parents", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public Collection<ConceptMini> findConceptParents(@PathVariable String branch,
													  @PathVariable String conceptId,
													  @RequestParam(defaultValue = "inferred") Relationship.CharacteristicType form) {

		return conceptService.findConceptParents(conceptId, BranchPathUriUtil.decodePath(branch), form);
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/concepts/{conceptId}/ancestors", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public Collection<ConceptMini> findConceptAncestors(@PathVariable String branch,
													  @PathVariable String conceptId,
													  @RequestParam(defaultValue = "inferred") Relationship.CharacteristicType form) {

		String branchPath = BranchPathUriUtil.decodePath(branch);
		Set<Long> ancestorIds = queryService.retrieveAncestors(conceptId, branchPath, form == Relationship.CharacteristicType.stated);
		return conceptService.findConceptMinis(branchPath, ancestorIds).getResultsMap().values();
	}

	@RequestMapping(value = "/rebuild/{branch}", method = RequestMethod.POST)
	public void rebuildBranchTransitiveClosure(@PathVariable String branch) {
		queryConceptUpdateService.rebuildStatedAndInferredSemanticIndex(branch);
	}
	
	@ResponseBody
	@RequestMapping(value = "/{branch}/concepts/{conceptId}/authoring-form", method = RequestMethod.GET)
	public Expression getConceptAuthoringForm(@PathVariable String branch,
													   @PathVariable String conceptId) {

		return expressionService.getConceptAuthoringForm(conceptId, branch);
	}

}
