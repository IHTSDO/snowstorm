package org.snomed.snowstorm.core.data.services.traceability;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Activity {

	private String userId;
	private String branchPath;
	private String sourceBranch;
	private ActivityType activityType;
	private Long commitTimestamp;
	private List<ConceptActivity> changes;

	public Activity() {
	}

	public Activity(String userId, String branchPath, long commitTimestamp, String sourceBranchPath, ActivityType activityType) {
		this.userId = userId;
		this.branchPath = branchPath;
		this.commitTimestamp = commitTimestamp;
		this.sourceBranch = sourceBranchPath;
		this.activityType = activityType;
		changes = new ArrayList<>();
	}

	public ConceptActivity addConceptActivity(String conceptId) {
		ConceptActivity conceptActivity = new ConceptActivity(conceptId);
		changes.add(conceptActivity);
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

	public ActivityType getActivityType() {
		return activityType;
	}

	public String getSourceBranch() {
		return sourceBranch;
	}

	public void setSourceBranch(String sourceBranch) {
		this.sourceBranch = sourceBranch;
	}

	public List<ConceptActivity> getChanges() {
		return changes;
	}

	public void setChanges(List<ConceptActivity> changes) {
		this.changes = changes;
	}
	@JsonIgnore
	public Map<String, ConceptActivity> getChangesMap() {
		return changes.stream().collect(Collectors.toMap(ConceptActivity::getConceptId, Function.identity()));
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
		private Set<ComponentChange> componentChanges;

		public ConceptActivity() {
		}

		public ConceptActivity(String conceptId) {
			this.conceptId = conceptId;
			componentChanges = new HashSet<>();
		}

		public void addComponentChange(ComponentChange change) {
			componentChanges.add(change);
		}

		public String getConceptId() {
			return conceptId;
		}

		@JsonIgnore
		public Long getConceptIdAsLong() {
			return Long.parseLong(conceptId);
		}

		public Set<ComponentChange> getComponentChanges() {
			return componentChanges;
		}

		@Override
		public String toString() {
			return "ConceptActivity{" +
					"conceptId='" + conceptId + '\'' +
					", changes=" + componentChanges +
					'}';
		}
	}

	public static final class ComponentChange {

		private ChangeType changeType;
		private ComponentType componentType;
		private Long componentSubType;
		private String componentId;
		private boolean effectiveTimeNull;

		public ComponentChange() {
		}

		public ComponentChange(ComponentType componentType, Long componentSubType, String componentId, ChangeType changeType, boolean effectiveTimeNull) {
			this.componentId = componentId;
			this.componentType = componentType;
			this.componentSubType = componentSubType;
			this.changeType = changeType;
			this.effectiveTimeNull = effectiveTimeNull;
		}

		public boolean isComponentSubType(Long type) {
			return Objects.equals(type, getComponentSubType());
		}

		public ChangeType getChangeType() {
			return changeType;
		}

		public ComponentType getComponentType() {
			return componentType;
		}

		public Long getComponentSubType() {
			return componentSubType;
		}

		public String getComponentId() {
			return componentId;
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

	public enum ActivityType {
		CONTENT_CHANGE,
		CLASSIFICATION_SAVE,
		REBASE,
		PROMOTION,
		CREATE_CODE_SYSTEM_VERSION
	}

	public enum ComponentType {
		CONCEPT, DESCRIPTION, RELATIONSHIP, REFERENCE_SET_MEMBER
	}

	public enum ChangeType {
		CREATE, DELETE, INACTIVATE, UPDATE
	}
}