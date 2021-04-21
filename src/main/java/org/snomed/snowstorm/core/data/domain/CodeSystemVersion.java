package org.snomed.snowstorm.core.data.domain;

import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;

@Document(indexName = "codesystem-version")
public class CodeSystemVersion {

	@Field(type = FieldType.Keyword)
	private String id;

	@Field(type = FieldType.Keyword)
	private String shortName;

	@Field(type = FieldType.Date, format = DateFormat.date_optional_time)
	private Date importDate;

	@Field(type = FieldType.Keyword)
	private String parentBranchPath;

	@Field(type = FieldType.Integer)
	private Integer effectiveDate;

	@Field(type = FieldType.Keyword)
	private String version;

	@Field(type = FieldType.Keyword)
	private String description;

	@Field(type = FieldType.Keyword)
	private String releasePackage;

	@Transient
	private Integer dependantVersionEffectiveTime;

	public CodeSystemVersion() {
	}

	public CodeSystemVersion(String shortName, Date importDate, String parentBranchPath, Integer effectiveDate, String version, String description) {
		this.shortName = shortName;
		this.importDate = importDate;
		this.parentBranchPath = parentBranchPath;
		this.effectiveDate = effectiveDate;
		this.version = version;
		this.description = description;
	}

	public String getBranchPath() {
		return parentBranchPath + "/" + version;
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

	public Integer getEffectiveDate() {
		return effectiveDate;
	}

	public void setEffectiveDate(Integer effectiveDate) {
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

	public String getReleasePackage() {
		return releasePackage;
	}

	public void setReleasePackage(String releasePackage) {
		this.releasePackage = releasePackage;
	}

	public Integer getDependantVersionEffectiveTime() {
		return dependantVersionEffectiveTime;
	}

	public void setDependantVersionEffectiveTime(Integer dependantVersionEffectiveTime) {
		this.dependantVersionEffectiveTime = dependantVersionEffectiveTime;
	}
}
