package org.snomed.snowstorm.core.data.domain;

import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;

@Document(indexName = "es-codesystem-version", type = "codesystem-v", shards = 8)
public class CodeSystemVersion {

	@Field(type = FieldType.keyword)
	private String id;

	@Field(type = FieldType.keyword)
	private String shortName;

	@Field(type = FieldType.Date)
	private Date importDate;

	@Field(type = FieldType.keyword)
	private String parentBranchPath;

	@Field(type = FieldType.keyword)
	private String effectiveDate;

	@Field(type = FieldType.keyword)
	private String version;

	@Field(type = FieldType.keyword)
	private String description;

	public CodeSystemVersion() {
	}

	public CodeSystemVersion(String shortName, Date importDate, String parentBranchPath, String effectiveDate, String version, String description) {
		this.shortName = shortName;
		this.importDate = importDate;
		this.parentBranchPath = parentBranchPath;
		this.effectiveDate = effectiveDate;
		this.version = version;
		this.description = description;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getShortName() {
		return shortName;
	}

	public void setShortName(String shortName) {
		this.shortName = shortName;
	}

	public Date getImportDate() {
		return importDate;
	}

	public void setImportDate(Date importDate) {
		this.importDate = importDate;
	}

	public String getParentBranchPath() {
		return parentBranchPath;
	}

	public void setParentBranchPath(String parentBranchPath) {
		this.parentBranchPath = parentBranchPath;
	}

	public String getEffectiveDate() {
		return effectiveDate;
	}

	public void setEffectiveDate(String effectiveDate) {
		this.effectiveDate = effectiveDate;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}
