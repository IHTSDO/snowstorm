package com.kaicube.snomed.elasticsnomed.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldIndex;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Document(type = "branch", indexName = "branch")
public class Branch extends Entity {

	@Field(type = FieldType.Date, index = FieldIndex.not_analyzed)
	private Date base;

	@Field(type = FieldType.Date, index = FieldIndex.not_analyzed)
	private Date head;

	@Field(type = FieldType.Boolean, index = FieldIndex.not_analyzed)
	private boolean locked;

	private Set<String> versionsReplaced;

	private Set<String> entitiesRemoved;

	public Branch() {
		head = new Date();
		versionsReplaced = new HashSet<>();
		entitiesRemoved = new HashSet<>();
	}

	public Branch(String path) {
		this();
		setPath(path);
	}

	public void addVersionsReplaced(Set<String> internalIds) {
		if (notMAIN()) {
			versionsReplaced.addAll(internalIds);
		}
	}

	public void addEntitiesRemoved(Set<String> internalIds) {
		if (notMAIN()) {
			entitiesRemoved.addAll(internalIds);
		}
	}

	private boolean notMAIN() {
		return !"MAIN".equals(getPath());
	}

	@JsonIgnore
	public String getFlatPath() {
		return getPath();
	}

	public void setHead(Date head) {
		this.head = head;
	}

	public Date getBase() {
		return base;
	}

	public void setBase(Date base) {
		this.base = base;
	}

	public Date getHead() {
		return head;
	}

	public void setLocked(boolean locked) {
		this.locked = locked;
	}

	public boolean isLocked() {
		return locked;
	}

	public Set<String> getVersionsReplaced() {
		return versionsReplaced;
	}

	public Set<String> getEntitiesRemoved() {
		return entitiesRemoved;
	}

	@Override
	public String toString() {
		return "Branch{" +
				"path=" + getPath() +
				", base=" + getMillis(base) +
				", head=" + getMillis(head) +
				", start=" + getMillis(getStart()) +
				", end=" + getMillis(getEnd()) +
				", versionsReplaced=" + versionsReplaced +
				", entitiesRemoved=" + entitiesRemoved +
				'}';
	}

	private Long getMillis(Date date) {
		return date == null ? null : date.getTime();
	}
}
