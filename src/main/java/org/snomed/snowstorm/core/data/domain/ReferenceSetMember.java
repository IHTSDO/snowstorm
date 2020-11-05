package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.rest.View;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.*;

@Document(indexName = "member")
public class ReferenceSetMember extends SnomedComponent<ReferenceSetMember> implements ReferenceSetMemberView {

	public interface Fields extends SnomedComponent.Fields {
		String MEMBER_ID = "memberId";
		String REFSET_ID = "refsetId";
		String CONCEPT_ID = "conceptId";// Non-standard field. See variable for comments.
		String REFERENCED_COMPONENT_ID = "referencedComponentId";
		String ADDITIONAL_FIELDS = "additionalFields";
		String ADDITIONAL_FIELDS_PREFIX = ADDITIONAL_FIELDS + ".";

		static String getAdditionalFieldTextTypeMapping(String fieldname) {
			return ADDITIONAL_FIELDS_PREFIX + fieldname;
		}

		static String getAdditionalFieldKeywordTypeMapping(String fieldname) {
			// Elasticsearch automatically creates an additional field for these,
			// useful for matching on the exact string.
			return ADDITIONAL_FIELDS_PREFIX + fieldname + ".keyword";
		}
	}
	
	public interface AssociationFields {
		String TARGET_COMP_ID = "targetComponentId";
		String MAP_TARGET = "mapTarget";
	}

	public interface LanguageFields {
		String ACCEPTABILITY_ID = "acceptabilityId";
		String ACCEPTABILITY_ID_FIELD_PATH = Fields.ADDITIONAL_FIELDS_PREFIX + ACCEPTABILITY_ID;
	}

	public interface OwlExpressionFields {
		String OWL_EXPRESSION = "owlExpression";
		String OWL_EXPRESSION_FIELD_PATH = Fields.ADDITIONAL_FIELDS_PREFIX + OWL_EXPRESSION;
		String OWL_EXPRESSION_KEYWORD_FIELD_PATH = Fields.getAdditionalFieldKeywordTypeMapping(OWL_EXPRESSION);
	}

	public interface MRCMAttributeDomainFields {
		String GROUPED = "grouped";
	}

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.Keyword)
	private String memberId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.Keyword)
	@NotNull
	@Size(min = 5, max = 18)
	private String moduleId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.Keyword)
	@NotNull
	@Size(min = 5, max = 18)
	private String refsetId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.Keyword, store = true)
	@NotNull
	@Size(min = 5, max = 18)
	private String referencedComponentId;

	// Used when the member can be considered to be part of a concept referencedComponentId is a concept or description
	@Field(type = FieldType.Keyword, store = true)
	private String conceptId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.Object)
	private Map<String, String> additionalFields;

	@Transient
	@JsonIgnore
	private ConceptMini referencedComponentConceptMini;

	@Transient
	@JsonView(value = View.Component.class)
	private SnomedComponent<?> referencedComponent;

	public ReferenceSetMember() {
		active = true;
		moduleId = Concepts.CORE_MODULE;
		additionalFields = new HashMap<>();
	}

	public ReferenceSetMember(String memberId, Integer effectiveTime, boolean active, String moduleId, String refsetId,
			String referencedComponentId) {
		this();
		this.memberId = memberId;
		setEffectiveTimeI(effectiveTime);
		this.active = active;
		this.moduleId = moduleId;
		this.refsetId = refsetId;
		this.referencedComponentId = referencedComponentId;
	}

	public ReferenceSetMember(String moduleId, String refsetId, String referencedComponentId) {
		this(UUID.randomUUID().toString(), null, true, moduleId, refsetId, referencedComponentId);
	}

	@Override
	public String getIdField() {
		return Fields.MEMBER_ID;
	}

	@Override
	public boolean isComponentChanged(ReferenceSetMember that) {
		return that == null
				|| active != that.isActive()
				|| !moduleId.equals(that.getModuleId())
				|| !additionalFields.equals(that.getAdditionalFields());
	}

	@Override
	protected Object[] getReleaseHashObjects() {
		Object[] hashObjects = new Object[2 + (additionalFields.size() * 2)];
		hashObjects[0] = active;
		hashObjects[1] = moduleId;
		int a = 2;
		for (String key : new TreeSet<>(additionalFields.keySet())) {
			hashObjects[a++] = key;
			hashObjects[a++] = additionalFields.get(key);
		}
		return hashObjects;
	}

	public String getAdditionalField(String fieldName) {
		return getAdditionalFields().get(fieldName);
	}

	public ReferenceSetMember setAdditionalField(String fieldName, String value) {
		getAdditionalFields().put(fieldName, value);
		return this;
	}

	public void setReferencedComponentConceptMini(ConceptMini referencedComponentConceptMini) {
		this.referencedComponentConceptMini = referencedComponentConceptMini;
	}

	public Object getReferencedComponent() {
		if (this.referencedComponent != null) {
			return this.referencedComponent;
		}

		return this.referencedComponentConceptMini;
	}

	public void setReferencedComponent(SnomedComponent<?> referencedComponent) {
		this.referencedComponent = referencedComponent;
	}

	@Override
	@JsonIgnore
	public String getId() {
		return getMemberId();
	}

	public String getMemberId() {
		return memberId;
	}

	public void setMemberId(String memberId) {
		this.memberId = memberId;
	}

	public String getModuleId() {
		return moduleId;
	}

	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}

	public String getRefsetId() {
		return refsetId;
	}

	public void setRefsetId(String refsetId) {
		this.refsetId = refsetId;
	}

	public String getReferencedComponentId() {
		return referencedComponentId;
	}

	public ReferenceSetMember setReferencedComponentId(String referencedComponentId) {
		this.referencedComponentId = referencedComponentId;
		return this;
	}

	public String getConceptId() {
		return conceptId;
	}

	public ReferenceSetMember setConceptId(String conceptId) {
		this.conceptId = conceptId;
		return this;
	}

	public Map<String, String> getAdditionalFields() {
		return additionalFields;
	}

	public void setAdditionalFields(Map<String, String> additionalFields) {
		this.additionalFields = additionalFields;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ReferenceSetMember that = (ReferenceSetMember) o;

		if (memberId != null && memberId.equals(that.memberId)) {
			return true;
		}

		return Objects.equals(moduleId, that.moduleId) &&
				Objects.equals(refsetId, that.refsetId) &&
				Objects.equals(referencedComponentId, that.referencedComponentId) &&
				Objects.equals(conceptId, that.conceptId) &&
				Objects.equals(additionalFields, that.additionalFields);
	}

	@Override
	public int hashCode() {
		if (memberId != null) {
			return memberId.hashCode();
		}
		return Objects.hash(memberId, moduleId, refsetId, referencedComponentId, conceptId, additionalFields);
	}

	@Override
	public String toString() {
		return "ReferenceSetMember{" +
				"memberId='" + memberId + '\'' +
				", effectiveTime='" + getEffectiveTimeI() + '\'' +
				", active=" + active +
				", moduleId='" + moduleId + '\'' +
				", refsetId='" + refsetId + '\'' +
				", referencedComponentId='" + referencedComponentId + '\'' +
				", additionalFields='" + additionalFields + '\'' +
				", conceptId='" + conceptId + '\'' +
				", internalId='" + getInternalId() + '\'' +
				", start='" + getStart() + '\'' +
				", end='" + getEnd() + '\'' +
				", path='" + getPath() + '\'' +
				'}';
	}

}
