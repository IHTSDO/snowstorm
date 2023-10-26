package org.snomed.snowstorm.core.data.services;

import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_DIALECTS;

@Component
public class AutoMerger {
    private enum ComponentType {
        Concept, Description, Relationship, Annotation,
        ClassAxiom, GciAxiom, LanguageReferenceSetMember
    }

    @Autowired
    private ConceptService conceptService;

    @Autowired
    @Lazy
    private ReferenceSetMemberService referenceSetMemberService;

    /**
     * Construct a new instance of Concept, which is a combination of both sourceConcept and targetConcept. The branch path
     * on which targetConcept has been edited must be supplied to help identify which fields of targetConcept have actually changed.
     *
     * @param sourceConcept The state that will be the baseline for the new, auto-merged Concept.
     * @param targetConcept Edits belonging to this Concept will be re-applied to the new, auto-merged Concept.
     * @param branchPath    The branch path on which targetConcept has changed.
     * @return A new instance of Concept which is a combination of both sourceConcept and targetConcept.
     */
    public Concept autoMerge(Concept sourceConcept, Concept targetConcept, String branchPath) {
        // Data required for auto-merging.
        joinAxiomReferenceSetMembers(targetConcept, branchPath);
        Concept targetConceptOld = findTargetConceptBeforeAuthoringChanges(sourceConcept.getConceptId(), branchPath);

        // Identify all components that have changed on targetConcept by comparing it against its original state (i.e. before authoring).
        Map<ComponentType, Set<String>> componentsChangedOnTargetNew = getComponentsChangedOnTargetNew(targetConceptOld, targetConcept);

        /*
         * Strategy -->
         * Create a new Concept, cloned from sourceConcept. If an individual field (i.e. module etc) is different on
         * targetConceptNew compared to targetConceptOld, apply the difference to the newly created Concept. Effectively, re-apply
         * the changes on targetConceptNew to sourceConcept.
         * */
        return rebaseTargetChangesOntoSource(sourceConcept, targetConceptOld, targetConcept, componentsChangedOnTargetNew);
    }

    // An Axiom ReferenceSetMember is transient and is therefore needed from store.
    private void joinAxiomReferenceSetMembers(Concept targetConcept, String branchPath) {
        for (Axiom classAxiom : targetConcept.getClassAxioms()) {
            if (classAxiom.getReferenceSetMember() == null) {
                classAxiom.setReferenceSetMember(referenceSetMemberService.findMember(branchPath, classAxiom.getAxiomId()));
            }
        }

        for (Axiom gciAxiom : targetConcept.getGciAxioms()) {
            if (gciAxiom.getReferenceSetMember() == null) {
                gciAxiom.setReferenceSetMember(referenceSetMemberService.findMember(branchPath, gciAxiom.getAxiomId()));
            }
        }
    }

    // Find the Concept at the point in time when the branchPath was created, i.e. before any authoring.
    private Concept findTargetConceptBeforeAuthoringChanges(String conceptId, String branchPath) {
        return conceptService.find(conceptId, DEFAULT_LANGUAGE_DIALECTS, ControllerHelper.parseBranchTimepoint(branchPath + "@^"));
    }

