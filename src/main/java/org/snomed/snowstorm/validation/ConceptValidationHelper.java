package org.snomed.snowstorm.validation;

import org.ihtsdo.drools.domain.Component;
import org.ihtsdo.drools.response.InvalidContent;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.validation.domain.DroolsDescription;
import org.snomed.snowstorm.validation.domain.DroolsRelationship;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class ConceptValidationHelper {

	private ConceptValidationHelper() {
	}

	public static Concept stripTemporaryUUIDsIfSet(final Concept concept) {
		if (concept != null) {
			if (concept.getConceptId() != null && concept.getConceptId().contains("-")) {
				concept.setConceptId(null);
			}
			concept.getDescriptions().stream().filter(description -> description != null && description.getId() != null && description.getId().contains("-"))
					.forEach(description -> description.setDescriptionId(null));
			concept.getRelationships().stream().filter(relationship -> relationship != null && relationship.getId() != null && relationship.getId().contains("-"))
					.forEach(relationship -> relationship.setRelationshipId(null));
		}
		return concept;
	}

	public static Concept generateTemporaryUUIDsIfNotSet(final Concept concept) {
		if (concept != null) {
			if (concept.getConceptId() == null) {
				concept.setConceptId(UUID.randomUUID().toString());
			}
			concept.getDescriptions().stream().filter(description -> description != null && description.getId() == null)
					.forEach(description -> description.setDescriptionId(UUID.randomUUID().toString()));
			concept.getRelationships().stream().filter(relationship -> relationship != null && relationship.getId() == null)
					.forEach(relationship -> relationship.setRelationshipId(UUID.randomUUID().toString()));
			concept.getAllOwlAxiomMembers().stream().filter(referenceSetMember -> referenceSetMember != null && referenceSetMember.getRefsetId() == null)
					.forEach(referenceSetMember -> referenceSetMember.setRefsetId(UUID.randomUUID().toString()));
			concept.getClassAndGciAxioms().stream().filter(axiom -> axiom.getAxiomId() == null)
					.forEach(axiom -> axiom.setAxiomId(UUID.randomUUID().toString()));
		}
	}

	public static List<InvalidContent> replaceTemporaryUUIDWithSCTID(final List<InvalidContent> invalidContentWarnings, final Concept concept) {
		replaceInvalidContentTemporaryUUIDWithSCTIDInConcept(invalidContentWarnings, concept);
		replaceInvalidContentTemporaryUUIDWithSCTIDIn(invalidContentWarnings, concept.getDescriptions(),
				(final InvalidContent invalidContentWarning, final Description description) -> {
					final Component component = invalidContentWarning.getComponent();
					if (component instanceof DroolsDescription) {
						final DroolsDescription droolsDescription = (DroolsDescription) component;
						if (description != null && description.getReleaseHash() != null &&
								description.getReleaseHash().equals(droolsDescription.getReleaseHash())) {
							invalidContentWarning.setComponent(new DroolsDescription(description));
						}
					}
				});
		replaceInvalidContentTemporaryUUIDWithSCTIDIn(invalidContentWarnings, concept.getRelationships(),
				(final InvalidContent invalidContentWarning, final Relationship relationship) -> {
					final Component component = invalidContentWarning.getComponent();
					if (component instanceof DroolsRelationship) {
						final DroolsRelationship droolsRelationship = (DroolsRelationship) component;
						if (relationship != null && relationship.getReleaseHash() != null &&
								relationship.getReleaseHash().equals(droolsRelationship.getReleaseHash())) {
							invalidContentWarning.setComponent(new DroolsRelationship(null, false, relationship));
						}
					}
				});
		replaceInvalidContentTemporaryUUIDWithSCTIDInAxiom(invalidContentWarnings, concept.getClassAxioms(), false);
		replaceInvalidContentTemporaryUUIDWithSCTIDInAxiom(invalidContentWarnings, concept.getGciAxioms(), true);
		return invalidContentWarnings;
	}

	private static void replaceInvalidContentTemporaryUUIDWithSCTIDInAxiom(final List<InvalidContent> invalidContentWarnings, final Set<Axiom> axioms, final boolean axiomGci) {
		axioms.forEach(axiom -> axiom.getRelationships().stream().<Consumer<? super InvalidContent>>map(relationship -> invalidContent -> {
			final Component component = invalidContent.getComponent();
			if (component instanceof DroolsRelationship) {
				final DroolsRelationship droolsRelationship = (DroolsRelationship) component;
				if (relationship != null && relationship.getReleaseHash() != null &&
						relationship.getReleaseHash().equals(droolsRelationship.getReleaseHash()) &&
						relationship.getId().equals(droolsRelationship.getId())) {
					invalidContent.setComponent(new DroolsRelationship(axiom.getAxiomId(), axiomGci, relationship));
				}
			}
		}).forEach(invalidContentWarnings::forEach));
	}

	private static <T extends SnomedComponent<T>> void replaceInvalidContentTemporaryUUIDWithSCTIDIn(final List<InvalidContent> invalidContentWarnings,
			final Set<T> components, final BiConsumer<InvalidContent, T> consumer) {
		components.stream().<Consumer<? super InvalidContent>>map(component -> invalidContentWarning ->
				consumer.accept(invalidContentWarning, component)).forEach(invalidContentWarnings::forEach);
	}

	private static void replaceInvalidContentTemporaryUUIDWithSCTIDInConcept(final List<InvalidContent> invalidContentWarnings, final Concept concept) {
		final String conceptId = concept.getConceptId();
		final List<InvalidContent> newInvalidContentWarnings = new ArrayList<>();
		for (final Iterator<InvalidContent> iterator = invalidContentWarnings.iterator(); iterator.hasNext();) {
			final InvalidContent invalidContent = iterator.next();
			if (!invalidContent.getConceptId().equals(conceptId)) {
				newInvalidContentWarnings.add(new InvalidContent(invalidContent.getRuleId(), conceptId, invalidContent.getComponent(),
						invalidContent.getMessage(), invalidContent.getSeverity()));
				iterator.remove();
			}
		}
		invalidContentWarnings.addAll(newInvalidContentWarnings);
	}

}
