package org.snomed.snowstorm.fhir.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hl7.fhir.r4.model.*;
import org.snomed.snowstorm.fhir.domain.contact.FHIRContactDetail;
import org.snomed.snowstorm.fhir.services.FHIRHelper;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;
import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

@Document(indexName = "fhir-concept-map")
public class FHIRConceptMap {

	public interface Fields {
		String URL = "url";
		String SOURCE = "sourceUri";
		String TARGET = "targetUri";
		String GROUP_SOURCE = "group.source.keyword";
		String GROUP_TARGET = "group.target.keyword";
	}
	@Id
	private String id;

	@Field(type = FieldType.Keyword)
	private String url;

	private FHIRIdentifier identifier;

	@Field(type = FieldType.Keyword)
	private String version;

	@Field(type = FieldType.Keyword)
	private String name;

	@Field(type = FieldType.Keyword)
	private String title;

	@Field(type = FieldType.Keyword)
	private String status;

	private Boolean experimental;

	@Field(type = FieldType.Keyword)
	private String publisher;

	private List<FHIRContactDetail> contact;

	@Field(type = FieldType.Text)
	private String description;

	@Field(type = FieldType.Text)
	private String purpose;

	@Field(type = FieldType.Text)
	private String copyright;

	@Field(type = FieldType.Keyword)
	private String sourceUri;

	@Field(type = FieldType.Keyword)
	private String targetUri;

	private List<FHIRConceptMapGroup> group;

	@JsonIgnore
	private boolean implicitSnomedMap;

	@JsonIgnore
	private String snomedRefsetId;

	@JsonIgnore
	private String snomedRefsetEquivalence;

	public FHIRConceptMap() {
		group = new ArrayList<>();
	}

	public FHIRConceptMap(ConceptMap hapiConceptMap) {
		this();
		id = hapiConceptMap.getId();
		url = hapiConceptMap.getUrl();
		Identifier hapiIdentifier = hapiConceptMap.getIdentifier();
		if (hapiIdentifier != null) {
			identifier = new FHIRIdentifier(hapiIdentifier);
		} else {
			identifier = new FHIRIdentifier();
		}
		version = hapiConceptMap.getVersion();
		name = hapiConceptMap.getName();
		title = hapiConceptMap.getTitle();
		status = hapiConceptMap.getStatus() != null ? hapiConceptMap.getStatus().toCode() : null;
		experimental = hapiConceptMap.hasExperimental() ? hapiConceptMap.getExperimental() : null;
		publisher = hapiConceptMap.getPublisher();
		for (ContactDetail contactDetail : hapiConceptMap.getContact()) {
			if (contact == null) {
				contact = new ArrayList<>();
			}
			contact.add(new FHIRContactDetail(contactDetail));
		}
		description = hapiConceptMap.getDescription();
		purpose = hapiConceptMap.getPurpose();
		copyright = hapiConceptMap.getCopyright();
		if (hapiConceptMap.hasSourceUriType()) {
			sourceUri = hapiConceptMap.getSourceUriType().getValueAsString();
		} else if (hapiConceptMap.hasSourceCanonicalType()) {
			throw FHIRHelper.exception("CanonicalType is not supported for ConceptMap.source.", OperationOutcome.IssueType.NOTSUPPORTED, 400);
		}
		if (hapiConceptMap.hasTargetUriType()) {
			targetUri = hapiConceptMap.getTargetUriType().getValueAsString();
		} else if (hapiConceptMap.hasTargetCanonicalType()) {
			throw FHIRHelper.exception("CanonicalType is not supported for ConceptMap.target.", OperationOutcome.IssueType.NOTSUPPORTED, 400);
		}

		for (ConceptMap.ConceptMapGroupComponent hapiGroup : hapiConceptMap.getGroup()) {
			group.add(new FHIRConceptMapGroup(hapiGroup));
		}
	}

	@JsonIgnore
	public ConceptMap getHapi() {
		ConceptMap map = new ConceptMap();
		map.setId(id);
		map.setUrl(url);
		if (identifier != null) {
			map.setIdentifier(identifier.getHapi());
		}
		map.setVersion(version);
		map.setName(name);
		map.setTitle(title);
		if (status != null) {
			map.setStatus(Enumerations.PublicationStatus.fromCode(status));
		}
		if (experimental != null) {
			map.setExperimental(experimental);
		}
		map.setPublisher(publisher);
		for (FHIRContactDetail contactDetail : orEmpty(contact)) {
			map.addContact(contactDetail.getHapi());
		}
		map.setDescription(description);
		map.setPurpose(purpose);
		map.setCopyright(copyright);
		if (sourceUri != null) {
			map.setSource(new UriType(sourceUri));
		}
		if (targetUri != null) {
			map.setTarget(new UriType(targetUri));
		}
		for (FHIRConceptMapGroup mapGroup : orEmpty(group)) {
			map.addGroup(mapGroup.getHapi());
		}

		if (id != null && id.startsWith("snomed_implicit_map_")) {
			Narrative text = new Narrative();
			text.setStatus(Narrative.NarrativeStatus.GENERATED);
			text.setDivAsString(format("This SNOMED CT Implicit Concept Map from %s to %s is generated using Reference Set %s.",
					sourceUri, targetUri, id.replace("snomed_implicit_map_", "")));
			map.setText(text);
		}

		return map;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public FHIRIdentifier getIdentifier() {
		return identifier;
	}

	public void setIdentifier(FHIRIdentifier identifier) {
		this.identifier = identifier;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Boolean getExperimental() {
		return experimental;
	}

	public void setExperimental(Boolean experimental) {
		this.experimental = experimental;
	}

	public String getPublisher() {
		return publisher;
	}

	public void setPublisher(String publisher) {
		this.publisher = publisher;
	}

	public List<FHIRContactDetail> getContact() {
		return contact;
	}

	public void setContact(List<FHIRContactDetail> contact) {
		this.contact = contact;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getPurpose() {
		return purpose;
	}

	public void setPurpose(String purpose) {
		this.purpose = purpose;
	}

	public String getCopyright() {
		return copyright;
	}

	public void setCopyright(String copyright) {
		this.copyright = copyright;
	}

	public String getSourceUri() {
		return sourceUri;
	}

	public void setSourceUri(String sourceUri) {
		this.sourceUri = sourceUri;
	}

	public String getTargetUri() {
		return targetUri;
	}

	public void setTargetUri(String targetUri) {
		this.targetUri = targetUri;
	}

	public List<FHIRConceptMapGroup> getGroup() {
		return group;
	}

	public void setGroup(List<FHIRConceptMapGroup> group) {
		this.group = group;
	}

	public boolean isImplicitSnomedMap() {
		return implicitSnomedMap;
	}

	public void setImplicitSnomedMap(boolean implicitSnomedMap) {
		this.implicitSnomedMap = implicitSnomedMap;
	}

	public String getSnomedRefsetId() {
		return snomedRefsetId;
	}

	public void setSnomedRefsetId(String snomedRefsetId) {
		this.snomedRefsetId = snomedRefsetId;
	}

	public String getSnomedRefsetEquivalence() {
		return snomedRefsetEquivalence;
	}

	public void setSnomedRefsetEquivalence(String snomedRefsetEquivalence) {
		this.snomedRefsetEquivalence = snomedRefsetEquivalence;
	}
}
