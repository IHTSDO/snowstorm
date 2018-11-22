package org.snomed.snowstorm.core.data.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Objects;

/**
 * Represents an edition or extension of SNOMED-CT
 */
@Document(indexName = "es-codesystem", type = "codesystem", shards = 8)
public class CodeSystem {

	public interface Fields {
		String SHORT_NAME = "shortName";
		String BRANCH_PATH = "branchPath";
	}

	@Id
	@Field(type = FieldType.keyword)
	private String shortName;

	@Field(type = FieldType.keyword)
	private String branchPath;

	public CodeSystem() {
	}

	public CodeSystem(String shortName, String branchPath) {
		this.shortName = shortName;
		this.branchPath = branchPath;
	}

	public String getShortName() {
		return shortName;
	}

	public void setShortName(String shortName) {
		this.shortName = shortName;
	}

	public String getBranchPath() {
		return branchPath;
	}

	public void setBranchPath(String branchPath) {
		this.branchPath = branchPath;
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
