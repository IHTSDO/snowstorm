package org.snomed.snowstorm.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ihtsdo.drools.domain.Component;
import org.ihtsdo.drools.response.InvalidContent;
import org.ihtsdo.drools.response.Severity;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.validation.domain.DroolsDescription;
import org.snomed.snowstorm.validation.domain.DroolsRelationship;
import org.snomed.snowstorm.validation.exception.DroolsValidationException;
import org.springframework.http.HttpHeaders;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class ConceptValidationHelper {

	private ConceptValidationHelper() {
	}

	public static List<InvalidContent> validate(final Concept concept, final String branchPath, final DroolsValidationService validationService,
			final ObjectMapper objectMapper) throws JsonProcessingException, ServiceException {
		final List<InvalidContent> invalidContents = validationService.validateConcept(branchPath, generateUUIDSIfNotSet(concept));
		final List<InvalidContent> invalidContentErrors = invalidContents.stream().filter(invalidContent -> invalidContent.getSeverity() == Severity.ERROR).collect(Collectors.toList());
		if (invalidContentErrors.isEmpty()) {
			return invalidContents.stream().filter(invalidContent -> invalidContent.getSeverity() == Severity.WARNING).collect(Collectors.toList());
		}
		throw new DroolsValidationException(objectMapper.writeValueAsString(invalidContentErrors).replace("\n", ""));
	}

	private static Concept generateUUIDSIfNotSet(final Concept concept) {
		if (concept != null) {
			if (concept.getConceptId() == null) {
				concept.setConceptId(UUID.randomUUID().toString());
			}
			concept.getDescriptions().stream().filter(description -> description != null && description.getId() == null)
					.forEach(description -> description.setDescriptionId(UUID.randomUUID().toString()));
			concept.getRelationships().stream().filter(relationship -> relationship != null && relationship.getId() == null)
					.forEach(relationship -> relationship.setRelationshipId(UUID.randomUUID().toString()));
			concept.getAllOwlAxiomMembers().stream().filter(referenceSetMember -> referenceSetMember != null && referenceSetMember.getId() == null)
					.forEach(referenceSetMember -> referenceSetMember.setRefsetId(UUID.randomUUID().toString()));
		}
		return concept;
	}

	public static HttpHeaders getCreateConceptHeaders(final HttpHeaders httpHeaders, final String validationResults) {
		httpHeaders.add("validation-results", validationResults.replace("\n", ""));
		return httpHeaders;
	}

	public static HttpHeaders getUpdateConceptHeaders(final String validationResults) {
		final HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.add("validation-results", validationResults.replace("\n", ""));
		return httpHeaders;
	}

	public static List<InvalidContent> replaceTemporaryUUIDWithSCTID(final List<InvalidContent> invalidContentWarnings, final Concept concept) {
		replaceInvalidContentTemporaryUUIDWithSCTIDInConcept(invalidContentWarnings, concept);
		replaceInvalidContentTemporaryUUIDWithSCTIDIn(invalidContentWarnings, concept.getDescriptions(),
				(final InvalidContent invalidContentWarning, final Description description) -> {
					final Component component = invalidContentWarning.getComponent();
					if (component instanceof DroolsDescription) {
						final DroolsDescription droolsDescription = (DroolsDescription) component;
						if (description.getReleaseHash().equals(droolsDescription.getReleaseHash())) {
							invalidContentWarning.setComponent(new DroolsDescription(description));
						}
					}
				});
		replaceInvalidContentTemporaryUUIDWithSCTIDIn(invalidContentWarnings, concept.getRelationships(),
				(final InvalidContent invalidContentWarning, final Relationship relationship) -> {
					final Component component = invalidContentWarning.getComponent();
					if (component instanceof DroolsRelationship) {
						final DroolsRelationship droolsRelationship = (DroolsRelationship) component;
						if (relationship.getReleaseHash().equals(droolsRelationship.getReleaseHash())) {
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
				if (relationship.getReleaseHash().equals(droolsRelationship.getReleaseHash()) && relationship.getId().equals(droolsRelationship.getId())) {
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
		for (final Iterator<InvalidContent> iterator = invalidContentWarnings.iterator(); iterator.hasNext(); ) {
			final InvalidContent invalidContent = iterator.next();
			if (!invalidContent.getConceptId().equals(conceptId)) {
				newInvalidContentWarnings.add(new InvalidContent(conceptId, invalidContent.getComponent(), invalidContent.getMessage(), invalidContent.getSeverity()));
				iterator.remove();
			}
		}
		invalidContentWarnings.addAll(newInvalidContentWarnings);
	}
}
