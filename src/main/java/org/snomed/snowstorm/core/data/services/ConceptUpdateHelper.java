package org.snomed.snowstorm.core.data.services;

import ch.qos.logback.classic.Level;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.DomainEntity;
import io.kaicode.elasticvc.domain.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.config.SearchLanguagesConfiguration;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.repositories.*;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierReservedBlock;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.data.services.pojo.PersistedComponents;
import org.snomed.snowstorm.core.util.DescriptionHelper;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.snomed.snowstorm.core.data.domain.Concepts.inactivationIndicatorNames;

@Service
public class ConceptUpdateHelper extends ComponentService {

	@Autowired
	private ConceptRepository conceptRepository;

	@Autowired
	private DescriptionRepository descriptionRepository;

	@Autowired
	private RelationshipRepository relationshipRepository;

	@Autowired
	@Lazy
	private ReferenceSetMemberService memberService;

	@Autowired
	private ReferenceSetTypeRepository referenceSetTypeRepository;

	@Autowired
	private QueryConceptRepository queryConceptRepository;

	@Autowired
	private AxiomConversionService axiomConversionService;

	@Autowired
	private SearchLanguagesConfiguration searchLanguagesConfiguration;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private IdentifierService identifierService;

	@Autowired
	private ValidatorService validatorService;

