package org.snomed.snowstorm.core.data.domain.jobs;

import org.snomed.snowstorm.core.rf2.RF2Type;
import org.springframework.data.elasticsearch.annotations.Document;

import javax.validation.constraints.NotNull;

@Document(indexName = "es-export", type = "export", shards = 8)
public final class ExportConfiguration {

	@NotNull
	private String branchPath;

	@NotNull
	private RF2Type type;

	private String filenameEffectiveDate;

	private String id;

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

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
}
