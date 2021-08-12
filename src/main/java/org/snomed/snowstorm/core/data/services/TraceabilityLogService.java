package org.snomed.snowstorm.core.data.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.CommitListener;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.data.services.pojo.PersistedComponents;
import org.snomed.snowstorm.core.data.services.traceability.Activity;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.CLAUSE_LIMIT;
import static io.kaicode.elasticvc.api.VersionControlHelper.LARGE_PAGE;
import static io.kaicode.elasticvc.domain.Commit.CommitType.*;
import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

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
	private TraceabilityLogServiceHelper traceabilityLogServiceHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private VersionControlHelper versionControlHelper;

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

		Activity.ActivityType activityType = null;
		switch (commit.getCommitType()) {
			case CONTENT:
				activityType = Activity.ActivityType.CONTENT_CHANGE;
				break;
			case PROMOTION:
				activityType = Activity.ActivityType.PROMOTION;
				break;
			case REBASE:
				activityType = Activity.ActivityType.REBASE;
				break;
		}

		final Map<String, String> internalMetadataMap = commit.getBranch().getMetadata().getMapOrCreate(BranchMetadataHelper.INTERNAL_METADATA_KEY);
		if (internalMetadataMap.containsKey(ImportService.IMPORT_TYPE_KEY)) {
			String commitPrefix = internalMetadataMap.get(ImportService.IMPORT_TYPE_KEY);
			activityType = Activity.ActivityType.CLASSIFICATION_SAVE;
			if (commit.getCommitType() == CONTENT && RF2Type.DELTA.getName().equals(commitPrefix)) {
				// RF2 Delta import
				persistedComponents = buildPersistedComponents(commit);
			}
		} else {
			// Rebase or Promotion
			// Log an empty-change activity.
			persistedComponents = new PersistedComponents();
		}

		logActivity(SecurityUtil.getUsername(), commit, persistedComponents, false, activityType);
	}

	private PersistedComponents buildPersistedComponents(final Commit commit) {
		return PersistedComponents.builder().withPersistedConcepts(traceabilityLogServiceHelper.loadChangesAndDeletionsWithinOpenCommitOnly(commit, Concept.class))
				.withPersistedDescriptions(traceabilityLogServiceHelper.loadChangesAndDeletionsWithinOpenCommitOnly(commit, Description.class))
				.withPersistedRelationships(traceabilityLogServiceHelper.loadChangesAndDeletionsWithinOpenCommitOnly(commit, Relationship.class))
				.withPersistedReferenceSetMembers(traceabilityLogServiceHelper.loadChangesAndDeletionsWithinOpenCommitOnly(commit, ReferenceSetMember.class)).build();
	}

	public void logActivityUsingComponentLookup(final String userId, final Commit commit, Activity.ActivityType activityType) {
		logActivity(userId, commit, buildPersistedComponents(commit), false, activityType);
	}

	/**
	 * This method may be called by a service class as an optimisation. In this case
	 * the transient change and delete flags may be populated.
	 */
	void logActivity(final String userId, final Commit commit, final PersistedComponents batchSavePersistedComponents, Activity.ActivityType activityType) {
		logActivity(userId, commit, batchSavePersistedComponents, true, activityType);
	}

	/**
	 * This method may be called by a service class as an optimisation. In this
	 * case the transient change and delete flags may be populated and depending
	 * on these states, the <code>useChangeFlag</code> should be set accordingly.
	 */
	void logActivity(String userId, final Commit commit, final PersistedComponents persistedComponents, boolean useChangeFlag, Activity.ActivityType activityType) {

		ServiceUtil.assertNotNull("activityType", activityType);
		ServiceUtil.assertNotNull("persistedComponents", persistedComponents);

		if (!enabled) {
			return;
		}

		if (userId == null) {
			userId = Config.SYSTEM_USERNAME;
		}

		Activity activity = new Activity(userId, commit.getBranch().getPath(), commit.getTimepoint().getTime(), commit.getSourceBranchPath(), activityType);

		Map<Long, Activity.ConceptActivity> activityMap = new Long2ObjectArrayMap<>();
		Map<Long, Long> componentToConceptIdMap = new Long2ObjectArrayMap<>();
		for (Concept concept : persistedComponents.getPersistedConcepts()) {
			if (!useChangeFlag || (concept.isChanged() || concept.isDeleted())) {
				getConceptActivityForComponent(activity, activityMap, concept.getConceptIdAsLong()).addComponentChange(getChange(concept));
			}
		}
		for (Description description : persistedComponents.getPersistedDescriptions()) {
			final long conceptId = parseLong(description.getConceptId());
			if (!useChangeFlag || (description.isChanged() || description.isDeleted())) {
				getConceptActivityForComponent(activity, activityMap, conceptId).addComponentChange(getChange(description));
			}
			componentToConceptIdMap.put(parseLong(description.getDescriptionId()), conceptId);
		}
		for (Relationship relationship : persistedComponents.getPersistedRelationships()) {
			final long sourceId = parseLong(relationship.getSourceId());
			if (!useChangeFlag || (relationship.isChanged() || relationship.isDeleted())) {
				getConceptActivityForComponent(activity, activityMap, sourceId).addComponentChange(getChange(relationship));
			}
			componentToConceptIdMap.put(parseLong(relationship.getRelationshipId()), sourceId);
		}

		// Deal with members that refer to descriptions or relationships by looking up their concepts.
		final Map<Long, List<ReferenceSetMember>> conceptMembersMap =
				filterRefsetMembersAndLookupComponentConceptIds(persistedComponents.getPersistedReferenceSetMembers(), useChangeFlag, commit, componentToConceptIdMap);

		// Record all refset members against concept activities
		for (Map.Entry<Long, List<ReferenceSetMember>> entry : conceptMembersMap.entrySet()) {
			final Activity.ConceptActivity conceptActivityForComponent = getConceptActivityForComponent(activity, activityMap, entry.getKey());
			for (ReferenceSetMember referenceSetMember : entry.getValue()) {
				conceptActivityForComponent.addComponentChange(getChange(referenceSetMember));
			}
		}

		Map<String, Activity.ConceptActivity> changes = activity.getChangesMap();
		boolean changeFound = changes.values().stream().anyMatch(conceptActivity -> !conceptActivity.getComponentChanges().isEmpty());
		if (commit.getCommitType() == CONTENT && !changeFound) {
			logger.info("Skipping traceability because there was no traceable change for commit {} at {}.", commit.getBranch().getPath(), commit.getTimepoint().getTime());
			return;
		}

		// Limit the number of inferred relationship changes logged
		long inferredChangesAccepted = 0;
		List<String> conceptChangesToRemove = new ArrayList<>();
		for (Activity.ConceptActivity conceptActivity : activityMap.values()) {
			if (inferredChangesAccepted > inferredMax) {
				// Remove activities with only inferred changes
				if (!conceptActivity.getComponentChanges().stream()
						.anyMatch(componentChange -> !componentChange.isComponentSubType(Long.parseLong(Concepts.INFERRED_RELATIONSHIP)))) {
					conceptChangesToRemove.add(conceptActivity.getConceptId());
				}
			} else {
				inferredChangesAccepted += conceptActivity.getComponentChanges().stream()
						.filter(componentChange -> componentChange.isComponentSubType(Long.parseLong(Concepts.INFERRED_RELATIONSHIP))).count();
			}
		}
		conceptChangesToRemove.forEach(changes::remove);

		if (!activityMap.isEmpty()) {
			List<Long> conceptsWithNoChange = new LongArrayList();
			for (Activity.ConceptActivity conceptActivity : activityMap.values()) {
				if (conceptActivity.getComponentChanges().isEmpty()) {
					conceptsWithNoChange.add(conceptActivity.getConceptIdAsLong());
				}
			}
			conceptsWithNoChange.stream().map(Object::toString).forEach(changes::remove);
		}

		try {
			logger.info("{}", objectMapper.writeValueAsString(activity));
		} catch (JsonProcessingException e) {
			logger.error("Failed to serialize activity {} to JSON.", activity.getCommitTimestamp());
		}

		activityConsumer.accept(activity);
	}

	private Map<Long, List<ReferenceSetMember>> filterRefsetMembersAndLookupComponentConceptIds(Iterable<ReferenceSetMember> persistedReferenceSetMembers, boolean useChangeFlag,
			Commit commit, Map<Long, Long> componentToConceptIdMap) {

		Map<Long, List<ReferenceSetMember>> conceptToMembersMap = new Long2ObjectArrayMap<>();

		List<ReferenceSetMember> membersToLog = new ArrayList<>();
		Set<Long> referencedDescriptions = new LongOpenHashSet();
		Set<Long> referencedRelationships = new LongOpenHashSet();
		for (ReferenceSetMember refsetMember : persistedReferenceSetMembers) {
			if (!useChangeFlag || (refsetMember.isChanged() || refsetMember.isDeleted())) {
				String conceptId = refsetMember.getConceptId();
				if (conceptId != null) {
					conceptToMembersMap.computeIfAbsent(parseLong(conceptId), id -> new ArrayList<>()).add(refsetMember);
				} else {
					membersToLog.add(refsetMember);
					final Long referencedComponentId = parseLong(refsetMember.getReferencedComponentId());
					if (!IdentifierService.isConceptId(referencedComponentId.toString())) {
						if (IdentifierService.isDescriptionId(referencedComponentId.toString())) {
							referencedDescriptions.add(referencedComponentId);
						} else if (IdentifierService.isRelationshipId(referencedComponentId.toString())) {
							referencedRelationships.add(referencedComponentId);
						}
					}
				}
			}
		}
		final Set<Long> descriptionIdsToLookup = referencedDescriptions.stream().filter(Predicate.not(componentToConceptIdMap::containsKey)).collect(Collectors.toSet());
		final Set<Long> relationshipIdsToLookup = referencedRelationships.stream().filter(Predicate.not(componentToConceptIdMap::containsKey)).collect(Collectors.toSet());
		BranchCriteria branchCriteria = null;
		if (!descriptionIdsToLookup.isEmpty()) {
			branchCriteria = versionControlHelper.getBranchCriteria(commit.getBranch());
			for (List<Long> descriptionIdsSegment : Iterables.partition(descriptionIdsToLookup, CLAUSE_LIMIT)) {
				try (final SearchHitsIterator<Description> stream = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
						.withQuery(branchCriteria.getEntityBranchCriteria(Description.class)
								.must(termsQuery(Description.Fields.DESCRIPTION_ID, descriptionIdsSegment)))
						.withFields(Description.Fields.DESCRIPTION_ID, Description.Fields.CONCEPT_ID)
						.withPageable(LARGE_PAGE)
						.build(), Description.class)) {
					stream.forEachRemaining(hit -> {
						final Description description = hit.getContent();
						componentToConceptIdMap.put(parseLong(description.getDescriptionId()), parseLong(description.getConceptId()));
					});
				}
			}
		}
		if (!relationshipIdsToLookup.isEmpty()) {
			if (branchCriteria == null) {
				branchCriteria = versionControlHelper.getBranchCriteria(commit.getBranch());
			}
			for (List<Long> relationshipsIdsSegment : Iterables.partition(relationshipIdsToLookup, CLAUSE_LIMIT)) {
				try (final SearchHitsIterator<Relationship> stream = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
						.withQuery(branchCriteria.getEntityBranchCriteria(Relationship.class)
								.must(termsQuery(Relationship.Fields.RELATIONSHIP_ID, relationshipsIdsSegment)))
						.withSourceFilter(new FetchSourceFilter(new String[]{Relationship.Fields.RELATIONSHIP_ID, Relationship.Fields.SOURCE_ID}, new String[]{}))
						.withPageable(LARGE_PAGE)
						.build(), Relationship.class)) {
					stream.forEachRemaining(hit -> {
						final Relationship relationship = hit.getContent();
						componentToConceptIdMap.put(parseLong(relationship.getRelationshipId()), parseLong(relationship.getSourceId()));
					});
				}
			}
		}
		membersToLog.forEach(refsetMember -> {
			final String referencedComponentId = refsetMember.getReferencedComponentId();
			final Long conceptId = componentToConceptIdMap.get(parseLong(referencedComponentId));
			if (conceptId != null) {
				conceptToMembersMap.computeIfAbsent(conceptId, id -> new ArrayList<>()).add(refsetMember);
			} else {
				logger.error("Refset member {} with referenced component {} can not be mapped to a concept id for traceability on branch {}",
						refsetMember.getId(), refsetMember.getReferencedComponentId(), commit.getBranch().getPath());
			}
		});
		return conceptToMembersMap;
	}

	private Activity.ConceptActivity getConceptActivityForComponent(Activity activity, Map<Long, Activity.ConceptActivity> activityMap, long conceptId) {
		return activityMap.computeIfAbsent(conceptId, id -> {
			String conceptId1 = Long.toString(conceptId);
			Activity.ConceptActivity conceptActivity = activity.addConceptActivity(conceptId1);
			activityMap.put(Long.parseLong(conceptId1), conceptActivity);
			return conceptActivity;
		});
	}

	private Activity.ComponentChange getChange(SnomedComponent<?> component) {
		Activity.ChangeType type;
		if (component.isCreating()) {
			type = Activity.ChangeType.CREATE;
		} else if (component.isDeleted()) {
			type = Activity.ChangeType.DELETE;
		} else if (!component.isActive()) {
			type = Activity.ChangeType.INACTIVATE;
		} else {
			type = Activity.ChangeType.UPDATE;
		}


		final Activity.ComponentChange componentChange = new Activity.ComponentChange(
				Activity.ComponentType.valueOf(component.getClass().getSimpleName().replaceAll("([a-z])([A-Z]+)", "$1_$2").toUpperCase()),
				getComponentSubType(component),
				component.getId(),
				type,
				component.getEffectiveTime() == null);
		logger.debug("Component change {} for component {}", componentChange, component);
		return componentChange;
	}

	private Long getComponentSubType(SnomedComponent<?> component) {
		if (component instanceof Relationship) {
			return parseLong(((Relationship) component).getTypeId());
		} else if (component instanceof Description) {
			return parseLong(((Description) component).getTypeId());
		} else if (component instanceof ReferenceSetMember) {
			return parseLong(((ReferenceSetMember) component).getRefsetId());
		}
		return null;
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
}