    private Map<ComponentType, Set<String>> getComponentsChangedOnTargetNew(Concept targetConceptOld, Concept targetConceptNew) {
        Map<ComponentType, Set<String>> changesOnTargetConceptNew = new HashMap<>();

        // Has Concept changed?
        boolean conceptHasChanged = !Objects.equals(targetConceptOld.buildReleaseHash(), targetConceptNew.buildReleaseHash());
        if (conceptHasChanged) {
            changesOnTargetConceptNew.put(ComponentType.Concept, Set.of(targetConceptOld.getConceptId()));
        }

        // Have Descriptions changed?
        Map<String, Description> targetDescriptionsOld = mapByIdentifier(targetConceptOld.getDescriptions());
        Map<String, Description> targetDescriptionsNew = mapByIdentifier(targetConceptNew.getDescriptions());
        Set<String> diffInDescriptions = getComponentIdsChanged(targetDescriptionsOld, targetDescriptionsNew);
        if (!diffInDescriptions.isEmpty()) {
            changesOnTargetConceptNew.put(ComponentType.Description, diffInDescriptions);
        }

        // Have Annotations changed?
        Map<String, ReferenceSetMember> targetAnnotationMembersOld = mapAnnotationMembersByIdentifier(targetConceptOld.getAllAnnotationMembers());
        Map<String, ReferenceSetMember> targetAnnotationMembersNew = mapAnnotationMembersByIdentifier(targetConceptNew.getAllAnnotationMembers());
        Set<String> diffInAnnotationMembers = getComponentIdsChanged(targetAnnotationMembersOld, targetAnnotationMembersNew);
        if (!diffInAnnotationMembers.isEmpty()) {
            changesOnTargetConceptNew.put(ComponentType.Annotation, diffInAnnotationMembers);
        }

        // Have Relationships changed?
        Map<String, Relationship> targetRelationshipsOld = mapByIdentifier(targetConceptOld.getRelationships());
        Map<String, Relationship> targetRelationshipsNew = mapByIdentifier(targetConceptNew.getRelationships());
        Set<String> diffInRelationships = getComponentIdsChanged(targetRelationshipsOld, targetRelationshipsNew);
        if (!diffInRelationships.isEmpty()) {
            changesOnTargetConceptNew.put(ComponentType.Relationship, diffInRelationships);
        }

        // Have Axioms (Class) changed?
        Map<String, Axiom> targetClassAxiomsOld = mapAxiomByIdentifier(targetConceptOld.getClassAxioms());
        Map<String, Axiom> targetClassAxiomsNew = mapAxiomByIdentifier(targetConceptNew.getClassAxioms());
        Set<String> diffInClassAxioms = getAxiomIdsChanged(targetClassAxiomsOld, targetClassAxiomsNew);
        if (!diffInClassAxioms.isEmpty()) {
            changesOnTargetConceptNew.put(ComponentType.ClassAxiom, diffInClassAxioms);
        }

        // Have Axioms (GCI) changed?
        Map<String, Axiom> targetGCIAxiomsOld = mapAxiomByIdentifier(targetConceptOld.getGciAxioms());
        Map<String, Axiom> targetGCIAxiomsNew = mapAxiomByIdentifier(targetConceptNew.getGciAxioms());
        Set<String> diffInGCIAxioms = getAxiomIdsChanged(targetGCIAxiomsOld, targetGCIAxiomsNew);
        if (!diffInGCIAxioms.isEmpty()) {
            changesOnTargetConceptNew.put(ComponentType.GciAxiom, diffInGCIAxioms);
        }

        // Have ReferenceSetMembers (language) changed?
        Map<String, ReferenceSetMember> targetLangMembersOld = mapLangMembersByIdentifier(targetConceptOld.getDescriptions());
        Map<String, ReferenceSetMember> targetLangMembersNew = mapLangMembersByIdentifier(targetConceptNew.getDescriptions());
        Set<String> diffInLangMembers = getComponentIdsChanged(targetLangMembersOld, targetLangMembersNew);
        if (!diffInLangMembers.isEmpty()) {
            changesOnTargetConceptNew.put(ComponentType.LanguageReferenceSetMember, diffInLangMembers);
        }

        return changesOnTargetConceptNew;
    }

    private Map<String, ReferenceSetMember> mapAnnotationMembersByIdentifier(Set<ReferenceSetMember> annotations) {
        if (annotations != null) {
            return annotations.stream().collect(Collectors.toMap(ReferenceSetMember::getId, Function.identity()));
        }
        return Collections.emptyMap();
    }

    private <T extends SnomedComponent<T>> Map<String, T> mapByIdentifier(Set<T> components) {
        Map<String, T> mapByIdentifier = new HashMap<>();
        for (T component : components) {
            mapByIdentifier.put(component.getId(), component);
        }

        return mapByIdentifier;
    }

    private Map<String, Axiom> mapAxiomByIdentifier(Set<Axiom> axioms) {
        Map<String, Axiom> mapByIdentifier = new HashMap<>();
        for (Axiom axiom : axioms) {
            mapByIdentifier.put(axiom.getId(), axiom);
        }

        return mapByIdentifier;
    }

    private Map<String, ReferenceSetMember> mapLangMembersByIdentifier(Set<Description> descriptions) {
        Map<String, ReferenceSetMember> mapByIdentifier = new HashMap<>();
        for (Description description : descriptions) {
            Set<ReferenceSetMember> langRefsetMembers = description.getLangRefsetMembers();
            for (ReferenceSetMember langRefsetMember : langRefsetMembers) {
                mapByIdentifier.put(langRefsetMember.getMemberId(), langRefsetMember);
            }
        }

        return mapByIdentifier;
    }

    private ReferenceSetMember getLangMemberFromDescriptions(Set<Description> descriptions, String langMemberId) {
        for (Description sourceDescription : descriptions) {
            for (ReferenceSetMember langRefsetMember : sourceDescription.getLangRefsetMembers()) {
                if (langMemberId.equals(langRefsetMember.getMemberId())) {
                    return langRefsetMember;
                }
            }
        }

        return null;
    }

    private <T extends SnomedComponent<T>> Set<String> getComponentIdsChanged(Map<String, T> before, Map<String, T> after) {
        Set<String> identifiers = new HashSet<>();
        for (Map.Entry<String, T> entrySet : after.entrySet()) {
            String key = entrySet.getKey();
            SnomedComponent<?> valueAfter = entrySet.getValue();
            SnomedComponent<?> valueBefore = before.get(key);

            if (valueBefore == null || !Objects.equals(valueBefore.buildReleaseHash(), valueAfter.buildReleaseHash())) {
                identifiers.add(valueAfter.getId());
            }
        }

        return identifiers;
    }

