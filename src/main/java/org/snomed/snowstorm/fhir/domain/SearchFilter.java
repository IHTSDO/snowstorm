package org.snomed.snowstorm.fhir.domain;

import org.hl7.fhir.r4.model.ValueSet;
import org.apache.commons.lang.NotImplementedException;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.fhir.services.FHIRHelper;
import org.snomed.snowstorm.fhir.services.FHIROperationException;

import ca.uhn.fhir.rest.param.*;

public class SearchFilter {
	
	String id;
	String code;
	TokenParam context;
	QuantityParam contextQuantity;
	String contextType;
	String date;
	StringParam description;
	String expansion;
	StringParam identifier;
	StringParam jurisdiction;
	StringParam name;
	StringParam publisher;
	StringParam reference;
	PublicationStatus status;
	StringParam title;
	String url;
	String version;
	
	public SearchFilter withId(String id) throws FHIROperationException {
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
	public SearchFilter withCode(String code) throws FHIROperationException {
		if (code != null) {
			throw new FHIROperationException(IssueType.TOOCOSTLY, "Server is unwilling to expand all known ValueSets to search for inclusion of any code");
		}
		return this;
	}
	public TokenParam getContext() {
		return context;
	}
	public SearchFilter withContext(TokenParam context) {
		//TODO Might have to split this myself.  HAPI doesn't seem to be happy with a Coding
		//says it's an unknown search parameter type
		this.context = context;
		return this;
	}
	public QuantityParam getContextQuantity() {
		return contextQuantity;
	}
	public SearchFilter withContextQuantity(QuantityParam contextQuantity) {
		this.contextQuantity = contextQuantity;
		if (contextQuantity != null) {
			throw new UnsupportedOperationException();
		}
		return this;
	}
	public String getContextType() {
		return contextType;
	}
	public SearchFilter withContextType(String contextType) {
		this.contextType = contextType;
		if (contextType != null) {
			throw new UnsupportedOperationException();
		}
		return this;
	}
	public String getDate() {
		return date;
	}
	public SearchFilter withDate(String date) {
		this.date = date;
		if (date != null) {
			throw new UnsupportedOperationException();
		}
		return this;
	}
	public StringParam getDescription() {
		return description;
	}
	public SearchFilter withDescription(StringParam description) {
		this.description = description;
		return this;
	}
	public String getExpansion() {
		return expansion;
	}
	public SearchFilter withExpansion(String expansion) {
		this.expansion = expansion;
		if (expansion != null) {
			throw new UnsupportedOperationException();
		}
		return this;
	}
	public StringParam getIdentifier() {
		return identifier;
	}
	public SearchFilter withIdentifier(StringParam identifier) {
		this.identifier = identifier;
		return this;
	}
	public StringParam getJurisdiction() {
		return jurisdiction;
	}
	public SearchFilter withJurisdiction(StringParam jurisdiction) {
		this.jurisdiction = jurisdiction;
		return this;
	}
	public StringParam getName() {
		return name;
	}
	public SearchFilter withName(StringParam name) {
		this.name = name;
		return this;
	}
	public StringParam getPublisher() {
		return publisher;
	}
	public SearchFilter withPublisher(StringParam publisher) {
		this.publisher = publisher;
		return this;
	}
	public StringParam getReference() {
		return reference;
	}
	public SearchFilter withReference(StringParam reference) {
		this.reference = reference;
		if (reference != null) {
			throw new UnsupportedOperationException();
		}
		return this;
	}
	public PublicationStatus getStatus() {
		return status;
	}
	public SearchFilter withStatus(String statusStr) {
		this.status = PublicationStatus.fromCode(statusStr);
		return this;
	}
	public StringParam getTitle() {
		return title;
	}
	public SearchFilter withTitle(StringParam title) {
		this.title = title;
		return this;
	}
	public String getUrl() {
		return url;
	}
	public SearchFilter withUrl(String url) {
		this.url = url;
		return this;
	}
	public String getVersion() {
		return version;
	}
	public SearchFilter withVersion(String version) {
		this.version = version;
		return this;
	}
	
	public boolean apply(ValueSet vs, QueryService queryService, FHIRHelper fhirHelper) {
		
		if (getId() != null && !getId().equals(vs.getId())) {
			return false;
		}
		
		if (getContext() != null && !fhirHelper.hasUsageContext(vs, getContext())) {
			return false;
		}
		
		if (!fhirHelper.stringMatches(vs.getDescription(), getDescription()))  {
			return false;
		}
		
		if (getIdentifier() != null && !fhirHelper.hasIdentifier(vs, getIdentifier())) {
			return false;
		}
		
		if (getJurisdiction() != null && !fhirHelper.hasJurisdiction(vs, getJurisdiction())) {
			return false;
		}
		
		if (!fhirHelper.stringMatches(vs.getName(), getName())) {
			return false;
		}
		
		if (!fhirHelper.stringMatches(vs.getPublisher(), getPublisher())) {
			return false;
		}
		
		if (!fhirHelper.enumerationMatches (vs.getStatus(), getStatus())) {
			return false;
		}
		
		if (!fhirHelper.stringMatches(vs.getTitle(), getTitle())) {
			return false;
		}
		
		if (getUrl() != null && !getUrl().equals(vs.getUrl())) {
			return false;
		}
		
		if (getVersion() != null && !getVersion().equals(vs.getVersion())) {
			return false;
		}
			
		return true;
	}
	
	public boolean apply(StructureDefinition sd, FHIRHelper fhirHelper) {
		throw new NotImplementedException();
	}
	
	public boolean apply(CodeSystem cs, FHIRHelper fhirHelper) {
		if (getId() != null && !getId().equals(cs.getId())) {
			return false;
		}
		
		if (getContext() != null && !fhirHelper.hasUsageContext(cs, getContext())) {
			return false;
		}
		
		if (!fhirHelper.stringMatches(cs.getDescription(), getDescription()))  {
			return false;
		}
		
		if (getIdentifier() != null && !fhirHelper.hasIdentifier(cs, getIdentifier())) {
			return false;
		}
		
		if (getJurisdiction() != null && !fhirHelper.hasJurisdiction(cs, getJurisdiction())) {
			return false;
		}
		
		if (!fhirHelper.stringMatches(cs.getName(), getName())) {
			return false;
		}
		
		if (!fhirHelper.stringMatches(cs.getPublisher(), getPublisher())) {
			return false;
		}
		
		if (!fhirHelper.enumerationMatches (cs.getStatus(), getStatus())) {
			return false;
		}
		
		if (!fhirHelper.stringMatches(cs.getTitle(), getTitle())) {
			return false;
		}
		
		if (getUrl() != null && !getUrl().equals(cs.getUrl())) {
			return false;
		}
		
		if (getVersion() != null && !getVersion().equals(cs.getVersion())) {
			return false;
		}
			
		return true;
	}

}
