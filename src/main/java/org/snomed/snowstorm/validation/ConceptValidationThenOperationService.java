package org.snomed.snowstorm.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ihtsdo.drools.response.InvalidContent;
import org.ihtsdo.drools.response.Severity;
import org.snomed.snowstorm.core.data.domain.Axiom;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.ConceptView;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.domain.SnomedComponent;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.rest.ControllerHelper;
import org.snomed.snowstorm.validation.domain.DroolsDescription;
import org.snomed.snowstorm.validation.domain.DroolsRelationship;
import org.snomed.snowstorm.validation.exception.DroolsValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.snomed.snowstorm.rest.ControllerHelper.getCreatedLocationHeaders;

@Service
public class ConceptValidationThenOperationService {

	public enum ConceptValidationOperation {
		CREATE,
		UPDATE
	}

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private DroolsValidationService validationService;

	@Autowired
	private ObjectMapper objectMapper;

	private ConceptValidationOperation operation;
	private Concept concept;
	private String acceptLanguageHeader;
	private String branchPath;

	public final ConceptValidationThenOperationService withParameters(final Concept concept, final String acceptLanguageHeader, final String branchPath) {
		this.concept = generateUUIDSIfNotSet(concept);
		this.acceptLanguageHeader = acceptLanguageHeader;
		this.branchPath = branchPath;
		return this;
	}

	private Concept generateUUIDSIfNotSet(final Concept concept) {
		if (concept != null) {
			if (concept.getConceptId() == null) {
				concept.setConceptId(generateRandomUUID());
			}
			concept.getDescriptions().stream().filter(description -> description != null && description.getId() == null)
					.forEach(description -> description.setDescriptionId(generateRandomUUID()));
			concept.getRelationships().stream().filter(relationship -> relationship != null && relationship.getId() == null)
					.forEach(relationship -> relationship.setRelationshipId(generateRandomUUID()));
			concept.getAllOwlAxiomMembers().stream().filter(referenceSetMember -> referenceSetMember != null && referenceSetMember.getId() == null)
					.forEach(referenceSetMember -> referenceSetMember.setRefsetId(generateRandomUUID()));
		}
		return concept;
	}

	/**
	 * Generates a random UUID with length == 10 characters.
	 *
	 * @return random UUID with character length of 10.
	 */
	public static String generateRandomUUID() {
		final int power = (int) Math.pow(10, (double) 10 - 1);
		return "" + power + new Random().nextInt(9 * power);
	}

	public final ConceptValidationThenOperationService thenDo(final ConceptValidationOperation operation) {
		this.operation = operation;
		return this;
	}

	public final ResponseEntity<ConceptView> execute() throws JsonProcessingException, ServiceException {
		throwExceptionIfFieldsNotSet();
		final List<InvalidContent> invalidContents = validationService.validateConcept(branchPath, concept);
		final List<InvalidContent> invalidContentErrors = invalidContents.stream().filter(invalidContent -> invalidContent.getSeverity() == Severity.ERROR).collect(Collectors.toList());
		if (invalidContentErrors.isEmpty()) {
			return operation == ConceptValidationOperation.CREATE ? createConcept(concept, acceptLanguageHeader, branchPath, invalidContents.stream().filter(invalidContent ->
					invalidContent.getSeverity() == Severity.WARNING).collect(Collectors.toList())) : updateConcept(concept, acceptLanguageHeader, branchPath,
					invalidContents.stream().filter(invalidContent -> invalidContent.getSeverity() == Severity.WARNING).collect(Collectors.toList()));
		}
		throw new DroolsValidationException(objectMapper.writeValueAsString(invalidContentErrors).replace("\n", ""));
	}

	private void throwExceptionIfFieldsNotSet() {
		if (concept == null || acceptLanguageHeader == null || branchPath == null || operation == null) {
			throw new IllegalArgumentException("The mandatory field '" + (concept == null ? "concept" : (acceptLanguageHeader == null ? "acceptLanguageHeader"
					: (branchPath == null ? "branchPath" : "operation"))) + "' is not set.");
		}
	}

