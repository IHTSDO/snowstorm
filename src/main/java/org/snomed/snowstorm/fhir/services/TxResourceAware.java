package org.snomed.snowstorm.fhir.services;

import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.domain.FHIRConcept;

import java.util.List;
import java.util.Optional;

/**
 * Mixin interface for services that need to resolve tx-resource overlay data.
 * Default methods delegate to TxResourceOverlay, keeping all inline-resource
 * logic in one place while services implement this interface to get the methods
 * in scope.
 */
public interface TxResourceAware {

	default List<FHIRCodeSystemVersion> getInlineVersionsByUrl(String url) {
		return TxResourceOverlay.getVersionsByUrl(url);
	}

	default Optional<FHIRConcept> findInlineConcept(FHIRCodeSystemVersion version, String code) {
		return TxResourceOverlay.findConcept(version, code);
	}
}
