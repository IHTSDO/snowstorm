package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.DomainEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.SearchLanguagesConfiguration;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.repositories.*;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierReservedBlock;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.data.services.pojo.PersistedComponents;
import org.snomed.snowstorm.core.util.DescriptionHelper;
import org.snomed.snowstorm.core.util.MapUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

	private Logger logger = LoggerFactory.getLogger(getClass());

	PersistedComponents saveNewOrUpdatedConcepts(Collection<Concept> concepts, Commit commit, Map<String, Concept> existingConceptsMap) throws ServiceException {
		final boolean savingMergedConcepts = commit.isRebase();

		validateConcepts(concepts);

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
			final Set<ReferenceSetMember> newVersionOwlAxiomMembers = concept.getAllOwlAxiomMembers();

			if (concept.isActive()) {
				// Clear inactivation refsets
				concept.setInactivationIndicator(null);
				concept.setAssociationTargets(null);
			} else {
				// Make stated relationships and axioms inactive
				concept.getRelationships().forEach(relationship -> {
					if (Concepts.STATED_RELATIONSHIP.equals(relationship.getCharacteristicTypeId())) {
						relationship.setActive(false);
					}
				});
				newVersionOwlAxiomMembers.forEach(axiom -> axiom.setActive(false));
				concept.getDescriptions().forEach(description -> description.setInactivationIndicator(inactivationIndicatorNames.get(Concepts.CONCEPT_NON_CURRENT)));
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
							refsetMembersToPersist.add(member);
						}
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
			refsetMembersToPersist.addAll(newVersionOwlAxiomMembers);
			concept.getClassAxioms().clear();
			concept.getGciAxioms().clear();
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

	private void updateAssociations(SnomedComponentWithAssociations newComponent, SnomedComponentWithAssociations existingComponent, List<ReferenceSetMember> refsetMembersToPersist) {
		Map<String, Set<String>> newAssociations = newComponent.getAssociationTargets();
		Map<String, Set<String>> existingAssociations = existingComponent == null ? null : existingComponent.getAssociationTargets();
		if (existingAssociations != null && !MapUtil.containsAllKeysAndSetsAreSupersets(newAssociations, existingAssociations)) {
			// One or more existing associations need to be made inactive

			if (newAssociations == null) {
				newAssociations = new HashMap<>();
			}
			// Check each association type
			for (String associationName : existingAssociations.keySet()) {
				// Associations for this type on both sides
				Set<String> existingAssociationsOfType = existingAssociations.get(associationName);
				Set<String> newAssociationsOfType = newAssociations.get(associationName);
				// Iterate existing set
				for (String existingAssociationTarget : existingAssociationsOfType) {
					// If new set doesn't exist or doesn't contain existing association make it inactive.
					if (newAssociationsOfType == null || !newAssociationsOfType.contains(existingAssociationTarget)) {
						// Existing association should be made inactive
						// Find the refset member for this association concept id and target concept id
						String associationRefsetId = Concepts.historicalAssociationNames.inverse().get(associationName);
						for (ReferenceSetMember existingMember : Optional.ofNullable(existingComponent.getAssociationTargetMembers()).orElse(Collections.emptySet())) {
							if (existingMember.isActive() && existingMember.getRefsetId().equals(associationRefsetId)
									&& existingAssociationTarget.equals(existingMember.getAdditionalField(ReferenceSetMember.AssociationFields.TARGET_COMP_ID))) {
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
		String newIndicatorId = null;
		if (newIndicator != null) {
			newIndicatorId = inactivationIndicatorNames.inverse().get(newIndicator);
			if (newIndicatorId == null) {
				throw new IllegalArgumentException(newComponent.getClass().getSimpleName() + " inactivation indicator not recognised '" + newIndicator + "'.");
			}
		}

		ReferenceSetMember newMember = newComponent.getInactivationIndicatorMember();
		ReferenceSetMember matchingExistingMember = null;
		if (existingComponent != null) {
			for (ReferenceSetMember existingIndicatorMember : existingComponent.getInactivationIndicatorMembers()) {
				if (matchingExistingMember == null && existingIndicatorMember.getAdditionalField("valueId").equals(newIndicatorId) &&
						(newMember == null || existingIndicatorMember.getId().equals(newMember.getId()))) {
					// Keep member
					if (!existingIndicatorMember.isActive()) {
						existingIndicatorMember.setActive(true);
						existingIndicatorMember.markChanged();
						refsetMembersToPersist.add(existingIndicatorMember);
					}
					matchingExistingMember = existingIndicatorMember;
				} else {
					// Remove member
					if (existingIndicatorMember.isActive()) {
						existingIndicatorMember.setActive(false);
						existingIndicatorMember.markChanged();
						refsetMembersToPersist.add(existingIndicatorMember);
					}
				}
			}
		}

		if (newIndicatorId != null && matchingExistingMember == null) {
			// Create new indicator
			ReferenceSetMember newIndicatorMember = new ReferenceSetMember(newComponent.getModuleId(), indicatorReferenceSet, newComponent.getId());
			newIndicatorMember.setAdditionalField("valueId", newIndicatorId);
			newIndicatorMember.setChanged(true);
			refsetMembersToPersist.add(newIndicatorMember);
			newComponent.getInactivationIndicatorMembers().clear();
			newComponent.addInactivationIndicatorMember(newIndicatorMember);
		}
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
