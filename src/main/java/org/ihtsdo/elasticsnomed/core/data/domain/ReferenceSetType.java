package org.ihtsdo.elasticsnomed.core.data.domain;

import com.google.common.collect.Lists;
import io.kaicode.elasticvc.domain.DomainEntity;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldIndex;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.List;

// TODO: This should probably not be a DomainEntity because it should apply to all branches without needing rebase.
@Document(indexName = "es-refset", type = "refset-type", shards = 8)
public class ReferenceSetType extends DomainEntity<ReferenceSetType> {

	public static final String FIELD_ID = "conceptId";

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String conceptId;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String fieldNames;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String fieldTypes;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String exportDir;

	public ReferenceSetType() {
	}

	public ReferenceSetType(String conceptId, String fieldNames, String fieldTypes, String exportDir) {
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

	public String getConceptId() {
		return conceptId;
	}

	public String getFieldNames() {
		return fieldNames;
	}

	public List<String> getFieldNameList() {
		return Lists.newArrayList(fieldNames.split("\\|"));
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

		return conceptId.equals(that.conceptId);
	}

	@Override
	public int hashCode() {
		return conceptId.hashCode();
	}

	@Override
	public String toString() {
		return "ReferenceSetType{" +
				"conceptId='" + conceptId + '\'' +
				", fieldNames='" + fieldNames + '\'' +
				", fieldTypes='" + fieldTypes + '\'' +
				", exportDir='" + exportDir + '\'' +
				", entity=" + super.toString() +
				'}';
	}
}
