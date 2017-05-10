package org.ihtsdo.elasticsnomed.services;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.CommitListener;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.DomainEntity;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.apache.log4j.Level;
import org.elasticsearch.common.Strings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.domain.*;
import org.ihtsdo.elasticsnomed.repositories.ConceptRepository;
import org.ihtsdo.elasticsnomed.repositories.DescriptionRepository;
import org.ihtsdo.elasticsnomed.repositories.ReferenceSetMemberRepository;
import org.ihtsdo.elasticsnomed.repositories.RelationshipRepository;
import org.ihtsdo.elasticsnomed.util.TimerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.*;

@Service
public class ConceptService extends ComponentService implements CommitListener {

	@Autowired
	private ConceptRepository conceptRepository;

	@Autowired
	private DescriptionRepository descriptionRepository;

	@Autowired
	private RelationshipRepository relationshipRepository;

	@Autowired
	private ReferenceSetMemberRepository referenceSetMemberRepository;

	@Autowired
	private BranchService branchService;

	@Autowired
	private DescriptionService descriptionService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private QueryIndexService queryIndexService;

	private Logger logger = LoggerFactory.getLogger(getClass());

	@PostConstruct
	public void init() {
		branchService.addCommitListener(this);
	}

	public Map<Class<? extends SnomedComponent>, ElasticsearchCrudRepository> getComponentTypeRepoMap() {
		Map<Class<? extends SnomedComponent>, ElasticsearchCrudRepository> map = new LinkedHashMap<>();
		map.put(Concept.class, conceptRepository);
		map.put(Description.class, descriptionRepository);
		map.put(Relationship.class, relationshipRepository);
		map.put(ReferenceSetMember.class, referenceSetMemberRepository);
		return map;
	}

	public Concept find(String id, String path) {
		final Page<Concept> concepts = doFind(Collections.singleton(id), path, new PageRequest(0, 10));
		if (concepts.getTotalElements() > 1) {
			final Branch latestBranch = branchService.findLatest(path);
			final QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(path);
			logger.error("Found more than one concept {} on branch (latest) {} using criteria {}",
					concepts.getContent(), latestBranch, branchCriteria);
			concepts.forEach(c -> logger.info("id:{} path:{}, start:{}, end:{}", c.getInternalId(), c.getFatPath(), c.getStartDebugFormat(), c.getEndDebugFormat()));
			throw new IllegalStateException("More than one concept found for id " + id + " on branch " + path);
		}
		Concept concept = concepts.getTotalElements() == 0 ? null : concepts.iterator().next();
		logger.info("Find id:{}, path:{} found:{}", id, path, concept);
		return concept;
	}

	public Collection<Concept> find(String path, Collection<? extends Object> ids) {
		return doFind(ids, path, new PageRequest(0, ids.size())).getContent();
	}

	public boolean exists(String id, String path) {
		return getNonExistentConcepts(Collections.singleton(id), path).isEmpty();
	}

	public Collection<String> getNonExistentConcepts(Collection<String> ids, String path) {
		final QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(path);

		final BoolQueryBuilder builder = boolQuery()
				.must(branchCriteria)
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
		final QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(path);

		// Gather children ids
		final Set<String> childrenIds = new HashSet<>();
		try (final CloseableIterator<Relationship> relationshipStream = openRelationshipStream(branchCriteria, termQuery("destinationId", conceptId), relationshipType)) {
			relationshipStream.forEachRemaining(relationship -> childrenIds.add(relationship.getSourceId()));
		}

		// Fetch concept details
		final Map<String, ConceptMini> conceptMiniMap = new HashMap<>();
		try (final CloseableIterator<Concept> conceptStream = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria)
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
		descriptionService.fetchDescriptions(branchCriteria, null, conceptMiniMap, null, false);

		return conceptMiniMap.values();
	}

	public Collection<ConceptMini> findConceptDescendants(String conceptId, String path, Relationship.CharacteristicType form) {
		QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(path);
		Set<Long> descendants = queryIndexService.retrieveDescendants(conceptId, branchCriteria, form == Relationship.CharacteristicType.stated);
		if (descendants.isEmpty()) {
			return Collections.emptySet();
		}
		Set<String> descendantIds = descendants.stream().map(Object::toString).collect(Collectors.toSet());
		Map<String, ConceptMini> conceptMinis = findConceptMinis(branchCriteria, descendantIds);
		return conceptMinis.values();
	}

