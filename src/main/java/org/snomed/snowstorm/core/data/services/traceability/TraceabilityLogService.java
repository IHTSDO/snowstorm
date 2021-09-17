package org.snomed.snowstorm.core.data.services.traceability;

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
import org.snomed.snowstorm.core.data.services.BranchMetadataHelper;
import org.snomed.snowstorm.core.data.services.ServiceUtil;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.data.services.pojo.PersistedComponents;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.CLAUSE_LIMIT;
import static io.kaicode.elasticvc.api.VersionControlHelper.LARGE_PAGE;
import static io.kaicode.elasticvc.domain.Commit.CommitType.CONTENT;
import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.snomed.snowstorm.core.data.services.traceability.Activity.ActivityType.CREATE_CODE_SYSTEM_VERSION;

@Service
public class TraceabilityLogService implements CommitListener {

	public static final String DISABLE_IMPORT_TRACEABILITY = "disableImportTraceability";

	@Value("${authoring.traceability.enabled}")
	private boolean enabled;

	@Autowired
	@Lazy // This should stop the JMS Template getting initialised if traceability is disabled.
	private TraceabilityConsumer traceabilityConsumer;

	@Value("${authoring.traceability.inferred-max}")
	private int inferredMax;

	@Autowired
	private TraceabilityLogServiceHelper traceabilityLogServiceHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private VersionControlHelper versionControlHelper;

	private final ObjectMapper objectMapper;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public TraceabilityLogService() {
		objectMapper = Jackson2ObjectMapperBuilder.json()
				.serializationInclusion(JsonInclude.Include.NON_NULL)
				.build();
	}

	@Override
	public void preCommitCompletion(final Commit commit) throws IllegalStateException {

		if (isTraceabilitySkippedForCommit(commit)) {
			return;
		}

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
		if (BranchMetadataHelper.isClassificationCommit(commit)) {
			activityType = Activity.ActivityType.CLASSIFICATION_SAVE;
		}
		if (BranchMetadataHelper.isCreatingCodeSystemVersion(commit)) {
			activityType = CREATE_CODE_SYSTEM_VERSION;
		}
		ServiceUtil.assertNotNull("Traceability activity type", activityType);

		PersistedComponents persistedComponents = activityType == Activity.ActivityType.PROMOTION || activityType == Activity.ActivityType.CREATE_CODE_SYSTEM_VERSION ?
				new PersistedComponents() : buildPersistedComponents(commit);

		logActivity(SecurityUtil.getUsername(), commit, persistedComponents, activityType);
	}

	private boolean isTraceabilitySkippedForCommit(Commit commit) {
		final Map<String, String> internalMap = commit.getBranch().getMetadata().getMapOrCreate(BranchMetadataHelper.INTERNAL_METADATA_KEY);
		return "true".equals(internalMap.get(DISABLE_IMPORT_TRACEABILITY));
	}

	private PersistedComponents buildPersistedComponents(final Commit commit) {
		final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaChangesAndDeletionsWithinOpenCommitOnly(commit);
		final String branchPath = commit.getBranch().getPath();
		return PersistedComponents.builder()
				.withPersistedConcepts(
						traceabilityLogServiceHelper.loadChangesAndDeletionsWithinOpenCommitOnly(Concept.class, branchCriteria, branchPath, commit))
				.withPersistedDescriptions(
						traceabilityLogServiceHelper.loadChangesAndDeletionsWithinOpenCommitOnly(Description.class, branchCriteria, branchPath, commit))
				.withPersistedRelationships(
						traceabilityLogServiceHelper.loadChangesAndDeletionsWithinOpenCommitOnly(Relationship.class, branchCriteria, branchPath, commit))
				.withPersistedReferenceSetMembers(
						traceabilityLogServiceHelper.loadChangesAndDeletionsWithinOpenCommitOnly(ReferenceSetMember.class, branchCriteria, branchPath, commit))
				.build();
	}

