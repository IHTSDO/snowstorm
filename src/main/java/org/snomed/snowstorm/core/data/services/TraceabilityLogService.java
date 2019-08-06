package org.snomed.snowstorm.core.data.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.CommitListener;
import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.domain.Commit;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.data.services.pojo.PersistedComponents;
import org.snomed.snowstorm.core.data.services.traceability.Activity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static io.kaicode.elasticvc.domain.Commit.CommitType.CONTENT;
import static io.kaicode.elasticvc.domain.Commit.CommitType.PROMOTION;
import static java.lang.Long.parseLong;

@Service
public class TraceabilityLogService implements CommitListener {

	@Autowired
	private JmsTemplate jmsTemplate;

	@Value("${authoring.traceability.enabled}")
	private boolean enabled;

	@Value("${jms.queue.prefix}")
	private String jmsQueuePrefix;

	@Value("${authoring.traceability.inferred-max}")
	private int inferredMax;

	@Autowired
	private BranchService branchService;

	private ObjectMapper objectMapper;

	private Consumer<Activity> activityConsumer;


	private final Logger logger = LoggerFactory.getLogger(getClass());

	public TraceabilityLogService() {
		objectMapper = new ObjectMapper();
		activityConsumer = activity -> jmsTemplate.convertAndSend(jmsQueuePrefix + ".traceability", activity);
	}

	@Override
	public void preCommitCompletion(Commit commit) throws IllegalStateException {
		if (commit.getCommitType() != CONTENT) {// Only use for rebase / promotion
			logActivity(SecurityUtil.getUsername(), commit, new PersistedComponents());
		}
	}