	public Collection<ConceptMini> findConceptParents(String conceptId, String path, Relationship.CharacteristicType form) {
		Concept concept = find(conceptId, path);
		return concept.getRelationships().stream().filter(r -> form.getConceptId().equals(r.getCharacteristicTypeId())).map(Relationship::target).collect(Collectors.toList());
	}

	private CloseableIterator<Relationship> openRelationshipStream(QueryBuilder branchCriteria,
																   QueryBuilder destinationCriteria,
																   Relationship.CharacteristicType relationshipType) {
		return elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria)
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
		final QueryBuilder branchCriteria = versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit);
		return doFind(conceptIds, branchCriteria, pageRequest, true, true);
	}

	private Page<Concept> doFind(Collection<? extends Object> conceptIds, String path, PageRequest pageRequest) {
		final QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(path);
		return doFind(conceptIds, branchCriteria, pageRequest, true, true);
	}

	public Map<String, ConceptMini> findConceptMinis(String path, Set<String> conceptIds) {
		if (conceptIds.isEmpty()) {
			return Collections.emptyMap();
		}
		final QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(path);
		return findConceptMinis(branchCriteria, conceptIds);
	}

	public Map<String, ConceptMini> findConceptMinis(QueryBuilder branchCriteria, Set<String> conceptIds) {
		if (conceptIds.isEmpty()) {
			return Collections.emptyMap();
		}
		Page<Concept> concepts = doFind(conceptIds, branchCriteria, new PageRequest(0, conceptIds.size()), false, false);
		return concepts.getContent().stream().map(ConceptMini::new).collect(Collectors.toMap(ConceptMini::getConceptId, Function.identity()));
	}

	private void populateConceptMinis(QueryBuilder branchCriteria, Map<String, ConceptMini> minisToPopulate) {
		if (!minisToPopulate.isEmpty()) {
			Set<String> conceptIds = minisToPopulate.keySet();
			Page<Concept> concepts = doFind(conceptIds, branchCriteria, new PageRequest(0, conceptIds.size()), false, false);
			concepts.getContent().forEach(c -> {
				ConceptMini conceptMini = minisToPopulate.get(c.getConceptId());
				conceptMini.setDefinitionStatus(c.getDefinitionStatus());
				conceptMini.addActiveFsns(c.getDescriptions().stream().filter(d -> d.isActive() && d.getTypeId().equals(Concepts.FSN)).collect(Collectors.toSet()));
			});
		}
	}

	private Page<Concept> doFind(Collection<? extends Object> conceptIdsToFind,
								 QueryBuilder branchCriteria,
								 PageRequest pageRequest,
								 boolean includeRelationships,
								 boolean includeDescriptionInactivationInfo) {

		final TimerUtil timer = new TimerUtil("Find concept", Level.DEBUG);
		timer.checkpoint("get branch criteria");

		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();

		Page<Concept> concepts;
		if (conceptIdsToFind != null && !conceptIdsToFind.isEmpty()) {
			List<Concept> allConcepts = new ArrayList<>();
			Page<Concept> tempPage = null;
			for (List<? extends Object> conceptIdsToFindSegment : Iterables.partition(conceptIdsToFind, CLAUSE_LIMIT)) {
				queryBuilder
						.withQuery(boolQuery()
								.must(branchCriteria)
								.must(termsQuery("conceptId", conceptIdsToFindSegment))
						)
						.withPageable(new PageRequest(0, conceptIdsToFindSegment.size()));
				tempPage = elasticsearchTemplate.queryForPage(queryBuilder.build(), Concept.class);
				allConcepts.addAll(tempPage.getContent());
			}
			concepts = new PageImpl<>(allConcepts, pageRequest, tempPage.getTotalElements());
		} else {
			queryBuilder
					.withQuery(boolQuery().must(branchCriteria))
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
						.must(branchCriteria))
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
		}

		// Fetch ConceptMini definition statuses
		for (List<String> conceptIds : Iterables.partition(conceptMiniMap.keySet(), CLAUSE_LIMIT)) {
			queryBuilder.withQuery(boolQuery()
					.must(termsQuery("conceptId", conceptIds))
					.must(branchCriteria))
					.withPageable(LARGE_PAGE);
			try (final CloseableIterator<Concept> conceptsForMini = elasticsearchTemplate.stream(queryBuilder.build(), Concept.class)) {
				conceptsForMini.forEachRemaining(concept ->
						conceptMiniMap.get(concept.getConceptId()).setDefinitionStatusId(concept.getDefinitionStatusId()));
			}
		}
		timer.checkpoint("get relationship def status " + getFetchCount(conceptMiniMap.size()));

		descriptionService.fetchDescriptions(branchCriteria, conceptIdMap, conceptMiniMap, timer, includeDescriptionInactivationInfo);
		timer.finish();

		return concepts;
	}

	private ConceptMini getConceptMini(Map<String, ConceptMini> conceptMiniMap, String id) {
		ConceptMini mini = conceptMiniMap.get(id);
		if (mini == null) {
			mini = new ConceptMini(id);
			if (id != null) {
				conceptMiniMap.put(id, mini);
			}
		}
		return mini;
	}

	public void deleteConceptAndComponents(String conceptId, String path, boolean force) {
		final Commit commit = branchService.openCommit(path);
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
		Set<ReferenceSetMember> langRefsetMembersToPersist = new HashSet<>();
		concept.getDescriptions().forEach(description -> {
			description.markDeleted();
			description.getLangRefsetMembers().values().forEach(member -> {
				member.markDeleted();
				langRefsetMembersToPersist.add(member);
			});
		});
		concept.getRelationships().forEach(Relationship::markDeleted);

		// Persist deletion
		doSaveBatchConcepts(Sets.newHashSet(concept), commit);
		doSaveBatchDescriptions(concept.getDescriptions(), commit);
		doSaveBatchMembers(langRefsetMembersToPersist, commit);
		doSaveBatchRelationships(concept.getRelationships(), commit);
		branchService.completeCommit(commit);
	}

	public Concept create(Concept conceptVersion, String path) {
		final Branch branch = branchService.findBranchOrThrow(path);
		if (conceptVersion.getConceptId() != null && exists(conceptVersion.getConceptId(), path)) {
			throw new IllegalArgumentException("Concept '" + conceptVersion.getConceptId() + "' already exists on branch '" + path + "'.");
		}
		return doSave(conceptVersion, branch);
	}

	public Iterable<Concept> create(List<Concept> concepts, String path) {
		final Branch branch = branchService.findBranchOrThrow(path);
		final Set<String> conceptIds = concepts.stream().filter(concept -> concept.getConceptId() != null).map(Concept::getConceptId).collect(Collectors.toSet());
		if (!conceptIds.isEmpty()) {
			final Collection<String> nonExistentConcepts = getNonExistentConcepts(conceptIds, path);
			conceptIds.removeAll(nonExistentConcepts);
			if (!conceptIds.isEmpty()) {
				throw new IllegalArgumentException("Some concepts already exist on branch '" + path + "', " + conceptIds);
			}
		}
		return doSave(concepts, branch);
	}

	public Concept update(Concept conceptVersion, String path) {
		final Branch branch = branchService.findBranchOrThrow(path);
		final String conceptId = conceptVersion.getConceptId();
		Assert.isTrue(!Strings.isNullOrEmpty(conceptId), "conceptId is required.");
		if (!exists(conceptId, path)) {
			throw new IllegalArgumentException("Concept '" + conceptId + "' does not exist on branch '" + path + "'.");
		}

		return doSave(conceptVersion, branch);
	}

	public Iterable<Concept> createUpdate(List<Concept> concepts, String path) {
		final Branch branch = branchService.findBranchOrThrow(path);
		return doSave(concepts, branch);
	}

	private <C extends SnomedComponent> boolean markDeletionsAndUpdates(Set<C> newComponents, Set<C> existingComponents, boolean rebase) {
		boolean anythingChanged = false;
		// Mark deletions
		for (C existingComponent : existingComponents) {
			if (!newComponents.contains(existingComponent)) {
				existingComponent.markDeleted();
				newComponents.add(existingComponent);
				anythingChanged = true;
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
				newComponent.clearReleaseDetails();
			}
			if (newComponent.isChanged()) {
				anythingChanged = true;
			}
		}
		return anythingChanged;
	}

	private Concept doSave(Concept concept, Branch branch) {
		return doSave(Collections.singleton(concept), branch).iterator().next();
	}

	private Iterable<Concept> doSave(Collection<Concept> concepts, Branch branch) {
		final Commit commit = branchService.openCommit(branch.getFatPath());
		final Iterable<Concept> savedConcepts = doSaveBatchConceptsAndComponents(concepts, commit);
		branchService.completeCommit(commit);
		return savedConcepts;
	}

	private ReferenceSetMember doSave(ReferenceSetMember member, Branch branch) {
		final Commit commit = branchService.openCommit(branch.getFatPath());
		final ReferenceSetMember savedMember = doSaveBatchMembers(Collections.singleton(member), commit).iterator().next();
		branchService.completeCommit(commit);
		return savedMember;
	}

	public void updateWithinCommit(Collection<Concept> concepts, Commit commit) {
		doSaveBatchConceptsAndComponents(concepts, commit);
	}

	public Iterable<Concept> doSaveBatchConceptsAndComponents(Collection<Concept> concepts, Commit commit) {
		final boolean savingMergedConcepts = commit.isRebase();
		final List<String> conceptIds = concepts.stream().filter(concept -> concept.getConceptId() != null).map(Concept::getConceptId).collect(Collectors.toList());
		final Map<String, Concept> existingConceptsMap = new HashMap<>();
		if (!conceptIds.isEmpty()) {
			for (List<String> conceptIdPartition : Iterables.partition(conceptIds, 500)) {
				final List<Concept> existingConcepts = doFind(conceptIdPartition, commit, new PageRequest(0, conceptIds.size())).getContent();
				for (Concept existingConcept : existingConcepts) {
					existingConceptsMap.put(existingConcept.getConceptId(), existingConcept);
				}
			}
		}

		List<Description> descriptionsToPersist = new ArrayList<>();
		List<Relationship> relationshipsToPersist = new ArrayList<>();
		List<ReferenceSetMember> langRefsetMembersToPersist = new ArrayList<>();
		for (Concept concept : concepts) {
			final Concept existingConcept = existingConceptsMap.get(concept.getConceptId());
			final Map<String, Description> existingDescriptions = new HashMap<>();

			// Inactivate relationships of inactive concept
			if (!concept.isActive()) {
				concept.getRelationships().forEach(relationship -> relationship.setActive(false));
			}

			// Mark changed concepts as changed
			if (existingConcept != null) {
				concept.setChanged(concept.isComponentChanged(existingConcept) || savingMergedConcepts);
				concept.copyReleaseDetails(existingConcept);
				concept.updateEffectiveTime();

				markDeletionsAndUpdates(concept.getDescriptions(), existingConcept.getDescriptions(), savingMergedConcepts);
				markDeletionsAndUpdates(concept.getRelationships(), existingConcept.getRelationships(), savingMergedConcepts);
				existingDescriptions.putAll(existingConcept.getDescriptions().stream().collect(Collectors.toMap(Description::getId, Function.identity())));
			} else {
				if (concept.getConceptId() == null) {
					concept.setConceptId(IDService.getHackId());
				}
				concept.setChanged(true);
				concept.clearReleaseDetails();
				Sets.union(concept.getDescriptions(), concept.getRelationships()).stream().forEach(component -> component.setChanged(true));
			}
			for (Description description : concept.getDescriptions()) {
				description.setConceptId(concept.getConceptId());
				final Description existingDescription = existingDescriptions.get(description.getDescriptionId());
				final Map<String, ReferenceSetMember> existingMembersToMatch = new HashMap<>();
				if (existingDescription != null) {
					existingMembersToMatch.putAll(existingDescription.getLangRefsetMembers());
				} else {
					if (description.getDescriptionId() == null) {
						description.setDescriptionId(IDService.getHackId());
					}
				}
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
							langRefsetMembersToPersist.add(member);
						}
						existingMembersToMatch.remove(languageRefsetId);
					} else {
						final ReferenceSetMember member = new ReferenceSetMember(languageRefsetId, description.getId());
						member.setAdditionalField("acceptabilityId", acceptabilityId);
						member.setConceptId(concept.getConceptId());
						member.setChanged(true);
						member.clearReleaseDetails();
						langRefsetMembersToPersist.add(member);
					}
				}
				for (ReferenceSetMember leftoverMember : existingMembersToMatch.values()) {
					// TODO: make inactive if released
					leftoverMember.markDeleted();
					langRefsetMembersToPersist.add(leftoverMember);
				}
			}
			concept.getRelationships()
					.forEach(relationship -> relationship.setSourceId(concept.getConceptId()));
			concept.getRelationships().stream()
					.filter(relationship -> relationship.getRelationshipId() == null)
					.forEach(relationship -> relationship.setRelationshipId(IDService.getHackId()));

			// Detach concept's components to be persisted separately
			descriptionsToPersist.addAll(concept.getDescriptions());
			concept.getDescriptions().clear();
			relationshipsToPersist.addAll(concept.getRelationships());
			concept.getRelationships().clear();
		}

		final Iterable<Concept> conceptsSaved = doSaveBatchConcepts(concepts, commit);
		Iterable<Description> descriptionsSaved = doSaveBatchDescriptions(descriptionsToPersist, commit);
		Iterable<Relationship> relationshipsSaved = doSaveBatchRelationships(relationshipsToPersist, commit);
		doSaveBatchMembers(langRefsetMembersToPersist, commit);

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
		populateConceptMinis(versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit), minisToLoad);

		return conceptsSaved;
	}

	// TODO: Real release process
	public void releaseSingleConceptForTest(Concept concept, String effectiveTime, String path) {
		if (!exists(concept.getId(), path)) {
			throw new IllegalArgumentException("Concept does not exist");
		}
		final Commit commit = branchService.openCommit(path);
		concept.release(effectiveTime);
		concept.setChanged(true);
		doSaveBatchConcepts(Collections.singleton(concept), commit);
		branchService.completeCommit(commit);
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

	/**
	 * Persists members updates within commit.
	 * @param members
	 * @param commit
	 * @return List of persisted components with updated metadata and filtered by deleted status.
	 */
	public Iterable<ReferenceSetMember> doSaveBatchMembers(Collection<ReferenceSetMember> members, Commit commit) {
		// Set conceptId on members where appropriate
		List<ReferenceSetMember> descriptionMembers = new ArrayList<>();
		LongSet descriptionIds = new LongArraySet();
		members.stream()
				.filter(member -> IDService.isDescriptionId(member.getReferencedComponentId()))
				.filter(member -> member.getConceptId() == null)
				.forEach(member -> {
					descriptionMembers.add(member);
					descriptionIds.add(parseLong(member.getReferencedComponentId()));
				});

		if (descriptionIds.size() != 0) {
			final NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();

			Long2ObjectMap descriptionMap = new Long2ObjectOpenHashMap<>();
			for (List<Long> descriptionIdsSegment : Iterables.partition(descriptionIds, CLAUSE_LIMIT)) {
				queryBuilder
						.withQuery(boolQuery()
								.must(termsQuery("descriptionId", descriptionIdsSegment))
								.must(versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit)))
						.withPageable(LARGE_PAGE);
				try (final CloseableIterator<Description> descriptions = elasticsearchTemplate.stream(queryBuilder.build(), Description.class)) {
					descriptions.forEachRemaining(description ->
							descriptionMap.put(parseLong(description.getDescriptionId()), description));
				}
			}

			descriptionMembers.parallelStream().forEach(member -> {
				Description description = (Description) descriptionMap.get(parseLong(member.getReferencedComponentId()));
				if (description == null) {
					logger.warn("Refset member refers to description which does not exist, this will not be persisted {} -> {}", member.getId(), member.getReferencedComponentId());
					members.remove(member);
					return;
				}
				member.setConceptId(description.getConceptId());
			});
		}

		return doSaveBatchComponents(members, commit, "memberId", referenceSetMemberRepository);
	}

	public <T extends SnomedComponent> void doSaveBatchComponents(Collection<T> components, Class<T> type, Commit commit) {
		if (type.equals(Concept.class)) {
			doSaveBatchConcepts((Collection<Concept>) components, commit);
		} else if (type.equals(Description.class)) {
			doSaveBatchDescriptions((Collection<Description>) components, commit);
		} else if (type.equals(Relationship.class)) {
			doSaveBatchRelationships((Collection<Relationship>) components, commit);
		} else if (ReferenceSetMember.class.isAssignableFrom(type)) {
			doSaveBatchMembers((Collection<ReferenceSetMember>) components, commit);
		} else {
			throw new IllegalArgumentException("SnomedComponent type " + type + " not regognised");
		}
	}

	@Override
	public void preCommitCompletion(Commit commit) {
		queryIndexService.updateStatedAndInferredTransitiveClosures(commit);
	}

	public void deleteAll() {
		ExecutorService executorService = Executors.newCachedThreadPool();
		List<Future> futures = Lists.newArrayList(
				executorService.submit(() -> conceptRepository.deleteAll()),
				executorService.submit(() -> descriptionRepository.deleteAll()),
				executorService.submit(() -> relationshipRepository.deleteAll()),
				executorService.submit(() -> referenceSetMemberRepository.deleteAll()),
				executorService.submit(() -> queryIndexService.deleteAll())
		);
		for (int i = 0; i < futures.size(); i++) {
			getWithTimeoutOrCancel(futures.get(i), i);
		}
	}

	private void getWithTimeoutOrCancel(Future<?> future, int index) {
		try {
			future.get(20, TimeUnit.SECONDS);
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			logger.info("Canceling deletion of type {}.", index);
			future.cancel(true);
		}
	}
}
