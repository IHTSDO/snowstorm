package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.rest.View;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Objects;

@Document(indexName = "identifier")
@JsonPropertyOrder({"alternateIdentifier", "effectiveTime", "active", "moduleId", "identifierSchemeId", "referencedComponentId",  "released", "releasedEffectiveTime"})
public class Identifier extends SnomedComponent<Identifier> {

	public interface Fields extends SnomedComponent.Fields {
		String INTERNAL_IDENTIFIER_ID = "internalIdentifierId";
		String ALTERNATE_IDENTIFIER = "alternateIdentifier";
		String IDENTIFIER_SCHEME_ID = "identifierSchemeId";
		String REFERENCED_COMPONENT_ID = "referencedComponentId";
	}

	@Field(type = FieldType.Keyword, store = true)
	private String internalIdentifierId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.Keyword, store = true)
	@NotNull
	private String alternateIdentifier;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.Keyword, store = true)
	@Size(min = 5, max = 18)
	@NotNull
	private String identifierSchemeId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.Keyword, store = true)
	@NotNull
	@Size(min = 5, max = 18)
	private String referencedComponentId;

	public Identifier() {
		active = true;
		setModuleId(Concepts.CORE_MODULE);
	}

	public Identifier(String alternateIdentifier, Integer effectiveTime, boolean active, String moduleId, String identifierSchemeId, String referencedComponentId) {
		this.setInternalIdentifierId(alternateIdentifier + "-" + identifierSchemeId);
		this.identifierSchemeId = identifierSchemeId;
		this.alternateIdentifier = alternateIdentifier;
		setEffectiveTimeI(effectiveTime);
		this.active = active;
		setModuleId(moduleId);
		this.referencedComponentId = referencedComponentId;
	}

	@Override
	public String getId() {
		return this.getInternalIdentifierId();
	}

	@Override
	public String getIdField() {
		return Fields.INTERNAL_IDENTIFIER_ID;
	}

	@Override
	public boolean isComponentChanged(Identifier that) {
		return that == null
				|| alternateIdentifier != that.alternateIdentifier
				|| isActive() != that.isActive()
				|| !getModuleId().equals(that.getModuleId())
				|| !alternateIdentifier.equals(that.alternateIdentifier)
				|| !referencedComponentId.equals(that.referencedComponentId);
	}

	public void setInternalIdentifierId(String internalIdentifierId) {
		this.internalIdentifierId = internalIdentifierId;
	}

	@JsonIgnore
	public String getInternalIdentifierId() {
		return internalIdentifierId;
	}

	public String getAlternateIdentifier() {
		return alternateIdentifier;
	}

	public String getReferencedComponentId() {
		return referencedComponentId;
	}

	public String getIdentifierSchemeId() {
		return identifierSchemeId;
	}

	@Override
	protected Object[] getReleaseHashObjects() {
		return new Object[]{alternateIdentifier, active, getModuleId(), identifierSchemeId, referencedComponentId};
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Identifier that = (Identifier) o;
		return active == that.active &&
				Objects.equals(identifierSchemeId, that.identifierSchemeId) &&
				Objects.equals(alternateIdentifier, that.alternateIdentifier) &&
				Objects.equals(getEffectiveTimeI(), that.getEffectiveTimeI()) &&
				Objects.equals(referencedComponentId, that.referencedComponentId) &&
				Objects.equals(getModuleId(), that.getModuleId());
	}

	@Override
	public int hashCode() {
		return Objects.hash(alternateIdentifier, getEffectiveTimeI(), active, getModuleId(), identifierSchemeId, referencedComponentId);
	}

	@Override
	public String toString() {
		return "Identifier{" +
				"alternateIdentifier='" + alternateIdentifier + '\'' +
				", effectiveTime='" + getEffectiveTime() + '\'' +
				", active=" + active + '\'' +
				", moduleId='" + getModuleId() + '\'' +
				", identifierSchemeId='" + identifierSchemeId + '\'' +
				", referencedComponentId='" + referencedComponentId + '\'' +
				'}';
	}
}
