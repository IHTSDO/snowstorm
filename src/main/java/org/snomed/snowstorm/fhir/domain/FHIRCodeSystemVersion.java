package org.snomed.snowstorm.fhir.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Enumerations;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.fhir.services.FHIRCodeSystemService;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;

import static org.snomed.snowstorm.fhir.config.FHIRConstants.*;

@Document(indexName = "fhir-codesystem-version")
public class FHIRCodeSystemVersion {

	@Id
	private String id;

	@Field(type = FieldType.Keyword)
	private String url;

	@Field(type = FieldType.Keyword)
	private String version;

	@Field(type = FieldType.Date, format = DateFormat.date_time)
	private Date date;

	@Field(type = FieldType.Keyword)
	private String name;

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

	@Field(type = FieldType.Keyword)
	private String content;

	@Transient
	private String snomedBranch;

	private static final DateTimeFormatter dateFormat = DateTimeFormat.forPattern("yyyyMMdd");
	private static final Logger logger = LoggerFactory.getLogger(FHIRCodeSystemVersion.class);

	public FHIRCodeSystemVersion() {
	}

	public FHIRCodeSystemVersion(CodeSystem codeSystem) {
		url = codeSystem.getUrl();
		String id = codeSystem.getId();
		if (id == null) {
			id = url.replace("http://", "").replace("/", "_");
			this.id = id;
		} else {
			this.id = codeSystem.getId().replace("CodeSystem/", "");
		}
		version = codeSystem.getVersion();
		date = codeSystem.getDate();
		title = codeSystem.getTitle();
		if (title != null) {
			title = title.replace(" Code System", "");
		}
		name = codeSystem.getName();
		Enumerations.PublicationStatus status = codeSystem.getStatus();
		this.status = status != null ? status.toCode() : Enumerations.PublicationStatus.ACTIVE.toCode();
		publisher = codeSystem.getPublisher();
		CodeSystem.CodeSystemHierarchyMeaning hierarchyMeaning = codeSystem.getHierarchyMeaning();
		this.hierarchyMeaning = hierarchyMeaning != null ? hierarchyMeaning.toCode() : null;
		compositional = codeSystem.getCompositional();
		CodeSystem.CodeSystemContentMode codeSystemContent = codeSystem.getContent();
		content = codeSystemContent != null ? codeSystemContent.toCode() : null;
	}

	public FHIRCodeSystemVersion(CodeSystemVersion snomedVersion) {
		this(snomedVersion.getCodeSystem());

		String moduleId = snomedVersion.getCodeSystem().getDefaultModuleId();
		id = FHIRCodeSystemService.SCT_ID_PREFIX + moduleId + "_" + snomedVersion.getEffectiveDate();
		version = SNOMED_URI + "/" + moduleId + VERSION + snomedVersion.getEffectiveDate();
		if (title == null) {
			title = "SNOMED CT release " + snomedVersion.getVersion();
		}
		try {
			DateTime dateTime = dateFormat.parseDateTime(snomedVersion.getEffectiveDate().toString());
			date = dateTime.toDate();
		} catch (IllegalArgumentException e) {
			logger.warn("Failed to parse effective time of code system version {}", snomedVersion);
		}
		snomedBranch = snomedVersion.getBranchPath();
	}

	public FHIRCodeSystemVersion(org.snomed.snowstorm.core.data.domain.CodeSystem snomedCodeSystem) {
		this(snomedCodeSystem, false);
	}

	public FHIRCodeSystemVersion(org.snomed.snowstorm.core.data.domain.CodeSystem snomedCodeSystem, boolean unversioned) {
		name = SNOMED_CT;
		url = SNOMED_URI;
		title = snomedCodeSystem.getName();
		status = Enumerations.PublicationStatus.ACTIVE.toCode();
		publisher = snomedCodeSystem.getOwner() != null ? snomedCodeSystem.getOwner() : SNOMED_INTERNATIONAL;
		hierarchyMeaning = CodeSystem.CodeSystemHierarchyMeaning.ISA.toCode();
		compositional = true;
		content = CodeSystem.CodeSystemContentMode.COMPLETE.toCode();
		if (unversioned) {
			url = SNOMED_URI_UNVERSIONED;
			String moduleId = snomedCodeSystem.getDefaultModuleId();
			id = FHIRCodeSystemService.SCT_ID_PREFIX + moduleId + UNVERSIONED;
			version = SNOMED_URI_UNVERSIONED + "/" + moduleId;
			snomedBranch = snomedCodeSystem.getBranchPath();
		}
	}

	public CodeSystem toHapiCodeSystem() {
		CodeSystem codeSystem = new CodeSystem();
		codeSystem.setId(id);
		codeSystem.setUrl(url);
		codeSystem.setVersion(version);
		codeSystem.setDate(date);
		codeSystem.setName(name);
		codeSystem.setTitle(title);
		codeSystem.setStatus(Enumerations.PublicationStatus.fromCode(status));
		codeSystem.setPublisher(publisher);
		if (hierarchyMeaning != null) {
			codeSystem.setHierarchyMeaning(CodeSystem.CodeSystemHierarchyMeaning.fromCode(hierarchyMeaning));
		}
		codeSystem.setCompositional(compositional);
		if (content != null) {
			codeSystem.setContent(CodeSystem.CodeSystemContentMode.fromCode(content));
		}
		return codeSystem;
	}

	public boolean isSnomed() {
		return snomedBranch != null;
	}

	public boolean isSnomedUnversioned() {
		return SNOMED_URI_UNVERSIONED.equals(url);
	}

	public String getCanonical() {
		return url + "|" + version;
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

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
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

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	@JsonIgnore
	public String getSnomedBranch() {
		return snomedBranch;
	}

	public void setSnomedBranch(String snomedBranch) {
		this.snomedBranch = snomedBranch;
	}

	@Override
	public String toString() {
		return getId();
	}
}
