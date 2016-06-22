package com.kaicube.snomed.elasticsnomed.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kaicube.snomed.elasticsnomed.services.PathUtil;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.Date;

@Document(type = "branch", indexName = "branch")
public class Branch {

	@Id
	private String id;
	private String path;
	private Date base;
	private Date head;

	public Branch() {
	}

	public Branch(String path) {
		this.path = PathUtil.flaten(path);
		head = new Date();
	}

	public String getPath() {
		return path;
	}

	public String getFatPath() {
		return PathUtil.fatten(path);
	}

	@JsonIgnore
	public String getFlatPath() {
		return path;
	}

	public void setHead(Date head) {
		this.head = head;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
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

	@Override
	public String toString() {
		return "Branch{" +
				"id='" + id + '\'' +
				", path='" + path + '\'' +
				'}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Branch branch = (Branch) o;

		return path != null ? path.equals(branch.path) : branch.path == null;

	}

	@Override
	public int hashCode() {
		return path != null ? path.hashCode() : 0;
	}
}
