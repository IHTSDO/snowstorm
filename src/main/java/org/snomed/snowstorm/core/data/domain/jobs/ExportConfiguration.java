package org.snomed.snowstorm.core.data.domain.jobs;

import io.swagger.v3.oas.annotations.media.Schema;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Document(indexName = "#{@indexNameProvider.indexName('export-config')}", createIndex = false)
public class ExportConfiguration {

	private String id;

	private Date startDate;

	@NotNull
	private String branchPath;

	@NotNull
	@Schema(defaultValue = "DELTA")
	private RF2Type type;

	@Pattern(regexp = "[0-9]{8}")
	private String filenameEffectiveDate;

	@Schema(defaultValue = "false")
	private boolean conceptsAndRelationshipsOnly;

	@Schema(defaultValue = "false")
	private boolean unpromotedChangesOnly;

	@Schema(defaultValue = "false")
	private boolean legacyZipNaming;

	@Schema(description = "Format: yyyymmdd. Add a transient effectiveTime to rows of content which are not yet versioned.")
	@Pattern(regexp = "[0-9]{8}")
	private String transientEffectiveTime;

	@Schema(description = "Format: yyyymmdd. Can be used to produce a delta after content is versioned by filtering a SNAPSHOT export by effectiveTime.")
	@Pattern(regexp = "[0-9]{8}")
	private String startEffectiveTime;

	private Set<String> moduleIds = new HashSet<>();

	@Schema(description = "If refsetIds are included, this indicates that the export will be a refset-only export.")
	private Set<String> refsetIds;

	@Schema(defaultValue = "PENDING")
	private ExportStatus status;

	@Schema
	@Field(type = FieldType.Keyword)
	private String exportFilePath;

	@Schema(defaultValue = "false")
	private boolean startExport;

	public ExportConfiguration() {
	}

	public ExportConfiguration(String branchPath, RF2Type type) {
		this.branchPath = branchPath;
		this.type = type;
	}

	public String getBranchPath() {
		return branchPath;
	}

	public void setBranchPath(String branchPath) {
		this.branchPath = branchPath;
	}

	public RF2Type getType() {
		return type;
	}

	public void setType(RF2Type type) {
		this.type = type;
	}

	public String getFilenameEffectiveDate() {
		return filenameEffectiveDate;
	}

	public void setFilenameEffectiveDate(String filenameEffectiveDate) {
		this.filenameEffectiveDate = filenameEffectiveDate;
	}

	public boolean isConceptsAndRelationshipsOnly() {
		return conceptsAndRelationshipsOnly;
	}

	public void setConceptsAndRelationshipsOnly(boolean conceptsAndRelationshipsOnly) {
		this.conceptsAndRelationshipsOnly = conceptsAndRelationshipsOnly;
	}

	public boolean isUnpromotedChangesOnly() {
		return unpromotedChangesOnly;
	}

	public void setUnpromotedChangesOnly(boolean unpromotedChangesOnly) {
		this.unpromotedChangesOnly = unpromotedChangesOnly;
	}

	public boolean isLegacyZipNaming() {
		return legacyZipNaming;
	}

	public void setLegacyZipNaming(boolean legacyZipNaming) {
		this.legacyZipNaming = legacyZipNaming;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setStartDate(Date startDate) {
		this.startDate = startDate;
	}

	public Date getStartDate() {
		return startDate;
	}

	public void setTransientEffectiveTime(String transientEffectiveTime) {
		this.transientEffectiveTime = transientEffectiveTime;
	}

	public String getTransientEffectiveTime() {
		return this.transientEffectiveTime;
	}

	public String getStartEffectiveTime() {
		return startEffectiveTime;
	}

	public void setStartEffectiveTime(String startEffectiveTime) {
		this.startEffectiveTime = startEffectiveTime;
	}

	public Set<String> getModuleIds() {
		return moduleIds;
	}

	public void setModuleIds(Set<String> moduleIds) {
		this.moduleIds = moduleIds;
	}
	
	public Set<String> getRefsetIds() {
		return refsetIds;
	}

	public void setRefsetIds(Set<String> refsetIds) {
		this.refsetIds = refsetIds;
	}

	public ExportStatus getStatus() {
		return status;
	}

	public void setStatus(ExportStatus status) {
		this.status = status;
	}

	public String getExportFilePath() {
		return exportFilePath;
	}

	public void setExportFilePath(String exportFilePath) {
		this.exportFilePath = exportFilePath;
	}

	public boolean isStartExport() {
		return startExport;
	}

	public void setStartExport(boolean startExport) {
		this.startExport = startExport;
	}
}
