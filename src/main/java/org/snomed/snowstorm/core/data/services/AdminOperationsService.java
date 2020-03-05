package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Lists;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.DomainEntity;
import io.kaicode.elasticvc.repositories.BranchRepository;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.SearchLanguagesConfiguration;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.rf2.RF2Constants;
import org.snomed.snowstorm.core.util.DescriptionHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.*;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.google.common.collect.Iterables.partition;
import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static java.lang.Long.parseLong;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.*;

@Service
public class AdminOperationsService {

	@Autowired
	private SearchLanguagesConfiguration searchLanguagesConfiguration;

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private BranchMergeService branchMergeService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private DomainEntityConfiguration domainEntityConfiguration;

	@Autowired
	private ConceptUpdateHelper conceptUpdateHelper;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private BranchRepository branchRepository;

	private Logger logger = LoggerFactory.getLogger(getClass());
	public static final int TEN_SECONDS_IN_MILLIS = 1000 * 10;

	public void reindexDescriptionsForLanguage(String languageCode) throws IOException {
		Map<String, Set<Character>> charactersNotFoldedSets = searchLanguagesConfiguration.getCharactersNotFoldedSets();
		Set<Character> foldedCharacters = charactersNotFoldedSets.getOrDefault(languageCode, Collections.emptySet());
		logger.info("Reindexing all description documents in version control with language code '{}' using {} folded characters.", languageCode, foldedCharacters.size());
		AtomicLong descriptionCount = new AtomicLong();
		AtomicLong descriptionUpdateCount = new AtomicLong();
		try (CloseableIterator<Description> descriptionsOnAllBranchesStream = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
						.withQuery(termQuery(Description.Fields.LANGUAGE_CODE, languageCode))
						.withSort(SortBuilders.fieldSort("internalId"))
						.withPageable(LARGE_PAGE)
						.build(),
				Description.class)) {

			List<UpdateQuery> updateQueries = new ArrayList<>();
			AtomicReference<IOException> exceptionThrown = new AtomicReference<>();
			descriptionsOnAllBranchesStream.forEachRemaining(description -> {
				if (exceptionThrown.get() == null) {

					String newFoldedTerm = DescriptionHelper.foldTerm(description.getTerm(), foldedCharacters);
					descriptionCount.incrementAndGet();
					if (!newFoldedTerm.equals(description.getTermFolded())) {
						UpdateRequest updateRequest = new UpdateRequest();
						try {
							updateRequest.doc(jsonBuilder()
									.startObject()
									.field(Description.Fields.TERM_FOLDED, newFoldedTerm)
									.endObject());
						} catch (IOException e) {
							exceptionThrown.set(e);
						}

						updateQueries.add(new UpdateQueryBuilder()
								.withClass(Description.class)
								.withId(description.getInternalId())
								.withUpdateRequest(updateRequest)
								.build());
						descriptionUpdateCount.incrementAndGet();
					}
					if (updateQueries.size() == 10_000) {
						logger.info("Bulk update {}", descriptionUpdateCount.get());
						elasticsearchTemplate.bulkUpdate(updateQueries);
						updateQueries.clear();
					}
				}
			});
			if (exceptionThrown.get() != null) {
				throw exceptionThrown.get();
			}
			if (!updateQueries.isEmpty()) {
				logger.info("Bulk update {}", descriptionUpdateCount.get());
				elasticsearchTemplate.bulkUpdate(updateQueries);
			}
		} finally {
			elasticsearchTemplate.refresh(Description.class);
		}
		logger.info("Completed reindexing of description documents with language code '{}'. Of the {} documents found {} were updated due to a character folding change.",
				languageCode, descriptionCount.get(), descriptionUpdateCount.get());
	}

	public Map<Class, Set<String>> findAndEndDonatedContent(String branch) {
		if (PathUtil.isRoot(branch)) {
			throw new IllegalArgumentException("Donated content should be ended on extension branch, not MAIN.");
		}

		logger.info("Finding and fixing donated content on {}.", branch);

		Map<Class, Set<String>> fixesApplied = new HashMap<>();
		branchMergeService.fixDuplicateComponents(branch, versionControlHelper.getBranchCriteria(branch), true, fixesApplied);

		logger.info("Completed donated content fixing on {}.", branch);
		return fixesApplied;
	}

	public Map<Class, Set<String>> findDuplicateAndHideParentVersion(String branch) {
		if (PathUtil.isRoot(branch)) {
			throw new IllegalArgumentException("This fixed can not be used on MAIN because there are no ancestor branches.");
		}

		logger.info("Finding duplicate content on {} and hiding parent version.", branch);

		Map<Class, Set<String>> fixesApplied = new HashMap<>();
		branchMergeService.fixDuplicateComponents(branch, versionControlHelper.getBranchCriteria(branch), false, fixesApplied);

		logger.info("Completed hiding parent version of duplicate content on {}.", branch);
		return fixesApplied;
	}

	public void rollbackCommit(String branchPath, long timepoint) {
		Branch branchVersion = branchService.findAtTimepointOrThrow(branchPath, new Date(timepoint));
		if (branchVersion.getEnd() != null) {
			throw new IllegalStateException(String.format("Branch %s at timepoint %s is already ended, it's not the latest commit.", branchPath, timepoint));
		}
		branchService.rollbackCompletedCommit(branchVersion, new ArrayList<>(domainEntityConfiguration.getAllDomainEntityTypes()));
	}

	public void restoreGroupNumberOfInactiveRelationships(String branchPath, String currentEffectiveTime, String previousReleaseBranch) {
		logger.info("Restoring group number of inactive relationships on branch {}.", branchPath);

		Map<Long, Relationship> inactiveRelationships = getAllInactiveRelationships(branchPath, currentEffectiveTime);
		logger.info("{} relationships inactive on this branch branch with effectiveTime {}.", inactiveRelationships.size(), currentEffectiveTime);

		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(previousReleaseBranch);
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(
				boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Relationship.class))
						.must(termQuery(Relationship.Fields.ACTIVE, false))
						.must(termQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, Concepts.INFERRED_RELATIONSHIP)))
				.withFilter(termsQuery(Relationship.Fields.RELATIONSHIP_ID, inactiveRelationships.keySet()))
				.withPageable(LARGE_PAGE)
				.build();

		List<Relationship> correctedRelationships = new ArrayList<>();
		try (CloseableIterator<Relationship> stream = elasticsearchTemplate.stream(searchQuery, Relationship.class)) {
			stream.forEachRemaining(relationshipInPreviousRelease -> {
				Relationship currentRelationship = inactiveRelationships.get(parseLong(relationshipInPreviousRelease.getRelationshipId()));
				if (currentRelationship.getGroupId() != relationshipInPreviousRelease.getGroupId()) {
					currentRelationship.setGroupId(relationshipInPreviousRelease.getGroupId());
					currentRelationship.copyReleaseDetails(relationshipInPreviousRelease);
					currentRelationship.markChanged();
					correctedRelationships.add(currentRelationship);
				}
			});
		}

		List<String> first10Ids = correctedRelationships.subList(0, correctedRelationships.size() > 10 ? 10 : correctedRelationships.size())
				.stream().map(Relationship::getRelationshipId).collect(Collectors.toList());

		logger.info("{} relationships found on the previous release branch {} with a different role group, examples: {}",
				correctedRelationships.size(), previousReleaseBranch, first10Ids);

		if (!correctedRelationships.isEmpty()) {
			try (Commit commit = branchService.openCommit(branchPath)) {
				for (List<Relationship> batch : Lists.partition(correctedRelationships, 1_000)) {
					logger.info("Correcting batch of {} relationships ...", batch.size());
					conceptUpdateHelper.doSaveBatchRelationships(batch, commit);
				}
				commit.markSuccessful();
			}
			logger.info("All inactive relationship groups restored.");
		}
	}

	private Map<Long, Relationship> getAllInactiveRelationships(String previousReleaseBranch, String effectiveTime) {
		Map<Long, Relationship> relationshipMap = new Long2ObjectOpenHashMap<>();
		try (CloseableIterator<Relationship> stream = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(
						boolQuery()
								.must(versionControlHelper.getBranchCriteria(previousReleaseBranch).getEntityBranchCriteria(Relationship.class))
								.must(termQuery(Relationship.Fields.EFFECTIVE_TIME, effectiveTime))
								.must(termQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, Concepts.INFERRED_RELATIONSHIP))
								.must(termQuery(Relationship.Fields.ACTIVE, false))
						)
				.withPageable(LARGE_PAGE)
				.build(), Relationship.class)) {
			stream.forEachRemaining(relationship -> {
				relationshipMap.put(parseLong(relationship.getRelationshipId()), relationship);
				if (relationshipMap.size() % 10_000 == 0) {
					System.out.print(".");
				}
			});
			System.out.println();
		}
		return relationshipMap;
	}

	public void hardDeleteBranch(String path) {
		Branch branch = branchService.findBranchOrThrow(path);
		if (PathUtil.isRoot(path)) {
			throw new IllegalArgumentException("The root branch can not be deleted.");
		}
		int childrenCount = branchService.findChildren(path).size();
		if (childrenCount != 0) {
			throw new IllegalStateException(String.format("Branch '%s' can not be deleted because is has children (%s).", path, childrenCount));
		}

		if (branch.isLocked()) {
			branchService.unlock(path);
		}
		branchService.lockBranch(path, "Deleting branch.");

		logger.info("Deleting all documents on branch {}.", path);
		DeleteQuery deleteQuery = new DeleteQuery();
		deleteQuery.setQuery(QueryBuilders.termQuery("path", path));
		for (Class<? extends DomainEntity> domainEntityType : domainEntityConfiguration.getAllDomainEntityTypes()) {
			logger.info("Deleting all {} type documents on branch {}.", domainEntityType.getSimpleName(), path);
			elasticsearchTemplate.delete(deleteQuery, domainEntityType);
			elasticsearchTemplate.refresh(domainEntityType);
		}

		logger.info("Deleting branch documents for path {}.", path);
		elasticsearchTemplate.delete(deleteQuery, Branch.class);
		elasticsearchTemplate.refresh(Branch.class);
	}

	public void deleteExtraInferredRelationships(String branchPath, InputStream relationshipsToKeepInputStream, int effectiveTime) throws IOException {
		logger.info("Starting process for deleteExtraInferredRelationships");

		Set<Long> relationshipIdsToKeep = new LongOpenHashSet();
		String effectiveTimeString = effectiveTime + "";
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(relationshipsToKeepInputStream))) {
			String line;
			String header = reader.readLine();
			if (header == null || !header.equals(RF2Constants.RELATIONSHIP_HEADER)) {
				throw new IllegalArgumentException("First line of file does not match the RF2 relationship header.");
			}
			long lineNumber = 0;
			while ((line = reader.readLine()) != null && !line.isEmpty()) {
				lineNumber++;
				String[] rows = line.split("\t");
				if (rows.length != 10) {
					throw new IllegalArgumentException(String.format("Line %s does not have the expected 10 columns.", lineNumber));
				}
				if (rows[1].equals(effectiveTimeString)) {
					relationshipIdsToKeep.add(Long.parseLong(rows[0]));
				}
			}
		}

		// Move with caution
		if (relationshipIdsToKeep.isEmpty()) {
			throw new IllegalStateException("Relationship snapshot has no rows at the given effectiveTime.");
		}

		logger.info("Read {} relationship ids to keep. Starting search for other relationships to delete on branch {}.", relationshipIdsToKeep.size(), branchPath);

		Set<Long> relationshipIdsToDelete = new LongOpenHashSet();
		Set<Long> relationshipIdsExpected = new LongOpenHashSet();
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(branchCriteria.getEntityBranchCriteria(Relationship.class)
						.must(termQuery(Relationship.Fields.EFFECTIVE_TIME, effectiveTime)))
				.withFields(Relationship.Fields.RELATIONSHIP_ID)
				.withPageable(LARGE_PAGE);
		try (CloseableIterator<Relationship> stream = elasticsearchTemplate.stream(queryBuilder.build(), Relationship.class)) {
			stream.forEachRemaining(relationship -> {
				Long relationshipId = Long.parseLong(relationship.getRelationshipId());
				if (!relationshipIdsToKeep.contains(relationshipId)) {
					relationshipIdsToDelete.add(relationshipId);
				} else {
					relationshipIdsExpected.add(relationshipId);
				}
			});
		}

		logger.info("Found {} of {} expected relationship IDs at this effectiveTime.", relationshipIdsExpected.size(), relationshipIdsToKeep.size());

		if (relationshipIdsToDelete.isEmpty()) {
			logger.info("No relationships found for deletion.");
			return;
		}

		logger.info("Found {} relationships to delete on branch {}, for example {}.", relationshipIdsToDelete.size(), branchPath, relationshipIdsToDelete.iterator().next());

		List<Relationship> relationshipObjectsToDelete = relationshipIdsToDelete.stream().map(id -> {
			Relationship relationship = new Relationship(id.toString());
			relationship.markDeleted();
			return relationship;
		}).collect(Collectors.toList());
		try (Commit commit = branchService.openCommit(branchPath, "Deleting batch of extra relationships.")) {
			for (List<Relationship> relationshipBatch : Lists.partition(relationshipObjectsToDelete, 1_000)) {
				conceptUpdateHelper.doSaveBatchRelationships(relationshipBatch, commit);
			}
			commit.markSuccessful();
		}

		logger.info("Extra relationships successfully deleted on {}.", branchPath);
	}

	@SuppressWarnings("rawtypes")
	public void promoteReleaseFix(String releaseFixBranchPath) {

		// Beware! This method takes the version control mechanism into its own hands. Here be dragons.

		final Branch releaseFixBranch = branchService.findLatest(releaseFixBranchPath);
		if (!branchService.exists(releaseFixBranchPath)) {
			throw new IllegalArgumentException("Branch given must be a code system version branch. Branch given does not exist.");
		}
		final String codeSystemPath = PathUtil.getParentPath(releaseFixBranchPath);
		if (codeSystemPath == null) {
			throw new IllegalArgumentException("Branch given must be a code system version branch. Branch given is root.");
		}
		final Optional<CodeSystem> codeSystemOptional = codeSystemService.findByBranchPath(codeSystemPath);
		if (!codeSystemOptional.isPresent()) {
			throw new IllegalArgumentException("Branch given must be a code system version branch. No code system found on the parent branch.");
		}
		CodeSystem codeSystem = codeSystemOptional.get();

		CodeSystemVersion latestImportedVersion = codeSystemService.findLatestImportedVersion(codeSystem.getShortName());
		if (latestImportedVersion == null) {
			throw new IllegalStateException("No code system version found on the parent branch.");
		}
		if (!latestImportedVersion.getBranchPath().equals(releaseFixBranchPath)) {
			throw new IllegalArgumentException("The latest code system version does not match the branch given. This fix can only be applied to the latest version.");
		}

		if (!codeSystemPath.equals("MAIN")) {
			throw new IllegalArgumentException("This method currently only works for the code system on the MAIN branch.");
			// To use this method with other code systems further work would be required to identify any branch "version replaced" ids which
			// should be copied to the promotion commit. This will be required if the version replaced is on a parent code system.
		}

		// The fix promotion will make a commit which could be back in time in relation to other commits on the code system branch.
		// I.E. the version of the branch we are committing in front of may not be the head of the branch.

		// The promoted fix will last for 10 seconds on the timeline.
		// The code system version branch will be rebased onto this commit.
		// After 10 seconds another commit will be made which will revert the code system branch back to it's previous state
		// without any trace of the fix. This is so newer commits on the same branch and child branches are not disturbed
		// by insertion of content back in time. The version control system was not designed to be able to deal with this.

		// Find commit on parent branch matching head of version branch
		logger.info("Release fix branch base " + releaseFixBranch.getBase().getTime());
		logger.info("Release fix branch head " + releaseFixBranch.getHead().getTime());
		final Branch codeSystemVersionCommit = branchService.findAtTimepointOrThrow(codeSystemPath, releaseFixBranch.getBase());
		final BranchCriteria codeSystemVersionCommitBranchCriteria = versionControlHelper.getBranchCriteriaAtTimepoint(codeSystemPath, codeSystemVersionCommit.getHead());

		// Date of now used to end components on the release branch
		Date now = new Date();

		// Promotion commit will be ten seconds after original version commit.
		logger.info("Code system version commit head " + codeSystemVersionCommit.getHead().getTime());
		Date promotionCommitTime = new Date(codeSystemVersionCommit.getHeadTimestamp() + TEN_SECONDS_IN_MILLIS);

		// Revert commit will be ten seconds after promotion commit.
		Date revertCommitTime = new Date(promotionCommitTime.getTime() + TEN_SECONDS_IN_MILLIS);

		logger.info("Promoting fixes from release branch into code system branch back in time " + promotionCommitTime);
		logger.info("Fixes from branch {} will be applied to branch {} at timepoint {} and then reverted at timepoint {}.",
				releaseFixBranchPath, codeSystemPath, promotionCommitTime.getTime(), revertCommitTime.getTime());

		// For each entity type
		final BranchCriteria changesOnFixBranch = versionControlHelper.getChangesOnBranchCriteria(releaseFixBranch);
		for (Class<? extends DomainEntity> type : domainEntityConfiguration.getAllDomainEntityTypes()) {
			// Grab all the entities of this type on the fix branch
			NativeSearchQuery query = new NativeSearchQueryBuilder()
					.withQuery(boolQuery().must(changesOnFixBranch.getEntityBranchCriteria(type)))
					.withPageable(LARGE_PAGE).build();
			Collection<DomainEntity> entitiesToPromote = new ArrayList<>();
			try (CloseableIterator<? extends DomainEntity> stream = elasticsearchTemplate.stream(query, type)) {
				stream.forEachRemaining(entitiesToPromote::add);
			}
			logger.info("Found {} {} to promote.", entitiesToPromote.size(), type.getSimpleName());
			if (!entitiesToPromote.isEmpty()) {
				// Manually promote all entity types including the semantic index.
				// We do not want any post-commit hooks to run because they will not be able to deal with a new historic commit.
				// Post-commit hooks will not run because we will not be asking the branch service for a Commit object.

				// Find existing versions of the same entities on the parent branch
				String typeIdField = domainEntityConfiguration.getAllIdFields().get(type);
				ElasticsearchCrudRepository typeRepository = domainEntityConfiguration.getAllTypeRepositoryMap().get(type);
				for (List<DomainEntity> entitiesToPromoteBatch : partition(entitiesToPromote, 1_000)) {
					List<DomainEntity> existingEntitiesBatch = new ArrayList<>();
					Map<String, Date> existingEntitiesOriginalStartDate = new HashMap<>();
					try (CloseableIterator<? extends DomainEntity> existingEntityStream = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
							.withQuery(boolQuery()
									.must(codeSystemVersionCommitBranchCriteria.getEntityBranchCriteria(type))
									.must(termsQuery(typeIdField, entitiesToPromoteBatch.stream().map(DomainEntity::getId).collect(Collectors.toList())))
							).withPageable(LARGE_PAGE).build(), type)) {
						existingEntityStream.forEachRemaining(existingEntity -> {
							if (existingEntity.getPath().equals(codeSystemPath)) {
								existingEntitiesBatch.add(existingEntity);
							} else {
								logger.error("Existing entity exists on a path which must be an ancestor of the codesystem branch. " +
												"Not promoting entity to avoid duplicate versions of {} {}. {} {}",
										existingEntity.getClass().getSimpleName(), existingEntity.getId(), existingEntity.getInternalId(), existingEntity.getPath());
							}
						});
					}

					if (!existingEntitiesBatch.isEmpty()) {
						logger.info("{} existing {} found on the code system path for this batch, for example {} {}.",
								existingEntitiesBatch.size(), type.getSimpleName(), type.getSimpleName(), existingEntitiesBatch.iterator().next().getId());

						// Update start date of existing entities to the revert commit.
						for (DomainEntity existingEntity : existingEntitiesBatch) {
							existingEntitiesOriginalStartDate.put(existingEntity.getId(), existingEntity.getStart());
							existingEntity.setStart(revertCommitTime);
						}
						logger.info("Start later {} {} in revert commit.", existingEntitiesBatch.size(), type.getSimpleName());
						typeRepository.saveAll(existingEntitiesBatch);

						// Save another version of the existing entities in the timeline before the promotion commit with the original start date.
						for (DomainEntity existingEntity : existingEntitiesBatch) {
							existingEntity.setStart(existingEntitiesOriginalStartDate.get(existingEntity.getId()));
							existingEntity.setEnd(promotionCommitTime);
							existingEntity.clearInternalId();
						}
						logger.info("Restore {} {} before revert commit.", existingEntitiesBatch.size(), type.getSimpleName());
						typeRepository.saveAll(existingEntitiesBatch);

						// We have created a gap in the timeline to insert the fix version of the component
						// to prevent having multiple versions of the same entity existing at once.
					}

					// End versions on the version branch
					for (DomainEntity entity : entitiesToPromoteBatch) {
						// End after set time period
						entity.setEnd(now);
					}
					logger.info("Ending {} {} on source branch with now {} end date.", existingEntitiesBatch.size(), type.getSimpleName(), now.getTime());
					typeRepository.saveAll(entitiesToPromoteBatch);

					// Copy the entities to the parent branch
					for (DomainEntity entity : entitiesToPromoteBatch) {
						entity.setPath(codeSystemPath);
						entity.setStart(promotionCommitTime);
						// End after set time period
						entity.setEnd(revertCommitTime);
						entity.clearInternalId();

						// For Snomed Components (this does not include the semantic index QueryConcept) set the effectiveTime
						// and related fields to simulate a release
						if (entity instanceof SnomedComponent) {
							SnomedComponent component = (SnomedComponent) entity;
							component.release(latestImportedVersion.getEffectiveDate());
						}
					}
					logger.info("Promoting {} {} before revert commit.", existingEntitiesBatch.size(), type.getSimpleName());
					typeRepository.saveAll(entitiesToPromoteBatch);
				}
			}
		}

		// End original code system version commit.
		// The end time of the branch version should be carried forward to the revert commit.
		final Date versionCommitOriginalEndTime = codeSystemVersionCommit.getEnd();
		codeSystemVersionCommit.setEnd(promotionCommitTime);

		Branch promotionCommit = new Branch(codeSystemVersionCommit.getPath());
		promotionCommit.setBase(codeSystemVersionCommit.getBase());
		promotionCommit.setStart(promotionCommitTime);
		promotionCommit.setHead(promotionCommitTime);
		promotionCommit.setEnd(revertCommitTime);
		promotionCommit.setMetadata(codeSystemVersionCommit.getMetadata());
		promotionCommit.addVersionsReplaced(codeSystemVersionCommit.getVersionsReplaced());
		promotionCommit.setCreation(codeSystemVersionCommit.getCreation());
		promotionCommit.setLastPromotion(codeSystemVersionCommit.getLastPromotion());

		Branch revertCommit = new Branch(codeSystemVersionCommit.getPath());
		revertCommit.setBase(codeSystemVersionCommit.getBase());
		revertCommit.setStart(revertCommitTime);
		revertCommit.setHead(revertCommitTime);
		revertCommit.setEnd(versionCommitOriginalEndTime);
		revertCommit.setMetadata(codeSystemVersionCommit.getMetadata());
		revertCommit.addVersionsReplaced(codeSystemVersionCommit.getVersionsReplaced());
		revertCommit.setCreation(codeSystemVersionCommit.getCreation());
		revertCommit.setLastPromotion(codeSystemVersionCommit.getLastPromotion());

		releaseFixBranch.setEnd(now);
		Branch releaseBranchPromotionCommit = new Branch(releaseFixBranch.getPath());
		releaseBranchPromotionCommit.setMetadata(releaseFixBranch.getMetadata());
		releaseBranchPromotionCommit.setCreation(releaseFixBranch.getCreation());
		releaseBranchPromotionCommit.setLastPromotion(promotionCommitTime);
		releaseBranchPromotionCommit.setStart(now);
		releaseBranchPromotionCommit.setBase(promotionCommitTime);
		releaseBranchPromotionCommit.setHead(now);
		releaseBranchPromotionCommit.setContainsContent(false);

		branchRepository.saveAll(Lists.newArrayList(codeSystemVersionCommit, promotionCommit, revertCommit, releaseFixBranch, releaseBranchPromotionCommit));
		logger.info("All content promoted and commits made. Fix promotion complete.");
	}

}
