package org.snomed.snowstorm.core.data.services;

import ch.qos.logback.classic.Level;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
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
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.repositories.*;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierReservedBlock;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.data.services.pojo.AsyncConceptChangeBatch;
import org.snomed.snowstorm.core.data.services.pojo.ResultMapPage;
import org.snomed.snowstorm.core.data.services.pojo.SAxiomRepresentation;
import org.snomed.snowstorm.core.util.MapUtil;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.elasticsearch.index.query.QueryBuilders.*;

@Service
public class ConceptService extends ComponentService {

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
	private DescriptionService descriptionService;

	@Autowired
	private ReferenceSetMemberService memberService;

	@Autowired
	private AxiomConversionService axiomConversionService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;
	
	@Autowired
	private IdentifierService identifierService;

	@Autowired
	private TraceabilityLogService traceabilityLogService;

	private final Cache<String, AsyncConceptChangeBatch> batchConceptChanges;
	private final ValidatorFactory validatorFactory;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public ConceptService() {
		batchConceptChanges = CacheBuilder.newBuilder().expireAfterWrite(2, TimeUnit.HOURS).build();
		validatorFactory = Validation.buildDefaultValidatorFactory();
	}

	public Concept find(String id, String path) {
		final Page<Concept> concepts = doFind(Collections.singleton(id), path, PageRequest.of(0, 10));
		if (concepts.getTotalElements() > 1) {
			final Branch latestBranch = branchService.findLatest(path);
			final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(path);
			logger.error("Found more than one concept {} on branch (latest) {} using criteria {}",
					concepts.getContent(), latestBranch, branchCriteria);
			concepts.forEach(c -> logger.info("id:{} path:{}, start:{}, end:{}", c.getInternalId(), c.getPath(), c.getStartDebugFormat(), c.getEndDebugFormat()));
			throw new IllegalStateException("More than one concept found for id " + id + " on branch " + path);
		}
		Concept concept = concepts.getTotalElements() == 0 ? null : concepts.iterator().next();
		logger.info("Find id:{}, path:{} found:{}", id, path, concept);
		return concept;
	}

	public Collection<Concept> find(String path, Collection<? extends Object> ids) {
		return doFind(ids, path, PageRequest.of(0, ids.size())).getContent();
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
		return doFind(null, path, pageRequest);
	}

