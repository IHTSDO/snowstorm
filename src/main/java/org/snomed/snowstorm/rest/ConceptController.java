package org.snomed.snowstorm.rest;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.annotation.JsonView;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.elasticsearch.common.Strings;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.expression.Expression;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.pojo.AsyncConceptChangeBatch;
import org.snomed.snowstorm.core.data.services.pojo.MapPage;
import org.snomed.snowstorm.core.data.services.pojo.ResultMapPage;
import org.snomed.snowstorm.core.pojo.BranchTimepoint;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.util.PageHelper;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.snomed.snowstorm.ecl.validation.EclValidator;
import org.snomed.snowstorm.rest.converter.SearchAfterHelper;
import org.snomed.snowstorm.rest.pojo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.SearchAfterPageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import javax.validation.Valid;
import java.util.*;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static org.snomed.snowstorm.core.pojo.BranchTimepoint.BRANCH_CREATION_TIMEPOINT;
import static org.snomed.snowstorm.rest.ControllerHelper.parseBranchTimepoint;
import static org.springframework.util.CollectionUtils.isEmpty;

@RestController
@Api(tags = "Concepts", description = "-")
@RequestMapping(produces = "application/json")
public class ConceptController {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private DescriptionService descriptionService;

	@Autowired
	private RelationshipService relationshipService;

	@Autowired
	private QueryService queryService;

	@Autowired
	private SemanticIndexService semanticIndexService;

	@Autowired
	private ExpressionService expressionService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private EclValidator eclValidator;

	@Value("${snowstorm.rest-api.allowUnlimitedConceptPagination:false}")
	private boolean allowUnlimitedConceptPagination;

	@RequestMapping(value = "/{branch}/concepts", method = RequestMethod.GET, produces = {"application/json", "text/csv"})
	public ItemsPage<?> findConcepts(
			@PathVariable String branch,
			@RequestParam(required = false) Boolean activeFilter,
			@RequestParam(required = false) String definitionStatusFilter,
			@RequestParam(required = false) String term,
			@RequestParam(required = false) Boolean termActive,

			@ApiParam(value = "Set of two character language codes to match. " +
					"The English language code 'en' will not be added automatically, in contrast to the Accept-Language header which always includes it. " +
					"Accept-Language header still controls result FSN and PT language selection.")
			@RequestParam(required = false) Set<String> language,

			@ApiParam(value = "Set of description language reference sets. The description must be preferred in at least one of these to match.")
			@RequestParam(required = false) Set<Long> preferredIn,

			@ApiParam(value = "Set of description language reference sets. The description must be acceptable in at least one of these to match.")
			@RequestParam(required = false) Set<Long> acceptableIn,

			@ApiParam(value = "Set of description language reference sets. The description must be preferred OR acceptable in at least one of these to match.")
			@RequestParam(required = false) Set<Long> preferredOrAcceptableIn,

			@RequestParam(required = false) String ecl,
			@RequestParam(required = false) String statedEcl,
			@RequestParam(required = false) Set<String> conceptIds,
			@RequestParam(required = false) boolean returnIdOnly,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "50") int limit,
			@RequestParam(required = false) String searchAfter,
			@ApiParam("Accept-Language header can take the format en-x-900000000000508004 which sets the language reference set to use in the results.")
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		branch = BranchPathUriUtil.decodePath(branch);

		// Parameter validation
		if (ecl != null && statedEcl != null) {
			throw new IllegalArgumentException("Parameters ecl and statedEcl can not be combined.");
		}

		if ((ecl != null || statedEcl != null) && activeFilter != null && !activeFilter) {
			throw new IllegalArgumentException("ECL search can not be used on inactive concepts.");
		}

		boolean stated = true;
		if (ecl != null && !ecl.isEmpty()) {
			eclValidator.validateEcl(ecl, branch);
			stated = false;
		} else {
			ecl = statedEcl;
		}

