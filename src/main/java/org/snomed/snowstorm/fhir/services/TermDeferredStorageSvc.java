package org.snomed.snowstorm.fhir.services;

import ca.uhn.fhir.jpa.entity.TermCodeSystem;
import ca.uhn.fhir.jpa.entity.TermCodeSystemVersion;
import ca.uhn.fhir.jpa.entity.TermConcept;
import ca.uhn.fhir.jpa.entity.TermConceptParentChildLink;
import ca.uhn.fhir.jpa.model.entity.ResourceTable;
import ca.uhn.fhir.jpa.term.api.ITermDeferredStorageSvc;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.ValueSet;

import java.util.List;

public class TermDeferredStorageSvc implements ITermDeferredStorageSvc {
	@Override
	public void saveDeferred() {
		System.out.println();
	}

	@Override
	public boolean isStorageQueueEmpty() {
		System.out.println();
		return false;
	}

	@Override
	public void setProcessDeferred(boolean b) {
		System.out.println();

	}

	@Override
	public void addConceptToStorageQueue(TermConcept termConcept) {
		System.out.println();

	}

	@Override
	public void addConceptLinkToStorageQueue(TermConceptParentChildLink termConceptParentChildLink) {
		System.out.println();

	}

	@Override
	public void addConceptMapsToStorageQueue(List<ConceptMap> list) {
		System.out.println();

	}

	@Override
	public void addValueSetsToStorageQueue(List<ValueSet> list) {
		System.out.println();

	}

	@Override
	public void deleteCodeSystem(TermCodeSystem termCodeSystem) {
		System.out.println();

	}

	@Override
	public void deleteCodeSystemForResource(ResourceTable resourceTable) {
		System.out.println();

	}

	@Override
	public void deleteCodeSystemVersion(TermCodeSystemVersion termCodeSystemVersion) {
		System.out.println();

	}

	@Override
	public void saveAllDeferred() {
		System.out.println();

	}

	@Override
	public void logQueueForUnitTest() {
		System.out.println();

	}
}
