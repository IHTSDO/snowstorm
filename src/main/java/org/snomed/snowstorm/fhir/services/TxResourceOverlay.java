package org.snomed.snowstorm.fhir.services;

import org.hl7.fhir.r4.model.CodeSystem;
import org.snomed.snowstorm.fhir.domain.FHIRCodeSystemVersion;
import org.snomed.snowstorm.fhir.domain.FHIRConcept;

import java.util.List;
import java.util.Optional;

/**
 * Provides in-request access to FHIR resources supplied as tx-resource parameters.
 * These are CodeSystem/ValueSet definitions sent inline with the request — they are
 * not stored in Elasticsearch and are only valid for the duration of the current request.
 * Concept data (code/coding/codeableConcept) always arrives via request parameters and
 * is never a tx-resource. Tx-resources carry structural definitions only (CS/VS metadata,
 * concept lists, designations). This class bridges those definitions into the FHIRConcept
 * model used by the validation pipeline, without involving the Elasticsearch persistence layer.
 */
public final class TxResourceOverlay {

	private TxResourceOverlay() {}

	/**
	 * Returns all inline CodeSystem versions for the given URL from the current request's
	 * tx-resource overlay.
	 */
	public static List<FHIRCodeSystemVersion> getVersionsByUrl(String url) {
		return TxResourceContext.get().values().stream()
				.filter(r -> r instanceof CodeSystem cs && url.equals(cs.getUrl()))
				.map(r -> toVersion((CodeSystem) r))
				.toList();
	}

	/**
	 * Finds a concept by code within an inline CodeSystem version.
	 * Returns empty if the version is not inline or the code is not present.
	 */
	public static Optional<FHIRConcept> findConcept(FHIRCodeSystemVersion version, String code) {
		if (version.getInlineCodeSystem() == null) return Optional.empty();
		return version.getInlineCodeSystem().getConcept().stream()
				.filter(c -> code.equals(c.getCode()))
				.findFirst()
				.map(c -> new FHIRConcept(c, version));
	}

	/**
	 * Converts an inline CodeSystem resource into a FHIRCodeSystemVersion with the
	 * inlineCodeSystem reference set.
	 */
	public static FHIRCodeSystemVersion toVersion(CodeSystem cs) {
		FHIRCodeSystemVersion version = new FHIRCodeSystemVersion(cs);
		if (version.getVersion() == null) {
			version.setVersion("0");
		}
		version.setInlineCodeSystem(cs);
		return version;
	}
}
