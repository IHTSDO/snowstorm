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
import org.snomed.snowstorm.fhir.services.FHIRHelper;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.*;

import java.util.*;

import static org.snomed.snowstorm.fhir.config.FHIRConstants.*;

@Document(indexName = "#{@indexNameProvider.indexName('fhir-codesystem-version')}", createIndex = false)
public class FHIRCodeSystemVersion {

	@Id
	private String id;

	@Field(type = FieldType.Keyword)
	private String url;

	@Field(type = FieldType.Keyword)
	private String version;

	@Field(type = FieldType.Keyword)
	private String language;

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

	@Field(type = FieldType.Boolean)
	private boolean experimental;

	@Field(type = FieldType.Boolean)
	private boolean caseSensitive = true;

	@Field(type = FieldType.Keyword)
	private String content;

	@Field(type = FieldType.Keyword)
	private Set<String> availableLanguages;

	@Field(type = FieldType.Nested)
	private List<FHIRExtension> extensions;

	@Transient
	private String snomedBranch;

	@Transient
	private org.snomed.snowstorm.core.data.domain.CodeSystem snomedCodeSystem;

	private static final DateTimeFormatter dateFormat = DateTimeFormat.forPattern("yyyyMMdd");
	private static final Logger logger = LoggerFactory.getLogger(FHIRCodeSystemVersion.class);

	public FHIRCodeSystemVersion() {
	}

	public FHIRCodeSystemVersion(CodeSystem codeSystem) {
		url = codeSystem.getUrl();
		String id = codeSystem.getId();
		if (id == null) {
			// Spec: https://build.fhir.org/resource.html#id
			// "Ids can be up to 64 characters long, and contain any combination of upper and lowercase ASCII letters, numerals, "-" and ".""
			id = url.replace("http://", "").replaceAll("[^a-zA-Z0-9.-]", "-");
			this.id = id;
		} else {
			this.id = codeSystem.getId().replace("CodeSystem/", "");
		}
		version = codeSystem.getVersion();
		experimental = codeSystem.getExperimental();
		caseSensitive = codeSystem.getCaseSensitive();
		date = codeSystem.getDate();
		title = codeSystem.getTitle();
		language = codeSystem.getLanguage();
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
		codeSystem.getExtension().stream().forEach( e -> {
			if (extensions == null){
				extensions = new ArrayList<>();
			}
			extensions.add(new FHIRExtension(e));
		});
		codeSystem.getConcept().stream().forEach( c->{
			c.getDesignation().stream().forEach( d ->{
				if (d.getLanguage()!=null){
					getAvailableLanguages().add(d.getLanguage());
				}
			});
		});
	}

	public FHIRCodeSystemVersion(CodeSystemVersion snomedVersion) {
		this(snomedVersion.getCodeSystem(), false);
		url = SNOMED_URI;

		String moduleId = snomedVersion.getCodeSystem().getUriModuleId();
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
		snomedCodeSystem = snomedVersion.getCodeSystem();
	}

	public FHIRCodeSystemVersion(org.snomed.snowstorm.core.data.domain.CodeSystem snomedCodeSystem, boolean unversioned) {
		name = SNOMED_CT;
		url = SNOMED_URI;
		title = snomedCodeSystem.getName();
		status = Enumerations.PublicationStatus.ACTIVE.toCode();
		publisher = snomedCodeSystem.getOwner() != null ? snomedCodeSystem.getOwner() : SNOMED_INTERNATIONAL;
		hierarchyMeaning = CodeSystem.CodeSystemHierarchyMeaning.ISA.toCode();
		compositional = true;
		String moduleId = snomedCodeSystem.getUriModuleId();
		content = CodeSystem.CodeSystemContentMode.COMPLETE.toCode();
		if (unversioned) {
			id = FHIRCodeSystemService.SCT_ID_PREFIX + moduleId + "_" + UNVERSIONED;
			url = SNOMED_URI_UNVERSIONED;
			version = SNOMED_URI_UNVERSIONED + "/" + moduleId;
		}
		snomedBranch = snomedCodeSystem.getBranchPath();
		var languages = snomedCodeSystem.getLanguages();
		if (languages != null) {
			language = languages.containsKey("en") ? "en" : languages.values().iterator().next();
			availableLanguages = new HashSet<>(languages.keySet());
		}
		this.snomedCodeSystem = snomedCodeSystem;
	}

	public CodeSystem toHapiCodeSystem() {
		CodeSystem codeSystem = new CodeSystem();
		codeSystem.setExtension(Optional.ofNullable(extensions).orElse(Collections.emptyList()).stream().map(fe -> fe.getHapi()).toList());
		codeSystem.setId(id);
		codeSystem.setUrl(url);
		if(!"0".equals(version)) {
			codeSystem.setVersion(version);
		}
		codeSystem.setLanguage(language);
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
		if (snomedCodeSystem != null && snomedCodeSystem.getParentUriModuleId() != null) {
			String supplements = SNOMED_URI + "|" + SNOMED_URI + "/" + snomedCodeSystem.getParentUriModuleId() + VERSION + snomedCodeSystem.getDependantVersionEffectiveTime();
			codeSystem.setSupplements(supplements);
		}
		return codeSystem;
	}

	public boolean isOnSnomedBranch() {
		return snomedBranch != null;
	}

	public boolean isSnomedUnversioned() {
		return SNOMED_URI_UNVERSIONED.equals(url);
	}

	public boolean isVersionMatch(String requestedVersion) {
		if (requestedVersion == null || requestedVersion.equals(version)) return true;
		return FHIRHelper.isSnomedUri(getUrl()) && version.substring(0, version.indexOf(VERSION)).equals(requestedVersion);
	}

	public String getCanonical() {
		if ("0".equals(version)){
			return url;
		} else {
			return url + "|" + version;
		}
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

	public List<FHIRExtension> getExtensions() {
		if (extensions == null){
			extensions = new ArrayList<>();
		}
		return extensions;
	}

	public void setExtensions(List<FHIRExtension> extensions) {
		this.extensions = extensions;
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

	public boolean isExperimental() {
		return experimental;
	}

	public boolean isCaseSensitive() {
		return caseSensitive;
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

	@JsonIgnore
	public org.snomed.snowstorm.core.data.domain.CodeSystem getSnomedCodeSystem() {
		return snomedCodeSystem;
	}

	@Override
	public String toString() {
		return getId();
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public Set<String> getAvailableLanguages() {
		return availableLanguages == null ? new HashSet<>() : availableLanguages;
	}

	public void setAvailableLanguages(Set<String> availableLanguages) {
		this.availableLanguages = availableLanguages;
	}
}
