package com.kaicube.snomed.elasticsnomed.services;

import com.google.common.collect.Sets;
import com.kaicube.elasticversioncontrol.api.BranchService;
import com.kaicube.elasticversioncontrol.api.ComponentService;
import com.kaicube.elasticversioncontrol.api.VersionControlHelper;
import com.kaicube.elasticversioncontrol.domain.Branch;
import com.kaicube.elasticversioncontrol.domain.Commit;
import com.kaicube.elasticversioncontrol.domain.DomainEntity;
import com.kaicube.snomed.elasticsnomed.domain.*;
import com.kaicube.snomed.elasticsnomed.repositories.ConceptRepository;
import com.kaicube.snomed.elasticsnomed.repositories.DescriptionRepository;
import com.kaicube.snomed.elasticsnomed.repositories.ReferenceSetMemberRepository;
import com.kaicube.snomed.elasticsnomed.repositories.RelationshipRepository;
import com.kaicube.snomed.elasticsnomed.util.TimerUtil;
import org.apache.log4j.Level;
import org.elasticsearch.common.Strings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
	private BranchService branchService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchTemplate elasticsearchTemplate;

	@Autowired
	private QueryIndexService queryIndexService;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public Concept find(String id, String path) {
		final Page<Concept> concepts = doFind(Collections.singleton(id), path, new PageRequest(0, 10));
		if (concepts.getTotalElements() > 1) {
			logger.error("Found more than one concept {}", concepts.getContent());
			throw new IllegalStateException("More than one concept found for id " + id + " on branch " + path);
		}
		Concept concept = concepts.getTotalElements() == 0 ? null : concepts.iterator().next();
		logger.info("Find id:{}, path:{} found:{}", id, path, concept);
		return concept;
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

	public Collection<ConceptMini> findConceptChildrenInferred(String conceptId, String path) {
		final QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(path);

		// Gather inferred children ids
		final Set<String> childrenIds = new HashSet<>();
		try (final CloseableIterator<Relationship> relationshipStream = openRelationshipStream(branchCriteria, termQuery("destinationId", conceptId))) {
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
			conceptStream.forEachRemaining(concept -> conceptMiniMap.put(concept.getConceptId(), new ConceptMini(concept).setLeafInferred(true)));
		}

		// Find inferred children of the inferred children to set the isLeafInferred flag
		try (final CloseableIterator<Relationship> relationshipStream = openRelationshipStream(branchCriteria, termsQuery("destinationId", childrenIds))) {
			relationshipStream.forEachRemaining(relationship -> conceptMiniMap.get(relationship.getDestinationId()).setLeafInferred(false));
		}

		// Fetch descriptions and Lang refsets
		fetchDescriptions(branchCriteria, null, conceptMiniMap, null);

		return conceptMiniMap.values();
	}

	private CloseableIterator<Relationship> openRelationshipStream(QueryBuilder branchCriteria, QueryBuilder destinationCriteria) {
		return elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria)
						.must(termQuery("active", true))
						.must(termQuery("typeId", Concepts.ISA))
						.must(destinationCriteria)
						.must(termQuery("characteristicTypeId", Concepts.INFERRED_RELATIONSHIP))
				)
				.withPageable(LARGE_PAGE)
				.build(), Relationship.class
		);
	}

	private Page<Concept> doFind(Collection<String> ids, String path, PageRequest pageRequest) {
		final TimerUtil timer = new TimerUtil("Find concept", Level.DEBUG);
		final QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(path);
		timer.checkpoint("get branch criteria");

		final BoolQueryBuilder builder = boolQuery()
				.must(branchCriteria);
		if (ids != null && !ids.isEmpty()) {
			builder.must(termsQuery("conceptId", ids));
		}

		final NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(builder)
				.withPageable(pageRequest);

		final Page<Concept> concepts = elasticsearchTemplate.queryForPage(queryBuilder.build(), Concept.class);
		timer.checkpoint("find concept");

		Map<String, Concept> conceptIdMap = new HashMap<>();
		for (Concept concept : concepts) {
			conceptIdMap.put(concept.getConceptId(), concept);
			concept.getDescriptions().clear();
			concept.getRelationships().clear();
		}

		Map<String, ConceptMini> conceptMiniMap = new HashMap<>();

		// Fetch Relationships
		queryBuilder.withQuery(boolQuery()
				.must(termsQuery("sourceId", conceptIdMap.keySet()))
				.must(branchCriteria))
				.withPageable(LARGE_PAGE);

		// Join Relationships
		try (final CloseableIterator<Relationship> relationships = elasticsearchTemplate.stream(queryBuilder.build(), Relationship.class)) {
			relationships.forEachRemaining(relationship -> {
				conceptIdMap.get(relationship.getSourceId()).addRelationship(relationship);
				relationship.setType(getConceptMini(conceptMiniMap, relationship.getTypeId()));
				relationship.setTarget(getConceptMini(conceptMiniMap, relationship.getDestinationId()));
			});
		}
		timer.checkpoint("get relationships");

		// Fetch ConceptMini definition statuses
		queryBuilder.withQuery(boolQuery()
				.must(termsQuery("conceptId", conceptMiniMap.keySet()))
				.must(branchCriteria))
				.withPageable(LARGE_PAGE);
		try (final CloseableIterator<Concept> conceptsForMini = elasticsearchTemplate.stream(queryBuilder.build(), Concept.class)) {
			conceptsForMini.forEachRemaining(concept ->
					conceptMiniMap.get(concept.getConceptId()).setDefinitionStatusId(concept.getDefinitionStatusId()));
		}
		timer.checkpoint("get relationship def status");

		fetchDescriptions(branchCriteria, conceptIdMap, conceptMiniMap, timer);
		timer.finish();

		return concepts;
	}

	private void fetchDescriptions(QueryBuilder branchCriteria, Map<String, Concept> conceptIdMap, Map<String, ConceptMini> conceptMiniMap, TimerUtil timer) {
		final NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();

		// Fetch Descriptions
		final Set<String> allConceptIds = new HashSet<>();
		if (conceptIdMap != null) {
			allConceptIds.addAll(conceptIdMap.keySet());
		}
		if (conceptMiniMap != null) {
			allConceptIds.addAll(conceptMiniMap.keySet());
		}
		if (allConceptIds.isEmpty()) {
			return;
		}

		queryBuilder.withQuery(boolQuery()
				.must(branchCriteria)
				.must(termsQuery("conceptId", allConceptIds)))
				.withPageable(LARGE_PAGE);
		Map<String, Description> descriptionIdMap = new HashMap<>();
		// Join Descriptions
		try (final CloseableIterator<Description> descriptions = elasticsearchTemplate.stream(queryBuilder.build(), Description.class)) {
			descriptions.forEachRemaining(description -> {
				descriptionIdMap.put(description.getDescriptionId(), description);
				final String descriptionConceptId = description.getConceptId();
				if (conceptIdMap != null) {
					final Concept concept = conceptIdMap.get(descriptionConceptId);
					if (concept != null) {
						concept.addDescription(description);
					}
				}
				if (conceptMiniMap != null) {
					final ConceptMini conceptMini = conceptMiniMap.get(descriptionConceptId);
					if (conceptMini != null && Concepts.FSN.equals(description.getTypeId()) && description.isActive()) {
						conceptMini.addActiveFsn(description);
					}
				}
			});
		}
		if (timer != null) timer.checkpoint("get descriptions");

		// Fetch Lang Refset Members
		queryBuilder.withQuery(boolQuery()
				.must(branchCriteria)
				.must(termsQuery("referencedComponentId", descriptionIdMap.keySet())))
				.withPageable(LARGE_PAGE);
		// Join Lang Refset Members
		try (final CloseableIterator<LanguageReferenceSetMember> langRefsetMembers = elasticsearchTemplate.stream(queryBuilder.build(), LanguageReferenceSetMember.class)) {
			langRefsetMembers.forEachRemaining(langRefsetMember ->
					descriptionIdMap.get(langRefsetMember.getReferencedComponentId()).addLanguageRefsetMember(langRefsetMember));
		}
		if (timer != null) timer.checkpoint("get lang refset");
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

	public Page<Description> findDescriptions(String path, String term, PageRequest pageRequest) {
		final QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(path);

		final BoolQueryBuilder builder = boolQuery()
				.must(branchCriteria);
		if (!Strings.isNullOrEmpty(term)) {
			builder.must(simpleQueryStringQuery(term).field("term"));
		}

		final NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(builder)
				.withSort(SortBuilders.scoreSort())
				.withPageable(pageRequest);

		final NativeSearchQuery build = queryBuilder.build();
		return elasticsearchTemplate.queryForPage(build, Description.class);
	}

	public Collection<String> listChangedConceptIds(String path) {
		final QueryBuilder branchCriteria = versionControlHelper.getChangesOnBranchCriteria(path);
		final List<String> conceptIds = new ArrayList<>();
		try (final CloseableIterator<Concept> stream = elasticsearchTemplate.stream(
				new NativeSearchQueryBuilder()
						.withQuery(boolQuery().must(branchCriteria))
						.withPageable(LARGE_PAGE)
						.build(), Concept.class)) {
			stream.forEachRemaining(concept -> conceptIds.add(concept.getConceptId()));
		}
		return conceptIds;
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
	}

	public Concept create(Concept conceptVersion, String path) {
		final Branch branch = branchService.findBranchOrThrow(path);
		if (conceptVersion.getConceptId() != null && exists(conceptVersion.getConceptId(), path)) {
			throw new IllegalArgumentException("Concept '" + conceptVersion.getConceptId() + "' already exists on branch '" + path + "'.");
		}
		return doSave(conceptVersion, branch);
	}

	public ReferenceSetMember create(ReferenceSetMember referenceSetMember, String path) {
		final Branch branch = branchService.findBranchOrThrow(path);
		// TODO Check if already exists
		referenceSetMember.setChanged(true);
		return doSave(referenceSetMember, branch);

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

	public Iterable<Concept> update(List<Concept> concepts, String path) {
		final Branch branch = branchService.findBranchOrThrow(path);
		return doSave(concepts, branch);
	}

	private <C extends SnomedComponent> boolean markDeletionsAndUpdates(Set<C> newComponents, Set<C> existingComponents) {
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
			newComponent.setChanged(newComponent.isComponentChanged(existingComponent));
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

	public Iterable<Concept> doSaveBatchConceptsAndComponents(Collection<Concept> concepts, Commit commit) {
		final List<String> conceptIds = concepts.stream().filter(concept -> concept.getConceptId() != null).map(Concept::getConceptId).collect(Collectors.toList());
		final Map<String, Concept> existingConceptsMap = new HashMap<>();
		if (!conceptIds.isEmpty()) {
			final List<Concept> existingConcepts = doFind(conceptIds, commit.getBranch().getFatPath(), new PageRequest(0, conceptIds.size())).getContent();
			for (Concept existingConcept : existingConcepts) {
				existingConceptsMap.put(existingConcept.getConceptId(), existingConcept);
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
				concept.getRelationships().stream()
						.forEach(relationship -> relationship.setActive(false));
			}

			// Mark changed concepts as changed
			if (existingConcept != null) {
				concept.setChanged(concept.isComponentChanged(existingConcept));
				concept.copyReleaseDetails(existingConcept);
				concept.updateEffectiveTime();

				markDeletionsAndUpdates(concept.getDescriptions(), existingConcept.getDescriptions());
				markDeletionsAndUpdates(concept.getRelationships(), existingConcept.getRelationships());
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
				final Description existingDescription = existingDescriptions.get(description.getDescriptionId());
				final Map<String, LanguageReferenceSetMember> existingMembersToMatch = new HashMap<>();
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
					final LanguageReferenceSetMember existingMember = existingMembersToMatch.get(languageRefsetId);
					if (existingMember != null) {
						final LanguageReferenceSetMember member = new LanguageReferenceSetMember(existingMember.getMemberId(), null, true,
								existingMember.getModuleId(), languageRefsetId, description.getId(), acceptabilityId);

						if (member.isComponentChanged(existingMember)) {
							member.setChanged(true);
							member.copyReleaseDetails(existingMember);
							member.updateEffectiveTime();
							langRefsetMembersToPersist.add(member);
						}

						existingMembersToMatch.remove(languageRefsetId);
					} else {
						final LanguageReferenceSetMember member = new LanguageReferenceSetMember(languageRefsetId, description.getId(), acceptabilityId);
						member.setChanged(true);
						member.clearReleaseDetails();
						langRefsetMembersToPersist.add(member);
					}
				}
				for (LanguageReferenceSetMember leftoverMember : existingMembersToMatch.values()) {
					// TODO: make inactive if released
					leftoverMember.markDeleted();
					langRefsetMembersToPersist.add(leftoverMember);
				}
			}
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
		doSaveBatchDescriptions(descriptionsToPersist, commit);
		doSaveBatchMembers(langRefsetMembersToPersist, commit);
		doSaveBatchRelationships(relationshipsToPersist, commit);

		Map<String, Concept> conceptMap = new HashMap<>();
		for (Concept concept : concepts) {
			conceptMap.put(concept.getConceptId(), concept);
		}
		for (Description description : descriptionsToPersist) {
			conceptMap.get(description.getConceptId()).addDescription(description);
		}
		for (Relationship relationship : relationshipsToPersist) {
			conceptMap.get(relationship.getSourceId()).addRelationship(relationship);
		}

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

	public Iterable<Concept> doSaveBatchConcepts(Collection<Concept> concepts, Commit commit) {
		return doSaveBatchComponents(concepts, commit, "conceptId", conceptRepository);
	}

	public void doSaveBatchDescriptions(Collection<Description> descriptions, Commit commit) {
		doSaveBatchComponents(descriptions, commit, "descriptionId", descriptionRepository);
	}

	public void doSaveBatchRelationships(Collection<Relationship> relationships, Commit commit) {
		doSaveBatchComponents(relationships, commit, "relationshipId", relationshipRepository);
	}

	public Iterable<ReferenceSetMember> doSaveBatchMembers(Collection<ReferenceSetMember> members, Commit commit) {
		return doSaveBatchComponents(members, commit, "memberId", referenceSetMemberRepository);
	}

	public void createTransitiveClosureForEveryConcept(String branch) {
		final Commit commit = branchService.openCommit(branch);
		postProcess(commit);
		branchService.completeCommit(commit);
	}

	public void postProcess(Commit commit) {
		queryIndexService.createTransitiveClosureForEveryConcept(commit);
	}

	public void deleteAll() {
		conceptRepository.deleteAll();
		descriptionRepository.deleteAll();
		relationshipRepository.deleteAll();
		referenceSetMemberRepository.deleteAll();
		queryIndexService.deleteAll();
	}
}
