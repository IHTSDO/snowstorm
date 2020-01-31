package org.snomed.snowstorm.core.data.domain.jobs;

import io.swagger.annotations.ApiModelProperty;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.springframework.data.elasticsearch.annotations.Document;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.Date;
import java.util.Set;

@Document(indexName = "export-config", type = "exportconfiguration")
public class ExportConfiguration {

	private String id;

	private Date startDate;

	@NotNull
	private String branchPath;

	@NotNull
	@ApiModelProperty(value = "DELTA")
	private RF2Type type;

	@Pattern(regexp = "[0-9]{8}")
	private String filenameEffectiveDate;

	@ApiModelProperty(value = "false")
	private boolean conceptsAndRelationshipsOnly;

	@ApiModelProperty(notes = "Format: yyyymmdd. Add a transient effectiveTime to rows of content which are not yet versioned.")
	@Pattern(regexp = "[0-9]{8}")
	private String transientEffectiveTime;

	@ApiModelProperty(notes = "Format: yyyymmdd. Can be used to produce a delta after content is versioned by filtering a SNAPSHOT export by effectiveTime.")
	@Pattern(regexp = "[0-9]{8}")
	private String startEffectiveTime;

	private Set<String> moduleIds;

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
}