	void logActivity(String userId, final Commit commit, final PersistedComponents persistedComponents, Activity.ActivityType activityType) {

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
			getConceptActivityForComponent(activity, activityMap, concept.getConceptIdAsLong()).addComponentChange(getChange(concept));
		}
		for (Description description : persistedComponents.getPersistedDescriptions()) {
			final long conceptId = parseLong(description.getConceptId());
			getConceptActivityForComponent(activity, activityMap, conceptId).addComponentChange(getChange(description));
			componentToConceptIdMap.put(parseLong(description.getDescriptionId()), conceptId);
		}
		for (Relationship relationship : persistedComponents.getPersistedRelationships()) {
			final long sourceId = parseLong(relationship.getSourceId());
			getConceptActivityForComponent(activity, activityMap, sourceId).addComponentChange(getChange(relationship));
			componentToConceptIdMap.put(parseLong(relationship.getRelationshipId()), sourceId);
		}

		// Deal with members that refer to descriptions or relationships by looking up their concepts.
		final Map<Long, List<ReferenceSetMember>> conceptMembersMap =
				filterRefsetMembersAndLookupComponentConceptIds(persistedComponents.getPersistedReferenceSetMembers(), commit, componentToConceptIdMap);

		// Record all refset members against concept activities
		for (Map.Entry<Long, List<ReferenceSetMember>> entry : conceptMembersMap.entrySet()) {
			final Activity.ConceptActivity conceptActivityForComponent = getConceptActivityForComponent(activity, activityMap, entry.getKey());
			for (ReferenceSetMember referenceSetMember : entry.getValue()) {
				conceptActivityForComponent.addComponentChange(getChange(referenceSetMember));
			}
		}

		Map<String, Activity.ConceptActivity> changes = activity.getChangesMap();
		boolean changeFound = changes.values().stream().anyMatch(conceptActivity -> !conceptActivity.getComponentChanges().isEmpty());
		if (commit.getCommitType() == CONTENT && !changeFound && activityType != CREATE_CODE_SYSTEM_VERSION) {
			logger.info("Skipping traceability because there was no traceable change for commit {} at {}.", commit.getBranch().getPath(), commit.getTimepoint().getTime());
			return;
		}

		// Limit the number of inferred relationship changes logged
		long inferredChangesAccepted = 0;
		List<String> conceptChangesToRemove = new ArrayList<>();
		for (Activity.ConceptActivity conceptActivity : activityMap.values()) {
			if (inferredChangesAccepted > inferredMax) {
				// Remove activities with only inferred changes
				if (conceptActivity.getComponentChanges().stream()
						.allMatch(componentChange -> componentChange.isComponentSubType(Long.parseLong(Concepts.INFERRED_RELATIONSHIP)))) {
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

		traceabilityConsumer.accept(activity);
	}

	private Map<Long, List<ReferenceSetMember>> filterRefsetMembersAndLookupComponentConceptIds(Iterable<ReferenceSetMember> persistedReferenceSetMembers,
			Commit commit, Map<Long, Long> componentToConceptIdMap) {

		Map<Long, List<ReferenceSetMember>> conceptToMembersMap = new Long2ObjectArrayMap<>();

		List<ReferenceSetMember> membersToLog = new ArrayList<>();
		Set<Long> referencedDescriptions = new LongOpenHashSet();
		Set<Long> referencedRelationships = new LongOpenHashSet();
		for (ReferenceSetMember refsetMember : persistedReferenceSetMembers) {
			String conceptId = refsetMember.getConceptId();
			if (conceptId != null) {
				conceptToMembersMap.computeIfAbsent(parseLong(conceptId), id -> new ArrayList<>()).add(refsetMember);
			} else {
				membersToLog.add(refsetMember);
				final String referencedComponentId = refsetMember.getReferencedComponentId();
				if (!IdentifierService.isConceptId(referencedComponentId)) {
					if (IdentifierService.isDescriptionId(referencedComponentId)) {
						referencedDescriptions.add(parseLong(referencedComponentId));
					} else if (IdentifierService.isRelationshipId(referencedComponentId)) {
						referencedRelationships.add(parseLong(referencedComponentId));
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
			return parseLong(((Relationship) component).getCharacteristicTypeId());
		} else if (component instanceof Description) {
			return parseLong(((Description) component).getTypeId());
		} else if (component instanceof ReferenceSetMember) {
			return parseLong(((ReferenceSetMember) component).getRefsetId());
		}
		return null;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setTraceabilityConsumer(TraceabilityConsumer traceabilityConsumer) {
		this.traceabilityConsumer = traceabilityConsumer;
	}
}
