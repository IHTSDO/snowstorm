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
	}

	@Override
	public boolean isStorageQueueEmpty() {
		return false;
	}

	@Override
	public void setProcessDeferred(boolean b) {

	}

	@Override
	public void addConceptToStorageQueue(TermConcept termConcept) {

	}

	@Override
	public void addConceptLinkToStorageQueue(TermConceptParentChildLink termConceptParentChildLink) {

	}

	@Override
	public void addConceptMapsToStorageQueue(List<ConceptMap> list) {

	}

	@Override
	public void addValueSetsToStorageQueue(List<ValueSet> list) {

	}

	@Override
	public void deleteCodeSystem(TermCodeSystem termCodeSystem) {

	}

	@Override
	public void deleteCodeSystemForResource(ResourceTable resourceTable) {

	}

	@Override
	public void deleteCodeSystemVersion(TermCodeSystemVersion termCodeSystemVersion) {

	}

	@Override
	public void saveAllDeferred() {

	}

	@Override
	public void logQueueForUnitTest() {

	}
}