	private ResponseEntity<ConceptView> createConcept(final Concept concept, final String acceptLanguageHeader, final String branchPath,
			final List<InvalidContent> invalidContentWarnings) throws ServiceException, JsonProcessingException {
		final Concept createdConcept = conceptService.create(concept, ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader), branchPath);
		return new ResponseEntity<>(createdConcept, getCreateConceptHeaders(createdConcept.getId(),
				objectMapper.writeValueAsString(replaceTemporaryUUIDWithSCTID(invalidContentWarnings, createdConcept))), HttpStatus.OK);
	}

	private ResponseEntity<ConceptView> updateConcept(final Concept concept, final String acceptLanguageHeader, final String branchPath,
			final List<InvalidContent> invalidContentWarnings) throws ServiceException, JsonProcessingException {
		return new ResponseEntity<>(conceptService.update(concept, ControllerHelper.parseAcceptLanguageHeaderWithDefaultFallback(acceptLanguageHeader), branchPath),
				getUpdateConceptHeaders(objectMapper.writeValueAsString(replaceTemporaryUUIDWithSCTID(invalidContentWarnings, concept))), HttpStatus.OK);
	}

	private static HttpHeaders getCreateConceptHeaders(final String id, final String validationResults) {
		final HttpHeaders httpHeaders = getCreatedLocationHeaders(id, null);
		httpHeaders.add("validation-results", validationResults.replace("\n", ""));
		return httpHeaders;
	}

	private static HttpHeaders getUpdateConceptHeaders(final String validationResults) {
		final HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.add("validation-results", validationResults.replace("\n", ""));
		return httpHeaders;
	}

	private List<InvalidContent> replaceTemporaryUUIDWithSCTID(final List<InvalidContent> invalidContentWarnings, final Concept concept) {
		replaceInvalidContentTemporaryUUIDWithSCTIDInConcept(invalidContentWarnings, concept);
		replaceInvalidContentTemporaryUUIDWithSCTIDIn(invalidContentWarnings, concept.getDescriptions(),
				(final InvalidContent invalidContentWarning, final Description description) -> {
					final DroolsDescription droolsDescription = (DroolsDescription) invalidContentWarning.getComponent();
					if (description.getReleaseHash().equals(droolsDescription.getReleaseHash())) {
						invalidContentWarning.setComponent(new DroolsDescription(description));
					}
				});
		replaceInvalidContentTemporaryUUIDWithSCTIDIn(invalidContentWarnings, concept.getRelationships(),
				(final InvalidContent invalidContentWarning, final Relationship relationship) -> {
					final DroolsRelationship droolsRelationship = (DroolsRelationship) invalidContentWarning.getComponent();
					if (relationship.getReleaseHash().equals(droolsRelationship.getReleaseHash())) {
						invalidContentWarning.setComponent(new DroolsRelationship(null, false, relationship));
					}
				});
		replaceInvalidContentTemporaryUUIDWithSCTIDInAxiom(invalidContentWarnings, concept.getClassAxioms(), false);
		replaceInvalidContentTemporaryUUIDWithSCTIDInAxiom(invalidContentWarnings, concept.getGciAxioms(), true);
		return invalidContentWarnings;
	}

	private void replaceInvalidContentTemporaryUUIDWithSCTIDInAxiom(final List<InvalidContent> invalidContentWarnings, final Set<Axiom> axioms, final boolean axiomGci) {
		axioms.forEach(axiom -> axiom.getRelationships().stream().<Consumer<? super InvalidContent>>map(relationship -> invalidContent -> {
			final DroolsRelationship droolsRelationship = (DroolsRelationship) invalidContent.getComponent();
			if (relationship.getReleaseHash().equals(droolsRelationship.getReleaseHash()) && relationship.getId().equals(droolsRelationship.getId())) {
				invalidContent.setComponent(new DroolsRelationship(axiom.getAxiomId(), axiomGci, relationship));
			}
		}).forEach(invalidContentWarnings::forEach));
	}

	private <T extends SnomedComponent<T>> void replaceInvalidContentTemporaryUUIDWithSCTIDIn(final List<InvalidContent> invalidContentWarnings,
			final Set<T> components, final BiConsumer<InvalidContent, T> consumer) {
		components.stream().<Consumer<? super InvalidContent>>map(component -> invalidContentWarning ->
				consumer.accept(invalidContentWarning, component)).forEach(invalidContentWarnings::forEach);
	}

	private void replaceInvalidContentTemporaryUUIDWithSCTIDInConcept(final List<InvalidContent> invalidContentWarnings, final Concept concept) {
		final String conceptId = concept.getConceptId();
		final List<InvalidContent> newInvalidContentWarnings = new ArrayList<>();
		for (final Iterator<InvalidContent> iterator = invalidContentWarnings.iterator(); iterator.hasNext();) {
			final InvalidContent invalidContent = iterator.next();
			if(!invalidContent.getConceptId().equals(conceptId)) {
				newInvalidContentWarnings.add(new InvalidContent(conceptId, invalidContent.getComponent(), invalidContent.getMessage(), invalidContent.getSeverity()));
				iterator.remove();
			}
		}
		invalidContentWarnings.addAll(newInvalidContentWarnings);
	}

	public final ConceptValidationOperation getOperation() {
		return operation;
	}

	public final Concept getConcept() {
		return concept;
	}

	public final String getAcceptLanguageHeader() {
		return acceptLanguageHeader;
	}

	public final String getBranchPath() {
		return branchPath;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ConceptValidationThenOperationService that = (ConceptValidationThenOperationService) o;
		return Objects.equals(conceptService, that.conceptService) &&
				Objects.equals(validationService, that.validationService) &&
				Objects.equals(objectMapper, that.objectMapper) &&
				operation == that.operation &&
				Objects.equals(concept, that.concept) &&
				Objects.equals(acceptLanguageHeader, that.acceptLanguageHeader) &&
				Objects.equals(branchPath, that.branchPath);
	}

	@Override
	public int hashCode() {
		return Objects.hash(conceptService, validationService, objectMapper, operation, concept, acceptLanguageHeader, branchPath);
	}
}
