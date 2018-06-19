package org.snomed.snowstorm.core.data.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.data.services.traceability.Activity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

import static java.lang.Long.parseLong;

@Service
public class TraceabilityLogService {

	@Autowired
	private JmsTemplate jmsTemplate;

	@Value("${authoring.traceability.enabled}")
	private boolean enabled;

	@Value("${jms.queue.prefix}")
	private String jmsQueuePrefix;

	private ObjectMapper objectMapper;

	private static final int RECORD_MAX_INFERRED_CHANGES = 1_000;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public TraceabilityLogService() {
		objectMapper = new ObjectMapper();
	}

	void logActivity(String userId, Date date, String branchPath, Collection<Concept> concepts, List<Description> descriptions,
					 List<Relationship> relationships, List<ReferenceSetMember> refsetMembers) {

		if (!enabled) {
			return;
		}

		String commitComment;
		if (concepts.size() == 1) {
			Concept concept = concepts.iterator().next();
			commitComment = (concept.isCreating() ? "Creating " : "Updating ") + "concept " + concept.getFsn();
		} else if (concepts.isEmpty()) {
			commitComment = "No concept changes.";
		} else {
			commitComment = "Bulk update.";
		}

		Activity activity = new Activity(userId, commitComment, branchPath, date.getTime());
		Map<Long, Activity.ConceptActivity> activityMap = new Long2ObjectArrayMap<>();
		Map<Long, Long> componentToConceptIdMap = new Long2ObjectArrayMap<>();
		for (Concept concept : concepts) {
			Activity.ConceptActivity conceptActivity = activity.addConceptActivity(concept);
			activityMap.put(concept.getConceptIdAsLong(), conceptActivity);
			if (concept.isChanged() || concept.isDeleted()) {
				conceptActivity.addComponentChange(getChange(concept)).statedChange();
			}
		}
		for (Description description : descriptions) {
			if (description.isChanged() || description.isDeleted()) {
				activityMap.get(parseLong(description.getConceptId())).addComponentChange(getChange(description)).statedChange();
			}
			componentToConceptIdMap.put(parseLong(description.getDescriptionId()), parseLong(description.getConceptId()));
		}
		for (Relationship relationship : relationships) {
			if (relationship.isChanged() || relationship.isDeleted()) {
				activityMap.get(parseLong(relationship.getSourceId()))
						.addComponentChange(getChange(relationship))
						.addStatedChange(!Concepts.INFERRED_RELATIONSHIP.equals(relationship.getCharacteristicTypeId()));
			}
			componentToConceptIdMap.put(parseLong(relationship.getRelationshipId()), parseLong(relationship.getSourceId()));
		}
		for (ReferenceSetMember refsetMember : refsetMembers) {
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

		if (activityMap.size() > RECORD_MAX_INFERRED_CHANGES) {
			// Limit the number of inferred changes recorded
			List<String> conceptChangesToRemove = new ArrayList<>();
			int inferredChangesAccepted = 0;
			for (Activity.ConceptActivity conceptActivity : activityMap.values()) {
				if (!conceptActivity.isStatedChange()) {
					if (inferredChangesAccepted < RECORD_MAX_INFERRED_CHANGES) {
						inferredChangesAccepted++;
					} else {
						conceptChangesToRemove.add(conceptActivity.getConcept().getConceptId());
					}
				}
			}
			Map<String, Activity.ConceptActivity> changes = activity.getChanges();
			for (String conceptId : conceptChangesToRemove) {
				changes.remove(conceptId);
			}
		}

		try {
			logger.info("{}", objectMapper.writeValueAsString(activity));
		} catch (JsonProcessingException e) {
			logger.error("Failed to serialize activity {} to JSON.", activity.getCommitTimestamp());
		}
		jmsTemplate.convertAndSend(jmsQueuePrefix + ".traceability", activity);
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