		List<LanguageDialect> languageDialects = ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader);

		QueryService.ConceptQueryBuilder queryBuilder = queryService.createQueryBuilder(stated)
				.activeFilter(activeFilter)
				.descriptionCriteria(descriptionCriteria -> descriptionCriteria
						.active(termActive)
						.term(term)
						.searchLanguageCodes(language)
				)
				.definitionStatusFilter(definitionStatusFilter)
				.ecl(ecl)
				.resultLanguageDialects(languageDialects)
				.conceptIds(conceptIds);

		queryBuilder.getDescriptionCriteria().preferredOrAcceptableValues(preferredOrAcceptableIn, preferredIn, acceptableIn);

		PageRequest pageRequest = getPageRequestWithSort(offset, limit, searchAfter, Sort.sort(Concept.class).by(Concept::getConceptId).descending());
		if (returnIdOnly) {
			return new ItemsPage<>(queryService.searchForIds(queryBuilder, branch, pageRequest));
		} else {
			return new ItemsPage<>(queryService.search(queryBuilder, branch, pageRequest));
		}
	}

	@RequestMapping(value = "/{branch}/concepts/{conceptId}", method = RequestMethod.GET, produces = {"application/json", "text/csv"})
	public ConceptMini findConcept(
			@PathVariable String branch,
			@PathVariable String conceptId,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		ResultMapPage<String, ConceptMini> conceptMinis = conceptService.findConceptMinis(BranchPathUriUtil.decodePath(branch), Collections.singleton(conceptId),
				ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader));

		ConceptMini concept = conceptMinis.getTotalElements() > 0 ? conceptMinis.getResultsMap().values().iterator().next() : null;
		return ControllerHelper.throwIfNotFound("Concept", concept);
	}

	@RequestMapping(value = "/{branch}/concepts/search", method = RequestMethod.POST, produces = {"application/json", "text/csv"})
	public ItemsPage<?> search(
			@PathVariable String branch,
			@RequestBody ConceptSearchRequest searchRequest,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		return findConcepts(BranchPathUriUtil.decodePath(branch),
				searchRequest.getActiveFilter(),
				searchRequest.getDefinitionStatusFilter(),
				searchRequest.getTermFilter(),
				searchRequest.getTermActive(),
				searchRequest.getLanguage(),
				searchRequest.getPreferredIn(),
				searchRequest.getAcceptableIn(),
				searchRequest.getPreferredOrAcceptableIn(),
				searchRequest.getEclFilter(),
				searchRequest.getStatedEclFilter(),
				searchRequest.getConceptIds(),
				searchRequest.isReturnIdOnly(),
				searchRequest.getOffset(),
				searchRequest.getLimit(),
				searchRequest.getSearchAfter(),
				acceptLanguageHeader);
	}

	@ApiOperation(value = "Load concepts in the browser format.",
			notes = "When enabled 'searchAfter' can be used for unlimited pagination. " +
					"Load the first page then take the 'searchAfter' value from the response and use that " +
					"as a parameter in the next page request instead of 'number'.")
	@RequestMapping(value = "/browser/{branch}/concepts", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public ItemsPage<Concept> getBrowserConcepts(
			@PathVariable String branch,
			@RequestParam(required = false) List<Long> conceptIds,
			@RequestParam(defaultValue = "0") int number,
			@RequestParam(defaultValue = "100") int size,
			@RequestParam(required = false) String searchAfter,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		PageRequest pageRequest = getPageRequestWithSort(number, size, searchAfter, Sort.sort(Concept.class).by(Concept::getConceptId).descending());
		conceptIds = PageHelper.subList(conceptIds, number, size);

		Page<Concept> page = conceptService.find(conceptIds, ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader), BranchPathUriUtil.decodePath(branch), pageRequest);
		return new ItemsPage<>(page);
	}

	@RequestMapping(value = "/browser/{branch}/concepts/bulk-load", method = RequestMethod.POST)
	@JsonView(value = View.Component.class)
	public Collection<Concept> getBrowserConcepts(
			@PathVariable String branch,
			@RequestBody ConceptBulkLoadRequest request,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		String path = BranchPathUriUtil.decodePath(branch);

		List<String> conceptIds = request.getConceptIds();
		Set<String> descriptionIds = request.getDescriptionIds();

		if (!isEmpty(descriptionIds)) {
			Page<Description> descriptions = descriptionService.findDescriptions(path, null, descriptionIds, null, LARGE_PAGE);
			descriptions.forEach(description -> conceptIds.add(description.getConceptId()));
		}

		return conceptService.find(path, conceptIds, ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader));
	}

	@ApiOperation(value = "Load a concept in the browser format.",
			notes = "During content authoring previous versions of the concept can be loaded from version control.\n" +
					"To do this use the branch path format {branch@" + BranchTimepoint.DATE_FORMAT_STRING + "} or {branch@epoch_milliseconds}.\n" +
					"The version of the concept when the branch was created can be loaded using {branch@" + BRANCH_CREATION_TIMEPOINT + "}.")
	@RequestMapping(value = "/browser/{branch}/concepts/{conceptId}", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public ConceptView findBrowserConcept(
			@PathVariable String branch,
			@PathVariable String conceptId,
			@ApiParam("If this parameter is set a descendantCount will be included in the response using stated/inferred as requested.")
			@RequestParam(required = false) Relationship.CharacteristicType descendantCountForm,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		List<LanguageDialect> languageDialects = ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader);
		BranchTimepoint branchTimepoint = parseBranchTimepoint(branch);
		Concept concept = conceptService.find(conceptId, languageDialects, branchTimepoint);
		if (descendantCountForm != null) {
			queryService.joinDescendantCount(concept, descendantCountForm, languageDialects, branchTimepoint);
		}
		return ControllerHelper.throwIfNotFound("Concept", concept);
	}

	@RequestMapping(value = "/{branch}/concepts/{conceptId}/descriptions", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public ConceptDescriptionsResult findConceptDescriptions(
			@PathVariable String branch,
			@PathVariable String conceptId,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		Concept concept = ControllerHelper.throwIfNotFound("Concept", conceptService.find(conceptId, ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader), BranchPathUriUtil.decodePath(branch)));
		return new ConceptDescriptionsResult(concept.getDescriptions());
	}

	@RequestMapping(value = "/{branch}/concepts/{conceptId}/descendants", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public ItemsPage<?> findConceptDescendants(@PathVariable String branch,
			@PathVariable String conceptId,
			@RequestParam(required = false, defaultValue = "false") boolean stated,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "50") int limit,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		return findConceptsWithECL("<" + conceptId, stated, branch, acceptLanguageHeader, offset, limit);
	}

	private ItemsPage<?> findConceptsWithECL(String ecl, boolean stated, String branch, String acceptLanguageHeader, int offset, int limit) {
		return findConcepts(branch, null, null, null, null, null, null, null, null, !stated ? ecl : null, stated ? ecl : null, null, false, offset, limit, null, acceptLanguageHeader);
	}

	@RequestMapping(value = "/{branch}/concepts/{conceptId}/inbound-relationships", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public InboundRelationshipsResult findConceptInboundRelationships(@PathVariable String branch, @PathVariable String conceptId) {
		List<Relationship> inboundRelationships = relationshipService.findInboundRelationships(conceptId, BranchPathUriUtil.decodePath(branch), null).getContent();
		return new InboundRelationshipsResult(inboundRelationships);
	}

	@ApiOperation(value = "Find concepts which reference this concept in the inferred or stated form (including stated axioms).",
			notes = "Pagination works on the referencing concepts. A referencing concept may have one or more references of different types.")
	@RequestMapping(value = "/{branch}/concepts/{conceptId}/references", method = RequestMethod.GET)
	public ConceptReferencesResult findConceptReferences(
			@PathVariable String branch,
			@PathVariable Long conceptId,
			@RequestParam(defaultValue = "false") boolean stated,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "1000") int limit,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		branch = BranchPathUriUtil.decodePath(branch);
		ControllerHelper.validatePageSize(offset, limit);

		MapPage<Long, Set<Long>> conceptReferencesPage = semanticIndexService.findConceptReferences(branch, conceptId, stated, ControllerHelper.getPageRequest(offset, limit));
		Map<Long, Set<Long>> conceptReferences = conceptReferencesPage.getMap();

		// Join concept minis with FSN and PT
		Set<Long> allConceptIds = new LongOpenHashSet();
		for (Long typeId : conceptReferences.keySet()) {
			allConceptIds.add(typeId);
			allConceptIds.addAll(conceptReferences.get(typeId));
		}
		Map<String, ConceptMini> conceptMiniMap = conceptService.findConceptMinis(branch, allConceptIds, ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader)).getResultsMap();
		Set<TypeReferences> typeSets = new TreeSet<>(Comparator.comparing((type) -> type.getReferenceType().getFsnTerm()));
		for (Long typeId : conceptReferences.keySet()) {
			ArrayList<ConceptMini> referencingConcepts = new ArrayList<>();
			typeSets.add(new TypeReferences(conceptMiniMap.get(typeId.toString()), referencingConcepts));
			for (Long referencingConceptId : conceptReferences.get(typeId)) {
				referencingConcepts.add(conceptMiniMap.get(referencingConceptId.toString()));
			}
		}
		return new ConceptReferencesResult(typeSets, conceptReferencesPage.getPageable(), conceptReferencesPage.getTotalElements());
	}

	@RequestMapping(value = "/browser/{branch}/concepts", method = RequestMethod.POST)
	@PreAuthorize("hasPermission('AUTHOR', #branch)")
	@JsonView(value = View.Component.class)
	public ConceptView createConcept(
			@PathVariable String branch,
			@RequestBody @Valid ConceptView concept,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) throws ServiceException {

		return conceptService.create((Concept) concept, ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader), BranchPathUriUtil.decodePath(branch));
	}

	@RequestMapping(value = "/browser/{branch}/concepts/{conceptId}", method = RequestMethod.PUT)
	@PreAuthorize("hasPermission('AUTHOR', #branch)")
	@JsonView(value = View.Component.class)
	public ConceptView updateConcept(
			@PathVariable String branch,
			@PathVariable String conceptId,
			@RequestBody @Valid ConceptView concept,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) throws ServiceException {

		Assert.isTrue(concept.getConceptId() != null && conceptId != null && concept.getConceptId().equals(conceptId), "The conceptId in the " +
				"path must match the one in the request body.");
		return conceptService.update((Concept) concept, ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader), BranchPathUriUtil.decodePath(branch));
	}

	@RequestMapping(value = "/{branch}/concepts/{conceptId}", method = RequestMethod.DELETE)
	@PreAuthorize("hasPermission('AUTHOR', #branch)")
	public void deleteConcept(
			@PathVariable String branch,
			@PathVariable String conceptId,
			@ApiParam("Force the deletion of a released description.")
			@RequestParam(defaultValue = "false") boolean force) {
		conceptService.deleteConceptAndComponents(conceptId, BranchPathUriUtil.decodePath(branch), force);
	}

	@ApiOperation(value = "Start a bulk concept create/update job.", notes = "Concepts can be created or updated using this endpoint.")
	@RequestMapping(value = "/browser/{branch}/concepts/bulk", method = RequestMethod.POST)
	@PreAuthorize("hasPermission('AUTHOR', #branch)")
	public ResponseEntity<ResponseEntity.BodyBuilder> createUpdateConceptBulkChange(@PathVariable String branch, @RequestBody @Valid List<ConceptView> concepts, UriComponentsBuilder uriComponentsBuilder) {
		List<Concept> conceptList = new ArrayList<>();
		concepts.forEach(conceptView -> conceptList.add((Concept) conceptView));
		AsyncConceptChangeBatch batchConceptChange = new AsyncConceptChangeBatch();
		String batchId = conceptService.createUpdateAsyncNewJob(batchConceptChange);
		conceptService.createUpdateAsync(conceptList, BranchPathUriUtil.decodePath(branch), batchId, SecurityContextHolder.getContext());
		return ResponseEntity.created(uriComponentsBuilder.path("/browser/{branch}/concepts/bulk/{bulkChangeId}")
				.buildAndExpand(branch, batchId).toUri()).build();
	}

	@ApiOperation("Fetch the status of a bulk concept creation or update.")
	@RequestMapping(value = "/browser/{branch}/concepts/bulk/{bulkChangeId}", method = RequestMethod.GET)
	@PreAuthorize("hasPermission('AUTHOR', #branch)")
	public AsyncConceptChangeBatch getConceptBulkChange(@PathVariable String branch, @PathVariable String bulkChangeId) {
		return ControllerHelper.throwIfNotFound("Bulk Change", conceptService.getBatchConceptChange(bulkChangeId));
	}

	@RequestMapping(value = "/browser/{branch}/concepts/{conceptId}/children", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public Collection<ConceptMini> findConceptChildren(@PathVariable String branch,
			@PathVariable String conceptId,
			@RequestParam(defaultValue = "inferred") Relationship.CharacteristicType form,
			@RequestParam(required = false, defaultValue = "false") Boolean includeDescendantCount,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		branch = BranchPathUriUtil.decodePath(branch);
		TimerUtil timer = new TimerUtil("Child listing: " + conceptId, Level.INFO, 5);

		List<ConceptMini> children = (List<ConceptMini>) findConceptsWithECL("<!" + conceptId, form == Relationship.CharacteristicType.stated, branch, acceptLanguageHeader,
				0, LARGE_PAGE.getPageSize()).getItems();

		timer.checkpoint("Find children");

		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);
		if (!includeDescendantCount) {
			queryService.joinIsLeafFlag(children, form, branchCriteria);
			timer.checkpoint("Join leaf flag");
		} else {
			queryService.joinDescendantCountAndLeafFlag(children, form, branch, branchCriteria);
			timer.checkpoint("Join descendant count and leaf flag");
		}
		return children;
	}

	@RequestMapping(value = "/browser/{branch}/concepts/{conceptId}/parents", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public Collection<ConceptMini> findConceptParents(@PathVariable String branch,
			@PathVariable String conceptId,
			@RequestParam(defaultValue = "inferred") Relationship.CharacteristicType form,
			@RequestParam(required = false, defaultValue = "false") Boolean includeDescendantCount,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		branch = BranchPathUriUtil.decodePath(branch);

		List<ConceptMini> parents = (List<ConceptMini>) findConceptsWithECL(">!" + conceptId, form == Relationship.CharacteristicType.stated, branch, acceptLanguageHeader,
				0, LARGE_PAGE.getPageSize()).getItems();

		if (includeDescendantCount) {
			BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);
			queryService.joinDescendantCountAndLeafFlag(parents, form, branch, branchCriteria);
		}
		return parents;
	}

	@RequestMapping(value = "/browser/{branch}/concepts/{conceptId}/ancestors", method = RequestMethod.GET)
	@JsonView(value = View.Component.class)
	public Collection<?> findConceptAncestors(@PathVariable String branch,
			@PathVariable String conceptId,
			@RequestParam(defaultValue = "inferred") Relationship.CharacteristicType form,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		branch = BranchPathUriUtil.decodePath(branch);
		return findConceptsWithECL(">" + conceptId, form == Relationship.CharacteristicType.stated, branch, acceptLanguageHeader, 0, LARGE_PAGE.getPageSize()).getItems();
	}

	@RequestMapping(value = "/{branch}/concepts/{conceptId}/authoring-form", method = RequestMethod.GET)
	public Expression getConceptAuthoringForm(
			@PathVariable String branch,
			@PathVariable String conceptId,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		return expressionService.getConceptAuthoringForm(conceptId, ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader), BranchPathUriUtil.decodePath(branch));
	}
	
	@RequestMapping(value = "/{branch}/concepts/{conceptId}/normal-form", method = RequestMethod.GET)
	public ExpressionStringPojo getConceptNormalForm(
			@PathVariable String branch,
			@PathVariable String conceptId,
			@RequestParam(defaultValue="false") boolean statedView,
			@RequestParam(defaultValue="false") boolean includeTerms,
			@RequestHeader(value = "Accept-Language", defaultValue = Config.DEFAULT_ACCEPT_LANG_HEADER) String acceptLanguageHeader) {

		Expression expression =  expressionService.getConceptNormalForm(conceptId, ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader),
				BranchPathUriUtil.decodePath(branch), statedView);

		return new ExpressionStringPojo(expression.toString(includeTerms));
	}


	private PageRequest getPageRequestWithSort(int offset, int size, String searchAfter, Sort sort) {
		ControllerHelper.validatePageSize(offset, size);
		PageRequest pageRequest;
		if (!Strings.isNullOrEmpty(searchAfter)) {
			if (!allowUnlimitedConceptPagination) {
				throw new IllegalArgumentException("Unlimited pagination of the full concept representation is disabled in this deployment.");
			}
			pageRequest = SearchAfterPageRequest.of(SearchAfterHelper.fromSearchAfterToken(searchAfter), size, sort);
		} else {
			pageRequest = PageRequest.of(offset, size, sort);
		}
		return pageRequest;
	}
}