    private Set<String> getAxiomIdsChanged(Map<String, Axiom> before, Map<String, Axiom> after) {
        Set<String> identifiers = new HashSet<>();
        for (Map.Entry<String, Axiom> entrySet : after.entrySet()) {
            String key = entrySet.getKey();
            Axiom valueAfter = entrySet.getValue();
            Axiom valueBefore = before.get(key);

            if (valueBefore == null || !Objects.equals(valueBefore.getReferenceSetMember().buildReleaseHash(), valueAfter.getReferenceSetMember().buildReleaseHash()) || !Objects.equals(valueBefore.toString(), valueAfter.toString())) {
                identifiers.add(valueAfter.getId());
            }
        }

        return identifiers;
    }

    // Base auto-merged Concept on sourceConcept; edits on targetConceptNew will be re-applied.
    private Concept rebaseTargetChangesOntoSource(Concept sourceConcept, Concept targetConceptOld, Concept targetConceptNew, Map<ComponentType, Set<String>> changesOnTargetConceptNew) {
        Concept mergedConcept = new Concept();
        mergedConcept.clone(sourceConcept); // mergedConcept is deeply based on sourceConcept

        for (Map.Entry<ComponentType, Set<String>> entrySet : changesOnTargetConceptNew.entrySet()) {
            ComponentType changedComponentType = entrySet.getKey(); // e.g. => Descriptions
            Set<String> changedComponentIds = entrySet.getValue(); // e.g. => 101, 201, 301, 401, 501

            if (ComponentType.Concept.equals(changedComponentType)) {
                reapplyConceptChanges(sourceConcept, targetConceptNew, targetConceptOld, changedComponentIds, mergedConcept);
            } else if (ComponentType.Description.equals(changedComponentType)) {
                reapplyDescriptionChanges(mapByIdentifier(sourceConcept.getDescriptions()), mapByIdentifier(targetConceptOld.getDescriptions()), mapByIdentifier(targetConceptNew.getDescriptions()), changedComponentIds, mergedConcept);
            } else if (ComponentType.Annotation.equals(changedComponentType)) {
                reapplyAnnotationMemberChanges(mapAnnotationMembersByIdentifier(sourceConcept.getAllAnnotationMembers()), mapAnnotationMembersByIdentifier(targetConceptOld.getAllAnnotationMembers()), mapAnnotationMembersByIdentifier(targetConceptNew.getAllAnnotationMembers()), changedComponentIds, mergedConcept);
            } else if (ComponentType.Relationship.equals(changedComponentType)) {
                reapplyRelationshipChanges(mapByIdentifier(sourceConcept.getRelationships()), mapByIdentifier(targetConceptOld.getRelationships()), mapByIdentifier(targetConceptNew.getRelationships()), changedComponentIds, mergedConcept);
            } else if (ComponentType.ClassAxiom.equals(changedComponentType)) {
                mergedConcept.setClassAxioms(reapplyAxiomsChanges(mapAxiomByIdentifier(sourceConcept.getClassAxioms()), mapAxiomByIdentifier(targetConceptOld.getClassAxioms()), mapAxiomByIdentifier(targetConceptNew.getClassAxioms()), changedComponentIds, mergedConcept));
            } else if (ComponentType.GciAxiom.equals(changedComponentType)) {
                mergedConcept.setGciAxioms(reapplyAxiomsChanges(mapAxiomByIdentifier(sourceConcept.getGciAxioms()), mapAxiomByIdentifier(targetConceptOld.getGciAxioms()), mapAxiomByIdentifier(targetConceptNew.getGciAxioms()), changedComponentIds, mergedConcept));
            } else if (ComponentType.LanguageReferenceSetMember.equals(changedComponentType)) {
                reapplyLangMemberChanges(mapLangMembersByIdentifier(sourceConcept.getDescriptions()), mapLangMembersByIdentifier(targetConceptOld.getDescriptions()), mapLangMembersByIdentifier(targetConceptNew.getDescriptions()), changedComponentIds, mergedConcept);
            }
        }

        return mergedConcept;
    }

    private void reapplyConceptChanges(Concept sourceConcept, Concept targetConceptNew, Concept targetConceptOld, Set<String> value, Concept mergedConcept) {
        mergedConcept.setActive(getValueChanged(targetConceptOld.isActive(), targetConceptNew.isActive(), sourceConcept.isActive()));
        mergedConcept.setModuleId(getValueChanged(targetConceptOld.getModuleId(), targetConceptNew.getModuleId(), sourceConcept.getModuleId()));
        mergedConcept.setDefinitionStatus(getValueChanged(targetConceptOld.getDefinitionStatus(), targetConceptNew.getDefinitionStatus(), sourceConcept.getDefinitionStatus()));
        mergedConcept.setReleaseHash(getValueChanged(targetConceptOld.getReleaseHash(), targetConceptNew.getReleaseHash(), sourceConcept.getReleaseHash()));
        mergedConcept.setReleased(getValueChanged(targetConceptOld.isReleased(), targetConceptNew.isReleased(), sourceConcept.isReleased()));

        mergedConcept.updateEffectiveTime();
    }

