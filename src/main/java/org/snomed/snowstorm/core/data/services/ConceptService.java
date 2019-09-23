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
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.elasticsearch.common.Strings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.repositories.*;
import org.snomed.snowstorm.core.data.services.pojo.AsyncConceptChangeBatch;
import org.snomed.snowstorm.core.data.services.pojo.PersistedComponents;
import org.snomed.snowstorm.core.data.services.pojo.ResultMapPage;
import org.snomed.snowstorm.core.data.services.pojo.SAxiomRepresentation;
import org.snomed.snowstorm.core.pojo.BranchTimepoint;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
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
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_CODES;

@Service
public class ConceptService extends ComponentService {

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
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private TraceabilityLogService traceabilityLogService;

	@Autowired
	private ConceptAttributeSortHelper conceptAttributeSortHelper;

	private final Cache<String, AsyncConceptChangeBatch> batchConceptChanges;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public ConceptService() {
		batchConceptChanges = CacheBuilder.newBuilder().expireAfterWrite(2, TimeUnit.HOURS).build();
	}

	public Concept find(String id, String path) {
		return find(id, DEFAULT_LANGUAGE_CODES, path);
	}

	public Concept find(String id, List<String> languageCodes, String path) {
		return find(id, languageCodes, new BranchTimepoint(path));
	}

	public Concept find(String id, List<String> languageCodes, BranchTimepoint branchTimepoint) {
		final Page<Concept> concepts = doFind(Collections.singleton(id), languageCodes, branchTimepoint, PageRequest.of(0, 10));
		if (concepts.getTotalElements() > 1) {
			logger.error("Found more than one concept {} on branch {}", concepts.getContent(), branchTimepoint);
			concepts.forEach(c -> logger.info("id:{} path:{}, start:{}, end:{}", c.getInternalId(), c.getPath(), c.getStartDebugFormat(), c.getEndDebugFormat()));
			throw new IllegalStateException("More than one concept found for id " + id + " on branch " + branchTimepoint.getBranchPath());
		}
		Concept concept = concepts.getTotalElements() == 0 ? null : concepts.iterator().next();
		logger.debug("Find id:{}, branchTimepoint:{} found:{}", id, branchTimepoint, concept);
		return concept;
	}

	public Collection<Concept> find(String path, Collection<?> ids, List<String> languageCodes) {
		return doFind(ids, languageCodes, new BranchTimepoint(path), PageRequest.of(0, ids.size())).getContent();
	}

	public boolean exists(String id, String path) {
		return getNonExistentConceptIds(Collections.singleton(id), path).isEmpty();
	}