	@Autowired
	private BranchService branchService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * Creates, updates or deletes a collection of concepts and their components in batch within an open commit. This method does not close the commit.
	 * @param newVersionConcepts	The set of concepts to persist.
	 * @param existingConceptsMap	The set of concept versions which already exist on this branch. If this is a rebase the parent components will be included if they
	 *                                 have not been overwritten by a newer version of the same component on this branch.
	 * @param existingConceptsFromParentMap	If this is a rebase commit this parameter must be the set of concept versions which already exist on the parent branch. These
	 *                                         components are used to preserve the latest release details when there is a newer version of a component in version control.
	 * @param commit				The commit in which to persist any changed components.
	 * @return	The set of components which have been persisted.
	 * @throws ServiceException	If there is a problem persisting the components to the store.
	 */
	PersistedComponents saveNewOrUpdatedConcepts(
			Collection<Concept> newVersionConcepts,
			Map<String, Concept> existingConceptsMap,
			Map<String, Concept> existingConceptsFromParentMap,
			Commit commit) throws ServiceException {

		final boolean rebaseConflictSave = commit.isRebase();

		validateConcepts(newVersionConcepts);

		// Grab branch metadata including values inherited from ancestor branches
		Metadata metadata = branchService.findBranchOrThrow(commit.getBranch().getPath(), true).getMetadata();
		String defaultModuleId = metadata.getString(Config.DEFAULT_MODULE_ID_KEY);
		String defaultNamespace = metadata.getString(Config.DEFAULT_NAMESPACE_KEY);
		final boolean contentAutomationsDisabled = BranchMetadataHelper.isContentAutomationsDisabledForCommit(commit);
		TimerUtil timerUtil = new TimerUtil("identifierService.reserveIdentifierBlock", Level.INFO, 1);
		IdentifierReservedBlock reservedIds = identifierService.reserveIdentifierBlock(newVersionConcepts, defaultNamespace);
		timerUtil.finish();

		// Assign identifier to new concepts before axiom conversion
		newVersionConcepts.stream().filter(concept -> concept.getConceptId() == null)
				.forEach(concept -> concept.setConceptId(reservedIds.getNextId(ComponentType.Concept).toString()));

		// Bulk convert axioms to OWLAxiom reference set members before persisting
		try {
			axiomConversionService.populateAxiomMembers(newVersionConcepts, commit.getBranch().getPath());
		} catch (ConversionException e) {
			throw new ServiceException("Failed to convert axiom to an OWL expression.", e);
		}

		// Create collections of components that will be written to store, including deletions
		List<Description> descriptionsToPersist = new ArrayList<>();
		List<Relationship> relationshipsToPersist = new ArrayList<>();
		List<ReferenceSetMember> refsetMembersToPersist = new ArrayList<>();

		for (Concept newVersionConcept : newVersionConcepts) {

			// Grab existing versions of this concept
			Concept existingConcept = existingConceptsMap.get(newVersionConcept.getConceptId());
			Concept existingConceptFromParent = existingConceptsFromParentMap == null ? null : existingConceptsFromParentMap.get(newVersionConcept.getConceptId());

			if (!contentAutomationsDisabled) {
				// Update the state of components automatically based on baked-in rules

				if (newVersionConcept.isActive()) {
					// Clear inactivation refsets
					newVersionConcept.setInactivationIndicator(null);
					newVersionConcept.setAssociationTargets(null);
				} else {
					// Make stated relationships and axioms inactive. We use the classification process to inactivate the inferred relationships.
					newVersionConcept.getRelationships().forEach(relationship -> {
						if (Concepts.STATED_RELATIONSHIP.equals(relationship.getCharacteristicTypeId())) {
							relationship.setActive(false);
						}
					});
					newVersionConcept.getAllOwlAxiomMembers().forEach(axiom -> axiom.setActive(false));
					newVersionConcept.getDescriptions().forEach(description -> {
						if (StringUtils.isEmpty(description.getInactivationIndicator())) {
							description.setInactivationIndicator(inactivationIndicatorNames.get(Concepts.CONCEPT_NON_CURRENT));
						}
					});
				}

				// Create or update concept inactivation indicator refset members based on the json inactivation map
				updateInactivationIndicator(newVersionConcept, existingConcept, existingConceptFromParent, refsetMembersToPersist, Concepts.CONCEPT_INACTIVATION_INDICATOR_REFERENCE_SET);

				// Create or update concept historical association refset members based on the json inactivation map
				updateAssociations(newVersionConcept, existingConcept, existingConceptFromParent, refsetMembersToPersist);

				for (Description description : newVersionConcept.getDescriptions()) {
					if (description.isActive()) {
						if (newVersionConcept.isActive()) {
							description.setInactivationIndicator(null);
						}
					} else {
						if (!description.getAcceptabilityMap().isEmpty()) {
							description.clearLanguageRefsetMembers();
							description.getAcceptabilityMap().clear();
						}
					}
				}
			}

			for (Description description : newVersionConcept.getDescriptions()) {
				description.setConceptId(newVersionConcept.getConceptId());
				if (description.getDescriptionId() == null) {
					description.setDescriptionId(reservedIds.getNextId(ComponentType.Description).toString());
				}

				final Description existingDescription = getExistingComponent(existingConcept, ConceptView::getDescriptions, description.getDescriptionId());
				final Description existingDescriptionFromParent = getExistingComponent(existingConcept, ConceptView::getDescriptions, description.getDescriptionId());

				final Map<String, Set<ReferenceSetMember>> existingMembersToMatch = new HashMap<>();
				if (existingDescription != null) {
					existingMembersToMatch.putAll(existingDescription.getLangRefsetMembersMap());
				}

				// Description inactivation indicator changes
				updateInactivationIndicator(description, existingDescription, existingDescriptionFromParent, refsetMembersToPersist,
						Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET);

				// Description association changes
				updateAssociations(description, existingDescription, existingDescriptionFromParent, refsetMembersToPersist);

				// Description acceptability / language reference set changes
				Set<ReferenceSetMember> newMembers = new HashSet<>();
				final Map<String, String> acceptabilityMap = description.getAcceptabilityMap();
				for (Map.Entry<String, String> acceptability : acceptabilityMap.entrySet()) {
					final String languageRefsetId = acceptability.getKey();
					final String acceptabilityId = Concepts.descriptionAcceptabilityNames.inverse().get(acceptability.getValue());
					if (acceptabilityId == null) {
						throw new IllegalArgumentException("Acceptability value not recognised '" + acceptability.getValue() + "'.");
					}

					final Set<ReferenceSetMember> existingMembers = existingMembersToMatch.get(languageRefsetId);
					ReferenceSetMember existingMember = existingMembers != null && !existingMembers.isEmpty() ? existingMembers.iterator().next() : null;
					ReferenceSetMember member;
					if (existingMember != null) {
						member = new ReferenceSetMember(existingMember.getMemberId(), null, true, existingMember.getModuleId(), languageRefsetId, description.getId());
						existingMembersToMatch.remove(languageRefsetId);
					} else {
						member = new ReferenceSetMember(description.getModuleId(), languageRefsetId, description.getId());
					}
					member.setAdditionalField("acceptabilityId", acceptabilityId);
					newMembers.add(member);
				}
				description.setLanguageRefsetMembers(newMembers);
			}

			// Apply relationship source ids
			newVersionConcept.getRelationships()
					.forEach(relationship -> relationship.setSourceId(newVersionConcept.getConceptId()));

			// Set new relationship ids
			newVersionConcept.getRelationships().stream()
					.filter(relationship -> relationship.getRelationshipId() == null)
					.forEach(relationship -> relationship.setRelationshipId(reservedIds.getNextId(ComponentType.Relationship).toString()));

			if (existingConcept != null || existingConceptFromParent != null) {
				// Existing concept
				newVersionConcept.setCreating(false);// May have been set true earlier during first save
				newVersionConcept.setChanged(existingConcept == null || newVersionConcept.isComponentChanged(existingConcept) || rebaseConflictSave);
				newVersionConcept.copyReleaseDetails(existingConcept, existingConceptFromParent);
				newVersionConcept.updateEffectiveTime();
			} else {
				// New concept
				newVersionConcept.setCreating(true);
				newVersionConcept.setChanged(true);
				newVersionConcept.clearReleaseDetails();
			}

			if (newVersionConcept.getModuleId() == null) {
				newVersionConcept.setModuleId(defaultModuleId);
			}

			// Descriptions
			markDeletionsAndUpdates(newVersionConcept, existingConcept, existingConceptFromParent, Concept::getDescriptions,
					defaultModuleId, descriptionsToPersist, rebaseConflictSave);

			// Relationships
			markDeletionsAndUpdates(newVersionConcept, existingConcept, existingConceptFromParent, Concept::getRelationships,
					defaultModuleId, relationshipsToPersist, rebaseConflictSave);

			// Axiom refset members
			markDeletionsAndUpdates(newVersionConcept, existingConcept, existingConceptFromParent, Concept::getAllOwlAxiomMembers,
					defaultModuleId, refsetMembersToPersist, rebaseConflictSave);

			for (Description description : newVersionConcept.getDescriptions()) {
				Description existingDescription = getExistingComponent(existingConcept, ConceptView::getDescriptions, description.getDescriptionId());
				Description existingDescriptionFromParent = getExistingComponent(existingConceptFromParent, ConceptView::getDescriptions, description.getDescriptionId());

				// Description language refset members
				markDeletionsAndUpdates(description, existingDescription, existingDescriptionFromParent, Description::getLangRefsetMembers,
						defaultModuleId, refsetMembersToPersist, rebaseConflictSave);
			}

			// Detach concept's components to ensure concept persisted without collections
			newVersionConcept.getDescriptions().clear();
			newVersionConcept.getRelationships().clear();
			newVersionConcept.getClassAxioms().clear();
			newVersionConcept.getGciAxioms().clear();
		}

		// TODO: Try saving all core component types at once - Elasticsearch likes multi-threaded writes.
		doSaveBatchConcepts(newVersionConcepts, commit);
		doSaveBatchDescriptions(descriptionsToPersist, commit);
		doSaveBatchRelationships(relationshipsToPersist, commit);

		memberService.doSaveBatchMembers(refsetMembersToPersist, commit);
		refsetMembersToPersist.addAll(doDeleteMembersWhereReferencedComponentDeleted(commit.getEntitiesDeleted(), commit));

		// Store assigned identifiers for registration with CIS
		identifierService.persistAssignedIdsForRegistration(reservedIds);

		return new PersistedComponents(newVersionConcepts, descriptionsToPersist, relationshipsToPersist, refsetMembersToPersist);
	}

