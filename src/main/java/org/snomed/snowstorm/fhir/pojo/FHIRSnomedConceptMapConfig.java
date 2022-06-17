package org.snomed.snowstorm.fhir.pojo;

public class FHIRSnomedConceptMapConfig {

	private String referenceSetId;

	private String name;

	private String sourceSystem;

	private String targetSystem;

	private String refsetEquivalence;

	public FHIRSnomedConceptMapConfig() {
	}

	public FHIRSnomedConceptMapConfig(String referenceSetId, String name, String sourceSystem, String targetSystem, String refsetEquivalence) {
		this.referenceSetId = referenceSetId;
		this.name = name;
		this.sourceSystem = sourceSystem;
		this.targetSystem = targetSystem;
		this.refsetEquivalence = refsetEquivalence;
	}

	public String getReferenceSetId() {
		return referenceSetId;
	}

	public void setReferenceSetId(String referenceSetId) {
		this.referenceSetId = referenceSetId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getSourceSystem() {
		return sourceSystem;
	}

	public void setSourceSystem(String sourceSystem) {
		this.sourceSystem = sourceSystem;
	}

	public String getTargetSystem() {
		return targetSystem;
	}

	public void setTargetSystem(String targetSystem) {
		this.targetSystem = targetSystem;
	}

	public String getRefsetEquivalence() {
		return refsetEquivalence;
	}

	public void setRefsetEquivalence(String refsetEquivalence) {
		this.refsetEquivalence = refsetEquivalence;
	}
}