    private void reapplyDescriptionChanges(Map<String, Description> sourceDescriptions, Map<String, Description> targetDescriptionsOld, Map<String, Description> targetDescriptionsNew, Set<String> changedDescriptionIds, Concept mergedConcept) {
        Map<String, Description> mergedDescriptions = new HashMap<>();

        // Merge changed Descriptions
        for (String changedDescriptionId : changedDescriptionIds) {
            Description sourceDescription = sourceDescriptions.get(changedDescriptionId);
            Description targetDescriptionOld = targetDescriptionsOld.get(changedDescriptionId);
            Description targetDescriptionNew = targetDescriptionsNew.get(changedDescriptionId);
            Description mergedDescription = new Description();

            // Didn't exist before user started authoring; nothing to re-apply.
            if (sourceDescription == null || targetDescriptionOld == null) {
                mergedDescription = targetDescriptionNew;
            } else {
                mergedDescription.setDescriptionId(sourceDescription.getDescriptionId());
                mergedDescription.setConceptId(sourceDescription.getConceptId());
                mergedDescription.copyReleaseDetails(sourceDescription);

                mergedDescription.setActive(getValueChanged(targetDescriptionOld.isActive(), targetDescriptionNew.isActive(), sourceDescription.isActive()));
                mergedDescription.setModuleId(getValueChanged(targetDescriptionOld.getModuleId(), targetDescriptionNew.getModuleId(), sourceDescription.getModuleId()));
                mergedDescription.setTerm(getValueChanged(targetDescriptionOld.getTerm(), targetDescriptionNew.getTerm(), sourceDescription.getTerm()));
                mergedDescription.setCaseSignificanceId(getValueChanged(targetDescriptionOld.getCaseSignificanceId(), targetDescriptionNew.getCaseSignificanceId(), sourceDescription.getCaseSignificanceId()));
                mergedDescription.setLanguageRefsetMembers(sourceDescription.getLangRefsetMembers());

                // Re-apply immutable fields (legal as not yet released)
                if (sourceDescription.getReleasedEffectiveTime() == null) {
                    mergedDescription.setLanguageCode(getValueChanged(targetDescriptionOld.getLanguageCode(), targetDescriptionNew.getLanguageCode(), sourceDescription.getLanguageCode()));
                    mergedDescription.setTypeId(getValueChanged(targetDescriptionOld.getTypeId(), targetDescriptionNew.getTypeId(), sourceDescription.getTypeId()));
                } else {
                    mergedDescription.setLanguageCode(sourceDescription.getLanguageCode());
                    mergedDescription.setTypeId(sourceDescription.getTypeId());
                }
            }

            mergedDescription.updateEffectiveTime();
            mergedDescriptions.put(changedDescriptionId, mergedDescription);
        }

        // Merge unchanged Descriptions
        for (Map.Entry<String, Description> entrySet : sourceDescriptions.entrySet()) {
            String unchangedDescriptionId = entrySet.getKey();
            Description description = entrySet.getValue();

            // There are no target edits if the identifier is absent; therefore, take from source.
            mergedDescriptions.putIfAbsent(unchangedDescriptionId, description);
        }

        mergedConcept.setDescriptions(new HashSet<>(mergedDescriptions.values()));
    }

