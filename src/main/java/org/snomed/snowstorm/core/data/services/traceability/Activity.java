package org.snomed.snowstorm.core.data.services.traceability;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Activity {

	private String userId;
	private String branchPath;
	private String sourceBranch;
	private MergeOperation mergeOperation;
	private Long commitTimestamp;
	private Map<String, ConceptActivity> changes;

	public Activity() {
	}

	public Activity(String userId, String branchPath, Long commitTimestamp) {
		this.userId = userId;
		this.branchPath = branchPath;
		this.commitTimestamp = commitTimestamp;
		changes = new HashMap<>();
	}

	public void setBranchOperation(MergeOperation mergeOperation, String sourceBranch) {
		this.mergeOperation = mergeOperation;
		this.sourceBranch = sourceBranch;
	}

	public ConceptActivity addConceptActivity(String conceptId) {
		ConceptActivity conceptActivity = new ConceptActivity(conceptId);
		changes.put(conceptId, conceptActivity);
		return conceptActivity;
	}

	public String getUserId() {
		return userId;
	}

	public String getBranchPath() {
		return branchPath;
	}

	public Long getCommitTimestamp() {
		return commitTimestamp;
	}

	public MergeOperation getMergeOperation() {
		return mergeOperation;
	}

	public String getSourceBranch() {
		return sourceBranch;
	}

	public Map<String, ConceptActivity> getChanges() {
		return changes;
	}

	@Override
	public String toString() {
		return "Activity{" +
				"userId='" + userId + '\'' +
				", branchPath='" + branchPath + '\'' +
				", commitTimestamp=" + commitTimestamp +
				", changes=" + changes +
				'}';
	}

	public static final class ConceptActivity {

		private String conceptId;
		private Set<ComponentChange> changes;

		public ConceptActivity() {
		}

		public ConceptActivity(String conceptId) {
			this.conceptId = conceptId;
			changes = new HashSet<>();
		}

		public void addComponentChange(ComponentChange change) {
			changes.add(change);
		}

		public String getConceptId() {
			return conceptId;
		}

		public Long getConceptIdAsLong() {
			return Long.parseLong(conceptId);
		}

		public Set<ComponentChange> getChanges() {
			return changes;
		}

		@Override
		public String toString() {
			return "ConceptActivity{" +
					"conceptId='" + conceptId + '\'' +
					", changes=" + changes +
					'}';
		}
	}

	public static final class ComponentChange {

		private ComponentType componentType;
		private ComponentSubType componentSubType;
		private String componentId;
		private ChangeType changeType;
		private boolean effectiveTimeNull;

		public ComponentChange() {
		}

		public ComponentChange(ComponentType componentType, ComponentSubType componentSubType, String componentId, ChangeType changeType, boolean effectiveTimeNull) {
			this.componentId = componentId;
			this.componentType = componentType;
			this.componentSubType = componentSubType;
			this.changeType = changeType;
			this.effectiveTimeNull = effectiveTimeNull;
		}

		public String getComponentId() {
			return componentId;
		}

		public ComponentType getComponentType() {
			return componentType;
		}

		public ComponentSubType getComponentSubType() {
			return componentSubType;
		}

		public ChangeType getChangeType() {
			return changeType;
		}

		public boolean isEffectiveTimeNull() {
			return effectiveTimeNull;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			ComponentChange that = (ComponentChange) o;

			return componentId.equals(that.componentId);
		}

		@Override
		public int hashCode() {
			return componentId.hashCode();
		}

		@Override
		public String toString() {
			return "ComponentChange{" +
					"componentType=" + componentType +
					", componentSubType=" + componentSubType +
					", componentId='" + componentId + '\'' +
					", changeType=" + changeType +
					", effectiveTimeNull=" + effectiveTimeNull +
					'}';
		}
	}

	public enum MergeOperation {
		REBASE, PROMOTE
	}

	public enum ComponentType {
		CONCEPT, DESCRIPTION, RELATIONSHIP, REFERENCE_SET_MEMBER
	}

	public enum ComponentSubType {
		INFERRED_RELATIONSHIP, STATED_RELATIONSHIP, FSN, SYNONYM, OWL_AXIOM, TEXT_DEFINITION
	}

	public enum ChangeType {
		CREATE, DELETE, INACTIVATE, UPDATE
	}
}
