package org.ihtsdo.elasticsnomed.core.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import org.ihtsdo.elasticsnomed.rest.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldIndex;
import org.springframework.data.elasticsearch.annotations.FieldType;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Document(type = "description", indexName = "es-description", shards = 8)
public class Description extends SnomedComponent<Description> implements SnomedComponentWithInactivationIndicator, SnomedComponentWithAssociations {

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@Size(min = 5, max = 18)
	private String descriptionId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.Boolean, index = FieldIndex.not_analyzed)
	private boolean active;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.analyzed)
	@NotNull
	@Size(min = 1, max = 255)
	private String term;

	@Field(type = FieldType.Integer)
	private int termLen;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	private String conceptId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@NotNull
	@Size(min = 5, max = 18)
	private String moduleId;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@NotNull
	@Size(min = 2, max = 2)
	private String languageCode;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@NotNull
	@Size(min = 5, max = 18)
	private String typeId;

	@Field(type = FieldType.String, index = FieldIndex.not_analyzed)
	@NotNull
	@Size(min = 5, max = 18)
	private String caseSignificanceId;

	// Populated when requesting an update
	private Map<String, String> acceptabilityMap;

	@JsonIgnore
	// Populated manually when loading from store
	private Map<String, ReferenceSetMember> langRefsetMembers;

	@JsonIgnore
	private ReferenceSetMember inactivationIndicatorMember;

	@JsonIgnore
	// Populated when requesting an update
	private String inactivationIndicatorName;

	@JsonIgnore
	private Set<ReferenceSetMember> associationTargetMembers;

	@JsonIgnore
	// Populated when requesting an update
	private Map<String, Set<String>> associationTargetStrings;

	private static final Logger logger = LoggerFactory.getLogger(Description.class);

	public Description() {
		active = true;
		term = "";
		moduleId = "";
		languageCode = "";
		typeId = "";
		caseSignificanceId = "";
		acceptabilityMap = new HashMap<>();
		langRefsetMembers = new HashMap<>();
	}

	public Description(String term) {
		this();
		setTerm(term);
	}

	public Description(String id, String term) {
		this(term);
		this.descriptionId = id;
	}

	public Description(String id, String effectiveTime, boolean active, String moduleId, String conceptId, String languageCode, String typeId, String term, String caseSignificanceId) {
		this();
		this.descriptionId = id;
		setEffectiveTime(effectiveTime);
		this.active = active;
		this.moduleId = moduleId;
		this.conceptId = conceptId;
		this.languageCode = languageCode;
		this.typeId = typeId;
		setTerm(term);
		this.caseSignificanceId = caseSignificanceId;
	}

	@Override
	public boolean isComponentChanged(Description that) {
		return that == null
				|| active != that.active
				|| !term.equals(that.term)
				|| !moduleId.equals(that.moduleId)
				|| !languageCode.equals(that.languageCode)
				|| !typeId.equals(that.typeId)
				|| !caseSignificanceId.equals(that.caseSignificanceId);
	}

	@Override
	protected Object[] getReleaseHashObjects() {
		return new Object[] {active, term, moduleId, languageCode, typeId, caseSignificanceId};
	}

	@JsonView(value = View.Component.class)
	public String getType() {
		return Concepts.descriptionTypeNames.get(typeId);
	}

	public void setType(String type) {
		typeId = Concepts.descriptionTypeNames.inverse().get(type);
	}

	@JsonView(value = View.Component.class)
	public String getCaseSignificance() {
		return Concepts.caseSignificanceNames.get(caseSignificanceId);
	}

	public Description setCaseSignificance(String caseSignificance) {
		caseSignificanceId = Concepts.caseSignificanceNames.inverse().get(caseSignificance);
		return this;
	}

	@JsonView(value = View.Component.class)
	public String getLang() {
		return languageCode;
	}

	public void setLang(String languageCode) {
		this.languageCode = languageCode;
	}

	public void clearLanguageRefsetMembers() {
		langRefsetMembers.clear();
	}

	public Description addLanguageRefsetMember(ReferenceSetMember member) {
		member.setReferencedComponentId(descriptionId);
		final ReferenceSetMember previousMember = langRefsetMembers.put(member.getRefsetId(), member);
		if (previousMember != null) {
			logger.debug("Lang member replaced other:\n{}\n{}", member, previousMember);
		}
		return this;
	}

	public Description addLanguageRefsetMember(String refsetId, String acceptability) {
		final ReferenceSetMember member = new ReferenceSetMember(moduleId, refsetId, descriptionId);
		member.setAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID, acceptability);
		final ReferenceSetMember previousMember = langRefsetMembers.put(member.getRefsetId(), member);
		if (previousMember != null) {
			logger.debug("Lang member replaced other:\n{}\n{}", member, previousMember);
		}
		return this;
	}

	@Override
	@JsonIgnore
	public String getId() {
		return descriptionId;
	}

	public Map<String, String> getAcceptabilityMapFromLangRefsetMembers() {
		Map<String, String> map = new HashMap<>();
		for (ReferenceSetMember member : langRefsetMembers.values()) {
			if (member.isActive()) map.put(member.getRefsetId(), Concepts.descriptionAcceptabilityNames.get(member.getAdditionalField("acceptabilityId")));
		}
		return map;
	}

	public Map<String, ReferenceSetMember> getLangRefsetMembers() {
		return langRefsetMembers;
	}

	@JsonView(value = View.Component.class)
	public Map<String, String> getAcceptabilityMap() {
		if (!langRefsetMembers.isEmpty()) {
			return getAcceptabilityMapFromLangRefsetMembers();
		} else {
			return acceptabilityMap;
		}
	}

	public Description setAcceptabilityMap(Map<String, String> acceptabilityMap) {
		this.acceptabilityMap = acceptabilityMap;
		return this;
	}

	@JsonView(value = View.Component.class)
	public String getInactivationIndicator() {
		if (inactivationIndicatorMember != null && inactivationIndicatorMember.isActive()) {
			return Concepts.inactivationIndicatorNames.get(inactivationIndicatorMember.getAdditionalField("valueId"));
		}
		return inactivationIndicatorName;
	}

	public void setInactivationIndicator(String inactivationIndicatorName) {
		this.inactivationIndicatorName = inactivationIndicatorName;
	}

	public void addAssociationTargetMember(ReferenceSetMember member) {
		if (associationTargetMembers == null) {
			associationTargetMembers = new HashSet<>();
		}
		associationTargetMembers.add(member);
	}

	@JsonView(value = View.Component.class)
	public Map<String, Set<String>> getAssociationTargets() {
		if (associationTargetMembers != null) {
			Map<String, Set<String>> map = new HashMap<>();
			associationTargetMembers.stream().filter(ReferenceSetMember::isActive).forEach(member -> {
				final String refsetId = member.getRefsetId();
				String association = Concepts.historicalAssociationNames.get(refsetId);
				if (association == null) {
					association = refsetId;
				}
				Set<String> associationType = map.computeIfAbsent(association, k -> new HashSet<>());
				associationType.add(member.getAdditionalField("targetComponentId"));
			});
			return map;
		}
		return associationTargetStrings;
	}

	public void setAssociationTargets(Map<String, Set<String>> associationTargetStrings) {
		this.associationTargetStrings = associationTargetStrings;
	}

	public Set<ReferenceSetMember> getAssociationTargetMembers() {
		return associationTargetMembers;
	}

	public String getDescriptionId() {
		return descriptionId;
	}

	public void setDescriptionId(String descriptionId) {
		this.descriptionId = descriptionId;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		this.term = term;
		if (term != null) {
			termLen = term.length();
		}
	}

	public void setTermLen(int termLen) {
		this.termLen = termLen;
	}

	public int getTermLen() {
		return termLen;
	}

	public String getConceptId() {
		return conceptId;
	}

	public void setConceptId(String conceptId) {
		this.conceptId = conceptId;
	}

	public String getModuleId() {
		return moduleId;
	}

	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}

	public String getLanguageCode() {
		return languageCode;
	}

	public void setLanguageCode(String languageCode) {
		this.languageCode = languageCode;
	}

	public String getTypeId() {
		return typeId;
	}

	public Description setTypeId(String typeId) {
		this.typeId = typeId;
		return this;
	}

	public String getCaseSignificanceId() {
		return caseSignificanceId;
	}

	public void setCaseSignificanceId(String caseSignificanceId) {
		this.caseSignificanceId = caseSignificanceId;
	}

	public ReferenceSetMember getInactivationIndicatorMember() {
		return inactivationIndicatorMember;
	}

	public void setInactivationIndicatorMember(ReferenceSetMember inactivationIndicatorMember) {
		this.inactivationIndicatorMember = inactivationIndicatorMember;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Description that = (Description) o;

		if (descriptionId != null && descriptionId.equals(that.descriptionId)) {
			return true;
		}

		if (term != null ? !term.equals(that.term) : that.term != null) return false;
		if (languageCode != null ? !languageCode.equals(that.languageCode) : that.languageCode != null) return false;
		return typeId != null ? typeId.equals(that.typeId) : that.typeId == null;
	}

	@Override
	public int hashCode() {
		int result = descriptionId != null ? descriptionId.hashCode() : 0;
		if (result != 0) {
			return result;
		}
		result = 31 * result + (term != null ? term.hashCode() : 0);
		result = 31 * result + (languageCode != null ? languageCode.hashCode() : 0);
		result = 31 * result + (typeId != null ? typeId.hashCode() : 0);
		return result;
	}

	@Override
	public String toString() {
		return "Description{" +
				"descriptionId='" + descriptionId + '\'' +
				", active=" + active +
				", term='" + term + '\'' +
				", conceptId='" + conceptId + '\'' +
				", effectiveTime='" + getEffectiveTime() + '\'' +
				", moduleId='" + moduleId + '\'' +
				", languageCode='" + languageCode + '\'' +
				", typeId='" + typeId + '\'' +
				", caseSignificanceId='" + caseSignificanceId + '\'' +
				", internalId='" + getInternalId() + '\'' +
				", start='" + getStart() + '\'' +
				", end='" + getEnd() + '\'' +
				", path='" + getPath() + '\'' +
				'}';
	}
}