    private void reapplyRelationshipChanges(Map<String, Relationship> sourceRelationships, Map<String, Relationship> targetRelationshipsOld, Map<String, Relationship> targetRelationshipsNew, Set<String> changedRelationshipIds, Concept mergedConcept) {
        Map<String, Relationship> mergedRelationships = new HashMap<>();

        // Merge changed Relationships
        for (String changedRelationshipId : changedRelationshipIds) {
            Relationship sourceRelationship = sourceRelationships.get(changedRelationshipId);
            Relationship targetRelationshipOld = targetRelationshipsOld.get(changedRelationshipId);
            Relationship targetRelationshipNew = targetRelationshipsNew.get(changedRelationshipId);
            Relationship mergedRelationship = new Relationship();

            // Didn't exist before user started authoring; nothing to re-apply.
            if (sourceRelationship == null || targetRelationshipOld == null) {
                mergedRelationship = targetRelationshipNew;
            } else {
                mergedRelationship.setRelationshipId(sourceRelationship.getRelationshipId());
                mergedRelationship.copyReleaseDetails(sourceRelationship);

                mergedRelationship.setActive(getValueChanged(targetRelationshipOld.isActive(), targetRelationshipNew.isActive(), sourceRelationship.isActive()));
                mergedRelationship.setModuleId(getValueChanged(targetRelationshipOld.getModuleId(), targetRelationshipNew.getModuleId(), sourceRelationship.getModuleId()));
                mergedRelationship.setRelationshipGroup(getValueChanged(targetRelationshipOld.getRelationshipGroup(), targetRelationshipNew.getRelationshipGroup(), sourceRelationship.getRelationshipGroup()));
                mergedRelationship.setCharacteristicTypeId(getValueChanged(targetRelationshipOld.getCharacteristicTypeId(), targetRelationshipNew.getCharacteristicTypeId(), sourceRelationship.getCharacteristicTypeId()));
                mergedRelationship.setModifier(getValueChanged(targetRelationshipOld.getModifier(), targetRelationshipNew.getModifier(), sourceRelationship.getModifier()));

                // Re-apply immutable fields (legal as not yet released)
                if (sourceRelationship.getReleasedEffectiveTime() == null) {
                    mergedRelationship.setSourceId(getValueChanged(targetRelationshipOld.getSourceId(), targetRelationshipNew.getSourceId(), sourceRelationship.getSourceId()));

                    if (targetRelationshipNew.getDestinationId() != null) {
                        mergedRelationship.setDestinationId(getValueChanged(targetRelationshipOld.getDestinationId(), targetRelationshipNew.getDestinationId(), sourceRelationship.getDestinationId()));
                        mergedRelationship.setTarget(getValueChanged(targetRelationshipOld.getTarget(), targetRelationshipNew.getTarget(), sourceRelationship.getTarget()));
                    } else {
                        mergedRelationship.setValue(getValueChanged(targetRelationshipOld.getValue(), targetRelationshipNew.getValue(), sourceRelationship.getValue()));
                    }

                    mergedRelationship.setTypeId(getValueChanged(targetRelationshipOld.getTypeId(), targetRelationshipNew.getTypeId(), sourceRelationship.getTypeId()));
                    mergedRelationship.setType(sourceRelationship.getType());
                } else {
                    mergedRelationship.setSourceId(sourceRelationship.getSourceId());

                    if (targetRelationshipNew.getDestinationId() != null) {
                        mergedRelationship.setDestinationId(sourceRelationship.getDestinationId());
                        mergedRelationship.setTarget(getValueChanged(targetRelationshipOld.getTarget(), targetRelationshipNew.getTarget(), sourceRelationship.getTarget()));
                    } else {
                        mergedRelationship.setValue(sourceRelationship.getValue());
                    }

                    mergedRelationship.setTypeId(sourceRelationship.getTypeId());
                    mergedRelationship.setType(sourceRelationship.getType());
                }
            }

            mergedRelationship.updateEffectiveTime();
            mergedRelationships.put(changedRelationshipId, mergedRelationship);
        }

        // Merge unchanged Relationships
        for (Map.Entry<String, Relationship> entrySet : sourceRelationships.entrySet()) {
            String unchangedRelationshipId = entrySet.getKey();
            Relationship relationship = entrySet.getValue();

            // There are no target edits if the identifier is absent; therefore, take from source.
            mergedRelationships.putIfAbsent(unchangedRelationshipId, relationship);
        }

        mergedConcept.setRelationships(new HashSet<>(mergedRelationships.values()));
    }

