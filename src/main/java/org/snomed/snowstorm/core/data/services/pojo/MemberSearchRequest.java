package org.snomed.snowstorm.core.data.services.pojo;

import java.io.Serializable;
import java.util.*;

public class MemberSearchRequest {

	private Boolean active;
	private Boolean isNullEffectiveTime;
	private String referenceSet;
	private String module;
	private Collection<? extends Serializable> referencedComponentIds;
	private String owlExpressionConceptId;
	private Boolean owlExpressionGCI;
	private final Map<String, String> additionalFields;
	private final Map<String, Set<String>> additionalFieldSets;
	private boolean includeNonSnomedMapTerms;

	public MemberSearchRequest() {
		additionalFields = new HashMap<>();
		additionalFieldSets = new HashMap<>();
	}

	/**
	 * @param active  Filter by the active field.
	 */
	public MemberSearchRequest active(Boolean active) {
		this.active = active;
		return this;
	}

	public Boolean getActive() {
		return active;
	}

	/**
	 * @param referenceSet  Filter by the reference set, can be a concept id or an ECL expression.
	 */
	public MemberSearchRequest referenceSet(String referenceSet) {
		this.referenceSet = referenceSet;
		return this;
	}

	public String getReferenceSet() {
		return referenceSet;
	}

	public MemberSearchRequest module(String module) {
		this.module = module;
		return this;
	}

	public String getModule() {
		return module;
	}

	/**
	 * @param referencedComponentIds Filter by the referencedComponentId field.
	 */
	public MemberSearchRequest referencedComponentIds(Collection<? extends Serializable> referencedComponentIds) {
		this.referencedComponentIds = referencedComponentIds;
		return this;
	}

	public Collection<? extends Serializable> getReferencedComponentIds() {
		return referencedComponentIds;
	}

	/**
	 * @param targetComponentIds Filter by the targetComponentId field. Not all reference set types have this field.
	 */
	public MemberSearchRequest targetComponentIds(Set<String> targetComponentIds) {
		if (targetComponentIds == null) {
			additionalFieldSets.remove("targetComponentId");
			return this;
		}
		return additionalFieldSets("targetComponentId", targetComponentIds);
	}

	/**
	 * @param mapTarget  Filter by the mapTarget field. Not all reference set types have this field.
	 */
	public MemberSearchRequest mapTarget(String mapTarget) {
		if (mapTarget == null) {
			additionalFieldSets.remove("mapTarget");
			return this;
		}
		return additionalField("mapTarget", mapTarget);
	}

	/**
	 * @param fieldName Filter by an additional field with this name and value combination.
	 * @param fieldValue Filter by an additional field with this name and value combination.
	 */
	public MemberSearchRequest additionalField(String fieldName, String fieldValue) {
		additionalFields.put(fieldName, fieldValue);
		return this;
	}
	
	/**
	 * @param fieldName Filter by an additional field with this name and value combination.
	 * @param fieldValues Filter by additional fields with this name and one or more of these values.
	 */
	public MemberSearchRequest additionalFieldSets(String fieldName, Set<String> fieldValues) {
		additionalFieldSets.put(fieldName, fieldValues);
		return this;
	}

	/**
	 * @param owlExpressionConceptId  Filter by a concept id within the owlExpression. Not all reference set types have this field.
	 */
	public MemberSearchRequest owlExpressionConceptId(String owlExpressionConceptId) {
		this.owlExpressionConceptId = owlExpressionConceptId;
		return this;
	}

	public String getOwlExpressionConceptId() {
		return owlExpressionConceptId;
	}

	/**
	 * @param owlExpressionGCI  Find OWL Axioms which are General Concept Inclusions. Can be true/false or null.
	 */
	public MemberSearchRequest owlExpressionGCI(Boolean owlExpressionGCI) {
		this.owlExpressionGCI = owlExpressionGCI;
		return this;
	}

	public Boolean getOwlExpressionGCI() {
		return owlExpressionGCI;
	}

	public Map<String, String> getAdditionalFields() {
		return additionalFields;
	}
	
	public boolean hasAdditionalField(String fieldName) {
		return additionalFields.containsKey(fieldName);
	}
	
	public Map<String, Set<String>> getAdditionalFieldSets() {
		return additionalFieldSets;
	}
	
	public boolean hasAdditionalFieldSet(String fieldName) {
		return additionalFieldSets.containsKey(fieldName);
	}

	public MemberSearchRequest referencedComponentId(String referencedComponentId) {
		return referencedComponentIds(Collections.singleton(referencedComponentId));
	}

	public MemberSearchRequest isNullEffectiveTime(Boolean isNullEffectiveTime) {
		this.isNullEffectiveTime = isNullEffectiveTime;
		return this;
	}
	
	public Boolean isNullEffectiveTime() {
		return this.isNullEffectiveTime;
	}

	public MemberSearchRequest includeNonSnomedMapTerms(boolean includeNonSnomedMapTerms) {
		this.includeNonSnomedMapTerms = includeNonSnomedMapTerms;
		return this;
	}

	public boolean isIncludeNonSnomedMapTerms() {
		return includeNonSnomedMapTerms;
	}
}
