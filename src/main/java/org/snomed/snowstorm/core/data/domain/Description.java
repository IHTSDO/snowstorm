package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.rest.View;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Document(indexName = "description")
public class Description extends SnomedComponent<Description> implements SnomedComponentWithInactivationIndicator, SnomedComponentWithAssociations {

	private static final Pattern TAG_PATTERN = Pattern.compile(".+ \\((.+)\\)");

	public interface Fields extends SnomedComponent.Fields {
		String DESCRIPTION_ID = "descriptionId";
		String TERM = "term";
		String TERM_FOLDED = "termFolded";
		String TERM_LEN = "termLen";
		String TAG = "tag";
		String CONCEPT_ID = "conceptId";
		String TYPE_ID = "typeId";
		String LANGUAGE_CODE = "languageCode";
	}

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.keyword, store = true)
	@Size(min = 5, max = 18)
	private String descriptionId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.keyword)
	@NotNull
	private String term;

	@Field(type = FieldType.text)
	private String termFolded;

	@Field(type = FieldType.Integer)
	private int termLen;

	@Field(type = FieldType.keyword)
	private String tag;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.keyword, store = true)
	private String conceptId;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.keyword)
	@NotNull
	@Size(min = 5, max = 18)
	private String moduleId;

	@Field(type = FieldType.keyword)
	@NotNull
	@Size(min = 2, max = 2)
	private String languageCode;

	@JsonView(value = View.Component.class)
	@Field(type = FieldType.keyword)
	@NotNull
	@Size(min = 5, max = 18)
	private String typeId;

	@Field(type = FieldType.keyword)
	@NotNull
	@Size(min = 5, max = 18)
	private String caseSignificanceId;

	// Populated when requesting an update
	private Map<String, String> acceptabilityMap;

	@JsonIgnore
	// Populated manually when loading from store
	private Map<String, ReferenceSetMember> langRefsetMembers;

	@JsonIgnore
	private Set<ReferenceSetMember> inactivationIndicatorMembers;

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
		moduleId = Concepts.CORE_MODULE;
		languageCode = "en";
		typeId = Concepts.SYNONYM;
		caseSignificanceId = Concepts.CASE_INSENSITIVE;
		acceptabilityMap = new HashMap<>();
		langRefsetMembers = new HashMap<>();
		inactivationIndicatorMembers = new HashSet<>();
	}

	public Description(String term) {
		this();
		setTerm(term);
	}

	public Description(String id, String term) {
		this(term);
		this.descriptionId = id;
	}

	public Description(String id, Integer effectiveTime, boolean active, String moduleId, String conceptId, String languageCode, String typeId, String term, String caseSignificanceId) {
		this();
		this.descriptionId = id;
		setEffectiveTimeI(effectiveTime);
		this.active = active;
		this.moduleId = moduleId;
		this.conceptId = conceptId;
		this.languageCode = languageCode;
		this.typeId = typeId;
		setTerm(term);
		this.caseSignificanceId = caseSignificanceId;
	}

	@Override
	public String getIdField() {
		return Fields.DESCRIPTION_ID;
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

	public Description setType(String type) {
		typeId = Concepts.descriptionTypeNames.inverse().get(type);
		return this;
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

	public Description setLang(String languageCode) {
		this.languageCode = languageCode;
		return this;
	}

	public Description clearLanguageRefsetMembers() {
		langRefsetMembers.clear();
		acceptabilityMap.clear();
		return this;
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
			if (member.isActive()) {
				String acceptabilityId = member.getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID);
				String acceptabilityStr = Concepts.descriptionAcceptabilityNames.get(acceptabilityId);
				map.put(member.getRefsetId(), acceptabilityStr);
			}
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

	@JsonIgnore
	public Map<String, String> getAcceptabilityMapAndClearMembers() {
		Map<String, String> acceptabilityMap = getAcceptabilityMap();
		clearLanguageRefsetMembers();
		return acceptabilityMap;
	}

	public Description setAcceptabilityMap(Map<String, String> acceptabilityMap) {
		this.acceptabilityMap = acceptabilityMap;
		return this;
	}

	@JsonView(value = View.Component.class)
	public String getInactivationIndicator() {
		Set<ReferenceSetMember> inactivationIndicatorMembers = getInactivationIndicatorMembers();
		if (inactivationIndicatorMembers != null) {
			for (ReferenceSetMember inactivationIndicatorMember : inactivationIndicatorMembers) {
				if (inactivationIndicatorMember.isActive()) {
					return Concepts.inactivationIndicatorNames.get(inactivationIndicatorMember.getAdditionalField("valueId"));
				}
			}
		}
		return inactivationIndicatorName;
	}

	public void setInactivationIndicator(String inactivationIndicatorName) {
		this.inactivationIndicatorName = inactivationIndicatorName;
	}

	@JsonIgnore
	public ReferenceSetMember getInactivationIndicatorMember() {
		Set<ReferenceSetMember> inactivationIndicatorMembers = getInactivationIndicatorMembers();
		if (inactivationIndicatorMembers != null) {
			for (ReferenceSetMember inactivationIndicatorMember : inactivationIndicatorMembers) {
				if (inactivationIndicatorMember.isActive()) {
					return inactivationIndicatorMember;
				}
			}
		}
		return null;
	}

	/*
	 * There should be at most one inactivation indicator apart from part way through a branch merge.
	 */
	@JsonIgnore
	public Set<ReferenceSetMember> getInactivationIndicatorMembers() {
		return inactivationIndicatorMembers;
	}

	public void addInactivationIndicatorMember(ReferenceSetMember inactivationIndicatorMember) {
		inactivationIndicatorMembers.add(inactivationIndicatorMember);
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

	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		this.term = term;
		if (term != null) {
			termLen = term.length();
		}
	}

	public String getTermFolded() {
		return termFolded;
	}

	public void setTermFolded(String termFolded) {
		this.termFolded = termFolded;
	}

	public void setTermLen(int termLen) {
		this.termLen = termLen;
	}

	public int getTermLen() {
		return termLen;
	}

	public String getTag() {
		if (Concepts.FSN.equals(typeId)) {
			Matcher matcher = TAG_PATTERN.matcher(term);
			if (matcher.matches()) {
				return matcher.group(1);
			}
		}
		return null;
	}

	public String getConceptId() {
		return conceptId;
	}

	public Description setConceptId(String conceptId) {
		this.conceptId = conceptId;
		return this;
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

	public Description setLanguageCode(String languageCode) {
		this.languageCode = languageCode;
		return this;
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

	public Description setCaseSignificanceId(String caseSignificanceId) {
		this.caseSignificanceId = caseSignificanceId;
		return this;
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
				", effectiveTime='" + getEffectiveTimeI() + '\'' +
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

	//TODO would be better to agree if refsetIds are Strings or Long and make consistent everywhere
	public boolean hasAcceptability(String acceptability, Object refsetId) {
		ReferenceSetMember entry = langRefsetMembers.get(refsetId.toString());
		return (entry != null 
				&& entry.isActive() 
				&& (acceptability == null || entry.getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID).equals(acceptability)));
	}
	
	/**
	 * @param acceptability one of Concept.PREFERRED, Concept.ACCEPTABLE or null for either
	 * @return true if the description has that acceptability in ANY langrefset
	 */
	public boolean hasAcceptability(String acceptability) {
		for (ReferenceSetMember entry : langRefsetMembers.values()) {
			if (entry.isActive() 
					&& acceptability == null || entry.getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID).equals(acceptability)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * @return true if the description is either accepted or preferred 
	 * in the given language refset 
	 */
	public boolean hasLanguageRefset(Long langRefsetId) {
		String langRefsetIdStr = langRefsetId.toString();
		return langRefsetMembers.values().stream()
				.filter(l -> l.isActive())
				.anyMatch(l ->l.getRefsetId().equals(langRefsetIdStr));
	}
	
	/**
	 * @return true if the description has the specified acceptability (or either, if null)
	 * for the given language refset and/or language where specified in the dialect
	 */
	public boolean hasAcceptability(String acceptability, LanguageDialect dialect) {
		//Is the language refset specified in the dialect?
		if (dialect.getLanguageReferenceSet() == null) {
			return languageCode.equals(dialect.getLanguageCode());
		} else if (hasLanguageRefset(dialect.getLanguageReferenceSet())) {
			return hasAcceptability(acceptability, dialect.getLanguageReferenceSet());
		} 
		return languageCode.equals(dialect.getLanguageCode()); 
	}
	
	/**
	 * @return true if the description has any acceptability in any of the dialects specified
	 */
	public boolean hasAcceptability(List<LanguageDialect> dialects) {
		return dialects.stream().anyMatch(d -> hasAcceptability(null, d));
	}
}