    private Set<Axiom> reapplyAxiomsChanges(Map<String, Axiom> sourceAxioms, Map<String, Axiom> targetAxiomsOld, Map<String, Axiom> targetAxiomsNew, Set<String> changedAxiomIds, Concept mergedConcept) {
        Map<String, Axiom> mergedAxioms = new HashMap<>();

        // Merge changed Axioms
        for (String changedAxiomId : changedAxiomIds) {
            Axiom sourceAxiom = sourceAxioms.get(changedAxiomId);
            Axiom targetAxiomOld = targetAxiomsOld.get(changedAxiomId);
            Axiom targetAxiomNew = targetAxiomsNew.get(changedAxiomId);
            Axiom mergedAxiom = new Axiom();
            ReferenceSetMember mergedReferenceSetMember = new ReferenceSetMember();

            // Didn't exist before user started authoring; nothing to re-apply.
            if (sourceAxiom == null || targetAxiomOld == null) {
                mergedAxiom = targetAxiomNew;

                ReferenceSetMember targetMember = targetAxiomNew.getReferenceSetMember();
                if (targetMember != null) {
                    mergedReferenceSetMember = targetMember;
                }
            } else {
                // Re-apply fields on Axiom
                mergedAxiom.setAxiomId(sourceAxiom.getAxiomId());
                mergedAxiom.setActive(getValueChanged(targetAxiomOld.isActive(), targetAxiomNew.isActive(), sourceAxiom.isActive()));
                mergedAxiom.setReleased(getValueChanged(targetAxiomOld.isReleased(), targetAxiomNew.isReleased(), sourceAxiom.isReleased()));
                mergedAxiom.setDefinitionStatusId(getValueChanged(targetAxiomOld.getDefinitionStatusId(), targetAxiomNew.getDefinitionStatusId(), sourceAxiom.getDefinitionStatusId()));
                mergedAxiom.setModuleId(getValueChanged(targetAxiomOld.getModuleId(), targetAxiomNew.getModuleId(), sourceAxiom.getModuleId()));
                if (targetAxiomOld.getRelationships().equals(targetAxiomNew.getRelationships())) {
                    mergedAxiom.setRelationships(targetAxiomOld.getRelationships());
                } else {
                    mergedAxiom.setRelationships(targetAxiomNew.getRelationships());
                }

                // Re-apply fields on ReferenceSetMember
                ReferenceSetMember sourceReferenceSetMember = sourceAxiom.getReferenceSetMember();
                ReferenceSetMember targetReferenceSetMemberOld = targetAxiomOld.getReferenceSetMember();
                ReferenceSetMember targetReferenceSetMemberNew = targetAxiomNew.getReferenceSetMember();

                mergedReferenceSetMember.setMemberId(sourceReferenceSetMember.getMemberId());
                mergedReferenceSetMember.setReleased(sourceReferenceSetMember.isReleased());
                mergedReferenceSetMember.setReleaseHash(sourceReferenceSetMember.getReleaseHash());
                mergedReferenceSetMember.setReleasedEffectiveTime(sourceReferenceSetMember.getReleasedEffectiveTime());
                mergedReferenceSetMember.setEffectiveTimeI(getValueChanged(targetReferenceSetMemberOld.getEffectiveTimeI(), targetReferenceSetMemberNew.getEffectiveTimeI(), sourceReferenceSetMember.getEffectiveTimeI()));

                mergedReferenceSetMember.setReferencedComponentId(sourceReferenceSetMember.getReferencedComponentId());
                mergedReferenceSetMember.setActive(getValueChanged(targetReferenceSetMemberOld.isActive(), targetReferenceSetMemberNew.isActive(), sourceReferenceSetMember.isActive()));
                mergedReferenceSetMember.setReleased(getValueChanged(targetReferenceSetMemberOld.isReleased(), targetReferenceSetMemberNew.isReleased(), sourceReferenceSetMember.isReleased()));
                mergedReferenceSetMember.setRefsetId(getValueChanged(targetReferenceSetMemberOld.getRefsetId(), targetReferenceSetMemberNew.getRefsetId(), sourceReferenceSetMember.getRefsetId()));
                mergedReferenceSetMember.setModuleId(getValueChanged(targetReferenceSetMemberOld.getModuleId(), targetReferenceSetMemberNew.getModuleId(), sourceReferenceSetMember.getModuleId()));
                mergedReferenceSetMember.setAdditionalField(
                        ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION,
                        getValueChanged(
                                targetReferenceSetMemberOld.getAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION),
                                targetReferenceSetMemberNew.getAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION),
                                sourceReferenceSetMember.getAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION)
                        )
                );
            }

            mergedReferenceSetMember.updateEffectiveTime();
            mergedAxiom.setReferenceSetMember(mergedReferenceSetMember);
            mergedAxioms.put(changedAxiomId, mergedAxiom);
        }

        // Merge unchanged Axioms
        for (Map.Entry<String, Axiom> entrySet : sourceAxioms.entrySet()) {
            String unchangedAxiomId = entrySet.getKey();
            Axiom axiom = entrySet.getValue();

            // There are no target edits if the identifier is absent; therefore, take from source.
            mergedAxioms.putIfAbsent(unchangedAxiomId, axiom);
        }

        return new HashSet<>(mergedAxioms.values());
    }

    private void reapplyLangMemberChanges(Map<String, ReferenceSetMember> sourceLangMembers, Map<String, ReferenceSetMember> targetLangMembersOld, Map<String, ReferenceSetMember> targetLangMembersNew, Set<String> changedLangMemberIds, Concept mergedConcept) {
        Map<String, ReferenceSetMember> mergedReferenceSetMembers = new HashMap<>();

        // Merge changed ReferenceSetMembers
        for (String changedLangMemberId : changedLangMemberIds) {
            ReferenceSetMember sourceReferenceSetMember = sourceLangMembers.get(changedLangMemberId);
            ReferenceSetMember targetReferenceSetMemberOld = targetLangMembersOld.get(changedLangMemberId);
            ReferenceSetMember targetReferenceSetMemberNew = targetLangMembersNew.get(changedLangMemberId);
            ReferenceSetMember mergedReferenceSetMember = new ReferenceSetMember();

            // Didn't exist before user started authoring; nothing to re-apply.
            if (sourceReferenceSetMember == null || targetReferenceSetMemberOld == null) {
                mergedReferenceSetMember = targetReferenceSetMemberNew;
            } else {
                mergedReferenceSetMember.setMemberId(sourceReferenceSetMember.getMemberId());
                mergedReferenceSetMember.setReferencedComponentId(sourceReferenceSetMember.getReferencedComponentId());
                mergedReferenceSetMember.setConceptId(sourceReferenceSetMember.getConceptId());
                mergedReferenceSetMember.copyReleaseDetails(sourceReferenceSetMember);

                // Re-apply immutable fields (legal as not yet released)
                if (sourceReferenceSetMember.getReleasedEffectiveTime() == null) {
                    mergedReferenceSetMember.setRefsetId(getValueChanged(targetReferenceSetMemberOld.getRefsetId(), targetReferenceSetMemberNew.getRefsetId(), sourceReferenceSetMember.getRefsetId()));
                    mergedReferenceSetMember.setAdditionalField(
                            ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID,
                            getValueChanged(
                                    targetReferenceSetMemberOld.getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID),
                                    targetReferenceSetMemberNew.getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID),
                                    sourceReferenceSetMember.getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID)
                            )
                    );
                } else {
                    mergedReferenceSetMember.setRefsetId(sourceReferenceSetMember.getRefsetId());
                    mergedReferenceSetMember.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, sourceReferenceSetMember.getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID));
                }

                // Re-apply mutable fields
                mergedReferenceSetMember.setActive(getValueChanged(targetReferenceSetMemberOld.isActive(), targetReferenceSetMemberNew.isActive(), sourceReferenceSetMember.isActive()));
                mergedReferenceSetMember.setModuleId(getValueChanged(targetReferenceSetMemberOld.getModuleId(), targetReferenceSetMemberNew.getModuleId(), sourceReferenceSetMember.getModuleId()));
            }

            mergedReferenceSetMember.updateEffectiveTime();
            mergedReferenceSetMembers.put(changedLangMemberId, mergedReferenceSetMember);
        }

        // Re-populate Description's LanguageReferenceSetMembers
        for (Description description : mergedConcept.getDescriptions()) {
            String descriptionId = description.getDescriptionId();
            Map<String, ReferenceSetMember> languageReferenceSetMembers = new HashMap<>();

            // Replace existing with newly merged
            for (ReferenceSetMember referenceSetMember : description.getLangRefsetMembers()) {
                String memberId = referenceSetMember.getMemberId();
                if (changedLangMemberIds.contains(memberId)) {
                    languageReferenceSetMembers.put(memberId, mergedReferenceSetMembers.get(memberId));
                    mergedReferenceSetMembers.remove(memberId);
                } else {
                    languageReferenceSetMembers.put(memberId, referenceSetMember);
                }
            }

            // Add newly created
            for (ReferenceSetMember referenceSetMember : mergedReferenceSetMembers.values()) {
                String memberId = referenceSetMember.getMemberId();
                String referencedComponentId = referenceSetMember.getReferencedComponentId();

                // Keep language associated with correct Description
                if (referencedComponentId.equals(descriptionId)) {
                    languageReferenceSetMembers.putIfAbsent(memberId, mergedReferenceSetMembers.get(memberId));
                }
            }

            description.setLanguageRefsetMembers(languageReferenceSetMembers.values());
        }
    }

    private void reapplyAnnotationMemberChanges(Map<String, ReferenceSetMember> sourceMembers, Map<String, ReferenceSetMember> targetMembersOld, Map<String, ReferenceSetMember> targetMembersNew, Set<String> changedComponentIds, Concept mergedConcept) {
        Map<String, ReferenceSetMember> mergedReferenceSetMembers = new HashMap<>();
        for (String changedMemberId : changedComponentIds) {
            ReferenceSetMember sourceReferenceSetMember = sourceMembers.get(changedMemberId);
            ReferenceSetMember targetReferenceSetMemberOld = targetMembersOld.get(changedMemberId);
            ReferenceSetMember targetReferenceSetMemberNew = targetMembersNew.get(changedMemberId);
            ReferenceSetMember mergedReferenceSetMember = new ReferenceSetMember();

            // Didn't exist before user started authoring; nothing to re-apply.
            if (sourceReferenceSetMember == null || targetReferenceSetMemberOld == null) {
                mergedReferenceSetMember = targetReferenceSetMemberNew;
            } else {
                mergedReferenceSetMember.setMemberId(sourceReferenceSetMember.getMemberId());
                mergedReferenceSetMember.setReferencedComponentId(sourceReferenceSetMember.getReferencedComponentId());
                mergedReferenceSetMember.setConceptId(sourceReferenceSetMember.getConceptId());
                mergedReferenceSetMember.copyReleaseDetails(sourceReferenceSetMember);

                // Re-apply immutable fields (legal as not yet released)
                if (sourceReferenceSetMember.getReleasedEffectiveTime() == null) {
                    mergedReferenceSetMember.setRefsetId(getValueChanged(targetReferenceSetMemberOld.getRefsetId(), targetReferenceSetMemberNew.getRefsetId(), sourceReferenceSetMember.getRefsetId()));
                    mergedReferenceSetMember.setAdditionalField(
                            ReferenceSetMember.AnnotationFields.ANNOTATION_TYPE_ID,
                            getValueChanged(
                                    targetReferenceSetMemberOld.getAdditionalField(ReferenceSetMember.AnnotationFields.ANNOTATION_TYPE_ID),
                                    targetReferenceSetMemberNew.getAdditionalField(ReferenceSetMember.AnnotationFields.ANNOTATION_TYPE_ID),
                                    sourceReferenceSetMember.getAdditionalField(ReferenceSetMember.AnnotationFields.ANNOTATION_TYPE_ID)
                            )
                    );
                    mergedReferenceSetMember.setAdditionalField(
                            ReferenceSetMember.AnnotationFields.ANNOTATION_VALUE,
                            getValueChanged(
                                    targetReferenceSetMemberOld.getAdditionalField(ReferenceSetMember.AnnotationFields.ANNOTATION_VALUE),
                                    targetReferenceSetMemberNew.getAdditionalField(ReferenceSetMember.AnnotationFields.ANNOTATION_VALUE),
                                    sourceReferenceSetMember.getAdditionalField(ReferenceSetMember.AnnotationFields.ANNOTATION_VALUE)
                            )
                    );
                    mergedReferenceSetMember.setAdditionalField(
                            ReferenceSetMember.AnnotationFields.ANNOTATION_LANGUAGE,
                            getValueChanged(
                                    targetReferenceSetMemberOld.getAdditionalField(ReferenceSetMember.AnnotationFields.ANNOTATION_LANGUAGE),
                                    targetReferenceSetMemberNew.getAdditionalField(ReferenceSetMember.AnnotationFields.ANNOTATION_LANGUAGE),
                                    sourceReferenceSetMember.getAdditionalField(ReferenceSetMember.AnnotationFields.ANNOTATION_LANGUAGE)
                            )
                    );
                } else {
                    mergedReferenceSetMember.setRefsetId(sourceReferenceSetMember.getRefsetId());
                    mergedReferenceSetMember.setAdditionalField(ReferenceSetMember.AnnotationFields.ANNOTATION_TYPE_ID, sourceReferenceSetMember.getAdditionalField(ReferenceSetMember.AnnotationFields.ANNOTATION_TYPE_ID));
                    mergedReferenceSetMember.setAdditionalField(ReferenceSetMember.AnnotationFields.ANNOTATION_VALUE, sourceReferenceSetMember.getAdditionalField(ReferenceSetMember.AnnotationFields.ANNOTATION_VALUE));
                    mergedReferenceSetMember.setAdditionalField(ReferenceSetMember.AnnotationFields.ANNOTATION_LANGUAGE, sourceReferenceSetMember.getAdditionalField(ReferenceSetMember.AnnotationFields.ANNOTATION_LANGUAGE));
                }

                // Re-apply mutable fields
                mergedReferenceSetMember.setActive(getValueChanged(targetReferenceSetMemberOld.isActive(), targetReferenceSetMemberNew.isActive(), sourceReferenceSetMember.isActive()));
                mergedReferenceSetMember.setModuleId(getValueChanged(targetReferenceSetMemberOld.getModuleId(), targetReferenceSetMemberNew.getModuleId(), sourceReferenceSetMember.getModuleId()));
            }

            mergedReferenceSetMember.updateEffectiveTime();
            mergedReferenceSetMembers.put(mergedReferenceSetMember.getMemberId(), mergedReferenceSetMember);
        }
        // Merge unchanged Annotatations
        for (Map.Entry<String, ReferenceSetMember> entrySet : sourceMembers.entrySet()) {
            String unchangedAnnotationId = entrySet.getKey();
            ReferenceSetMember annotationMember = entrySet.getValue();

            // There are no target edits if the identifier is absent; therefore, take from source.
            mergedReferenceSetMembers.putIfAbsent(unchangedAnnotationId, annotationMember);
        }

        if (!mergedReferenceSetMembers.isEmpty()) {
            Set<Annotation> annotations = new HashSet<>();
            mergedReferenceSetMembers.values().forEach(item -> {
                Annotation annotation = new Annotation().fromRefsetMember(item);
                annotations.add(annotation);
            });
            mergedConcept.setAnnotations(annotations);
        }
    }

    // valueDefault is coming from Source and may have newer/different value than valueOld.
    private String getValueChanged(String valueOld, String valueNew, String valueDefault) {
        boolean valueHasChanged = !Objects.equals(valueOld, valueNew);
        if (valueHasChanged) {
            return valueNew;
        } else {
            return valueDefault;
        }
    }

    // valueDefault is coming from Source and may have newer/different value than valueOld.
    private Boolean getValueChanged(Boolean valueOld, Boolean valueNew, Boolean valueDefault) {
        boolean valueHasChanged = !Objects.equals(valueOld, valueNew);
        if (valueHasChanged) {
            return valueNew;
        } else {
            return valueDefault;
        }
    }

    // valueDefault is coming from Source and may have newer/different value than valueOld.
    private Integer getValueChanged(Integer valueOld, Integer valueNew, Integer valueDefault) {
        boolean valueHasChanged = !Objects.equals(valueOld, valueNew);
        if (valueHasChanged) {
            return valueNew;
        } else {
            return valueDefault;
        }
    }

    // valueDefault is coming from Source and may have newer/different value than valueOld.
    private ConceptMini getValueChanged(ConceptMini valueOld, ConceptMini valueNew, ConceptMini valueDefault) {
        boolean valueHasChanged = !Objects.equals(valueOld, valueNew);
        if (valueHasChanged) {
            return valueNew;
        } else {
            return valueDefault;
        }
    }
}