	private <C extends SnomedComponent<?>, T extends SnomedComponent<?>> Collection<C> getExistingComponents(T existingConcept, Function<T, Collection<C>> getter) {
		if (existingConcept != null) {
			final Collection<C> collection = getter.apply(existingConcept);
			return collection != null ? collection : Collections.emptySet();
		}
		return Collections.emptySet();
	}

	private <C extends SnomedComponent<?>, T extends SnomedComponent<?>> C getExistingComponent(T existingConcept, Function<T, Collection<C>> getter, String componentId) {
		if (componentId == null) {
			return null;
		}
		return getExistingComponents(existingConcept, getter).stream().filter(item -> componentId.equals(item.getId())).findFirst().orElse(null);
	}

	private void validateConcepts(Collection<Concept> concepts) {
		validatorService.validate(concepts);
		for (Concept concept : concepts) {
			for (Axiom gciAxiom : Optional.ofNullable(concept.getGciAxioms()).orElse(Collections.emptySet())) {
				boolean parentFound = false;
				boolean attributeFound = false;
				for (Relationship relationship : gciAxiom.getRelationships()) {
					if (Concepts.ISA.equals(relationship.getTypeId())) {
						parentFound = true;
					} else {
						attributeFound = true;
					}
				}
				if (!parentFound || !attributeFound) {
					throw new IllegalArgumentException("The relationships of a GCI axiom must include at least one parent and one attribute.");
				}
			}
		}
	}