	void logActivity(String userId, Commit commit, PersistedComponents batchSavePersistedComponents) {

		if (!enabled) {
			return;
		}

		if (userId == null) {
			userId = Config.SYSTEM_USERNAME;
		}

		Iterable<Concept> persistedConcepts = batchSavePersistedComponents.getPersistedConcepts();
		Iterable<Description> persistedDescriptions = batchSavePersistedComponents.getPersistedDescriptions();
		Iterable<Relationship> persistedRelationships = batchSavePersistedComponents.getPersistedRelationships();
		Iterable<ReferenceSetMember> persistedReferenceSetMembers = batchSavePersistedComponents.getPersistedReferenceSetMembers();

		Set<Concept> concepts = StreamSupport.stream(persistedConcepts.spliterator(), false).collect(Collectors.toSet());

		Activity activity = new Activity(userId, commit.getBranch().getPath(), commit.getTimepoint().getTime());
		Map<Long, Activity.ConceptActivity> activityMap = new Long2ObjectArrayMap<>();
		Map<Long, Long> componentToConceptIdMap = new Long2ObjectArrayMap<>();
		for (Concept concept : concepts) {
			Activity.ConceptActivity conceptActivity = activity.addConceptActivity(concept);
			activityMap.put(concept.getConceptIdAsLong(), conceptActivity);
			if (concept.isChanged() || concept.isDeleted()) {
				conceptActivity.addComponentChange(getChange(concept)).statedChange();
			}
		}
		for (Description description : persistedDescriptions) {
			if (description.isChanged() || description.isDeleted()) {
				activityMap.get(parseLong(description.getConceptId())).addComponentChange(getChange(description)).statedChange();
			}
			componentToConceptIdMap.put(parseLong(description.getDescriptionId()), parseLong(description.getConceptId()));
		}
		for (Relationship relationship : persistedRelationships) {
			if (relationship.isChanged() || relationship.isDeleted()) {
				activityMap.get(parseLong(relationship.getSourceId()))
						.addComponentChange(getChange(relationship))
						.addStatedChange(!Concepts.INFERRED_RELATIONSHIP.equals(relationship.getCharacteristicTypeId()));
			}
			componentToConceptIdMap.put(parseLong(relationship.getRelationshipId()), parseLong(relationship.getSourceId()));
		}
		for (ReferenceSetMember refsetMember : persistedReferenceSetMembers) {
			if (refsetMember.isChanged() || refsetMember.isDeleted()) {
				String referencedComponentId = refsetMember.getReferencedComponentId();
				long referencedComponentLong = parseLong(referencedComponentId);
				Activity.ConceptActivity conceptActivity = null;
				String componentType = null;
				if (IdentifierService.isConceptId(referencedComponentId)) {
					conceptActivity = activityMap.get(referencedComponentLong);
					componentType = "Concept";
				} else {
					Long conceptId = componentToConceptIdMap.get(referencedComponentLong);
					if (IdentifierService.isDescriptionId(referencedComponentId)) {
						componentType = "Description";
					} else if (IdentifierService.isRelationshipId(referencedComponentId)) {
						componentType = "Relationship";
					}
					if (conceptId != null) {
						conceptActivity = activityMap.get(conceptId);
					}
				}
				if (conceptActivity != null && componentType != null) {
					conceptActivity.addComponentChange(new Activity.ComponentChange(
							componentType,
							referencedComponentId,
							"UPDATE"))
							.statedChange();
				}
			}
		}

		Map<String, Activity.ConceptActivity> changes = activity.getChanges();
		boolean changeFound = false;
		for (Activity.ConceptActivity conceptActivity : changes.values()) {
			if (!conceptActivity.getChanges().isEmpty()) {
				changeFound = true;
				break;
			}
		}
		if (commit.getCommitType() == CONTENT && !changeFound) {
			logger.info("Skipping traceability because there was no traceable change.");
			return;
		}

		boolean anyStatedChanges = false;
		if (!activityMap.isEmpty()) {
			List<Long> conceptsWithNoChange = new LongArrayList();
			for (Activity.ConceptActivity conceptActivity : activityMap.values()) {
				if (conceptActivity.getChanges().isEmpty()) {
					conceptsWithNoChange.add(conceptActivity.getConcept().getConceptIdAsLong());
				} else if (conceptActivity.isStatedChange()) {
					anyStatedChanges = true;
				}
			}
			for (Long conceptId : conceptsWithNoChange) {
				changes.remove(conceptId.toString());
			}
		}

		String commitComment = createCommitComment(userId, commit, concepts, anyStatedChanges);
		activity.setCommitComment(commitComment);

		if (activityMap.size() > inferredMax) {
			// Limit the number of inferred changes recorded
			List<String> conceptChangesToRemove = new ArrayList<>();
			int inferredChangesAccepted = 0;
			for (Activity.ConceptActivity conceptActivity : activityMap.values()) {
				if (!conceptActivity.isStatedChange()) {
					if (inferredChangesAccepted < inferredMax) {
						inferredChangesAccepted++;
					} else {
						conceptChangesToRemove.add(conceptActivity.getConcept().getConceptId());
					}
				}
			}
			for (String conceptId : conceptChangesToRemove) {
				changes.remove(conceptId);
			}
		}

		try {
			logger.info("{}", objectMapper.writeValueAsString(activity));
		} catch (JsonProcessingException e) {
			logger.error("Failed to serialize activity {} to JSON.", activity.getCommitTimestamp());
		}

		activityConsumer.accept(activity);
	}

	String createCommitComment(String userId, Commit commit, Collection<Concept> concepts, boolean anyStatedChanges) {
		Commit.CommitType commitType = commit.getCommitType();
		if (commitType == CONTENT) {
			if (!anyStatedChanges) {
				return "Classified ontology.";
			} else if (concepts.size() == 1) {
				Concept concept = concepts.iterator().next();
				return (concept.isCreating() ? "Creating " : "Updating ") + "concept " + concept.getFsn().getTerm();
			} else {
				return "Bulk update to " + concepts.size() + " concepts.";
			}
		} else {
			String path = commit.getBranch().getPath();
			String sourceBranchPath = commitType == PROMOTION ? commit.getSourceBranchPath() : PathUtil.getParentPath(path);
			return String.format("%s performed merge of %s to %s", userId, sourceBranchPath, path);
		}
	}

	void setActivityConsumer(Consumer<Activity> activityConsumer) {
		this.activityConsumer = activityConsumer;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isEnabled() {
		return enabled;
	}

	private Activity.ComponentChange getChange(SnomedComponent component) {
		String type;
		if (component.isCreating()) {
			type = "CREATE";
		} else if (component.isDeleted()) {
			type = "DELETE";
		} else if (!component.isActive()) {
			type = "INACTIVATE";
		} else {
			type = "UPDATE";
		}

		return new Activity.ComponentChange(
				component.getClass().getSimpleName(),
				component.getId(),
				type);
	}
}
