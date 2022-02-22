package org.snomed.snowstorm.fhir.domain;

import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Enumerations;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "fhir-codesystem-version")
public class FHIRCodeSystemVersion {

	@Id
	private String id;

	@Field(type = FieldType.Keyword)
	private String url;

	@Field(type = FieldType.Keyword)
	private String version;

	@Field(type = FieldType.Keyword)
	private String title;

	@Field(type = FieldType.Keyword)
	private String status;

	@Field(type = FieldType.Keyword)
	private String publisher;

	@Field(type = FieldType.Keyword)
	private String hierarchyMeaning;

	@Field(type = FieldType.Boolean)
	private boolean compositional;

	public FHIRCodeSystemVersion() {
	}

	public FHIRCodeSystemVersion(CodeSystem codeSystem) {
		System.out.println(codeSystem);
		url = codeSystem.getUrl();
		String id = codeSystem.getId();
		if (id == null) {
			id = url.replace("http://", "").replace("/", "_");
			this.id = id;
		} else {
			this.id = codeSystem.getId().replace("CodeSystem/", "");
		}
		version = codeSystem.getVersion();
		title = codeSystem.getTitle();
		if (title != null) {
			title = title.replace(" Code System", "");
		}
		Enumerations.PublicationStatus status = codeSystem.getStatus();
		this.status = status != null ? status.toCode() : Enumerations.PublicationStatus.ACTIVE.toCode();
		publisher = codeSystem.getPublisher();
		CodeSystem.CodeSystemHierarchyMeaning hierarchyMeaning = codeSystem.getHierarchyMeaning();
		this.hierarchyMeaning = hierarchyMeaning != null ? hierarchyMeaning.toCode() : null;
		compositional = codeSystem.getCompositional();
	}

	public CodeSystem toHapiCodeSystem() {
		CodeSystem codeSystem = new CodeSystem();
		codeSystem.setId(id);
		codeSystem.setUrl(url);
		codeSystem.setVersion(version);
		codeSystem.setTitle(title);
		codeSystem.setStatus(Enumerations.PublicationStatus.fromCode(status));
		codeSystem.setPublisher(publisher);
		if (hierarchyMeaning != null) {
			codeSystem.setHierarchyMeaning(CodeSystem.CodeSystemHierarchyMeaning.fromCode(hierarchyMeaning));
		}
		codeSystem.setCompositional(compositional);
		return codeSystem;
	}

	public String getIdAndVersion() {
		return id + ((version == null || version.isEmpty()) ? "" : "_" + version);
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

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
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

	public String getPublisher() {
		return publisher;
	}

	public void setPublisher(String publisher) {
		this.publisher = publisher;
	}

	public String getHierarchyMeaning() {
		return hierarchyMeaning;
	}

	public void setHierarchyMeaning(String hierarchyMeaning) {
		this.hierarchyMeaning = hierarchyMeaning;
	}

	public boolean isCompositional() {
		return compositional;
	}

	public void setCompositional(boolean compositional) {
		this.compositional = compositional;
	}
}
