package com.kaicube.snomed.elasticsnomed.domain;

import com.kaicube.snomed.elasticsnomed.services.PathUtil;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldIndex;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Document(type = "concept", indexName = "snomed")
public class Concept {

	@Id
	private String id;

	private Long conceptId;

	private String fsn;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String path;

	@Field(type = FieldType.Date)
	private Date commit;

	@Field(type = FieldType.Nested)
	private Set<Description> descriptions;

	public Concept() {
		descriptions = new HashSet<>();
	}

	public Concept(Long id, String fsn) {
		this.conceptId = id;
		this.fsn = fsn;
	}

	public void addDescription(Description description) {
		descriptions.add(description);
	}

	public void setCommit(Date commit) {
		this.commit = commit;
	}

	public Date getCommit() {
		return commit;
	}

	public Long getConceptId() {
		return conceptId;
	}

	public void setConceptId(Long conceptId) {
		this.conceptId = conceptId;
	}

	public String getFsn() {
		return fsn;
	}

	public void setFsn(String fsn) {
		this.fsn = fsn;
	}

	public Set<Description> getDescriptions() {
		return descriptions;
	}

	public String getPath() {
		return path;
	}

	public String getFatPath() {
		return PathUtil.fatten(path);
	}

	public void setPath(String path) {
		this.path = PathUtil.flaten(path);
	}

	@Override
	public String toString() {
		return "Concept{" +
				"id=" + id +
				", conceptId=" + conceptId +
				", fsn='" + fsn + '\'' +
				", path='" + path + '\'' +
				", commit='" + commit + '\'' +
				'}';
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
}
