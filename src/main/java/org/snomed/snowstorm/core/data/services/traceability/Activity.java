package org.snomed.snowstorm.core.data.services.traceability;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.snomed.snowstorm.core.data.domain.Concept;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Activity {

	private String userId;
	private String commitComment;
	private String branchPath;
	private Long commitTimestamp;
	private Map<String, ConceptActivity> changes;

	public Activity() {
	}

	public Activity(String userId, String commitComment, String branchPath, Long commitTimestamp) {
		this.userId = userId;
		this.commitComment = commitComment;
		this.branchPath = branchPath;
		this.commitTimestamp = commitTimestamp;
		changes = new HashMap<>();
	}

	public ConceptActivity addConceptActivity(Concept concept) {
		String conceptId = concept.getConceptId();
		ConceptActivity conceptActivity = new ConceptActivity(concept);
		changes.put(conceptId, conceptActivity);
		return conceptActivity;
	}

	public String getUserId() {
		return userId;
	}

	public String getCommitComment() {
		return commitComment;
	}

	public String getBranchPath() {
		return branchPath;
	}

	public Long getCommitTimestamp() {
		return commitTimestamp;
	}

	public Map<String, ConceptActivity> getChanges() {
		return changes;
	}

	public void setCommitComment(String commitComment) {
		this.commitComment = commitComment;
	}

	@Override
	public String toString() {
		return "Activity{" +
				"userId='" + userId + '\'' +
				", commitComment='" + commitComment + '\'' +
				", branchPath='" + branchPath + '\'' +
				", commitTimestamp=" + commitTimestamp +
				'}';
	}

	public static final class ConceptActivity {

		private Concept concept;
		private Set<ComponentChange> changes;
		private boolean statedChange;

		public ConceptActivity() {
		}

		public ConceptActivity(Concept concept) {
			this.concept = concept;
			changes = new HashSet<>();
		}

		public ConceptActivity addComponentChange(ComponentChange change) {
			changes.add(change);
			return this;
		}

		public Concept getConcept() {
			return concept;
		}

		public Set<ComponentChange> getChanges() {
			return changes;
		}

		public void statedChange() {
			statedChange = true;
		}

		public void addStatedChange(boolean addStatedChange) {
			if (addStatedChange) {
				statedChange();
			}
		}

		@JsonIgnore
		public boolean isStatedChange() {
			return statedChange;
		}
	}

	public static final class ComponentChange {

		private String componentType;
		private String componentId;
		private String type;

		public ComponentChange() {
		}

		public ComponentChange(String componentType, String componentId, String type) {
			this.componentType = componentType;
			this.componentId = componentId;
			this.type = type;
		}

		public String getComponentType() {
			return componentType;
		}

		public String getComponentId() {
			return componentId;
		}

		public String getType() {
			return type;
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
	}
}