	private void updateAssociations(SnomedComponentWithAssociations newComponentVersion, SnomedComponentWithAssociations existingComponentVersion,
			SnomedComponentWithAssociations existingComponentVersionFromParent, List<ReferenceSetMember> refsetMembersToPersist) {

		Map<String, Set<String>> newVersionAssociations = newComponentVersion.getAssociationTargets();
		if (newVersionAssociations == null) {
			newVersionAssociations = new HashMap<>();
		}

		Map<String, Set<String>> membersRequired = new HashMap<>();
		for (Map.Entry<String, Set<String>> entry : newVersionAssociations.entrySet()) {
			final String refsetId = Concepts.historicalAssociationNames.inverse().get(entry.getKey());
			if (refsetId == null) {
				throw new IllegalArgumentException(newComponentVersion.getClass().getSimpleName() + " inactivation indicator not recognised '" + entry.getKey() + "'.");
			}
			membersRequired.computeIfAbsent(refsetId, id -> new HashSet<>()).addAll(entry.getValue());
		}

		List<ReferenceSetMember> activeSet = updateMetadataRefset(membersRequired, ReferenceSetMember.AssociationFields.TARGET_COMP_ID, existingComponentVersion, existingComponentVersionFromParent,
				SnomedComponentWithAssociations::getAssociationTargetMembers, newComponentVersion.getId(), newComponentVersion.getModuleId(), refsetMembersToPersist);
		activeSet.forEach(newComponentVersion::addAssociationTargetMember);
	}

	private void updateInactivationIndicator(SnomedComponentWithInactivationIndicator newComponent,
			SnomedComponentWithInactivationIndicator existingComponent,
			SnomedComponentWithInactivationIndicator existingConceptFromParent,
			Collection<ReferenceSetMember> refsetMembersToPersist,
			String indicatorReferenceSet) {

		String newIndicatorName = newComponent.getInactivationIndicator();
		final String newIndicatorId = newIndicatorName != null ? inactivationIndicatorNames.inverse().get(newIndicatorName) : null;
		if (newIndicatorName != null && newIndicatorId == null) {
			throw new IllegalArgumentException(newComponent.getClass().getSimpleName() + " inactivation indicator not recognised '" + newIndicatorName + "'.");
		}
		Map<String, Set<String>> membersRequired = new HashMap<>();
		if (newIndicatorId != null) {
			membersRequired.put(indicatorReferenceSet, Sets.newHashSet(newIndicatorId));
		}
		List<ReferenceSetMember> memberToKeep = updateMetadataRefset(membersRequired, ReferenceSetMember.AttributeValueFields.VALUE_ID, existingComponent, existingConceptFromParent, SnomedComponentWithInactivationIndicator::getInactivationIndicatorMembers,
				newComponent.getId(), newComponent.getModuleId(), refsetMembersToPersist);
		memberToKeep.forEach(newComponent::addInactivationIndicatorMember);
	}

