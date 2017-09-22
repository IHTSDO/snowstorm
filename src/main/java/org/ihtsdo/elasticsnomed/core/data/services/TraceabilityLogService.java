package org.ihtsdo.elasticsnomed.core.data.services;

import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import org.ihtsdo.elasticsnomed.core.data.domain.*;
import org.ihtsdo.elasticsnomed.core.data.services.identifier.IdentifierService;
import org.ihtsdo.elasticsnomed.core.data.services.traceability.Activity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static java.lang.Long.parseLong;

@Service
public class TraceabilityLogService {

	@Autowired
	private JmsTemplate jmsTemplate;

	@Value("${authoring.traceability.enabled}")
	private boolean enabled;

	@Value("${jms.queue.prefix}")
	private String jmsQueuePrefix;

	private final Logger logger = LoggerFactory.getLogger(getClass());

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
				conceptActivity.addComponentChange(getChange(concept));
			}
		}
		for (Description description : descriptions) {
			if (description.isChanged() || description.isDeleted()) {
				activityMap.get(parseLong(description.getConceptId())).addComponentChange(getChange(description));
			}
			componentToConceptIdMap.put(parseLong(description.getDescriptionId()), parseLong(description.getConceptId()));
		}
		for (Relationship relationship : relationships) {
			if (relationship.isChanged() || relationship.isDeleted()) {
				activityMap.get(parseLong(relationship.getSourceId())).addComponentChange(getChange(relationship));
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
						"UPDATE"));
			}
		}

		logger.info("{}", activity);
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
