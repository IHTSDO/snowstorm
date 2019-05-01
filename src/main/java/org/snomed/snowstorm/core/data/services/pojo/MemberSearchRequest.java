package org.snomed.snowstorm.core.data.services.pojo;

public class MemberSearchRequest {

	private Boolean active;
	private String referenceSet;
	private String referencedComponentId;
	private String targetComponentId;
	private String mapTarget;

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

	/**
	 * @param referencedComponentId  Filter by the referencedComponentId field.
	 */
	public MemberSearchRequest referencedComponentId(String referencedComponentId) {
		this.referencedComponentId = referencedComponentId;
		return this;
	}

	public String getReferencedComponentId() {
		return referencedComponentId;
	}

	/**
	 * @param targetComponentId  Filter by the targetComponentId field. Not all reference set types have this field.
	 */
	public MemberSearchRequest targetComponentId(String targetComponentId) {
		this.targetComponentId = targetComponentId;
		return this;
	}

	public String getTargetComponentId() {
		return targetComponentId;
	}

	/**
	 * @param mapTarget  Filter by the mapTarget field. Not all reference set types have this field.
	 */
	public MemberSearchRequest mapTarget(String mapTarget) {
		this.mapTarget = mapTarget;
		return this;
	}

	public String getMapTarget() {
		return mapTarget;
	}
}