	private <T> List<ReferenceSetMember> updateMetadataRefset(Map<String, Set<String>> membersRequired, String fieldName, T existingComponent, T existingConceptFromParent,
				Function<T, Collection<ReferenceSetMember>> getter, String refComponent, String moduleId, Collection<ReferenceSetMember> refsetMembersToPersist) {

		List<ReferenceSetMember> existingMembers = new ArrayList<>();
		if (existingComponent != null) {
			existingMembers.addAll(getter.apply(existingComponent));
		}
		if (existingConceptFromParent != null) {
			existingMembers.addAll(getter.apply(existingConceptFromParent));
		}
		existingMembers.sort(Comparator.comparing(ReferenceSetMember::getReleasedEffectiveTime, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(ReferenceSetMember::isActive));

		final List<ReferenceSetMember> toKeep = new ArrayList<>();
		final List<ReferenceSetMember> notNeeded = new ArrayList<>();

		// Find existing members to keep
		for (ReferenceSetMember existingMember : existingMembers) {
			final String refsetId = existingMember.getRefsetId();
			String value = existingMember.getAdditionalField(fieldName);
			if (membersRequired.containsKey(refsetId) && membersRequired.get(refsetId).contains(value)) {
				// Keep member
				toKeep.add(existingMember);
				membersRequired.get(refsetId).remove(value);
			} else {
				notNeeded.add(existingMember);
			}
		}

		List<ReferenceSetMember> toPersist = new ArrayList<>();

		Set<String> allIds = new HashSet<>();
		Set<String> duplicateIds = existingMembers.stream()
				.map(ReferenceSetMember::getMemberId)
				.filter(id -> !allIds.add(id))
				.collect(Collectors.toSet());

		for (ReferenceSetMember member : toKeep) {
			if (!member.isActive() || duplicateIds.contains(member.getMemberId())) {
				member.setActive(true);
				member.markChanged();
				toPersist.add(member);
				duplicateIds.remove(member.getMemberId());
			}
		}
		for (ReferenceSetMember member : notNeeded) {
			if ((member.isActive() || duplicateIds.contains(member.getMemberId())) &&
					toPersist.stream().map(ReferenceSetMember::getMemberId)
							.noneMatch(id -> id.equals(member.getMemberId()))) {

				member.setActive(false);
				member.markChanged();
				toPersist.add(member);
				duplicateIds.remove(member.getMemberId());
			}
		}

		// Create members as required
		for (Map.Entry<String, Set<String>> entry : membersRequired.entrySet()) {
			final String refsetId = entry.getKey();
			for (String value : entry.getValue()) {
				// Create new indicator
				ReferenceSetMember newIndicatorMember = new ReferenceSetMember(moduleId, refsetId, refComponent);
				newIndicatorMember.setAdditionalField(fieldName, value);
				newIndicatorMember.setChanged(true);
				toPersist.add(newIndicatorMember);
				toKeep.add(newIndicatorMember);
			}
		}

		refsetMembersToPersist.addAll(toPersist);
		return toKeep;
	}

	void doDeleteConcept(String path, Commit commit, Concept concept) {
		// Mark concept and components as deleted
		logger.info("Deleting concept {} on branch {} at timepoint {}", concept.getConceptId(), path, commit.getTimepoint());
		concept.markDeleted();
		Set<ReferenceSetMember> membersToDelete = new HashSet<>(concept.getAllOwlAxiomMembers());
		concept.getDescriptions().forEach(description -> {
			description.markDeleted();
			membersToDelete.addAll(description.getLangRefsetMembers());
			ReferenceSetMember inactivationIndicatorMember = description.getInactivationIndicatorMember();
			if (inactivationIndicatorMember != null) {
				membersToDelete.add(inactivationIndicatorMember);
			}
		});
		ReferenceSetMember inactivationIndicatorMember = concept.getInactivationIndicatorMember();
		if (inactivationIndicatorMember != null) {
			inactivationIndicatorMember.markDeleted();
		}
		membersToDelete.addAll(concept.getAssociationTargetMembers());
		concept.getRelationships().forEach(Relationship::markDeleted);

		MemberSearchRequest memberSearchRequest = new MemberSearchRequest();
		memberSearchRequest.referenceSet(Concepts.REFSET_DESCRIPTOR_REFSET);
		memberSearchRequest.referencedComponentId(concept.getId());
		List<ReferenceSetMember> membersInRefSet = memberService.findMembers(path, memberSearchRequest, PageRequest.of(0, 100)).getContent();
		if (!membersInRefSet.isEmpty()) {
			Set<String> ids = new HashSet<>();
			membersInRefSet.forEach(member -> ids.add(member.getId()));
			membersToDelete.addAll(membersInRefSet);

			logger.info("{} |{}| will be removed from {} |Reference set descriptor| on branch {}.", concept.getId(), concept.getFsn().getTerm(), Concepts.REFSET_DESCRIPTOR_REFSET, path);
			logger.debug("{} will be removed from {} |Reference set descriptor| on branch {}.", ids, Concepts.REFSET_DESCRIPTOR_REFSET, path);
		}

		// Persist deletion
		doSaveBatchConcepts(Sets.newHashSet(concept), commit);
		doSaveBatchDescriptions(concept.getDescriptions(), commit);
		membersToDelete.forEach(ReferenceSetMember::markDeleted);
		memberService.doSaveBatchMembers(membersToDelete, commit);
		doSaveBatchRelationships(concept.getRelationships(), commit);
	}


	/**
	 * Persists concept updates within commit.
	 */
	public void doSaveBatchConcepts(Collection<Concept> concepts, Commit commit) {
		doSaveBatchComponents(concepts, commit, "conceptId", conceptRepository);
	}

	/**
	 * Persists description updates within commit.
	 */
	public void doSaveBatchDescriptions(Collection<Description> descriptions, Commit commit) {
		Map<String, Set<Character>> charactersNotFoldedSets = searchLanguagesConfiguration.getCharactersNotFoldedSets();
		for (Description description : descriptions) {
			description.setTermFolded(DescriptionHelper.foldTerm(description.getTerm(),
					charactersNotFoldedSets.getOrDefault(description.getLanguageCode(), Collections.emptySet())));
		}
		doSaveBatchComponents(descriptions, commit, "descriptionId", descriptionRepository);
	}

	/**
	 * Persists relationships updates within commit.
	 */
	public void doSaveBatchRelationships(Collection<Relationship> relationships, Commit commit) {
		doSaveBatchComponents(relationships, commit, "relationshipId", relationshipRepository);
	}

	private void doSaveBatchReferenceSetType(Collection<ReferenceSetType> referenceSetTypes, Commit commit) {
		doSaveBatchComponents(referenceSetTypes, commit, ReferenceSetType.Fields.CONCEPT_ID, referenceSetTypeRepository);
	}

	private void doSaveBatchQueryConcept(Collection<QueryConcept> queryConcepts, Commit commit) {
		doSaveBatchComponents(queryConcepts, commit, QueryConcept.Fields.CONCEPT_ID_FORM, queryConceptRepository);
	}

	List<ReferenceSetMember> doDeleteMembersWhereReferencedComponentDeleted(Set<String> entityVersionsDeleted, Commit commit) {
		NativeSearchQuery query = new NativeSearchQueryBuilder()
				.withQuery(
						boolQuery()
								.must(versionControlHelper.getBranchCriteria(commit.getBranch()).getEntityBranchCriteria(ReferenceSetMember.class))
								.must(termsQuery("referencedComponentId", entityVersionsDeleted))
				).withPageable(LARGE_PAGE).build();

		List<ReferenceSetMember> membersToDelete = new ArrayList<>();
		try (SearchHitsIterator<ReferenceSetMember> stream = elasticsearchTemplate.searchForStream(query, ReferenceSetMember.class)) {
			stream.forEachRemaining(hit -> {
				ReferenceSetMember member = hit.getContent();
				member.markDeleted();
				membersToDelete.add(member);
			});
		}

		for (List<ReferenceSetMember> membersBatch : Iterables.partition(membersToDelete, 500)) {
			doSaveBatchComponents(membersBatch, ReferenceSetMember.class, commit);
		}
		return membersToDelete;
	}

	private <C extends SnomedComponent, T extends SnomedComponent<?>> void markDeletionsAndUpdates(T newConcept, T existingConcept, T existingConceptFromParent,
			Function<T, Collection<C>> getter, String defaultModuleId, Collection<C> componentsToPersist, boolean rebase) {

		final Collection<C> newComponents = getExistingComponents(newConcept, getter);
		final Collection<C> existingComponents = getExistingComponents(existingConcept, getter);
		final Collection<C> existingComponentsFromParent = getExistingComponents(existingConceptFromParent, getter);

		final Map<String, C> existingComponentMap = existingComponents.stream().collect(Collectors.toMap(DomainEntity::getId, Function.identity()));
		final Map<String, C> rebaseParentExistingComponentMap = existingComponentsFromParent.stream().collect(Collectors.toMap(DomainEntity::getId, Function.identity()));

		// Mark updates
		for (C newComponent : newComponents) {
			final C existingComponent = existingComponentMap.get(newComponent.getId());

			newComponent.setChanged(existingComponent == null || newComponent.isComponentChanged(existingComponent) || rebase);
			if (existingComponent != null) {
				newComponent.setCreating(false);// May have been set true earlier
				newComponent.copyReleaseDetails(existingComponent, rebaseParentExistingComponentMap.get(newComponent.getId()));
				newComponent.updateEffectiveTime();
			} else {
				newComponent.setCreating(true);
				newComponent.clearReleaseDetails();
			}

			// Trying Concept module in attempt to restore effective time for the case
			// where content has changed and then been reverted.
			if (shouldRestoreDefaultModuleId(defaultModuleId, existingComponent, newComponent)) {
				logger.trace("Setting module of {} to be same as Concept.", newComponent.getId());
				newComponent.setModuleId(newConcept.getModuleId());
				newComponent.updateEffectiveTime();
				if (newComponent.getEffectiveTime() == null) {
					logger.trace("Setting module of {} to be same as branch default.", newComponent.getId());
					newComponent.setModuleId(defaultModuleId);
				}
			}
		}
		componentsToPersist.addAll(newComponents);// All added but only those with changed or deleted flag will be written to store.

		// Mark deletions on existing versions from this branch (and the parent branch if rebasing)
		Set<String> uniqueIds = new HashSet<>();
		existingComponents.forEach(existingComponent -> {
			if (!newComponents.contains(existingComponent)) {
				if (uniqueIds.add(existingComponent.getId()) && existingComponent.isReleased()) {
					C existingParentComponent = rebaseParentExistingComponentMap.get(existingComponent.getId());
					if (existingComponent.isActive() ||
							(existingParentComponent != null && !Objects.equals(existingComponent.getReleasedEffectiveTime(), existingParentComponent.getReleasedEffectiveTime()))) {
						existingComponent.setActive(false);
						existingComponent.setChanged(true);
						existingComponent.copyReleaseDetails(existingComponent, existingParentComponent);
						existingComponent.updateEffectiveTime();
						componentsToPersist.add(existingComponent);
					}
				} else {
					existingComponent.markDeleted();
					componentsToPersist.add(existingComponent);
				}
			}
		});
	}

	private <C extends SnomedComponent<?>> boolean shouldRestoreDefaultModuleId(String defaultModuleId, C existingComponent, C newComponent) {
		// Can't restore if nothing to restore to
		if (defaultModuleId == null) {
			return false;
		}

		// If modified anything other than Description, restore
		if (!(existingComponent instanceof Description)) {
			return true;
		}

		Description existingDescription = (Description) existingComponent;
		Description newDescription = (Description) newComponent;

		// Has the active state changed?
		boolean existingActive = existingDescription.isActive();
		boolean newActive = newDescription.isActive();
		boolean differentActive = existingActive != newActive;
		if (differentActive) {
			return true;
		}

		// Has the term changed?
		String existingTerm = existingDescription.getTerm();
		String newTerm = newDescription.getTerm();
		boolean differentTerm = existingTerm != null && !existingTerm.equals(newTerm);
		if (differentTerm) {
			return true;
		}

		// Has the type changed?
		String existingType = existingDescription.getType();
		String newType = newDescription.getType();
		boolean differentType = existingType != null && !existingType.equals(newType);
		if (differentType) {
			return true;
		}

		// Has the case significance changed?
		String existingCaseSignificance = existingDescription.getCaseSignificanceId();
		String newCaseSignificance = newDescription.getCaseSignificanceId();
		boolean differentCaseSignificance = existingCaseSignificance != null && !existingCaseSignificance.equals(newCaseSignificance);
		if (differentCaseSignificance) {
			return true;
		}

		return false;
	}

	@SuppressWarnings("unchecked")
	<T extends DomainEntity> void doSaveBatchComponents(Collection<T> components, Class<T> type, Commit commit) {
		if (type.equals(Concept.class)) {
			doSaveBatchConcepts((Collection<Concept>) components, commit);
		} else if (type.equals(Description.class)) {
			doSaveBatchDescriptions((Collection<Description>) components, commit);
		} else if (type.equals(Relationship.class)) {
			doSaveBatchRelationships((Collection<Relationship>) components, commit);
		} else if (type.equals(ReferenceSetMember.class)) {
			memberService.doSaveBatchMembers((Collection<ReferenceSetMember>) components, commit);
		} else if (type.equals(ReferenceSetType.class)) {
			doSaveBatchReferenceSetType((Collection<ReferenceSetType>) components, commit);
		} else if (type.equals(QueryConcept.class)) {
			doSaveBatchQueryConcept((Collection<QueryConcept>) components, commit);
		} else {
			throw new IllegalArgumentException("DomainEntity type " + type + " not recognised");
		}
	}

	public ElasticsearchOperations getElasticsearchTemplate() {
		return elasticsearchTemplate;
	}

	public VersionControlHelper getVersionControlHelper() {
		return versionControlHelper;
	}

}
