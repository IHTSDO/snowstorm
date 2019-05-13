package org.snomed.snowstorm.core.data.domain.jobs;

import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Collection;
import java.util.UUID;

/**
 * During authoring SCTID identifiers are assigned to new content.
 * These identifiers are stored using documents of this type until
 * they are registered with the Component Identifier Service.
 * Once the identifiers are registered these documents are deleted.
 */
@Document(indexName = "identifiers-for-registration")
public class IdentifiersForRegistration {

	private String id;

	@Field(type = FieldType.Integer)
	private int namespace;

	@Field(type = FieldType.Long)
	private Collection<Long> ids;

	public IdentifiersForRegistration(int namespace, Collection<Long> ids) {
		id = UUID.randomUUID().toString();
		this.namespace = namespace;
		this.ids = ids;
	}

	public IdentifiersForRegistration() {
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public int getNamespace() {
		return namespace;
	}

	public void setNamespace(int namespace) {
		this.namespace = namespace;
	}

	public Collection<Long> getIds() {
		return ids;
	}

	public void setIds(Collection<Long> ids) {
		this.ids = ids;
	}
}
