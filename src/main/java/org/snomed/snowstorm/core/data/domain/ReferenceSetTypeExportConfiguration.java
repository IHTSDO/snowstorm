package org.snomed.snowstorm.core.data.domain;

import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/*
 * Instances of this class are created automatically using the "refset.types" section of the application configuration.
 * Each instance represents an abstract reference set type that can be exported. Each type has a unique name and set of fields.
 * Actual reference sets, with members, must exist in the SNOMED CT hierarchy as a descendant of the correct refset type in order to be exported and have the expected fields.
 */
public class ReferenceSetTypeExportConfiguration {

	private String conceptId;
	private String name;
	private String fieldNames;
	private String fieldTypes;
	private String exportDir;

	public ReferenceSetTypeExportConfiguration() {
	}

	public ReferenceSetTypeExportConfiguration(String conceptId, String name, String fieldNames, String fieldTypes, String exportDir) {
		this.conceptId = conceptId;
		this.name = name;
		this.fieldNames = fieldNames;
		this.fieldTypes = fieldTypes;
		this.exportDir = exportDir;
	}

	public String getConceptId() {
		return conceptId;
	}

	public String getName() {
		return name;
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
		ReferenceSetTypeExportConfiguration that = (ReferenceSetTypeExportConfiguration) o;
		return conceptId.equals(that.conceptId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(conceptId);
	}

	@Override
	public String toString() {
		return conceptId + "|" + name;
	}
}