	public Collection<String> getNonExistentConceptIds(Collection<String> ids, String path) {
		final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(path);

		final BoolQueryBuilder builder = boolQuery()
				.must(branchCriteria.getEntityBranchCriteria(Concept.class))
				.must(termsQuery("conceptId", ids));

		Set<String> conceptsNotFound = new HashSet<>(ids);
		try (final CloseableIterator<Concept> conceptStream = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(builder)
				.withPageable(LARGE_PAGE)
				.build(), Concept.class)) {
			conceptStream.forEachRemaining(concept -> conceptsNotFound.remove(concept.getConceptId()));
		}
		return conceptsNotFound;
	}

	public Page<Concept> findAll(String path, PageRequest pageRequest) {
		return findAll(path, DEFAULT_LANGUAGE_CODES, pageRequest);
	}

	public Page<Concept> findAll(String path, List<String> languageCodes, PageRequest pageRequest) {
		return doFind(null, languageCodes, new BranchTimepoint(path), pageRequest);
	}

	private Page<Concept> doFind(Collection<?> conceptIds, List<String> languageCodes, Commit commit, PageRequest pageRequest) {
		final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit);
		return doFind(conceptIds, languageCodes, branchCriteria, pageRequest, true, true);
	}

	private Page<Concept> doFind(Collection<?> conceptIds, List<String> languageCodes, BranchTimepoint branchTimepoint, PageRequest pageRequest) {
		final BranchCriteria branchCriteria = getBranchCriteria(branchTimepoint);
		return doFind(conceptIds, languageCodes, branchCriteria, pageRequest, true, true);
	}

	private BranchCriteria getBranchCriteria(BranchTimepoint branchTimepoint) {
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

	public ResultMapPage<String, ConceptMini> findConceptMinis(String path, Collection<?> conceptIds, List<String> languageCodes) {
		if (conceptIds.isEmpty()) {
			return new ResultMapPage<>(new HashMap<>(), 0);
		}
		final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(path);
		return findConceptMinis(branchCriteria, conceptIds, languageCodes);
	}

	public ResultMapPage<String, ConceptMini> findConceptMinis(BranchCriteria branchCriteria, List<String> languageCodes, PageRequest pageRequest) {
		return findConceptMinis(branchCriteria, null, languageCodes, pageRequest);
	}

	public ResultMapPage<String, ConceptMini> findConceptMinis(BranchCriteria branchCriteria, Collection<?> conceptIds, List<String> languageCodes) {
		if (conceptIds.isEmpty()) {
			return new ResultMapPage<>(new HashMap<>(), 0);
		}
		return findConceptMinis(branchCriteria, conceptIds, languageCodes, PageRequest.of(0, conceptIds.size()));
	}

	private ResultMapPage<String, ConceptMini> findConceptMinis(BranchCriteria branchCriteria, Collection<?> conceptIds, List<String> languageCodes, PageRequest pageRequest) {
		if (conceptIds != null && conceptIds.isEmpty()) {
			return new ResultMapPage<>(new HashMap<>(), 0);
		}
		Page<Concept> concepts = doFind(conceptIds, languageCodes, branchCriteria, pageRequest, false, false);
		return new ResultMapPage<>(
				concepts.getContent().stream().map(concept -> new ConceptMini(concept, languageCodes)).collect(Collectors.toMap(ConceptMini::getConceptId, Function.identity())),
				concepts.getTotalElements());
	}

	private void populateConceptMinis(BranchCriteria branchCriteria, Map<String, ConceptMini> minisToPopulate, List<String> languageCodes) {
		if (!minisToPopulate.isEmpty()) {
			Set<String> conceptIds = minisToPopulate.keySet();
			Page<Concept> concepts = doFind(conceptIds, languageCodes, branchCriteria, PageRequest.of(0, conceptIds.size()), false, false);
			concepts.getContent().forEach(c -> {
				ConceptMini conceptMini = minisToPopulate.get(c.getConceptId());
				conceptMini.setDefinitionStatus(c.getDefinitionStatus());
				conceptMini.addActiveDescriptions(c.getDescriptions().stream().filter(SnomedComponent::isActive).collect(Collectors.toSet()));
			});
		}
	}

	private Page<Concept> doFind(
			Collection<?> conceptIdsToFind,
			List<String> languageCodes,
			BranchCriteria branchCriteria,
			PageRequest pageRequest,
			boolean includeRelationships,
			boolean includeDescriptionInactivationInfo) {

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
				Page<Concept> page = elasticsearchTemplate.queryForPage(queryBuilder.build(), Concept.class);
				allConcepts.addAll(page.getContent());
				total = page.getTotalElements();
			}
			concepts = new PageImpl<>(allConcepts, pageRequest, total);
		} else {
			queryBuilder
					.withQuery(boolQuery().must(branchCriteria.getEntityBranchCriteria(Concept.class)))
					.withPageable(pageRequest);
			concepts = elasticsearchTemplate.queryForPage(queryBuilder.build(), Concept.class);
		}
		for (Concept concept : concepts) {
			concept.setRequestedLanguages(languageCodes);
		}
		timer.checkpoint("find concept");

		Map<String, Concept> conceptIdMap = new HashMap<>();
		for (Concept concept : concepts) {
			conceptIdMap.put(concept.getConceptId(), concept);
			concept.getDescriptions().clear();
			concept.getRelationships().clear();
		}

		Map<String, ConceptMini> conceptMiniMap = new HashMap<>();

		if (includeRelationships) {
			// Fetch Relationships
			for (List<String> conceptIds : Iterables.partition(conceptIdMap.keySet(), CLAUSE_LIMIT)) {
				queryBuilder.withQuery(boolQuery()
						.must(termsQuery("sourceId", conceptIds))
						.must(branchCriteria.getEntityBranchCriteria(Relationship.class)))
						.withPageable(LARGE_PAGE);
				try (final CloseableIterator<Relationship> relationships = elasticsearchTemplate.stream(queryBuilder.build(), Relationship.class)) {
					relationships.forEachRemaining(relationship -> {
						// Join Relationships
						conceptIdMap.get(relationship.getSourceId()).addRelationship(relationship);

						// Add placeholders for relationship type and target details
						relationship.setType(getConceptMini(conceptMiniMap, relationship.getTypeId(), languageCodes));
						relationship.setTarget(getConceptMini(conceptMiniMap, relationship.getDestinationId(), languageCodes));
					});
				}
			}
			timer.checkpoint("get relationships " + getFetchCount(conceptIdMap.size()));

			// Fetch Axioms
			for (List<String> conceptIds : Iterables.partition(conceptIdMap.keySet(), CLAUSE_LIMIT)) {
				queryBuilder.withQuery(boolQuery()
						.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.OWL_AXIOM_REFERENCE_SET))
						.must(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, conceptIds))
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class)))
						.withPageable(LARGE_PAGE);

				try (final CloseableIterator<ReferenceSetMember> axiomMembers = elasticsearchTemplate.stream(queryBuilder.build(), ReferenceSetMember.class)) {
					axiomMembers.forEachRemaining(axiomMember -> {
						joinAxiom(axiomMember, conceptIdMap, conceptMiniMap, languageCodes);
					});
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
			try (final CloseableIterator<Concept> conceptsForMini = elasticsearchTemplate.stream(queryBuilder.build(), Concept.class)) {
				conceptsForMini.forEachRemaining(concept ->
						conceptMiniMap.get(concept.getConceptId()).setDefinitionStatusId(concept.getDefinitionStatusId()));
			}
		}
		timer.checkpoint("get relationship def status " + getFetchCount(conceptMiniMap.size()));

		descriptionService.joinDescriptions(branchCriteria, conceptIdMap, conceptMiniMap, timer, includeDescriptionInactivationInfo);

		conceptAttributeSortHelper.sortAttributes(conceptIdMap.values());
		timer.checkpoint("Sort attributes");

		timer.finish();

		return concepts;
	}

	/**
	 * Converts axiom owlExpression to axiom objects containing relationship and adds to the correct concept in the concept map.
	 * Only works for regular or GCI axioms. Transitive/reflexive/property-chain axioms will be ignored.
	 * conceptMiniMap can be null if conceptMinis for the relationships within converted axioms are not required.
	 *
	 * @param axiomMember The member to convert and join.
	 * @param conceptIdMap Map of all concepts being processed.
	 * @param conceptMiniMap Map of conceptMinis to contain placeholders for the types and targets of the axiom relationships.
	 * @param languageCodes The language codes to be added to new conceptMinis in the conceptMiniMap.
	 */
	private void joinAxiom(ReferenceSetMember axiomMember, Map<String, Concept> conceptIdMap, @Nullable Map<String, ConceptMini> conceptMiniMap, @Nullable List<String> languageCodes) {
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
						relationship.setType(getConceptMini(conceptMiniMap, relationship.getTypeId(), languageCodes));
						relationship.setTarget(getConceptMini(conceptMiniMap, relationship.getDestinationId(), languageCodes));
					});
				}
			}

		} catch (ConversionException e) {
			logger.error("Failed to deserialise axiom {}", axiomMember.getId(), e);
		}
	}

	private static ConceptMini getConceptMini(Map<String, ConceptMini> conceptMiniMap, String id, List<String> languageCodes) {
		if (id == null) return new ConceptMini((String)null, languageCodes);
		return conceptMiniMap.computeIfAbsent(id, i -> new ConceptMini(id, languageCodes));
	}

	public Concept create(Concept conceptVersion, String path) throws ServiceException {
		return create(conceptVersion, DEFAULT_LANGUAGE_CODES, path);
	}

	public Concept create(Concept conceptVersion, List<String> languageCodes, String path) throws ServiceException {
		final Branch branch = branchService.findBranchOrThrow(path);
		if (conceptVersion.getConceptId() != null && exists(conceptVersion.getConceptId(), path)) {
			throw new IllegalArgumentException("Concept '" + conceptVersion.getConceptId() + "' already exists on branch '" + path + "'.");
		}
		return doSave(conceptVersion, languageCodes, branch);
	}

	public Iterable<Concept> batchCreate(List<Concept> concepts, String path) throws ServiceException {
		return batchCreate(concepts, DEFAULT_LANGUAGE_CODES, path);
	}

	public Iterable<Concept> batchCreate(List<Concept> concepts, List<String> languageCodes, String path) throws ServiceException {
		final Branch branch = branchService.findBranchOrThrow(path);
		final Set<String> conceptIds = concepts.stream().filter(concept -> concept.getConceptId() != null).map(Concept::getConceptId).collect(Collectors.toSet());
		if (!conceptIds.isEmpty()) {
			final Collection<String> nonExistentConceptIds = getNonExistentConceptIds(conceptIds, path);
			conceptIds.removeAll(nonExistentConceptIds);
			if (!conceptIds.isEmpty()) {
				throw new IllegalArgumentException("Some concepts already exist on branch '" + path + "', " + conceptIds);
			}
		}
		PersistedComponents persistedComponents = doSave(concepts, branch);
		joinComponentsToConceptsWithExpandedDescriptions(persistedComponents, branch, languageCodes);
		return persistedComponents.getPersistedConcepts();
	}

	public Concept update(Concept conceptVersion, String path) throws ServiceException {
		return update(conceptVersion, DEFAULT_LANGUAGE_CODES, path);
	}

	public Concept update(Concept conceptVersion, List<String> languageCodes, String path) throws ServiceException {
		final Branch branch = branchService.findBranchOrThrow(path);
		final String conceptId = conceptVersion.getConceptId();
		Assert.isTrue(!Strings.isNullOrEmpty(conceptId), "conceptId is required.");
		if (!exists(conceptId, path)) {
			throw new IllegalArgumentException("Concept '" + conceptId + "' does not exist on branch '" + path + "'.");
		}

		return doSave(conceptVersion, languageCodes, branch);
	}

	public PersistedComponents createUpdate(List<Concept> concepts, String path) throws ServiceException {
		final Branch branch = branchService.findBranchOrThrow(path);
		return doSave(concepts, branch);
	}

	@Async
	public void createUpdateAsync(List<Concept> concepts, String path, AsyncConceptChangeBatch batchConceptChange, SecurityContext securityContext) {
		SecurityContextHolder.setContext(securityContext);
		synchronized (batchConceptChanges) {
			batchConceptChanges.put(batchConceptChange.getId(), batchConceptChange);
		}
		try {
			PersistedComponents persistedComponents = createUpdate(concepts, path);
			batchConceptChange.setConceptIds(StreamSupport.stream(persistedComponents.getPersistedConcepts().spliterator(), false).map(Concept::getConceptIdAsLong).collect(Collectors.toList()));
			batchConceptChange.setStatus(AsyncConceptChangeBatch.Status.COMPLETED);
		} catch (IllegalArgumentException | ServiceException e) {
			batchConceptChange.setStatus(AsyncConceptChangeBatch.Status.FAILED);
			batchConceptChange.setMessage(e.getMessage());
			logger.error("Batch concept change failed, id:{}, branch:{}", batchConceptChange.getId(), path, e);
		}
	}

	public AsyncConceptChangeBatch getBatchConceptChange(String id) {
		return batchConceptChanges.getIfPresent(id);
	}

	private Concept doSave(Concept concept, List<String> languageCodes, Branch branch) throws ServiceException {
		PersistedComponents persistedComponents = doSave(Collections.singleton(concept), branch);

		// Join components to concept
		joinComponentsToConceptsWithExpandedDescriptions(persistedComponents, branch, languageCodes);
		return persistedComponents.getPersistedConcepts().iterator().next();
	}

	private void joinComponentsToConceptsWithExpandedDescriptions(PersistedComponents persistedComponents, Branch branch, List<String> languageCodes) {
		HashMap<String, ConceptMini> conceptMiniMap = new HashMap<>();
		joinComponentsToConcepts(persistedComponents, conceptMiniMap, languageCodes);
		// Populate relationship descriptions
		populateConceptMinis(versionControlHelper.getBranchCriteria(branch), conceptMiniMap, languageCodes);
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

		PersistedComponents persistedComponents = conceptUpdateHelper.saveNewOrUpdatedConcepts(concepts, commit, getExistingConceptsForSave(concepts, commit));

		// Log traceability activity
		if (traceabilityLogService.isEnabled()) {
			joinComponentsToConcepts(persistedComponents, null, null);
			traceabilityLogService.logActivity(SecurityUtil.getUsername(), commit, persistedComponents);
		}

		return persistedComponents;
	}

	public void deleteConceptAndComponents(String conceptId, String path, boolean force) {
		try (final Commit commit = branchService.openCommit(path, branchMetadataHelper.getBranchLockMetadata("Deleting concept " + conceptId))) {
			deleteConceptsAndComponentsWithinCommit(Collections.singleton(conceptId), commit, force);
		}
	}

	public void deleteConceptsAndComponentsWithinCommit(Collection<String> conceptIds, Commit commit, boolean force) {
		if (conceptIds.isEmpty()) {
			return;
		}

		String path = commit.getBranch().getPath();
		for (String conceptId : conceptIds) {
			final Concept concept = find(conceptId, DEFAULT_LANGUAGE_CODES, path);
			if (concept == null) {
				throw new IllegalArgumentException("Concept " + conceptId + " not found.");
			}
			if (concept.isReleased() && !force) {
				throw new IllegalStateException("Released concept will not be deleted.");
			}
			conceptUpdateHelper.doDeleteConcept(path, commit, concept);
		}
	}

	private void joinComponentsToConcepts(PersistedComponents persistedComponents, Map<String, ConceptMini> conceptMiniMap, List<String> languageCodes) {
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
		for (Description description : persistedDescriptions) {
			if (!description.isDeleted()) {
				conceptMap.get(description.getConceptId()).addDescription(description);
			}
		}
		for (Relationship relationship : persistedRelationships) {
			if (!relationship.isDeleted()) {
				conceptMap.get(relationship.getSourceId()).addRelationship(relationship);
				if (conceptMiniMap != null) {
					relationship.setType(getConceptMini(conceptMiniMap, relationship.getTypeId(), languageCodes));
					relationship.setTarget(getConceptMini(conceptMiniMap, relationship.getDestinationId(), languageCodes));
				}
			}
		}
		StreamSupport.stream(persistedReferenceSetMembers.spliterator(), false)
				.filter(member -> !member.isDeleted())
				.filter(member -> Concepts.OWL_AXIOM_REFERENCE_SET.equals(member.getRefsetId()))
				.forEach(member -> joinAxiom(member, conceptMap, conceptMiniMap, languageCodes));
	}

	<T extends SnomedComponent> void doSaveBatchComponents(List<T> componentsToSave, Class<T> componentType, Commit commit) {
		conceptUpdateHelper.doSaveBatchComponents(componentsToSave, componentType, commit);
	}

	private Map<String, Concept> getExistingConceptsForSave(Collection<Concept> concepts, Commit commit) {
		Map<String, Concept> existingConceptsMap = new HashMap<>();
		final List<String> conceptIds = concepts.stream().filter(concept -> concept.getConceptId() != null).map(Concept::getConceptId).collect(Collectors.toList());
		if (!conceptIds.isEmpty()) {
			for (List<String> conceptIdPartition : Iterables.partition(conceptIds, 500)) {
				final List<Concept> existingConcepts = doFind(conceptIdPartition, DEFAULT_LANGUAGE_CODES, commit, PageRequest.of(0, conceptIds.size())).getContent();
				for (Concept existingConcept : existingConcepts) {
					existingConceptsMap.put(existingConcept.getConceptId(), existingConcept);
				}
			}
		}
		return existingConceptsMap;
	}

	public void deleteAll() {
		ExecutorService executorService = Executors.newCachedThreadPool();
		List<Future> futures = Lists.newArrayList(
				executorService.submit(() -> conceptRepository.deleteAll()),
				executorService.submit(() -> descriptionRepository.deleteAll()),
				executorService.submit(() -> relationshipRepository.deleteAll()),
				executorService.submit(() -> referenceSetMemberRepository.deleteAll()),
				executorService.submit(() -> queryConceptRepository.deleteAll())
		);
		for (int i = 0; i < futures.size(); i++) {
			getFutureWithTimeoutOrCancel(futures.get(i), i);
		}
	}

	private void getFutureWithTimeoutOrCancel(Future<?> future, int index) {
		try {
			future.get(20, TimeUnit.SECONDS);
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
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
		try (CloseableIterator<Concept> conceptStream = elasticsearchTemplate.stream(queryBuilder.build(), Concept.class)) {
			conceptStream.forEachRemaining(c -> ids.add(c.getConceptIdAsLong()));
		}

		return ids;
	}
}
