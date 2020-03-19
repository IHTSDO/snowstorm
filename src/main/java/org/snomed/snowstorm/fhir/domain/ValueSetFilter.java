package org.snomed.snowstorm.fhir.domain;

import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.fhir.services.FHIRHelper;
import org.snomed.snowstorm.fhir.services.FHIROperationException;

import ca.uhn.fhir.rest.param.QuantityParam;

public class ValueSetFilter {
	
	String id;
	String code;
	String context;
	QuantityParam contextQuantity;
	String contextType;
	String date;
	String description;
	String expansion;
	String identifier;
	String jurisdiction;
	String name;
	String publisher;
	String reference;
	PublicationStatus status;
	String title;
	String url;
	String version;
	
	public ValueSetFilter withId(String id) throws FHIROperationException {
		if (id != null && !id.startsWith ("ValueSet/")) {
			id = "ValueSet/" + id;
		}
		this.id = id;
		return this;
	}
	public String getId() {
		return id;
	}
	public String getCode() {
		return code;
	}
	public ValueSetFilter withCode(String code) throws FHIROperationException {
		if (code != null) {
			throw new FHIROperationException(IssueType.TOOCOSTLY, "Server is unwilling to expand all known ValueSets to search for inclusion of any code");
		}
		return this;
	}
	public String getContext() {
		return context;
	}
	public ValueSetFilter withContext(String context) {
		this.context = context;
		return this;
	}
	public QuantityParam getContextQuantity() {
		return contextQuantity;
	}
	public ValueSetFilter withContextQuantity(QuantityParam contextQuantity) {
		this.contextQuantity = contextQuantity;
		if (contextQuantity != null) {
			throw new UnsupportedOperationException();
		}
		return this;
	}
	public String getContextType() {
		return contextType;
	}
	public ValueSetFilter withContextType(String contextType) {
		this.contextType = contextType;
		if (contextType != null) {
			throw new UnsupportedOperationException();
		}
		return this;
	}
	public String getDate() {
		return date;
	}
	public ValueSetFilter withDate(String date) {
		this.date = date;
		if (date != null) {
			throw new UnsupportedOperationException();
		}
		return this;
	}
	public String getDescription() {
		return description;
	}
	public ValueSetFilter withDescription(String description) {
		this.description = description;
		return this;
	}
	public String getExpansion() {
		return expansion;
	}
	public ValueSetFilter withExpansion(String expansion) {
		this.expansion = expansion;
		if (expansion != null) {
			throw new UnsupportedOperationException();
		}
		return this;
	}
	public String getIdentifier() {
		return identifier;
	}
	public ValueSetFilter withIdentifier(String identifier) {
		this.identifier = identifier;
		return this;
	}
	public String getJurisdiction() {
		return jurisdiction;
	}
	public ValueSetFilter withJurisdiction(String jurisdiction) {
		this.jurisdiction = jurisdiction;
		return this;
	}
	public String getName() {
		return name;
	}
	public ValueSetFilter withName(String name) {
		this.name = name;
		return this;
	}
	public String getPublisher() {
		return publisher;
	}
	public ValueSetFilter withPublisher(String publisher) {
		this.publisher = publisher;
		return this;
	}
	public String getReference() {
		return reference;
	}
	public ValueSetFilter withReference(String reference) {
		this.reference = reference;
		if (reference != null) {
			throw new UnsupportedOperationException();
		}
		return this;
	}
	public PublicationStatus getStatus() {
		return status;
	}
	public ValueSetFilter withStatus(String statusStr) {
		this.status = PublicationStatus.fromCode(statusStr);
		return this;
	}
	public String getTitle() {
		return title;
	}
	public ValueSetFilter withTitle(String title) {
		this.title = title;
		return this;
	}
	public String getUrl() {
		return url;
	}
	public ValueSetFilter withUrl(String url) {
		this.url = url;
		return this;
	}
	public String getVersion() {
		return version;
	}
	public ValueSetFilter withVersion(String version) {
		this.version = version;
		return this;
	}
	public static boolean apply(ValueSetFilter filter, ValueSet vs, 
			QueryService queryService, FHIRHelper fhirHelper) {
		
		if (filter.getId() != null && !filter.getId().equals(vs.getId())) {
			return false;
		}
		
		if (filter.getContext()!= null && ! fhirHelper.hasUsageContext(vs, filter.getContext())) {
			return false;
		}
		
		if (filter.getIdentifier() != null && !fhirHelper.hasIdentifier(vs, filter.getIdentifier())) {
			return false;
		}
		
		if (filter.getJurisdiction() != null && !fhirHelper.hasJurisdiction(vs, filter.getJurisdiction())) {
			return false;
		}
		
		if (filter.getName() != null && 
				(vs.getName() == null || 
				!vs.getName().toLowerCase().contains(filter.getName().toLowerCase()))) {
			return false;
		}
		
		if (filter.getPublisher() != null && !filter.getPublisher().toLowerCase().equals(vs.getPublisher().toLowerCase())) {
			return false;
		}
		
		if (filter.getStatus() != null && !filter.getStatus().equals(vs.getStatus())) {
			return false;
		}
		
		if (filter.getTitle() != null && !vs.getTitle().toLowerCase().contains(filter.getTitle().toLowerCase())) {
			return false;
		}
		
		if (filter.getUrl() != null && !filter.getUrl().equals(vs.getUrl())) {
			return false;
		}
		
		if (filter.getVersion() != null && !filter.getVersion().equals(vs.getVersion())) {
			return false;
		}
			
		return true;
	}
	
}
