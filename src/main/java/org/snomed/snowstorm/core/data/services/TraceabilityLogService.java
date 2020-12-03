package org.snomed.snowstorm.core.data.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kaicode.elasticvc.api.CommitListener;
import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.domain.Commit;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.domain.SnomedComponent;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.data.services.persistedcomponent.DefaultPersistedComponentLoaderChainService;
import org.snomed.snowstorm.core.data.services.pojo.PersistedComponents;
import org.snomed.snowstorm.core.data.services.traceability.Activity;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
	private DefaultPersistedComponentLoaderChainService persistedComponentLoaderChainService;

	private Consumer<Activity> activityConsumer;

	private final ObjectMapper objectMapper;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public TraceabilityLogService() {
		objectMapper = Jackson2ObjectMapperBuilder.json()
				.serializationInclusion(JsonInclude.Include.NON_NULL)
				.build();
		activityConsumer = activity -> jmsTemplate.convertAndSend(jmsQueuePrefix + ".traceability", activity);
	}

	@Override
	public void preCommitCompletion(final Commit commit) throws IllegalStateException {
		// Most commits don't use this because ConceptService calls logActivity directly.

		PersistedComponents persistedComponents = null;
		String importType = null;
		final Map<String, String> metadata = commit.getBranch().getMetadata();
		if (metadata != null && metadata.containsKey(ImportService.IMPORT_TYPE_KEY)) {
			importType = metadata.get(ImportService.IMPORT_TYPE_KEY);
			if (commit.getCommitType() == CONTENT && RF2Type.DELTA.getName().equals(importType)) {
				// RF2 Delta import
				persistedComponents = persistedComponentLoaderChainService.load(commit);
			}
		} else {
			// Rebase or Promotion
			// Log an empty-change activity.
			persistedComponents = new PersistedComponents();
		}

		logActivity(SecurityUtil.getUsername(), commit, persistedComponents, false, importType);
	}

	/**
	 * Logs the activity that is associated to the commit. It should be noted
	 * that some services call this method explicitly so that means they will
	 * have the states in memory for checking whether the component is changed
	 * or deleted, both of these fields are classified as {@code Transient} so
	 * the states will not be persisted when retrieved from the data store.
	 * In the case where this method is called implicitly through the
	 * {@link CommitListener}, by performing a {@link RF2Type#DELTA} import,
	 * it will load the components from the data store so the {@code changed}
	 * and {@code deleted} states will not be persisted which means that the
	 * <code>useChangeFlag == false</code> so that other checks are ignored when
	 * doing the comparison between what has changed. It's worth noting when doing
	 * a {@link RF2Type#DELTA} import, it will show all the changes inside the
	 * traceability log.
	 *
	 * @param userId                       The Id of the user.
	 * @param commit                       The commit which contains the {@link RF2Type#DELTA} import.
	 * @param batchSavePersistedComponents Contains all the persisted components.
	 */
	void logActivity(final String userId, final Commit commit, final PersistedComponents batchSavePersistedComponents) {
		logActivity(userId, commit, batchSavePersistedComponents, true, null);
	}

	/**
	 * Logs the activity that is associated to the commit. It should be noted
	 * that some services call this method explicitly so that means they will
	 * have the states in memory for checking whether the component is changed
	 * or deleted, both of these fields are classified as {@code Transient} so
	 * the states will not be persisted when retrieved from the data store.
	 * In the case where this method is called implicitly through the
	 * {@link CommitListener}, by performing a {@link RF2Type#DELTA} import,
	 * it will load the components from the data store so the {@code changed}
	 * and {@code deleted} states will not be persisted which means that the
	 * <code>useChangeFlag == false</code> so that other checks are ignored when
	 * doing the comparison between what has changed. It's worth noting when doing
	 * a {@link RF2Type#DELTA} import, it will show all the changes inside the
	 * traceability log.
	 *
	 * @param userId                       The Id of the user.
	 * @param commit                       The commit which contains the {@link RF2Type#DELTA} import.
	 * @param batchSavePersistedComponents Contains all the persisted components.
	 * @param useChangeFlag                Used to determine whether the {@code changed} and
	 *                                     {@code deleted} should be used when doing comparison between what has changed.
	 * @param importType                   The type of import.
	 */
	void logActivity(String userId, final Commit commit, final PersistedComponents batchSavePersistedComponents, boolean useChangeFlag, final String importType) {

		if (!enabled) {
			return;
		}

		if (batchSavePersistedComponents == null) {
			logger.debug("Persisted components should not be null when writing to the traceability log.");
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
			if (!useChangeFlag || (concept.isChanged() || concept.isDeleted())) {
				conceptActivity.addComponentChange(getChange(concept)).statedChange();
			}
		}
		for (Description description : persistedDescriptions) {
			if (!useChangeFlag || (description.isChanged() || description.isDeleted())) {
				activityMap.get(parseLong(description.getConceptId())).addComponentChange(getChange(description)).statedChange();
			}
			componentToConceptIdMap.put(parseLong(description.getDescriptionId()), parseLong(description.getConceptId()));
		}
		for (Relationship relationship : persistedRelationships) {
			if (!useChangeFlag || (relationship.isChanged() || relationship.isDeleted())) {
				activityMap.get(parseLong(relationship.getSourceId()))
						.addComponentChange(getChange(relationship))
						.addStatedChange(!Concepts.INFERRED_RELATIONSHIP.equals(relationship.getCharacteristicTypeId()));
			}
			componentToConceptIdMap.put(parseLong(relationship.getRelationshipId()), parseLong(relationship.getSourceId()));
		}
		for (ReferenceSetMember refsetMember : persistedReferenceSetMembers) {
			if (!useChangeFlag || (refsetMember.isChanged() || refsetMember.isDeleted())) {
				String referencedComponentId = refsetMember.getReferencedComponentId();
				String componentId = referencedComponentId;
				long referencedComponentLong = parseLong(referencedComponentId);
				Activity.ConceptActivity conceptActivity = null;
				String componentType = null;
				if (IdentifierService.isConceptId(referencedComponentId)) {
					conceptActivity = activityMap.get(referencedComponentLong);
					Map<String, String> additionalFields = refsetMember.getAdditionalFields();
					if (additionalFields != null && additionalFields.size() == 1 && additionalFields.keySet().contains(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION)) {
						componentType = "OWLAxiom";
						componentId = refsetMember.getMemberId();
					} else {
						componentType = "Concept";
					}
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
							componentId,
							"UPDATE"))
							.statedChange();
				}
			}
		}

		Map<String, Activity.ConceptActivity> changes = activity.getChanges();
		boolean changeFound = changes.values().stream().anyMatch(conceptActivity -> !conceptActivity.getChanges().isEmpty());
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
			conceptsWithNoChange.stream().map(Object::toString).forEach(changes::remove);
		}

		activity.setCommitComment(createCommitComment(userId, commit, concepts, anyStatedChanges, importType));

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
			conceptChangesToRemove.forEach(changes::remove);
		}

		try {
			logger.info("{}", objectMapper.writeValueAsString(activity));
		} catch (JsonProcessingException e) {
			logger.error("Failed to serialize activity {} to JSON.", activity.getCommitTimestamp());
		}

		activityConsumer.accept(activity);
	}

	String createCommitComment(final String userId, final Commit commit, final Collection<Concept> concepts, final boolean anyStatedChanges, final String importType) {
		Commit.CommitType commitType = commit.getCommitType();
		final String commitCommentPrefix = RF2Type.DELTA.getName().equals(importType) ? "RF2 Import - " : "";
		if (commitType == CONTENT) {
			if (!anyStatedChanges) {
				return "Classified ontology.";
			} else if (concepts.size() == 1) {
				Concept concept = concepts.iterator().next();
				return commitCommentPrefix + (concept.isCreating() ? "Creating " : concept.isDeleted() ? "Deleting " : "Updating ") + "concept " + concept.getFsn().getTerm();
			} else {
				return commitCommentPrefix + "Bulk update to " + concepts.size() + " concepts.";
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
