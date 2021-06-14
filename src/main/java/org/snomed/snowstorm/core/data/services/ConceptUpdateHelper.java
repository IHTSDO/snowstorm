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
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.config.SearchLanguagesConfiguration;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.repositories.*;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierReservedBlock;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.data.services.pojo.PersistedComponents;
import org.snomed.snowstorm.core.util.DescriptionHelper;
import org.snomed.snowstorm.core.util.SetUtils;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.snomed.snowstorm.core.data.services.ConceptService.DISABLE_CONTENT_AUTOMATIONS_METADATA_KEY;

@Service
public class ConceptUpdateHelper extends ComponentService {

	public static final Comparator<ReferenceSetMember> REFERENCE_SET_MEMBER_COMPARATOR_BY_RELEASED = Comparator.comparing(ReferenceSetMember::isReleased).thenComparing(ReferenceSetMember::isActive);
	@Autowired
	private ConceptRepository conceptRepository;

	@Autowired
	private DescriptionRepository descriptionRepository;

	@Autowired
	private RelationshipRepository relationshipRepository;

	@Autowired
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
		boolean enableContentAutomations = !Boolean.parseBoolean(metadata.getString(DISABLE_CONTENT_AUTOMATIONS_METADATA_KEY));
		TimerUtil timerUtil = new TimerUtil("identifierService.reserveIdentifierBlock", Level.INFO, 1);
		IdentifierReservedBlock reservedIds = identifierService.reserveIdentifierBlock(newVersionConcepts, defaultNamespace);
		timerUtil.finish();

		// Assign identifier to new concepts before axiom conversion
		newVersionConcepts.stream().filter(concept -> concept.getConceptId() == null)
				.forEach(concept -> concept.setConceptId(reservedIds.getNextId(ComponentType.Concept).toString()));

		// Bulk convert axioms to OWLAxiom reference set members before persisting
		axiomConversionService.populateAxiomMembers(newVersionConcepts, commit.getBranch().getPath());

		// Create collections of components that will be written to store, including deletions
		List<Description> descriptionsToPersist = new ArrayList<>();
		List<Relationship> relationshipsToPersist = new ArrayList<>();
		List<ReferenceSetMember> refsetMembersToPersist = new ArrayList<>();

