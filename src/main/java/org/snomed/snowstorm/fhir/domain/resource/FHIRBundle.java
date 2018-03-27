package org.snomed.snowstorm.fhir.domain.resource;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.snomed.snowstorm.fhir.domain.element.FHIREntry;

public class FHIRBundle extends FHIRResource {
	
	public long total;
	Collection<FHIREntry> entry;
	
	public FHIRBundle (List<? extends FHIRResource> resources) {
		this.entry = resources.stream().map(resource -> new FHIREntry(resource)).collect(Collectors.toList());
	}
	
	public enum Type {
		Document, message, transaction, transaction_response,
		batch, batch_response, history, searchset, collection;
		
		@Override
		public String toString() {
			return name().toLowerCase().replaceAll("_", "-");
		}
	}
}
