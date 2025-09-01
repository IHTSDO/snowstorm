package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.snomed.snowstorm.core.data.domain.fieldpermissions.CodeSystemCreate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.snomed.snowstorm.fhir.config.FHIRConstants.SNOMED_URI;

/**
 * Represents an edition or extension of SNOMED-CT
 */
@Document(indexName = "#{@indexNameProvider.indexName('codesystem')}", createIndex = false)
@JsonPropertyOrder({"name", "owner", "shortName", "branchPath", "uriModuleId", "uri", "dependantVersionEffectiveTime", "dailyBuildAvailable", "latestDailyBuild",
		"countryCode", "defaultLanguageCode", "defaultLanguageReferenceSets", "maintainerType", "latestVersion", "languages", "modules"})
public class CodeSystem implements CodeSystemCreate {

	public interface Fields {
		String SHORT_NAME = "shortName";
		String BRANCH_PATH = "branchPath";
	}

	@Id
	@Field(type = FieldType.Keyword)
	@NotNull
	private String shortName;

	@Field(type = FieldType.Keyword)
	private String uriModuleId;

	@Field(type = FieldType.Keyword)
	private String name;

	@Field(type = FieldType.Keyword)
	private String owner;

	@Field(type = FieldType.Keyword)
	private String countryCode;

	@Field(type = FieldType.Keyword)
	private String maintainerType;

	@Field(type = FieldType.Keyword)
	private String defaultLanguageCode;

	@Field(type = FieldType.Keyword)
	private String[] defaultLanguageReferenceSets;

	@Field(type = FieldType.Keyword)
	@NotNull
	@Pattern(regexp = "MAIN.*")
	private String branchPath;

	@Field(type = FieldType.Boolean)
	private boolean dailyBuildAvailable;

	@Field(type = FieldType.Keyword)
	private String latestDailyBuild;

	@Transient
	private String defaultModuleId;

	@Transient
	private Integer dependantVersionEffectiveTime;

	@Transient
	@JsonIgnore
	private String parentUriModuleId;

	@Transient
	private CodeSystemVersion latestVersion;

	@Transient
	private Map<String, String> languages;

	@Transient
	private Collection<ConceptMini> modules;

	@Transient
	private Set<String> userRoles;

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

	@JsonIgnore
	public String getShortCode() {
		if (shortName != null && shortName.contains("-")) {
			return shortName.substring(shortName.indexOf("-") + 1);
		}
		return null;
	}

	public String getShortName() {
		return shortName;
	}

	public void setShortName(String shortName) {
		this.shortName = shortName;
	}

	public String getUriModuleId() {
		return uriModuleId;
	}

	public CodeSystem setUriModuleId(String uriModuleId) {
		this.uriModuleId = uriModuleId;
		return this;
	}

	/**
	 * SNOMED CT Edition URI, this is generated from the URI Module ID if that is set.
	 * @return SNOMED CT Edition URI or null if the URI Module ID is not set.
	 */
	public String getUri() {
		return uriModuleId != null ? String.join("/", SNOMED_URI, uriModuleId) : null;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getOwner() {
		return owner;
	}

	public CodeSystem setOwner(String owner) {
		this.owner = owner;
		return this;
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

	public String getLatestDailyBuild() {
		return latestDailyBuild;
	}

	public void setLatestDailyBuild(String latestDailyBuild) {
		this.latestDailyBuild = latestDailyBuild;
	}

	@JsonIgnore
	public String getDefaultModuleId() {
		return defaultModuleId;
	}

	public void setDefaultModuleId(String defaultModuleId) {
		this.defaultModuleId = defaultModuleId;
	}

	public Integer getDependantVersionEffectiveTime() {
		return dependantVersionEffectiveTime;
	}

	public void setDependantVersionEffectiveTime(Integer dependantVersionEffectiveTime) {
		this.dependantVersionEffectiveTime = dependantVersionEffectiveTime;
	}

	public String getParentUriModuleId() {
		return parentUriModuleId;
	}

	public void setParentUriModuleId(String parentUriModuleId) {
		this.parentUriModuleId = parentUriModuleId;
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

	public Set<String> getUserRoles() {
		return userRoles;
	}

	public void setUserRoles(Set<String> userRoles) {
		this.userRoles = userRoles;
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
