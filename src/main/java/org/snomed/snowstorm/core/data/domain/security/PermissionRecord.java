package org.snomed.snowstorm.core.data.domain.security;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.rest.View;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Set;

@Document(indexName = "admin-permission")
@JsonPropertyOrder({"role", "path", "global", "userGroups"})
public class PermissionRecord {

	public interface Fields {
		String ROLE = "role";
		String GLOBAL = "global";
		String PATH = "path";
		String USER_GROUPS = "userGroups";
	}

	@Id
	@Field(type = FieldType.keyword)
	private String key;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.Boolean)
	private boolean global;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.keyword)
	private String role;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.keyword)
	private String path;

	// These may or may not be of type Role.
	@JsonView(value = View.Component.class)
	@Field(type = FieldType.keyword)
	private Set<String> userGroups;

	// For Jackson
	private PermissionRecord() {
	}

	public PermissionRecord(String role) {
		this.role = role;
	}

	public PermissionRecord(String role, String path) {
		this.role = role;
		this.path = path;
	}

	// Composite key enforces uniqueness, not used for lookup, not API visible.
	public String getKey() {
		return (path != null ? path : "global") + "-" + role;
	}

	public boolean isGlobal() {
		return path == null;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public Set<String> getUserGroups() {
		return userGroups;
	}

	public void setUserGroups(Set<String> userGroups) {
		this.userGroups = userGroups;
	}
}
