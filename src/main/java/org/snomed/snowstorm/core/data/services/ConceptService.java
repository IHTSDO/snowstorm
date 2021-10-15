package org.snomed.snowstorm.core.data.services;

import ch.qos.logback.classic.Level;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.DomainEntity;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.elasticsearch.common.Strings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.repositories.*;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.data.services.pojo.*;
import org.snomed.snowstorm.core.pojo.BranchTimepoint;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.util.PageHelper;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_DIALECTS;
import static org.springframework.util.CollectionUtils.isEmpty;

@Service
public class ConceptService extends ComponentService {

	private static final Map<ComponentType, Class<? extends DomainEntity<?>>> COMPONENT_DOCUMENT_TYPES = new EnumMap<>(ComponentType.class);

	static {
		COMPONENT_DOCUMENT_TYPES.put(ComponentType.Concept, Concept.class);
		COMPONENT_DOCUMENT_TYPES.put(ComponentType.Description, Description.class);
		COMPONENT_DOCUMENT_TYPES.put(ComponentType.Relationship, Relationship.class);
		COMPONENT_DOCUMENT_TYPES.put(ComponentType.Axiom, ReferenceSetMember.class);
	}

	@Autowired
	private ConceptUpdateHelper conceptUpdateHelper;

	@Autowired
	private ConceptRepository conceptRepository;

	@Autowired
	private DescriptionRepository descriptionRepository;

	@Autowired
	private RelationshipRepository relationshipRepository;

	@Autowired
	private ReferenceSetMemberRepository referenceSetMemberRepository;

	@Autowired
	private QueryConceptRepository queryConceptRepository;

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMetadataHelper branchMetadataHelper;

	@Autowired
	private DescriptionService descriptionService;

	@Autowired
	private AxiomConversionService axiomConversionService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchRestTemplate elasticsearchTemplate;

	@Autowired
	@Lazy
	private CodeSystemService codeSystemService;

	@Autowired
	private ConceptAttributeSortHelper conceptAttributeSortHelper;

	@Autowired
	private RelationshipService relationshipService;

	private final Cache<String, AsyncConceptChangeBatch> batchConceptChanges;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public ConceptService() {
		batchConceptChanges = CacheBuilder.newBuilder().expireAfterWrite(2, TimeUnit.HOURS).build();
	}

	public Concept find(String id, String path) {
		return find(id, DEFAULT_LANGUAGE_DIALECTS, path);
	}

	public Concept find(String id, List<LanguageDialect> languageDialects, String path) {
		return find(id, languageDialects, new BranchTimepoint(path));
	}

	public Concept find(String id, List<LanguageDialect> languageDialects, BranchTimepoint branchTimepoint) {
		final Page<Concept> concepts = doFind(Collections.singleton(id), languageDialects, branchTimepoint, PageRequest.of(0, 10));
		if (concepts.getTotalElements() > 1) {
			logger.error("Found more than one concept {} on branch {}", concepts.getContent(), branchTimepoint);
			concepts.forEach(c -> logger.info("id:{} path:{}, start:{}, end:{}", c.getInternalId(), c.getPath(), c.getStartDebugFormat(), c.getEndDebugFormat()));
			throw new IllegalStateException("More than one concept found for id " + id + " on branch " + branchTimepoint.getBranchPath());
		}
		Concept concept = concepts.getTotalElements() == 0 ? null : concepts.iterator().next();
		logger.debug("Find id:{}, branchTimepoint:{} found:{}", id, branchTimepoint, concept);
		return concept;
	}

	public Collection<Concept> find(String path, Collection<?> ids, List<LanguageDialect> languageDialects) {
		if (isEmpty(ids)) {
			return Collections.emptySet();
		}
		return doFind(ids, languageDialects, new BranchTimepoint(path), PageRequest.of(0, ids.size())).getContent();
	}

	public Collection<Concept> find(BranchCriteria branchCriteria, String path, Collection<?> conceptIds, List<LanguageDialect> languageDialects) {
		if (isEmpty(conceptIds)) {
			return Collections.emptySet();
		}
		return doFind(conceptIds, languageDialects, branchCriteria, PageRequest.of(0, conceptIds.size()), true, true, path).getContent();
	}

	public Page<Concept> find(List<Long> conceptIds, List<LanguageDialect> languageDialects, String path, PageRequest pageRequest) {
		return doFind(conceptIds, languageDialects, new BranchTimepoint(path), pageRequest);
	}

