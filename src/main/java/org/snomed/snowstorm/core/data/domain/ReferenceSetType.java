package org.snomed.snowstorm.core.data.domain;

import com.google.common.collect.Lists;
import io.kaicode.elasticvc.domain.DomainEntity;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Document(indexName = "es-refset", type = "refset-type")
public class ReferenceSetType extends DomainEntity<ReferenceSetType> {

	public interface Fields extends SnomedComponent.Fields {
		String NAME = "name";
		String CONCEPT_ID = "conceptId";
		String EXPORT_DIR = "exportDir";
	}

	public static final String FIELD_ID = "conceptId";

	@Field(type = FieldType.keyword)
	private String name;

	@Field(type = FieldType.keyword)
	private String conceptId;

	@Field(type = FieldType.keyword)
	private String fieldNames;

	@Field(type = FieldType.keyword)
	private String fieldTypes;

	@Field(type = FieldType.keyword)
	private String exportDir;

	public ReferenceSetType() {
	}

	public ReferenceSetType(String name, String conceptId, String fieldNames, String fieldTypes, String exportDir) {
		this.name = name;
		this.conceptId = conceptId;
		this.fieldNames = fieldNames;
		this.fieldTypes = fieldTypes;
		this.exportDir = exportDir;
		markChanged();
	}

	@Override
	public String getId() {
		return conceptId;
	}

	@Override
	public boolean isComponentChanged(ReferenceSetType existingComponent) {
		return true;
	}

	public String getName() {
		return name;
	}

	public String getConceptId() {
		return conceptId;
	}

	public String getFieldNames() {
		return fieldNames;
	}

	public List<String> getFieldNameList() {
		return fieldNames.isEmpty() ? Collections.emptyList() : Lists.newArrayList(fieldNames.split(","));
	}

	public String getFieldTypes() {
		return fieldTypes;
	}

	public String getExportDir() {
		return exportDir;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ReferenceSetType that = (ReferenceSetType) o;
		return Objects.equals(name, that.name) &&
				Objects.equals(conceptId, that.conceptId) &&
				Objects.equals(fieldNames, that.fieldNames) &&
				Objects.equals(fieldTypes, that.fieldTypes) &&
				Objects.equals(exportDir, that.exportDir);
	}

	@Override
	public int hashCode() {

		return Objects.hash(name, conceptId, fieldNames, fieldTypes, exportDir);
	}

	@Override
	public String toString() {
		return name + "|" + conceptId;
	}
}
