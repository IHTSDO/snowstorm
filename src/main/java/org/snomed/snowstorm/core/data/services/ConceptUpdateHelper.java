package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.DomainEntity;
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
import java.util.stream.Stream;

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

	PersistedComponents saveNewOrUpdatedConcepts(Collection<Concept> concepts, Commit commit, Map<String, Concept> existingConceptsMap) throws ServiceException {
		final boolean savingMergedConcepts = commit.isRebase();

		validateConcepts(concepts);

		// Grab branch metadata including values inherited from ancestor branches
		Map<String, String> metadata = branchService.findBranchOrThrow(commit.getBranch().getPath(), true).getMetadata();
		String defaultModuleId = metadata != null ? metadata.get(Config.DEFAULT_MODULE_ID_KEY) : null;
		String defaultNamespace = metadata != null ? metadata.get(Config.DEFAULT_NAMESPACE_KEY) : null;
		boolean enableContentAutomations = metadata == null || !"true".equals(metadata.get(DISABLE_CONTENT_AUTOMATIONS_METADATA_KEY));
		IdentifierReservedBlock reservedIds = identifierService.reserveIdentifierBlock(concepts, defaultNamespace);

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
			final Set<ReferenceSetMember> newVersionOwlAxiomMembers = concept.getAllOwlAxiomMembers();

			if (enableContentAutomations) {
				if (concept.isActive()) {
					// Clear inactivation refsets
					concept.setInactivationIndicator(null);
					concept.setAssociationTargets(null);
				} else {
					// Make stated relationships and axioms inactive. We use the classification process to inactivate the inferred relationships.
					concept.getRelationships().forEach(relationship -> {
						if (Concepts.STATED_RELATIONSHIP.equals(relationship.getCharacteristicTypeId())) {
							relationship.setActive(false);
						}
					});
					newVersionOwlAxiomMembers.forEach(axiom -> axiom.setActive(false));
					concept.getDescriptions().forEach(description -> {
							if (StringUtils.isEmpty(description.getInactivationIndicator())) {
								description.setInactivationIndicator(inactivationIndicatorNames.get(Concepts.CONCEPT_NON_CURRENT));
							}
					});
				}
			}

			// Mark changed concepts as changed
			if (existingConcept != null) {
				concept.setCreating(false);// May have been set true earlier during first save
				concept.setChanged(concept.isComponentChanged(existingConcept) || savingMergedConcepts);
				concept.copyReleaseDetails(existingConcept);
				concept.updateEffectiveTime();

				markDeletionsAndUpdates(concept.getDescriptions(), existingConcept.getDescriptions(), savingMergedConcepts);
				markDeletionsAndUpdates(concept.getRelationships(), existingConcept.getRelationships(), savingMergedConcepts);
				markDeletionsAndUpdates(newVersionOwlAxiomMembers, existingConcept.getAllOwlAxiomMembers(), savingMergedConcepts);
				existingDescriptions.putAll(existingConcept.getDescriptions().stream().collect(Collectors.toMap(Description::getId, Function.identity())));
			} else {
				concept.setCreating(true);
				concept.setChanged(true);
				concept.clearReleaseDetails();

				Stream.of(
						concept.getDescriptions().stream(),
						concept.getRelationships().stream(),
						newVersionOwlAxiomMembers.stream())
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
				if (description.isActive()) {
					if (concept.isActive()) {
						description.setInactivationIndicator(null);
					}
				} else {
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

						if (member.isComponentChanged(existingMember) || savingMergedConcepts) {
							member.setChanged(true);
							member.copyReleaseDetails(existingMember);
							member.updateEffectiveTime();
						}
						refsetMembersToPersist.add(member);
						existingMembersToMatch.remove(languageRefsetId);
					} else {
						final ReferenceSetMember member = new ReferenceSetMember(description.getModuleId(), languageRefsetId, description.getId());
						member.setAdditionalField("acceptabilityId", acceptabilityId);
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

			// Apply relationship source ids
			concept.getRelationships()
					.forEach(relationship -> relationship.setSourceId(concept.getConceptId()));

			// Set new relationship ids
			concept.getRelationships().stream()
					.filter(relationship -> relationship.getRelationshipId() == null)
					.forEach(relationship -> relationship.setRelationshipId(reservedIds.getNextId(ComponentType.Relationship).toString()));

			// Detach concept's components to be persisted separately
			descriptionsToPersist.addAll(concept.getDescriptions());
			concept.getDescriptions().clear();
			relationshipsToPersist.addAll(concept.getRelationships());
			concept.getRelationships().clear();
			refsetMembersToPersist.addAll(newVersionOwlAxiomMembers);
			concept.getClassAxioms().clear();
			concept.getGciAxioms().clear();
		}

		// Apply default module to changed components
		if (defaultModuleId != null) {
			applyDefaultModule(concepts, defaultModuleId);
			applyDefaultModule(descriptionsToPersist, defaultModuleId);
			applyDefaultModule(relationshipsToPersist, defaultModuleId);
			applyDefaultModule(refsetMembersToPersist, defaultModuleId);
		}

		// TODO: Try saving all core component types at once - Elasticsearch likes multi-threaded writes.
		doSaveBatchConcepts(concepts, commit);
		doSaveBatchDescriptions(descriptionsToPersist, commit);
		doSaveBatchRelationships(relationshipsToPersist, commit);

		memberService.doSaveBatchMembers(refsetMembersToPersist, commit);
		doDeleteMembersWhereReferencedComponentDeleted(commit.getEntitiesDeleted(), commit);

		// Store assigned identifiers for registration with CIS
		identifierService.persistAssignedIdsForRegistration(reservedIds);

		return new PersistedComponents(concepts, descriptionsToPersist, relationshipsToPersist, refsetMembersToPersist);
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

		String newIndicator = newComponent.getInactivationIndicator();
		String newIndicatorId = null;
		if (newIndicator != null) {
			newIndicatorId = inactivationIndicatorNames.inverse().get(newIndicator);
			if (newIndicatorId == null) {
				throw new IllegalArgumentException(newComponent.getClass().getSimpleName() + " inactivation indicator not recognised '" + newIndicator + "'.");
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

	private <C extends SnomedComponent> void markDeletionsAndUpdates(Set<C> newComponents, Set<C> existingComponents, boolean rebase) {
		// Mark updates
		final Map<String, C> map = existingComponents.stream().collect(Collectors.toMap(DomainEntity::getId, Function.identity()));
		for (C newComponent : newComponents) {
			final C existingComponent = map.get(newComponent.getId());
			newComponent.setChanged(newComponent.isComponentChanged(existingComponent) || rebase);
			if (existingComponent != null) {
				newComponent.setCreating(false);// May have been set true earlier
				newComponent.copyReleaseDetails(existingComponent);
				newComponent.updateEffectiveTime();
			} else {
				newComponent.setCreating(true);
				newComponent.clearReleaseDetails();
			}
		}
		// Mark deletions
		for (C existingComponent : existingComponents) {
			if (!newComponents.contains(existingComponent)) {
				if (existingComponent.isReleased()) {
					existingComponent.setActive(false);
					existingComponent.setChanged(true);
					existingComponent.updateEffectiveTime();
				} else {
					existingComponent.markDeleted();
				}
				newComponents.add(existingComponent);// Add to newComponents collection so the deletion is persisted
			}
		}
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
