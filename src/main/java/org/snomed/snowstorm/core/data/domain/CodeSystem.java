package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.snomed.snowstorm.core.data.domain.fieldpermissions.CodeSystemCreate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an edition or extension of SNOMED-CT
 */
@Document(indexName = "codesystem")
@JsonPropertyOrder({"name", "shortName", "branchPath", "dependantVersionEffectiveTime", "dailyBuildAvailable",
		"countryCode", "defaultLanguageReferenceSets", "maintainerType", "latestVersion", "languages", "modules"})
public class CodeSystem implements CodeSystemCreate {

	public interface Fields {
		String SHORT_NAME = "shortName";
		String BRANCH_PATH = "branchPath";
	}

	@Id
	@Field(type = FieldType.keyword)
	@NotNull
	private String shortName;

	@Field(type = FieldType.keyword)
	private String name;

	@Field(type = FieldType.keyword)
	private String countryCode;

	@Field(type = FieldType.keyword)
	private String maintainerType;

	@Field(type = FieldType.keyword)
	private String defaultLanguageCode;

	@Field(type = FieldType.keyword)
	private String[] defaultLanguageReferenceSets;

	@Field(type = FieldType.keyword)
	@NotNull
	@Pattern(regexp = "MAIN.*")
	private String branchPath;

	@Field(type = FieldType.Boolean)
	private boolean dailyBuildAvailable;

	@Transient
	private Integer dependantVersionEffectiveTime;

	@Transient
	private Map<String, String> languages;

	@Transient
	private Collection<ConceptMini> modules;

	@Transient
	private CodeSystemVersion latestVersion;

	public CodeSystem() {
	}

	public CodeSystem(String shortName, String branchPath) {
		this.shortName = shortName;
		this.branchPath = branchPath;
	}

	public CodeSystem(String shortName, String branchPath, String name, String countryCode) {
		this.shortName = shortName;
		this.branchPath = branchPath;
		this.name = name;
		this.countryCode = countryCode;
	}

	public String getShortName() {
		return shortName;
	}

	public void setShortName(String shortName) {
		this.shortName = shortName;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public void setCountryCode(String countryCode) {
		this.countryCode = countryCode;
	}

	public String getMaintainerType() {
		return maintainerType;
	}

	public void setMaintainerType(String maintainerType) {
		this.maintainerType = maintainerType;
	}

	public String getDefaultLanguageCode() {
		return defaultLanguageCode;
	}

	public void setDefaultLanguageCode(String defaultLanguageCode) {
		this.defaultLanguageCode = defaultLanguageCode;
	}

	public String[] getDefaultLanguageReferenceSets() {
		return defaultLanguageReferenceSets;
	}

	public void setDefaultLanguageReferenceSets(String[] defaultLanguageReferenceSets) {
		this.defaultLanguageReferenceSets = defaultLanguageReferenceSets;
	}

	public String getBranchPath() {
		return branchPath;
	}

	public void setBranchPath(String branchPath) {
		this.branchPath = branchPath;
	}

	public boolean isDailyBuildAvailable() {
		return dailyBuildAvailable;
	}

	public void setDailyBuildAvailable(boolean dailyBuildAvailable) {
		this.dailyBuildAvailable = dailyBuildAvailable;
	}

	public Integer getDependantVersionEffectiveTime() {
		return dependantVersionEffectiveTime;
	}

	public void setDependantVersionEffectiveTime(Integer dependantVersionEffectiveTime) {
		this.dependantVersionEffectiveTime = dependantVersionEffectiveTime;
	}

	public Map<String, String> getLanguages() {
		return languages;
	}

	public void setLanguages(Map<String, String> languages) {
		this.languages = languages;
	}

	public Collection<ConceptMini> getModules() {
		return modules;
	}

	public void setModules(Collection<ConceptMini> modules) {
		this.modules = modules;
	}

	public CodeSystemVersion getLatestVersion() {
		return latestVersion;
	}

	public void setLatestVersion(CodeSystemVersion latestVersion) {
		this.latestVersion = latestVersion;
	}

	@Override
	public String toString() {
		return shortName;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CodeSystem that = (CodeSystem) o;
		return Objects.equals(shortName, that.shortName) &&
				Objects.equals(branchPath, that.branchPath);
	}

	@Override
	public int hashCode() {
		return Objects.hash(shortName, branchPath);
	}
}