	public ConceptHistory loadConceptHistory(String conceptId, String branch, boolean showFutureVersions, boolean showInternalReleases) {
		CodeSystem codeSystem = codeSystemService.findClosestCodeSystemUsingAnyBranch(branch, false);
		List<CodeSystemVersion> codeSystemVersions = codeSystemService.findAllVersions(codeSystem.getShortName(), showFutureVersions, showInternalReleases);

		// Create branch criteria for each code system version
		Map<String, BranchCriteria> codeSystemVersionBranchCriteria = new HashMap<>();
		for (CodeSystemVersion codeSystemVersion : codeSystemVersions) {
			String branchPath = codeSystemVersion.getBranchPath();
			codeSystemVersionBranchCriteria.put(branchPath, versionControlHelper.getBranchCriteria(branchPath));
		}

		Function<ComponentType, BoolQueryBuilder> defaultFullQuery = componentType -> {
			BoolQueryBuilder fullQuery = boolQuery();
			fullQuery.must(
					boolQuery() //Query for released Components
							.must(existsQuery(SnomedComponent.Fields.EFFECTIVE_TIME))
							.must(existsQuery(SnomedComponent.Fields.PATH))
			);

			if (ComponentType.Axiom.equals(componentType)) {
				fullQuery.must(
						boolQuery() //Query for Axioms
								.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.OWL_AXIOM_REFERENCE_SET))
								.must(boolQuery()
										// One of:
										.should(termQuery(ReferenceSetMember.Fields.CONCEPT_ID, conceptId))
										.should(termQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, conceptId)))
				);
			} else {
				fullQuery.must(
						boolQuery() //Query for Concepts, Descriptions & Relationships
								// One of:
								.should(termQuery(Concept.Fields.CONCEPT_ID, conceptId))
								.should(termQuery(Relationship.Fields.SOURCE_ID, conceptId))
				);
			}
			return fullQuery;
		};

		ConceptHistory conceptHistory = new ConceptHistory(conceptId);
		for (Map.Entry<ComponentType, Class<? extends DomainEntity<?>>> entrySet : COMPONENT_DOCUMENT_TYPES.entrySet()) {
			ComponentType componentType = entrySet.getKey();
			Class<? extends DomainEntity<?>> documentType = entrySet.getValue();
			BoolQueryBuilder componentQuery = defaultFullQuery.apply(componentType);

			BoolQueryBuilder codeSystemQuery = boolQuery();
			for (CodeSystemVersion codeSystemVersion : codeSystemVersions) {
				codeSystemQuery
						.should(
								boolQuery()
										.must(termQuery(SnomedComponent.Fields.EFFECTIVE_TIME, codeSystemVersion.getEffectiveDate()))
										// Branch criteria for this code system version and component type
										.must(codeSystemVersionBranchCriteria.get(codeSystemVersion.getBranchPath()).getEntityBranchCriteria(documentType))
						);
			}
			componentQuery.must(codeSystemQuery);

			SearchHits<? extends DomainEntity<?>> searchHits = elasticsearchTemplate.search(
					new NativeSearchQueryBuilder()
							.withQuery(componentQuery)
							.withPageable(LARGE_PAGE)
							.build(),
					documentType
			);
			for (SearchHit<? extends DomainEntity<?>> searchHit : searchHits.getSearchHits()) {
				if (searchHit.getContent() instanceof SnomedComponent<?>) {
					SnomedComponent<?> snomedComponent = (SnomedComponent<?>) searchHit.getContent();
					conceptHistory.addToHistory(snomedComponent.getReleasedEffectiveTime().toString(), snomedComponent.getPath(), componentType);
				}
			}
		}

		return conceptHistory;
	}

	public boolean exists(String id, String path) {
		final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(path);
		return getNonExistentConceptIds(Collections.singleton(id), branchCriteria).isEmpty();
	}

	public Collection<String> getNonExistentConceptIds(Collection<String> ids, BranchCriteria branchCriteria) {
		final BoolQueryBuilder builder = boolQuery()
				.must(branchCriteria.getEntityBranchCriteria(Concept.class))
				.must(termsQuery(Concept.Fields.CONCEPT_ID, ids));

		Set<String> conceptsNotFound = new HashSet<>(ids);
		try (final SearchHitsIterator<Concept> conceptStream = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
				.withQuery(builder)
				.withPageable(LARGE_PAGE)
				.build(), Concept.class)) {
			conceptStream.forEachRemaining(hit -> conceptsNotFound.remove(hit.getContent().getConceptId()));
		}
		return conceptsNotFound;
	}

	public Page<Concept> findAll(String path, PageRequest pageRequest) {
		return findAll(path, DEFAULT_LANGUAGE_DIALECTS, pageRequest);
	}

	public Page<Concept> findAll(String path, List<LanguageDialect> languageDialects, PageRequest pageRequest) {
		return doFind(null, languageDialects, new BranchTimepoint(path), pageRequest);
	}

	private Page<Concept> doFind(Collection<?> conceptIds, List<LanguageDialect> languageDialects, BranchTimepoint branchTimepoint, PageRequest pageRequest) {
		final BranchCriteria branchCriteria = getBranchCriteria(branchTimepoint);
		return doFind(conceptIds, languageDialects, branchCriteria, pageRequest, true, true, branchTimepoint.getBranchPath());
	}

	protected BranchCriteria getBranchCriteria(BranchTimepoint branchTimepoint) {
		if (branchTimepoint.isBranchCreationTimepoint()) {
			return versionControlHelper.getBranchCriteriaAtBranchCreationTimepoint(branchTimepoint.getBranchPath());
		} else if (branchTimepoint.isBranchBaseTimepoint()) {
			return versionControlHelper.getBranchCriteriaForParentBranchAtBranchBaseTimepoint(branchTimepoint.getBranchPath());
		} else if (branchTimepoint.getTimepoint() != null) {
			return versionControlHelper.getBranchCriteriaAtTimepoint(branchTimepoint.getBranchPath(), branchTimepoint.getTimepoint());
		} else {
			return versionControlHelper.getBranchCriteria(branchTimepoint.getBranchPath());
		}
	}

	public ResultMapPage<String, ConceptMini> findConceptMinis(String path, Collection<?> conceptIds, List<LanguageDialect> languageDialects) {
		if (conceptIds.isEmpty()) {
			return new ResultMapPage<>(new HashMap<>(), 0);
		}
		final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(path);
		return findConceptMinis(branchCriteria, conceptIds, languageDialects);
	}

	public ResultMapPage<String, ConceptMini> findConceptMinis(BranchCriteria branchCriteria, List<LanguageDialect> languageDialects, PageRequest pageRequest) {
		return findConceptMinis(branchCriteria, null, languageDialects, pageRequest);
	}

	public ResultMapPage<String, ConceptMini> findConceptMinis(BranchCriteria branchCriteria, Collection<?> conceptIds, List<LanguageDialect> languageDialects) {
		if (conceptIds.isEmpty()) {
			return new ResultMapPage<>(new HashMap<>(), 0);
		}
		return findConceptMinis(branchCriteria, conceptIds, languageDialects, PageRequest.of(0, conceptIds.size()));
	}

	private ResultMapPage<String, ConceptMini> findConceptMinis(BranchCriteria branchCriteria, Collection<?> conceptIds, List<LanguageDialect> languageDialects, PageRequest pageRequest) {
		if (conceptIds != null && conceptIds.isEmpty()) {
			return new ResultMapPage<>(new HashMap<>(), 0);
		}
		Page<Concept> concepts = doFind(conceptIds, languageDialects, branchCriteria, pageRequest, false, false, null);
		Map<String, Concept> conceptMap = new HashMap<>();
		for (Concept concept : concepts) {
			String id = concept.getId();
			Concept existingValue = conceptMap.put(id, concept);
			if (existingValue != null) {
				String error = String.format("Duplicate concept document found with id %s, A:%s:%s:%s B:%s:%s:%s.", id, concept.getPath(), concept.getStart().getTime(), concept.getStart(),
						existingValue.getPath(), existingValue.getStart().getTime(), existingValue.getStart());
				logger.error(error);
				throw new IllegalStateException(error);
			}
		}
		return new ResultMapPage<>(
				concepts.getContent().stream().map(concept -> new ConceptMini(concept, languageDialects)).collect(Collectors.toMap(ConceptMini::getConceptId, Function.identity())),
				concepts.getTotalElements());
	}

	public void populateConceptMinis(BranchCriteria branchCriteria, Map<String, ConceptMini> minisToPopulate, List<LanguageDialect> languageDialects) {
		if (!minisToPopulate.isEmpty()) {
			Set<String> conceptIds = minisToPopulate.keySet();
			Page<Concept> concepts = doFind(conceptIds, languageDialects, branchCriteria, PageRequest.of(0, conceptIds.size()), false, false, null);
			concepts.getContent().forEach(c -> {
				ConceptMini conceptMini = minisToPopulate.get(c.getConceptId());
				conceptMini.populate(c);
			});
		}
	}

	public Page<Concept> doFind(
			Collection<?> conceptIdsToFind,
			List<LanguageDialect> languageDialects,
			BranchCriteria branchCriteria,
			PageRequest pageRequest,
			boolean includeRelationships,
			boolean includeDescriptionInactivationInfo,
			String branchPath) {

		final TimerUtil timer = new TimerUtil("Find concept", Level.DEBUG);
		timer.checkpoint("get branch criteria");

		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();

		Page<Concept> concepts;
		if (conceptIdsToFind != null && !conceptIdsToFind.isEmpty()) {
			List<Concept> allConcepts = new ArrayList<>();
			long total = 0;
			for (List<?> conceptIdsToFindSegment : Iterables.partition(conceptIdsToFind, CLAUSE_LIMIT)) {
				queryBuilder
						.withQuery(boolQuery()
								.must(branchCriteria.getEntityBranchCriteria(Concept.class))
								.must(termsQuery("conceptId", conceptIdsToFindSegment))
						)
						.withPageable(PageRequest.of(0, conceptIdsToFindSegment.size()));
				SearchHits<Concept> searchHits = elasticsearchTemplate.search(queryBuilder.build(), Concept.class);
				allConcepts.addAll(searchHits.stream().map(SearchHit::getContent).collect(Collectors.toList()));
				total += searchHits.getTotalHits();
			}
			concepts = new PageImpl<>(allConcepts, pageRequest, total);
		} else {
			Query conceptQuery = new NativeSearchQueryBuilder()
					.withQuery(boolQuery().must(branchCriteria.getEntityBranchCriteria(Concept.class)))
					.withPageable(pageRequest)
					.build();
			conceptQuery.setTrackTotalHits(true);
			SearchHits<Concept> searchHits = elasticsearchTemplate.search(conceptQuery, Concept.class);
			concepts = PageHelper.toSearchAfterPage(searchHits, pageRequest);
		}
		timer.checkpoint("find concept");

		Map<String, Concept> conceptIdMap = new HashMap<>();
		for (Concept concept : concepts) {
			concept.setRequestedLanguageDialects(languageDialects);
			conceptIdMap.put(concept.getConceptId(), concept);
			concept.getDescriptions().clear();
			concept.getRelationships().clear();
		}

		Map<String, ConceptMini> conceptMiniMap = new HashMap<>();

		if (includeRelationships) {
			// Fetch Relationships
			joinRelationships(conceptIdMap, conceptMiniMap, languageDialects, branchPath, branchCriteria, timer, false);

			// Fetch Axioms
			for (List<String> conceptIds : Iterables.partition(conceptIdMap.keySet(), CLAUSE_LIMIT)) {
				queryBuilder.withQuery(boolQuery()
						.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.OWL_AXIOM_REFERENCE_SET))
						.must(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, conceptIds))
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class)))
						.withPageable(LARGE_PAGE);

				try (final SearchHitsIterator<ReferenceSetMember> axiomMembers = elasticsearchTemplate.searchForStream(queryBuilder.build(), ReferenceSetMember.class)) {
					axiomMembers.forEachRemaining(axiomMember -> joinAxiom(axiomMember.getContent(), conceptIdMap, conceptMiniMap, languageDialects));
				}
			}
			timer.checkpoint("get axioms " + getFetchCount(conceptIdMap.size()));
		}

		// Fetch ConceptMini definition statuses
		for (List<String> conceptIds : Iterables.partition(conceptMiniMap.keySet(), CLAUSE_LIMIT)) {
			queryBuilder.withQuery(boolQuery()
					.must(termsQuery("conceptId", conceptIds))
					.must(branchCriteria.getEntityBranchCriteria(Concept.class)))
					.withPageable(LARGE_PAGE);
			try (final SearchHitsIterator<Concept> conceptsForMini = elasticsearchTemplate.searchForStream(queryBuilder.build(), Concept.class)) {
				conceptsForMini.forEachRemaining(hit ->
				{
					Concept concept = hit.getContent();
					ConceptMini conceptMini = conceptMiniMap.get(concept.getConceptId());
					conceptMini.setDefinitionStatusId(concept.getDefinitionStatusId());
					conceptMini.setModuleId(concept.getModuleId());
				});
			}
		}
		timer.checkpoint("get relationship def status " + getFetchCount(conceptMiniMap.size()));

		descriptionService.joinDescriptions(branchCriteria, conceptIdMap, conceptMiniMap, timer, includeDescriptionInactivationInfo);

		conceptAttributeSortHelper.sortAttributes(conceptIdMap.values());
		timer.checkpoint("Sort attributes");

		timer.finish();

		return concepts;
	}

	public void joinRelationships(Map<String, Concept> conceptIdMap, Map<String, ConceptMini> typeAndTargetConceptMiniMap, List<LanguageDialect> languageDialects,
			String branchPath, BranchCriteria branchCriteria, TimerUtil timer, boolean activeOnly) {

		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();
		for (List<String> conceptIds : Iterables.partition(conceptIdMap.keySet(), CLAUSE_LIMIT)) {
			final BoolQueryBuilder boolQuery = boolQuery()
					.must(termsQuery("sourceId", conceptIds))
					.must(branchCriteria.getEntityBranchCriteria(Relationship.class));
			if (activeOnly) {
				boolQuery.must(termsQuery("active", true));
			}
			queryBuilder.withQuery(boolQuery).withPageable(LARGE_PAGE);
			try (final SearchHitsIterator<Relationship> relationships = elasticsearchTemplate.searchForStream(queryBuilder.build(), Relationship.class)) {
				relationships.forEachRemaining(hit -> {
					//Set concrete value
					Relationship relationship = hit.getContent();
					if (branchPath != null) {
						relationshipService.setConcreteValueFromMRCM(branchPath, relationship);
					}
					// Join Relationships
					conceptIdMap.get(relationship.getSourceId()).addRelationship(relationship);

					// Add placeholders for relationship type and target details
					relationship.setType(getConceptMini(typeAndTargetConceptMiniMap, relationship.getTypeId(), languageDialects));
					relationship.setTarget(getConceptMini(typeAndTargetConceptMiniMap, relationship.getDestinationId(), languageDialects));
				});
			}
		}
		timer.checkpoint("get relationships " + getFetchCount(conceptIdMap.size()));
	}

	/**
	 * Converts axiom owlExpression to axiom objects containing relationship and adds to the correct concept in the concept map.
	 * Only works for regular or GCI axioms. Transitive/reflexive/property-chain axioms will be ignored.
	 * conceptMiniMap can be null if conceptMinis for the relationships within converted axioms are not required.
	 *
	 * @param axiomMember The member to convert and join.
	 * @param conceptIdMap Map of all concepts being processed.
	 * @param conceptMiniMap Map of conceptMinis to contain placeholders for the types and targets of the axiom relationships.
	 * @param languageDialects The language dialects to be added to new conceptMinis in the conceptMiniMap.
	 */
	private void joinAxiom(ReferenceSetMember axiomMember, Map<String, Concept> conceptIdMap, @Nullable Map<String, ConceptMini> conceptMiniMap, @Nullable List<LanguageDialect> languageDialects) {
		try {
			String referencedComponentId = axiomMember.getReferencedComponentId();
			SAxiomRepresentation axiomRepresentation = axiomConversionService.convertAxiomMemberToAxiomRepresentation(axiomMember);
			if (axiomRepresentation != null) {// Will be null if the axiom is an Ontology Axiom for example a property chain or transitive axiom rather than an Additional Axiom or GCI.
				Concept concept = conceptIdMap.get(referencedComponentId);
				Set<Relationship> relationships;
				if (axiomRepresentation.getLeftHandSideNamedConcept() != null) {
					// Regular Axiom
					relationships = axiomRepresentation.getRightHandSideRelationships();
					concept.addAxiom(new Axiom(axiomMember, axiomRepresentation.isPrimitive() ? Concepts.PRIMITIVE : Concepts.FULLY_DEFINED, relationships));
				} else {
					// GCI Axiom
					relationships = axiomRepresentation.getLeftHandSideRelationships();
					concept.addGeneralConceptInclusionAxiom(new Axiom(axiomMember, axiomRepresentation.isPrimitive() ? Concepts.PRIMITIVE : Concepts.FULLY_DEFINED, relationships));
				}

				if (conceptMiniMap != null) {
					// Add placeholders for relationship type and target details
					relationships.forEach(relationship -> {
						relationship.setType(getConceptMini(conceptMiniMap, relationship.getTypeId(), languageDialects));
						relationship.setTarget(getConceptMini(conceptMiniMap, relationship.getDestinationId(), languageDialects));
					});
				}
			}

		} catch (ConversionException e) {
			logger.error("Failed to deserialise axiom {}", axiomMember.getId(), e);
		}
	}

	private static ConceptMini getConceptMini(Map<String, ConceptMini> conceptMiniMap, String id, List<LanguageDialect> languageDialects) {
		if (id == null) return new ConceptMini((String)null, languageDialects);
		return conceptMiniMap.computeIfAbsent(id, i -> new ConceptMini(id, languageDialects));
	}

	// Used by tests
	public Concept create(Concept conceptVersion, String path) throws ServiceException {
		return create(conceptVersion, DEFAULT_LANGUAGE_DIALECTS, path);
	}

	public Concept create(Concept conceptVersion, List<LanguageDialect> languageDialects, String path) throws ServiceException {
		final Branch branch = branchService.findBranchOrThrow(path);
		if (conceptVersion.getConceptId() != null && exists(conceptVersion.getConceptId(), path)) {
			throw new IllegalArgumentException("Concept '" + conceptVersion.getConceptId() + "' already exists on branch '" + path + "'.");
		}
		return doSave(conceptVersion, languageDialects, branch);
	}

	// Used by tests
	public Iterable<Concept> batchCreate(List<Concept> concepts, String path) throws ServiceException {
		final Branch branch = branchService.findBranchOrThrow(path);
		final Set<String> conceptIds = concepts.stream().map(Concept::getConceptId).filter(Objects::nonNull).collect(Collectors.toSet());
		if (!conceptIds.isEmpty()) {
			final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(path);
			final Collection<String> nonExistentConceptIds = getNonExistentConceptIds(conceptIds, branchCriteria);
			conceptIds.removeAll(nonExistentConceptIds);
			if (!conceptIds.isEmpty()) {
				throw new IllegalArgumentException("Some concepts already exist on branch '" + path + "', " + conceptIds);
			}
		}
		PersistedComponents persistedComponents = doSave(concepts, branch);
		joinComponentsToConceptsWithExpandedDescriptions(persistedComponents, branch, DEFAULT_LANGUAGE_DIALECTS);
		return persistedComponents.getPersistedConcepts();
	}

	public Concept update(Concept conceptVersion, String path) throws ServiceException {
		return update(conceptVersion, DEFAULT_LANGUAGE_DIALECTS, path);
	}

	public Concept update(Concept conceptVersion, List<LanguageDialect> languageDialects, String path) throws ServiceException {
		final Branch branch = branchService.findBranchOrThrow(path);
		final String conceptId = conceptVersion.getConceptId();
		Assert.isTrue(!Strings.isNullOrEmpty(conceptId), "conceptId is required.");
		if (!exists(conceptId, path)) {
			throw new IllegalArgumentException("Concept '" + conceptId + "' does not exist on branch '" + path + "'.");
		}

		return doSave(conceptVersion, languageDialects, branch);
	}

	public PersistedComponents createUpdate(List<Concept> concepts, String path) throws ServiceException {
		final Branch branch = branchService.findBranchOrThrow(path);
		return doSave(concepts, branch);
	}

	public String newCreateUpdateAsyncJob() {
		final AsyncConceptChangeBatch batchConceptChange = new AsyncConceptChangeBatch();
		synchronized (batchConceptChanges) {
			batchConceptChanges.put(batchConceptChange.getId(), batchConceptChange);
		}
		return batchConceptChange.getId();
	}

	@Async
	public void createUpdateAsync(String batchConceptChangeId, String path, List<Concept> concepts, SecurityContext securityContext) {
		SecurityContextHolder.setContext(securityContext);
		AsyncConceptChangeBatch batchConceptChange = batchConceptChanges.getIfPresent(batchConceptChangeId);
		if (batchConceptChange == null) {
			logger.error("Batch concept change {} not found.", batchConceptChangeId);
			return;
		}
		try {
			PersistedComponents persistedComponents = createUpdate(concepts, path);
			batchConceptChange.setConceptIds(StreamSupport.stream(persistedComponents.getPersistedConcepts().spliterator(), false).map(Concept::getConceptIdAsLong).collect(Collectors.toList()));
			batchConceptChange.setStatus(AsyncConceptChangeBatch.Status.COMPLETED);
		} catch (IllegalArgumentException | IllegalStateException | ServiceException e) {
			batchConceptChange.setStatus(AsyncConceptChangeBatch.Status.FAILED);
			batchConceptChange.setMessage(e.getMessage());
			logger.error("Batch concept change failed, id:{}, branch:{}", batchConceptChange.getId(), path, e);
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	public AsyncConceptChangeBatch getBatchConceptChange(String id) {
		return batchConceptChanges.getIfPresent(id);
	}

	private Concept doSave(Concept concept, List<LanguageDialect> languageDialects, Branch branch) throws ServiceException {
		PersistedComponents persistedComponents = doSave(Collections.singleton(concept), branch);

		// Join components to concept
		joinComponentsToConceptsWithExpandedDescriptions(persistedComponents, branch, languageDialects);
		return persistedComponents.getPersistedConcepts().iterator().next();
	}

	private void joinComponentsToConceptsWithExpandedDescriptions(PersistedComponents persistedComponents, Branch branch, List<LanguageDialect> languageDialects) {
		HashMap<String, ConceptMini> conceptMiniMap = new HashMap<>();
		joinComponentsToConcepts(persistedComponents, conceptMiniMap, languageDialects);
		// Populate relationship descriptions
		populateConceptMinis(versionControlHelper.getBranchCriteria(branch), conceptMiniMap, languageDialects);
		conceptAttributeSortHelper.sortAttributes(persistedComponents.getPersistedConcepts());
	}

	private PersistedComponents doSave(Collection<Concept> concepts, Branch branch) throws ServiceException {
		try (final Commit commit = branchService.openCommit(branch.getPath(), branchMetadataHelper.getBranchLockMetadata(String.format("Saving %s concepts.", concepts.size())))) {
			final PersistedComponents persistedComponents = updateWithinCommit(concepts, commit);
			commit.markSuccessful();
			return persistedComponents;
		}
	}

	public PersistedComponents updateWithinCommit(Collection<Concept> concepts, Commit commit) throws ServiceException {
		if (concepts.isEmpty()) {
			return new PersistedComponents();
		}

		Map<String, Concept> existingConceptsMap = getExistingConceptsForSave(concepts, commit);
		
		//Populate source concepts on rebase
		Map<String, Concept> existingRebaseSourceConceptsMap = null;
		if (commit.getCommitType().equals(Commit.CommitType.REBASE)) {
			existingRebaseSourceConceptsMap = getExistingSourceConceptsForSave(concepts, commit);
		}

		return conceptUpdateHelper.saveNewOrUpdatedConcepts(concepts, existingConceptsMap, existingRebaseSourceConceptsMap, commit);
	}

	public void deleteConceptAndComponents(String conceptId, String path, boolean force) {
		try (final Commit commit = branchService.openCommit(path, branchMetadataHelper.getBranchLockMetadata("Deleting concept " + conceptId))) {
			deleteConceptsAndComponentsWithinCommit(Collections.singleton(conceptId), commit, force);
			commit.markSuccessful();
		}
	}

	public List<Concept> deleteConceptsAndComponentsWithinCommit(Collection<String> conceptIds, Commit commit, boolean force) {
		if (conceptIds.isEmpty()) {
			return Collections.emptyList();
		}

		String path = commit.getBranch().getPath();
		List<Concept> concepts = new ArrayList<>();
		for (String conceptId : conceptIds) {
			final Concept concept = find(conceptId, DEFAULT_LANGUAGE_DIALECTS, path);
			if (concept == null) {
				throw new IllegalArgumentException("Concept " + conceptId + " not found.");
			}
			if (concept.isReleased() && !force) {
				throw new IllegalStateException("Released concept will not be deleted.");
			}
			conceptUpdateHelper.doDeleteConcept(path, commit, concept);
			concepts.add(concept);
		}
		return concepts;
	}

	private void joinComponentsToConcepts(PersistedComponents persistedComponents, Map<String, ConceptMini> conceptMiniMap, List<LanguageDialect> languageDialects) {
		Iterable<Concept> persistedConcepts = persistedComponents.getPersistedConcepts();
		Iterable<Description> persistedDescriptions = persistedComponents.getPersistedDescriptions();
		Iterable<Relationship> persistedRelationships = persistedComponents.getPersistedRelationships();
		Iterable<ReferenceSetMember> persistedReferenceSetMembers = persistedComponents.getPersistedReferenceSetMembers();

		// Join not-deleted components onto their concept for traceability log
		Map<String, Concept> conceptMap = new HashMap<>();
		for (Concept concept : persistedConcepts) {
			if (!concept.isDeleted()) {
				conceptMap.put(concept.getConceptId(), concept);
				// Clearing concept component collections; this join method can get called twice.
				concept.getDescriptions().clear();
				concept.getRelationships().clear();
				concept.getClassAxioms().clear();
				concept.getGciAxioms().clear();
			}
		}
		Map<String, Description> descriptionMap = new HashMap<>();
		for (Description description : persistedDescriptions) {
			if (!description.isDeleted()) {
				conceptMap.get(description.getConceptId()).addDescription(description);
				descriptionMap.put(description.getId(), description);
			}
		}
		for (Relationship relationship : persistedRelationships) {
			if (!relationship.isDeleted()) {
				conceptMap.get(relationship.getSourceId()).addRelationship(relationship);
				if (conceptMiniMap != null) {
					relationship.setType(getConceptMini(conceptMiniMap, relationship.getTypeId(), languageDialects));
					relationship.setTarget(getConceptMini(conceptMiniMap, relationship.getDestinationId(), languageDialects));
				}
			}
		}
		for (ReferenceSetMember member : persistedReferenceSetMembers) {
			if (!member.isDeleted()) {
				if (Concepts.OWL_AXIOM_REFERENCE_SET.equals(member.getRefsetId())) {
					joinAxiom(member, conceptMap, conceptMiniMap, languageDialects);
				} else {
					Set<String> strings = member.getAdditionalFields().keySet();
					if (IdentifierService.isDescriptionId(member.getReferencedComponentId())
							&& strings.contains(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID)) {

						Description description = descriptionMap.get(member.getReferencedComponentId());
						if (description != null) {
							description.addLanguageRefsetMember(member);
						}
					}
				}
			}
		}
	}

	<T extends DomainEntity<?>> void doSaveBatchComponents(List<T> componentsToSave, Class<T> componentType, Commit commit) {
		conceptUpdateHelper.doSaveBatchComponents(componentsToSave, componentType, commit);
	}

	private Map<String, Concept> getExistingConceptsForSave(Collection<Concept> concepts, Commit commit) {
		Map<String, Concept> existingConceptsMap = new HashMap<>();
		final List<String> conceptIds = concepts.stream().map(Concept::getConceptId).filter(Objects::nonNull).collect(Collectors.toList());
		if (!conceptIds.isEmpty()) {
			for (List<String> conceptIdPartition : Iterables.partition(conceptIds, 500)) {
				final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit);
				final List<Concept> existingConcepts = doFind(conceptIdPartition, DEFAULT_LANGUAGE_DIALECTS, branchCriteria, PageRequest.of(0, conceptIds.size()), true, true, null).getContent();
				for (Concept existingConcept : existingConcepts) {
					existingConceptsMap.put(existingConcept.getConceptId(), existingConcept);
				}
			}
		}
		return existingConceptsMap;
	}
	
	private Map<String, Concept> getExistingSourceConceptsForSave(Collection<Concept> concepts, Commit commit) {
		Map<String, Concept> existingSourceConceptsMap = new HashMap<>();
		final List<String> conceptIds = concepts.stream().map(Concept::getConceptId).filter(Objects::nonNull).collect(Collectors.toList());
		if (!conceptIds.isEmpty()) {
			BranchTimepoint branchTimePoint = new BranchTimepoint(commit.getSourceBranchPath(), BranchTimepoint.DATE_FORMAT.format(commit.getBranch().getBase()));
			for (List<String> conceptIdPartition : Iterables.partition(conceptIds, 500)) {
				final List<Concept> existingConcepts = doFind(conceptIdPartition, DEFAULT_LANGUAGE_DIALECTS, branchTimePoint, PageRequest.of(0, conceptIds.size())).getContent();
				for (Concept existingConcept : existingConcepts) {
					existingSourceConceptsMap.put(existingConcept.getConceptId(), existingConcept);
				}
			}
		}
		return existingSourceConceptsMap;
	}

	public void deleteAll() throws InterruptedException {
		ExecutorService executorService = Executors.newCachedThreadPool();
		List<Future<?>> futures = Lists.newArrayList(
				executorService.submit(() -> conceptRepository.deleteAll()),
				executorService.submit(() -> descriptionRepository.deleteAll()),
				executorService.submit(() -> relationshipRepository.deleteAll()),
				executorService.submit(() -> referenceSetMemberRepository.deleteAll()),
				executorService.submit(() -> queryConceptRepository.deleteAll())
		);
		for (int i = 0; i < futures.size(); i++) {
			getFutureWithTimeoutOrCancel(futures.get(i), i);
		}
		executorService.shutdown();
	}

	private void getFutureWithTimeoutOrCancel(Future<?> future, int index) throws InterruptedException {
		try {
			future.get(20, TimeUnit.SECONDS);
		} catch (ExecutionException | TimeoutException e) {
			logger.info("Canceling deletion of type {}.", index);
			future.cancel(true);
		}
	}

	public Collection<Long> findAllActiveConcepts(BranchCriteria branchCriteria) {
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Concept.class))
						.must(termQuery(SnomedComponent.Fields.ACTIVE, true)))
				.withPageable(LARGE_PAGE)
				.withFields(Concept.Fields.CONCEPT_ID);
		List<Long> ids = new LongArrayList();
		try (SearchHitsIterator<Concept> conceptStream = elasticsearchTemplate.searchForStream(queryBuilder.build(), Concept.class)) {
			conceptStream.forEachRemaining(c -> ids.add(c.getContent().getConceptIdAsLong()));
		}

		return ids;
	}

	public boolean isActive(String conceptId, BranchCriteria branchCriteria) {
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Concept.class))
						.must(termQuery(SnomedComponent.Fields.ACTIVE, true))
						.must(termQuery(Concept.Fields.CONCEPT_ID, conceptId))
				)
				.withFields(Concept.Fields.CONCEPT_ID);
		final SearchHits<Concept> hits = elasticsearchTemplate.search(queryBuilder.build(), Concept.class);
		return hits.isEmpty();
	}

	public void addClauses(Set<String> conceptIds, Boolean active, BoolQueryBuilder conceptQuery) {
		conceptQuery.must(termsQuery(Concept.Fields.CONCEPT_ID, conceptIds));

		if (active != null) {
			conceptQuery.must(termsQuery(SnomedComponent.Fields.ACTIVE, active));
		}
	}
}