	public Collection<ConceptMini> findConceptChildren(String conceptId, String path, Relationship.CharacteristicType relationshipType) {
		final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(path);

		// Gather children ids
		final Set<String> childrenIds = new HashSet<>();
		try (final CloseableIterator<Relationship> relationshipStream = openRelationshipStream(branchCriteria, termQuery("destinationId", conceptId), relationshipType)) {
			relationshipStream.forEachRemaining(relationship -> childrenIds.add(relationship.getSourceId()));
		}

		// Fetch concept details
		final Map<String, ConceptMini> conceptMiniMap = new HashMap<>();
		try (final CloseableIterator<Concept> conceptStream = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Concept.class))
						.must(termsQuery("conceptId", childrenIds))
				)
				.withPageable(LARGE_PAGE)
				.build(), Concept.class
		)) {
			conceptStream.forEachRemaining(concept -> conceptMiniMap.put(concept.getConceptId(), new ConceptMini(concept).setLeaf(relationshipType, true)));
		}

		// Find children of the children to set the isLeaf flag
		try (final CloseableIterator<Relationship> relationshipStream = openRelationshipStream(branchCriteria, termsQuery("destinationId", childrenIds), relationshipType)) {
			relationshipStream.forEachRemaining(relationship -> conceptMiniMap.get(relationship.getDestinationId()).setLeaf(relationshipType, false));
		}
		// Fetch descriptions and Lang refsets
		descriptionService.joinDescriptions(branchCriteria, null, conceptMiniMap, null, false);

		return conceptMiniMap.values();
	}

	public Collection<ConceptMini> findConceptParents(String conceptId, String path, Relationship.CharacteristicType form) {
		Concept concept = find(conceptId, path);
		return concept.getRelationships().stream().filter(r -> form.getConceptId().equals(r.getCharacteristicTypeId())).map(Relationship::target).collect(Collectors.toList());
	}

	private CloseableIterator<Relationship> openRelationshipStream(BranchCriteria branchCriteria,
																   QueryBuilder destinationCriteria,
																   Relationship.CharacteristicType relationshipType) {
		return elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Relationship.class))
						.must(termQuery("active", true))
						.must(termQuery("typeId", Concepts.ISA))
						.must(destinationCriteria)
						.must(termQuery("characteristicTypeId", relationshipType.getConceptId()))
				)
				.withPageable(LARGE_PAGE)
				.build(), Relationship.class
		);
	}

	private Page<Concept> doFind(Collection<? extends Object> conceptIds, Commit commit, PageRequest pageRequest) {
		final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit);
		return doFind(conceptIds, branchCriteria, pageRequest, true, true);
	}

	private Page<Concept> doFind(Collection<? extends Object> conceptIds, String path, PageRequest pageRequest) {
		final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(path);
		return doFind(conceptIds, branchCriteria, pageRequest, true, true);
	}

	public ResultMapPage<String, ConceptMini> findConceptMinis(String path, Collection<? extends Object> conceptIds) {
		if (conceptIds.isEmpty()) {
			return new ResultMapPage<>(new HashMap<>(), 0);
		}
		final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(path);
		return findConceptMinis(branchCriteria, conceptIds);
	}

	public ResultMapPage<String, ConceptMini> findConceptMinis(BranchCriteria branchCriteria, PageRequest pageRequest) {
		return findConceptMinis(branchCriteria, null, pageRequest);
	}

	public ResultMapPage<String, ConceptMini> findConceptMinis(BranchCriteria branchCriteria, Collection<? extends Object> conceptIds) {
		if (conceptIds.isEmpty()) {
			return new ResultMapPage<>(new HashMap<>(), 0);
		}
		return findConceptMinis(branchCriteria, conceptIds, PageRequest.of(0, conceptIds.size()));
	}

	private ResultMapPage<String, ConceptMini> findConceptMinis(BranchCriteria branchCriteria, Collection<? extends Object> conceptIds, PageRequest pageRequest) {
		if (conceptIds != null && conceptIds.isEmpty()) {
			return new ResultMapPage<>(new HashMap<>(), 0);
		}
		Page<Concept> concepts = doFind(conceptIds, branchCriteria, pageRequest, false, false);
		return new ResultMapPage<>(
				concepts.getContent().stream().map(ConceptMini::new).collect(Collectors.toMap(ConceptMini::getConceptId, Function.identity())),
				concepts.getTotalElements());
	}

	private void populateConceptMinis(BranchCriteria branchCriteria, Map<String, ConceptMini> minisToPopulate) {
		if (!minisToPopulate.isEmpty()) {
			Set<String> conceptIds = minisToPopulate.keySet();
			Page<Concept> concepts = doFind(conceptIds, branchCriteria, PageRequest.of(0, conceptIds.size()), false, false);
			concepts.getContent().forEach(c -> {
				ConceptMini conceptMini = minisToPopulate.get(c.getConceptId());
				conceptMini.setDefinitionStatus(c.getDefinitionStatus());
				conceptMini.addActiveDescriptions(c.getDescriptions().stream().filter(SnomedComponent::isActive).collect(Collectors.toSet()));
			});
		}
	}

	private Page<Concept> doFind(Collection<? extends Object> conceptIdsToFind,
								 BranchCriteria branchCriteria,
								 PageRequest pageRequest,
								 boolean includeRelationships,
								 boolean includeDescriptionInactivationInfo) {

		final TimerUtil timer = new TimerUtil("Find concept", Level.INFO);
		timer.checkpoint("get branch criteria");

		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();

		Page<Concept> concepts;
		if (conceptIdsToFind != null && !conceptIdsToFind.isEmpty()) {
			List<Concept> allConcepts = new ArrayList<>();
			Page<Concept> tempPage = null;
			for (List<? extends Object> conceptIdsToFindSegment : Iterables.partition(conceptIdsToFind, CLAUSE_LIMIT)) {
				queryBuilder
						.withQuery(boolQuery()
								.must(branchCriteria.getEntityBranchCriteria(Concept.class))
								.must(termsQuery("conceptId", conceptIdsToFindSegment))
						)
						.withPageable(PageRequest.of(0, conceptIdsToFindSegment.size()));
				tempPage = elasticsearchTemplate.queryForPage(queryBuilder.build(), Concept.class);
				allConcepts.addAll(tempPage.getContent());
			}
			concepts = new PageImpl<>(allConcepts, pageRequest, tempPage.getTotalElements());
		} else {
			queryBuilder
					.withQuery(boolQuery().must(branchCriteria.getEntityBranchCriteria(Concept.class)))
					.withPageable(pageRequest);
			concepts = elasticsearchTemplate.queryForPage(queryBuilder.build(), Concept.class);
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
						relationship.setType(getConceptMini(conceptMiniMap, relationship.getTypeId()));
						relationship.setTarget(getConceptMini(conceptMiniMap, relationship.getDestinationId()));
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
						joinAxiom(axiomMember, conceptIdMap, conceptMiniMap);
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
		timer.finish();

		return concepts;
	}

	private void joinAxiom(ReferenceSetMember axiomMember, Map<String, Concept> conceptIdMap, Map<String, ConceptMini> conceptMiniMap) {
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

				// Add placeholders for relationship type and target details
				relationships.forEach(relationship -> {
					relationship.setType(getConceptMini(conceptMiniMap, relationship.getTypeId()));
					relationship.setTarget(getConceptMini(conceptMiniMap, relationship.getDestinationId()));
				});
			}

		} catch (ConversionException e) {
			logger.error("Failed to deserialise axiom {}", axiomMember.getId(), e);
		}
	}

	private ConceptMini getConceptMini(Map<String, ConceptMini> conceptMiniMap, String id) {
		if (id == null) return new ConceptMini((String)null);
		return conceptMiniMap.computeIfAbsent(id, i -> new ConceptMini(id));
	}

	public void deleteConceptAndComponents(String conceptId, String path, boolean force) {
		try (final Commit commit = branchService.openCommit(path)) {
			final Concept concept = find(conceptId, path);
			if (concept == null) {
				throw new IllegalArgumentException("Concept " + conceptId + " not found.");
			}
			if (concept.isReleased() && !force) {
				throw new IllegalStateException("Released concept will not be deleted.");
			}

			// Mark concept and components as deleted
			logger.info("Deleting concept {} on branch {} at timepoint {}", concept.getConceptId(), path, commit.getTimepoint());
			concept.markDeleted();
			Set<ReferenceSetMember> membersToDelete = new HashSet<>();
			concept.getDescriptions().forEach(description -> {
				description.markDeleted();
				membersToDelete.addAll(description.getLangRefsetMembers().values());
				ReferenceSetMember inactivationIndicatorMember = description.getInactivationIndicatorMember();
				if (inactivationIndicatorMember != null) {
					membersToDelete.add(inactivationIndicatorMember);
				}
			});
			ReferenceSetMember inactivationIndicatorMember = concept.getInactivationIndicatorMember();
			if (inactivationIndicatorMember != null) {
				inactivationIndicatorMember.markDeleted();
			}
			Set<ReferenceSetMember> associationTargetMembers = concept.getAssociationTargetMembers();
			if (associationTargetMembers != null) {
				membersToDelete.addAll(associationTargetMembers);
			}
			concept.getRelationships().forEach(Relationship::markDeleted);

			// Persist deletion
			doSaveBatchConcepts(Sets.newHashSet(concept), commit);
			doSaveBatchDescriptions(concept.getDescriptions(), commit);
			membersToDelete.forEach(ReferenceSetMember::markDeleted);
			memberService.doSaveBatchMembers(membersToDelete, commit);
			doSaveBatchRelationships(concept.getRelationships(), commit);
			commit.markSuccessful();
		}
	}

	public Concept create(Concept conceptVersion, String path) throws ServiceException {
		final Branch branch = branchService.findBranchOrThrow(path);
		if (conceptVersion.getConceptId() != null && exists(conceptVersion.getConceptId(), path)) {
			throw new IllegalArgumentException("Concept '" + conceptVersion.getConceptId() + "' already exists on branch '" + path + "'.");
		}
		return doSave(conceptVersion, branch);
	}

	public Iterable<Concept> create(List<Concept> concepts, String path) throws ServiceException {
		final Branch branch = branchService.findBranchOrThrow(path);
		final Set<String> conceptIds = concepts.stream().filter(concept -> concept.getConceptId() != null).map(Concept::getConceptId).collect(Collectors.toSet());
		if (!conceptIds.isEmpty()) {
			final Collection<String> nonExistentConceptIds = getNonExistentConceptIds(conceptIds, path);
			conceptIds.removeAll(nonExistentConceptIds);
			if (!conceptIds.isEmpty()) {
				throw new IllegalArgumentException("Some concepts already exist on branch '" + path + "', " + conceptIds);
			}
		}
		return doSave(concepts, branch);
	}

	public Concept update(Concept conceptVersion, String path) throws ServiceException {
		final Branch branch = branchService.findBranchOrThrow(path);
		final String conceptId = conceptVersion.getConceptId();
		Assert.isTrue(!Strings.isNullOrEmpty(conceptId), "conceptId is required.");
		if (!exists(conceptId, path)) {
			throw new IllegalArgumentException("Concept '" + conceptId + "' does not exist on branch '" + path + "'.");
		}

		return doSave(conceptVersion, branch);
	}

	public Iterable<Concept> createUpdate(List<Concept> concepts, String path) throws ServiceException {
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
			Iterable<Concept> updatedConcepts = createUpdate(concepts, path);
			batchConceptChange.setConceptIds(StreamSupport.stream(updatedConcepts.spliterator(), false).map(Concept::getConceptIdAsLong).collect(Collectors.toList()));
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

	private <C extends SnomedComponent> void markDeletionsAndUpdates(Set<C> newComponents, Set<C> existingComponents, boolean rebase) {
		// Mark deletions
		for (C existingComponent : existingComponents) {
			if (!newComponents.contains(existingComponent)) {
				existingComponent.markDeleted();
				newComponents.add(existingComponent);// Add to newComponents collection so the deletion is persisted
			}
		}
		// Mark updates
		final Map<String, C> map = existingComponents.stream().collect(Collectors.toMap(DomainEntity::getId, Function.identity()));
		for (C newComponent : newComponents) {
			final C existingComponent = map.get(newComponent.getId());
			newComponent.setChanged(newComponent.isComponentChanged(existingComponent) || rebase);
			if (existingComponent != null) {
				newComponent.copyReleaseDetails(existingComponent);
				newComponent.updateEffectiveTime();
			} else {
				newComponent.setCreating(true);
				newComponent.clearReleaseDetails();
			}
		}
	}

	private Concept doSave(Concept concept, Branch branch) throws ServiceException {
		return doSave(Collections.singleton(concept), branch).iterator().next();
	}

	private Iterable<Concept> doSave(Collection<Concept> concepts, Branch branch) throws ServiceException {
		try (final Commit commit = branchService.openCommit(branch.getPath())) {
			final Iterable<Concept> savedConcepts = doSaveBatchConceptsAndComponents(concepts, commit);
			commit.markSuccessful();
			return savedConcepts;
		}
	}

	public void updateWithinCommit(Collection<Concept> concepts, Commit commit) throws ServiceException {
		doSaveBatchConceptsAndComponents(concepts, commit);
	}

	private Iterable<Concept> doSaveBatchConceptsAndComponents(Collection<Concept> concepts, Commit commit) throws ServiceException {
		final boolean savingMergedConcepts = commit.isRebase();

		validateConcepts(concepts);

		final List<String> conceptIds = concepts.stream().filter(concept -> concept.getConceptId() != null).map(Concept::getConceptId).collect(Collectors.toList());
		final Map<String, Concept> existingConceptsMap = new HashMap<>();
		if (!conceptIds.isEmpty()) {
			for (List<String> conceptIdPartition : Iterables.partition(conceptIds, 500)) {
				final List<Concept> existingConcepts = doFind(conceptIdPartition, commit, PageRequest.of(0, conceptIds.size())).getContent();
				for (Concept existingConcept : existingConcepts) {
					existingConceptsMap.put(existingConcept.getConceptId(), existingConcept);
				}
			}
		}

		IdentifierReservedBlock reservedIds = identifierService.reserveIdentifierBlock(concepts);

		// Assign identifiers to new concepts
		concepts.stream()
				.filter(concept -> concept.getConceptId() == null)
				.forEach(concept -> concept.setConceptId(reservedIds.getNextId(ComponentType.Concept).toString()));

		// Convert axioms to OWLAxiom reference set members before persisting
		axiomConversionService.populateAxiomMembers(concepts, commit.getBranch().getPath());

		List<Description> descriptionsToPersist = new ArrayList<>();
		List<Relationship> relationshipsToPersist = new ArrayList<>();
		List<ReferenceSetMember> refsetMembersToPersist = new ArrayList<>();
		for (Concept concept : concepts) {
			final Concept existingConcept = existingConceptsMap.get(concept.getConceptId());
			final Map<String, Description> existingDescriptions = new HashMap<>();

			// Inactivate relationships of inactive concept
			if (!concept.isActive()) {
				concept.getRelationships().forEach(relationship -> relationship.setActive(false));
			}

			Set<ReferenceSetMember> newOwlAxiomMembers = concept.getAllOwlAxiomMembers();

			// Mark changed concepts as changed
			if (existingConcept != null) {
				concept.setChanged(concept.isComponentChanged(existingConcept) || savingMergedConcepts);
				concept.copyReleaseDetails(existingConcept);
				concept.updateEffectiveTime();

				markDeletionsAndUpdates(concept.getDescriptions(), existingConcept.getDescriptions(), savingMergedConcepts);
				markDeletionsAndUpdates(concept.getRelationships(), existingConcept.getRelationships(), savingMergedConcepts);
				markDeletionsAndUpdates(newOwlAxiomMembers, existingConcept.getAllOwlAxiomMembers(), savingMergedConcepts);
				existingDescriptions.putAll(existingConcept.getDescriptions().stream().collect(Collectors.toMap(Description::getId, Function.identity())));
			} else {
				concept.setCreating(true);
				concept.setChanged(true);
				concept.clearReleaseDetails();

				Stream.of(
						concept.getDescriptions().stream(),
						concept.getRelationships().stream(),
						newOwlAxiomMembers.stream())
						.flatMap(i -> i)
						.forEach(component -> {
							component.setCreating(true);
							component.setChanged(true);
							component.clearReleaseDetails();
						});
			}

			// Concept inactivation indicator changes
			updateInactivationIndicator(concept, existingConcept, refsetMembersToPersist, Concepts.CONCEPT_INACTIVATION_INDICATOR_REFERENCE_SET);

			// Concept association changes
			updateAssociations(concept, existingConcept, refsetMembersToPersist);

			for (Description description : concept.getDescriptions()) {
				description.setConceptId(concept.getConceptId());
				final Description existingDescription = existingDescriptions.get(description.getDescriptionId());
				final Map<String, ReferenceSetMember> existingMembersToMatch = new HashMap<>();
				if (existingDescription != null) {
					existingMembersToMatch.putAll(existingDescription.getLangRefsetMembers());
				} else {
					description.setCreating(true);
					if (description.getDescriptionId() == null) {
						description.setDescriptionId(reservedIds.getNextId(ComponentType.Description).toString());
					}
				}
				if (!description.isActive()) {
					description.clearLanguageRefsetMembers();
				}

				// Description inactivation indicator changes
				updateInactivationIndicator(description, existingDescription, refsetMembersToPersist, Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET);

				// Description association changes
				updateAssociations(description, existingDescription, refsetMembersToPersist);

				// Description acceptability / language reference set changes
				for (Map.Entry<String, String> acceptability : description.getAcceptabilityMap().entrySet()) {
					final String acceptabilityId = Concepts.descriptionAcceptabilityNames.inverse().get(acceptability.getValue());
					if (acceptabilityId == null) {
						throw new IllegalArgumentException("Acceptability value not recognised '" + acceptability.getValue() + "'.");
					}

					final String languageRefsetId = acceptability.getKey();
					final ReferenceSetMember existingMember = existingMembersToMatch.get(languageRefsetId);
					if (existingMember != null) {
						final ReferenceSetMember member = new ReferenceSetMember(existingMember.getMemberId(), null, true,
								existingMember.getModuleId(), languageRefsetId, description.getId());
						member.setAdditionalField("acceptabilityId", acceptabilityId);
						member.setConceptId(concept.getConceptId());

						if (member.isComponentChanged(existingMember) || savingMergedConcepts) {
							member.setChanged(true);
							member.copyReleaseDetails(existingMember);
							member.updateEffectiveTime();
							refsetMembersToPersist.add(member);
						}
						existingMembersToMatch.remove(languageRefsetId);
					} else {
						final ReferenceSetMember member = new ReferenceSetMember(description.getModuleId(), languageRefsetId, description.getId());
						member.setAdditionalField("acceptabilityId", acceptabilityId);
						member.setConceptId(concept.getConceptId());
						member.setChanged(true);
						refsetMembersToPersist.add(member);
					}
				}
				for (ReferenceSetMember leftoverMember : existingMembersToMatch.values()) {
					if (leftoverMember.isActive()) {
						leftoverMember.setActive(false);
						leftoverMember.markChanged();
						refsetMembersToPersist.add(leftoverMember);
					}
				}
			}
			concept.getRelationships()
					.forEach(relationship -> relationship.setSourceId(concept.getConceptId()));
			concept.getRelationships().stream()
					.filter(relationship -> relationship.getRelationshipId() == null)
					.forEach(relationship -> relationship.setRelationshipId(reservedIds.getNextId(ComponentType.Relationship).toString()));

			// Detach concept's components to be persisted separately
			descriptionsToPersist.addAll(concept.getDescriptions());
			concept.getDescriptions().clear();
			relationshipsToPersist.addAll(concept.getRelationships());
			concept.getRelationships().clear();
			refsetMembersToPersist.addAll(newOwlAxiomMembers);
			concept.getAdditionalAxioms().clear();
			concept.getGciAxioms().clear();
		}

		// TODO: Try saving all core component types at once - Elasticsearch likes multi-threaded writes.
		final Iterable<Concept> conceptsSaved = doSaveBatchConcepts(concepts, commit);
		Iterable<Description> descriptionsSaved = doSaveBatchDescriptions(descriptionsToPersist, commit);
		Iterable<Relationship> relationshipsSaved = doSaveBatchRelationships(relationshipsToPersist, commit);

		Iterable<ReferenceSetMember> referenceSetMembersSaved = memberService.doSaveBatchMembers(refsetMembersToPersist, commit);
		doDeleteMembersWhereReferencedComponentDeleted(commit.getEntityVersionsDeleted(), commit);

		Map<String, Concept> conceptMap = new HashMap<>();
		for (Concept concept : conceptsSaved) {
			conceptMap.put(concept.getConceptId(), concept);
		}
		for (Description description : descriptionsSaved) {
			conceptMap.get(description.getConceptId()).addDescription(description);
		}
		Map<String, ConceptMini> minisToLoad = new HashMap<>();
		for (Relationship relationship : relationshipsSaved) {
			conceptMap.get(relationship.getSourceId()).addRelationship(relationship);
			relationship.setType(getConceptMini(minisToLoad, relationship.getTypeId()));
			relationship.setTarget(getConceptMini(minisToLoad, relationship.getDestinationId()));
		}
		StreamSupport.stream(referenceSetMembersSaved.spliterator(), false)
				.filter(member -> Concepts.OWL_AXIOM_REFERENCE_SET.equals(member.getRefsetId()))
				.forEach(member -> joinAxiom(member, conceptMap, minisToLoad));
		populateConceptMinis(versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit), minisToLoad);

		// Store assigned identifiers for registration with CIS
		identifierService.persistAssignedIdsForRegistration(reservedIds);

		// Log traceability activity
		traceabilityLogService.logActivity(SecurityUtil.getUsername(), commit,
				concepts, descriptionsToPersist, relationshipsToPersist, refsetMembersToPersist);

		return conceptsSaved;
	}

	private void validateConcepts(Collection<Concept> concepts) {
		Validator validator = validatorFactory.getValidator();
		for (Concept concept : concepts) {
			Set<ConstraintViolation<Concept>> violations = validator.validate(concept);
			if (!violations.isEmpty()) {
				ConstraintViolation<Concept> violation = violations.iterator().next();
				throw new IllegalArgumentException(String.format("Invalid concept property %s %s", violation.getPropertyPath().toString(), violation.getMessage()));
			}
		}
	}

	private void updateAssociations(SnomedComponentWithAssociations newComponent, SnomedComponentWithAssociations existingComponent, List<ReferenceSetMember> refsetMembersToPersist) {
		Map<String, Set<String>> newAssociations = newComponent.getAssociationTargets();
		Map<String, Set<String>> existingAssociations = existingComponent == null ? null : existingComponent.getAssociationTargets();
		if (existingAssociations != null && !MapUtil.containsAllKeysAndSetsAreSupersets(newAssociations, existingAssociations)) {
			// One or more existing associations need to be made inactive

			Set<ReferenceSetMember> existingAssociationTargetMembers = existingComponent.getAssociationTargetMembers();
			if (newAssociations == null) {
				newAssociations = new HashMap<>();
			}
			for (String associationName : existingAssociations.keySet()) {
				Set<String> existingAssociationsOfType = existingAssociations.get(associationName);
				Set<String> newAssociationsOfType = newAssociations.get(associationName);
				for (String existingAssociationOfType : existingAssociationsOfType) {
					if (newAssociationsOfType == null || !newAssociationsOfType.contains(existingAssociationOfType)) {
						// Existing association should be made inactive
						String associationRefsetId = Concepts.historicalAssociationNames.inverse().get(associationName);
						for (ReferenceSetMember existingMember : existingAssociationTargetMembers) {
							if (existingMember.isActive() && existingMember.getRefsetId().equals(associationRefsetId)
									&& existingAssociationOfType.equals(existingMember.getAdditionalField("targetComponentId"))) {
								existingMember.setActive(false);
								existingMember.markChanged();
								refsetMembersToPersist.add(existingMember);
							}
						}
					}
				}
			}
		}
		if (newAssociations != null) {
			Map<String, Set<String>> missingKeyValues = MapUtil.collectMissingKeyValues(existingAssociations, newAssociations);
			if (!missingKeyValues.isEmpty()) {
				// One or more new associations need to be created
				for (String associationName : missingKeyValues.keySet()) {
					Set<String> missingValues = missingKeyValues.get(associationName);
					for (String missingValue : missingValues) {
						String associationRefsetId = Concepts.historicalAssociationNames.inverse().get(associationName);
						if (associationRefsetId == null) {
							throw new IllegalArgumentException("Association reference set not recognised '" + associationName + "'.");
						}
						ReferenceSetMember newTargetMember = new ReferenceSetMember(newComponent.getModuleId(), associationRefsetId, newComponent.getId());
						newTargetMember.setAdditionalField("targetComponentId", missingValue);
						newTargetMember.markChanged();
						refsetMembersToPersist.add(newTargetMember);
						newComponent.addAssociationTargetMember(newTargetMember);
					}
				}
			}
		}
	}

	private void updateInactivationIndicator(SnomedComponentWithInactivationIndicator newComponent,
														   SnomedComponentWithInactivationIndicator existingComponent,
														   Collection<ReferenceSetMember> refsetMembersToPersist,
														   String indicatorReferenceSet) {

		String newIndicator = newComponent.getInactivationIndicator();
		String existingIndicator = existingComponent == null ? null : existingComponent.getInactivationIndicator();
		if (existingIndicator != null && (newIndicator == null || !newIndicator.equals(existingIndicator))) {
			// Make existing indicator inactive
			ReferenceSetMember existingIndicatorMember = existingComponent.getInactivationIndicatorMember();
			existingIndicatorMember.setActive(false);
			existingIndicatorMember.markChanged();
			refsetMembersToPersist.add(existingIndicatorMember);
		}
		if (newIndicator != null && (existingIndicator == null || !newIndicator.equals(existingIndicator))) {
			// Create new indicator
			String newIndicatorId = Concepts.inactivationIndicatorNames.inverse().get(newIndicator);
			if (newIndicatorId == null) {
				throw new IllegalArgumentException(newComponent.getClass().getSimpleName() + " inactivation indicator not recognised '" + newIndicator + "'.");
			}
			ReferenceSetMember newIndicatorMember = new ReferenceSetMember(newComponent.getModuleId(), indicatorReferenceSet, newComponent.getId());
			newIndicatorMember.setAdditionalField("valueId", newIndicatorId);
			newIndicatorMember.setChanged(true);
			refsetMembersToPersist.add(newIndicatorMember);
			newComponent.setInactivationIndicatorMember(newIndicatorMember);
		}
	}

	/**
	 * Persists concept updates within commit.
	 * @param concepts
	 * @param commit
	 * @return List of persisted components with updated metadata and filtered by deleted status.
	 */
	public Iterable<Concept> doSaveBatchConcepts(Collection<Concept> concepts, Commit commit) {
		return doSaveBatchComponents(concepts, commit, "conceptId", conceptRepository);
	}

	/**
	 * Persists description updates within commit.
	 * @param descriptions
	 * @param commit
	 * @return List of persisted components with updated metadata and filtered by deleted status.
	 */
	public Iterable<Description> doSaveBatchDescriptions(Collection<Description> descriptions, Commit commit) {
		return doSaveBatchComponents(descriptions, commit, "descriptionId", descriptionRepository);
	}

	/**
	 * Persists relationships updates within commit.
	 * @param relationships
	 * @param commit
	 * @return List of persisted components with updated metadata and filtered by deleted status.
	 */
	public Iterable<Relationship> doSaveBatchRelationships(Collection<Relationship> relationships, Commit commit) {
		return doSaveBatchComponents(relationships, commit, "relationshipId", relationshipRepository);
	}

	private void doDeleteMembersWhereReferencedComponentDeleted(Set<String> entityVersionsDeleted, Commit commit) {
		NativeSearchQuery query = new NativeSearchQueryBuilder()
				.withQuery(
						boolQuery()
								.must(versionControlHelper.getBranchCriteria(commit.getBranch()).getEntityBranchCriteria(ReferenceSetMember.class))
								.must(termsQuery("referencedComponentId", entityVersionsDeleted))
				).withPageable(LARGE_PAGE).build();

		List<ReferenceSetMember> membersToDelete = new ArrayList<>();
		try (CloseableIterator<ReferenceSetMember> stream = elasticsearchTemplate.stream(query, ReferenceSetMember.class)) {
			stream.forEachRemaining(member -> {
				member.markDeleted();
				membersToDelete.add(member);
			});
		}

		for (List<ReferenceSetMember> membersBatch : Iterables.partition(membersToDelete, 500)) {
			doSaveBatchComponents(membersBatch, ReferenceSetMember.class, commit);
		}
	}

	public <T extends SnomedComponent> void doSaveBatchComponents(Collection<T> components, Class<T> type, Commit commit) {
		if (type.equals(Concept.class)) {
			doSaveBatchConcepts((Collection<Concept>) components, commit);
		} else if (type.equals(Description.class)) {
			doSaveBatchDescriptions((Collection<Description>) components, commit);
		} else if (type.equals(Relationship.class)) {
			doSaveBatchRelationships((Collection<Relationship>) components, commit);
		} else if (ReferenceSetMember.class.isAssignableFrom(type)) {
			memberService.doSaveBatchMembers((Collection<ReferenceSetMember>) components, commit);
		} else {
			throw new IllegalArgumentException("SnomedComponent type " + type + " not regognised");
		}
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

	public ElasticsearchOperations getElasticsearchTemplate() {
		return elasticsearchTemplate;
	}

	public VersionControlHelper getVersionControlHelper() {
		return versionControlHelper;
	}
}