		for (Concept newVersionConcept : newVersionConcepts) {

			// Grab existing versions of this concept
			Concept existingConcept = existingConceptsMap.get(newVersionConcept.getConceptId());
			Concept existingConceptFromParent = existingConceptsFromParentMap == null ? null : existingConceptsFromParentMap.get(newVersionConcept.getConceptId());

			if (enableContentAutomations) {
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
				updateInactivationIndicator(newVersionConcept, existingConcept, refsetMembersToPersist, Concepts.CONCEPT_INACTIVATION_INDICATOR_REFERENCE_SET);

				// Create or update concept historical association refset members based on the json inactivation map
				updateAssociations(newVersionConcept, existingConcept, refsetMembersToPersist);

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
				
				final Map<String, Set<ReferenceSetMember>> existingMembersToMatch = new HashMap<>();
				if (existingDescription != null) {
					existingMembersToMatch.putAll(existingDescription.getLangRefsetMembersMap());
				}

				// Description inactivation indicator changes
				updateInactivationIndicator(description, existingDescription, refsetMembersToPersist, Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET);

				// Description association changes
				updateAssociations(description, existingDescription, refsetMembersToPersist);

				// Description acceptability / language reference set changes
				Set<ReferenceSetMember> newMembers = new HashSet<>();
				for (Map.Entry<String, String> acceptability : description.getAcceptabilityMap().entrySet()) {
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

			markDeletionsAndUpdates(newVersionConcept, existingConcept, existingConceptFromParent, Concept::getDescriptions, defaultModuleId, descriptionsToPersist, rebaseConflictSave);
			markDeletionsAndUpdates(newVersionConcept, existingConcept, existingConceptFromParent, Concept::getRelationships, defaultModuleId, relationshipsToPersist, rebaseConflictSave);
			markDeletionsAndUpdates(newVersionConcept, existingConcept, existingConceptFromParent, Concept::getAllOwlAxiomMembers, defaultModuleId, refsetMembersToPersist, rebaseConflictSave);

			for (Description description : newVersionConcept.getDescriptions()) {
				Description existingDescription = getExistingComponent(existingConcept, ConceptView::getDescriptions, description.getDescriptionId());
				Description existingDescriptionFromParent = getExistingComponent(existingConceptFromParent, ConceptView::getDescriptions, description.getDescriptionId());

				markDeletionsAndUpdates(description, existingDescription, existingDescriptionFromParent, Description::getLangRefsetMembers, defaultModuleId, refsetMembersToPersist, rebaseConflictSave);
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
		doDeleteMembersWhereReferencedComponentDeleted(commit.getEntitiesDeleted(), commit);

		// Store assigned identifiers for registration with CIS
		identifierService.persistAssignedIdsForRegistration(reservedIds);

		return new PersistedComponents(newVersionConcepts, descriptionsToPersist, relationshipsToPersist, refsetMembersToPersist);
	}

	private <C extends SnomedComponent<?>, T extends SnomedComponent<?>> Collection<C> getExistingComponents(T existingConcept, Function<T, Collection<C>> getter) {
		if (existingConcept != null) {
			return getter.apply(existingConcept);
		}
		return Collections.emptySet();
	}

	private <C extends SnomedComponent<?>, T extends SnomedComponent<?>> C getExistingComponent(T existingConcept, Function<T, Collection<C>> getter, String componentId) {
		if (componentId == null) {
			return null;
		}
		return getExistingComponents(existingConcept, getter).stream().filter(item -> componentId.equals(item.getId())).findFirst().orElse(null);
	}

	private <T extends SnomedComponent<?>> void applyDefaultModule(Collection<T> components, String defaultModuleId) {
		for (T component : components) {
			if (component.getEffectiveTime() == null) {
				component.setModuleId(defaultModuleId);
				component.updateEffectiveTime();
			}
		}
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

	private void updateAssociations(SnomedComponentWithAssociations newComponentVersion, SnomedComponentWithAssociations existingComponentVersion, List<ReferenceSetMember> refsetMembersToPersist) {

		Map<String, Set<String>> newVersionAssociations = newComponentVersion.getAssociationTargets();
		if (newVersionAssociations == null) {
			newVersionAssociations = new HashMap<>();
		}
		final Collection<ReferenceSetMember> newVersionMembers = newComponentVersion.getAssociationTargetMembers();
		Collection<ReferenceSetMember> existingVersionMembers = existingComponentVersion != null ? existingComponentVersion.getAssociationTargetMembers() : Collections.emptySet();
		if (existingVersionMembers == null) {
			existingVersionMembers = Collections.emptySet();
		}

		// New component version doesn't have refset members joined, it may have come from the REST API
		// Attempt to match existing members to the association target key/value map
		// Inactivate members which are no longer needed
		// Create members which are in the key/value map but do not yet exist
		Set<ReferenceSetMember> membersToKeep = new HashSet<>();
		Set<ReferenceSetMember> membersToCreate = new HashSet<>();
		Set<ReferenceSetMember> membersToRetire = new HashSet<>();
		for (String associationTypeName : newVersionAssociations.keySet()) {
			String associationRefsetId = Concepts.historicalAssociationNames.inverse().get(associationTypeName);
			for (String associationValue : newVersionAssociations.get(associationTypeName)) {

				ReferenceSetMember bestMember = getBestRefsetMember(associationRefsetId, ReferenceSetMember.AssociationFields.TARGET_COMP_ID, associationValue, newVersionMembers, existingVersionMembers);
				if (bestMember != null) {
					// Keep
					membersToKeep.add(bestMember);
				} else {
					// Create new
					bestMember = new ReferenceSetMember(newComponentVersion.getModuleId(), associationRefsetId, newComponentVersion.getId());
					bestMember.setAdditionalField(ReferenceSetMember.AssociationFields.TARGET_COMP_ID, associationValue);
					membersToCreate.add(bestMember);
				}
				newComponentVersion.addAssociationTargetMember(bestMember);
			}
		}

		List<ReferenceSetMember> toPersist = new ArrayList<>();

		// Persist new
		membersToCreate.forEach(member -> {
			member.markChanged();
			toPersist.add(member);
		});

		// Persist winners
		membersToKeep.forEach(member -> {
			if (!member.isActive()) {
				member.setActive(true);
				member.markChanged();
				toPersist.add(member);
			}
		});

		ensureAnyDuplicateMembersArePersisted(existingVersionMembers, toPersist);

		// Retire all others
		membersToRetire.addAll(SetUtils.remainder(newVersionMembers, membersToKeep));
		membersToRetire.addAll(SetUtils.remainder(existingVersionMembers, membersToKeep));
		membersToRetire.forEach(member -> {
			if (member.isActive()) {
				member.setActive(false);
				member.markChanged();
				toPersist.add(member);
			}
		});

		refsetMembersToPersist.addAll(toPersist);
	}

	// Identify duplicate rows for the same refset member and make sure one is persisted to overwrite the other.
	private void ensureAnyDuplicateMembersArePersisted(Collection<ReferenceSetMember> members, List<ReferenceSetMember> toPersist) {
		Set<String> memberIds = new HashSet<>();
		for (ReferenceSetMember member : members) {
			if (!memberIds.add(member.getId())) {
				if (toPersist.stream().noneMatch(m -> m.getId().equals(member.getId()))) {
					member.markChanged();
					toPersist.add(member);
				}
			}
		}
	}

	private ReferenceSetMember getBestRefsetMember(String refsetId, String additionalFieldKey, String additionalFieldValue, Collection<ReferenceSetMember> newVersionMembers, Collection<ReferenceSetMember> existingVersionMembers) {
		ReferenceSetMember bestMember = null;
		if (existingVersionMembers != null) {
			bestMember = getBestRefsetMemberInSetOrKeep(refsetId, additionalFieldKey, additionalFieldValue, existingVersionMembers, null);
		}
		if (newVersionMembers != null) {
			bestMember = getBestRefsetMemberInSetOrKeep(refsetId, additionalFieldKey, additionalFieldValue, newVersionMembers, bestMember);
		}
		return bestMember;
	}

	private ReferenceSetMember getBestRefsetMemberInSetOrKeep(String refsetId, String additionalFieldKey, String requiredValue, Collection<ReferenceSetMember> members, ReferenceSetMember candidate) {
		for (ReferenceSetMember newVersionMember : members) {
			final String actualValue = newVersionMember.getAdditionalField(additionalFieldKey);
			if (refsetId.equals(newVersionMember.getRefsetId()) && requiredValue.equals(actualValue)) {
				if (candidate == null) {
					candidate = newVersionMember;
				} else {
					// only replace candidate if it is stronger in terms released and active flags
					if (REFERENCE_SET_MEMBER_COMPARATOR_BY_RELEASED.compare(candidate, newVersionMember) < 0) {
						candidate = newVersionMember;
					}
				}
			}
		}
		return candidate;
	}

	private void updateInactivationIndicator(SnomedComponentWithInactivationIndicator newComponent,
			SnomedComponentWithInactivationIndicator existingComponent,
			Collection<ReferenceSetMember> refsetMembersToPersist,
			String indicatorReferenceSet) {

		String newIndicatorName = newComponent.getInactivationIndicator();
		String newIndicatorId = null;
		if (newIndicatorName != null) {
			newIndicatorId = inactivationIndicatorNames.inverse().get(newIndicatorName);
			if (newIndicatorId == null) {
				throw new IllegalArgumentException(newComponent.getClass().getSimpleName() + " inactivation indicator not recognised '" + newIndicatorName + "'.");
			}
		}

		ReferenceSetMember newMember = newComponent.getInactivationIndicatorMember();
		ReferenceSetMember matchingExistingMember = null;
		List<ReferenceSetMember> toPersist = new ArrayList<>();
		if (existingComponent != null) {
			for (ReferenceSetMember existingIndicatorMember : existingComponent.getInactivationIndicatorMembers()) {
				if (matchingExistingMember == null && existingIndicatorMember.getAdditionalField("valueId").equals(newIndicatorId) &&
						(newMember == null || existingIndicatorMember.getId().equals(newMember.getId()))) {
					// Keep member
					if (!existingIndicatorMember.isActive()) {
						existingIndicatorMember.setActive(true);
						existingIndicatorMember.markChanged();
						toPersist.add(existingIndicatorMember);
					}
					matchingExistingMember = existingIndicatorMember;
				} else {
					// Remove member
					if (existingIndicatorMember.isActive()) {
						existingIndicatorMember.setActive(false);
						existingIndicatorMember.markChanged();
						toPersist.add(existingIndicatorMember);
					}
				}
			}
			ensureAnyDuplicateMembersArePersisted(existingComponent.getInactivationIndicatorMembers(), toPersist);
		}

		if (newIndicatorId != null && matchingExistingMember == null) {
			// Create new indicator
			ReferenceSetMember newIndicatorMember = new ReferenceSetMember(newComponent.getModuleId(), indicatorReferenceSet, newComponent.getId());
			newIndicatorMember.setAdditionalField("valueId", newIndicatorId);
			newIndicatorMember.setChanged(true);
			toPersist.add(newIndicatorMember);
			newComponent.getInactivationIndicatorMembers().clear();
			newComponent.addInactivationIndicatorMember(newIndicatorMember);
		}
		refsetMembersToPersist.addAll(toPersist);
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
		Collection<ReferenceSetMember> associationTargetMembers = concept.getAssociationTargetMembers();
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

	void doDeleteMembersWhereReferencedComponentDeleted(Set<String> entityVersionsDeleted, Commit commit) {
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

			// Trying Concept module in attempt to restore effective time for the case
			// where content has changed and then been reverted.
			if (defaultModuleId != null) {
				newComponent.setModuleId(newConcept.getModuleId());
				newComponent.updateEffectiveTime();
				if (newComponent.getEffectiveTime() == null) {
					newComponent.setModuleId(defaultModuleId);
				}
			}

			newComponent.setChanged(existingComponent == null || newComponent.isComponentChanged(existingComponent) || rebase);
			if (existingComponent != null) {
				newComponent.setCreating(false);// May have been set true earlier
				newComponent.copyReleaseDetails(existingComponent, rebaseParentExistingComponentMap.get(newComponent.getId()));
				newComponent.updateEffectiveTime();
			} else {
				newComponent.setCreating(true);
				newComponent.clearReleaseDetails();
			}
		}
		componentsToPersist.addAll(newComponents);// All added but only those with changed or deleted flag will be written to store.

		// Mark deletions on existing versions from this branch (and the parent branch if rebasing)
		Set<String> uniqueIds = new HashSet<>();
		existingComponents.forEach(existingComponent -> {
			if (!newComponents.contains(existingComponent)) {
				if (uniqueIds.add(existingComponent.getId()) && existingComponent.isReleased()) {
					existingComponent.setActive(false);
					existingComponent.setChanged(true);
					existingComponent.updateEffectiveTime();
				} else {
					existingComponent.markDeleted();
				}
				componentsToPersist.add(existingComponent);
			}
		});
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
