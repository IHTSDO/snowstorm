package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Iterables;
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
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.SearchLanguagesConfiguration;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.rf2.RF2Constants;
import org.snomed.snowstorm.core.util.DescriptionHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.collect.Iterables.partition;
import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static java.lang.Long.parseLong;
import static java.lang.String.format;
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

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private RelationshipService relationshipService;

	private Logger logger = LoggerFactory.getLogger(getClass());
	public static final int ONE_SECOND_IN_MILLIS = 1000;

	public void reindexDescriptionsForLanguage(String languageCode) throws IOException {
		Map<String, Set<Character>> charactersNotFoldedSets = searchLanguagesConfiguration.getCharactersNotFoldedSets();
		Set<Character> foldedCharacters = charactersNotFoldedSets.getOrDefault(languageCode, Collections.emptySet());
		logger.info("Reindexing all description documents in version control with language code '{}' using {} folded characters.", languageCode, foldedCharacters.size());
		AtomicLong descriptionCount = new AtomicLong();
		AtomicLong descriptionUpdateCount = new AtomicLong();
		try (SearchHitsIterator<Description> descriptionsOnAllBranchesStream = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
						.withQuery(termQuery(Description.Fields.LANGUAGE_CODE, languageCode))
						.withSort(SortBuilders.fieldSort("internalId"))
						.withPageable(LARGE_PAGE)
						.build(),
				Description.class)) {

			List<UpdateQuery> updateQueries = new ArrayList<>();
			AtomicReference<IOException> exceptionThrown = new AtomicReference<>();
			descriptionsOnAllBranchesStream.forEachRemaining(hit -> {
				Description description = hit.getContent();
				if (exceptionThrown.get() == null) {

					String newFoldedTerm = DescriptionHelper.foldTerm(description.getTerm(), foldedCharacters);
					descriptionCount.incrementAndGet();
					if (!newFoldedTerm.equals(description.getTermFolded())) {
						final Document document = Document.create();
						document.put(Description.Fields.TERM_FOLDED, newFoldedTerm);
						updateQueries.add(UpdateQuery.builder(description.getInternalId())
								.withDocument(document)
								.build());
						descriptionUpdateCount.incrementAndGet();
					}
					if (updateQueries.size() == 10_000) {
						logger.info("Bulk update {}", descriptionUpdateCount.get());
						elasticsearchTemplate.bulkUpdate(updateQueries, elasticsearchTemplate.getIndexCoordinatesFor(Description.class));
						updateQueries.clear();
					}
				}
			});
			if (exceptionThrown.get() != null) {
				throw exceptionThrown.get();
			}
			if (!updateQueries.isEmpty()) {
				logger.info("Bulk update {}", descriptionUpdateCount.get());
				elasticsearchTemplate.bulkUpdate(updateQueries, elasticsearchTemplate.getIndexCoordinatesFor(Description.class));
			}
		} finally {
			elasticsearchTemplate.indexOps(Description.class).refresh();
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

	public Map<Class, AtomicLong> reduceVersionsReplaced(String branch) {
		// For all entries in the versionsReplaced map check if the document is from a child branch. If so remove from the set.
		Branch latest = branchService.findBranchOrThrow(branch);
		Map<Class, AtomicLong> reducedByType = new HashMap<>();
		Map<String, Set<String>> versionsReplaced = latest.getVersionsReplaced();
		final Map<Class<? extends DomainEntity>, ElasticsearchRepository> componentTypeRepoMap = domainEntityConfiguration.getAllTypeRepositoryMap();
		for (Class<? extends DomainEntity> type : componentTypeRepoMap.keySet()) {
			Set<String> toRemove = new HashSet<>();
			Set<String> versionsReplacedForType = versionsReplaced.getOrDefault(type.getSimpleName(), Collections.emptySet());
			for (List<String> versionsReplacedSegment : Iterables.partition(versionsReplacedForType, 1_000)) {
				try (final SearchHitsIterator<? extends DomainEntity> entitiesReplaced = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
						.withQuery(boolQuery()
								.must(prefixQuery("path", branch + "/"))
								.must(termsQuery("_id", versionsReplacedSegment))
						)
						.withPageable(ConceptService.LARGE_PAGE)
						.build(), type)) {

					entitiesReplaced.forEachRemaining(entity -> toRemove.add(entity.getId()));
				}
			}
			if (!toRemove.isEmpty()) {
				versionsReplacedForType.removeAll(toRemove);
				reducedByType.computeIfAbsent(type, (t) -> new AtomicLong(0)).addAndGet(toRemove.size());
			}
		}
		latest.setVersionsReplaced(versionsReplaced);
		branchRepository.save(latest);
		return reducedByType;
	}

	public void restoreGroupNumberOfInactiveRelationships(String branchPath, String currentEffectiveTime, String previousReleaseBranch) {
		logger.info("Restoring group number of inactive relationships on branch {}.", branchPath);

		Map<Long, Relationship> inactiveRelationships = getAllInactiveRelationships(branchPath, currentEffectiveTime);
		logger.info("{} relationships inactive on this branch with effectiveTime {}.", inactiveRelationships.size(), currentEffectiveTime);

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
		try (SearchHitsIterator<Relationship> stream = elasticsearchTemplate.searchForStream(searchQuery, Relationship.class)) {
			stream.forEachRemaining(hit -> {
				Relationship relationshipInPreviousRelease = hit.getContent();
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
		try (SearchHitsIterator<Relationship> stream = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
				.withQuery(
						boolQuery()
								.must(versionControlHelper.getBranchCriteria(previousReleaseBranch).getEntityBranchCriteria(Relationship.class))
								.must(termQuery(Relationship.Fields.EFFECTIVE_TIME, effectiveTime))
								.must(termQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, Concepts.INFERRED_RELATIONSHIP))
								.must(termQuery(Relationship.Fields.ACTIVE, false))
						)
				.withPageable(LARGE_PAGE)
				.build(), Relationship.class)) {
			stream.forEachRemaining(hit -> {
				relationshipMap.put(parseLong(hit.getContent().getRelationshipId()), hit.getContent());
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
			throw new IllegalStateException(format("Branch '%s' can not be deleted because is has children (%s).", path, childrenCount));
		}

		if (branch.isLocked()) {
			branchService.unlock(path);
		}
		branchService.lockBranch(path, "Deleting branch.");

		logger.info("Deleting all documents on branch {}.", path);

		Query deleteQuery = new NativeSearchQueryBuilder().withQuery(QueryBuilders.termQuery("path", path)).build();
		for (Class<? extends DomainEntity> domainEntityType : domainEntityConfiguration.getAllDomainEntityTypes()) {
			logger.info("Deleting all {} type documents on branch {}.", domainEntityType.getSimpleName(), path);
			elasticsearchTemplate.delete(deleteQuery, domainEntityType, elasticsearchTemplate.getIndexCoordinatesFor(domainEntityType));
			elasticsearchTemplate.indexOps(domainEntityType).refresh();
		}

		logger.info("Deleting branch documents for path {}.", path);
		elasticsearchTemplate.delete(deleteQuery, Branch.class, elasticsearchTemplate.getIndexCoordinatesFor(Branch.class));
		elasticsearchTemplate.indexOps(Branch.class).refresh();
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
					throw new IllegalArgumentException(format("Line %s does not have the expected 10 columns.", lineNumber));
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
		try (SearchHitsIterator<Relationship> stream = elasticsearchTemplate.searchForStream(queryBuilder.build(), Relationship.class)) {
			stream.forEachRemaining(hit -> {
				Long relationshipId = Long.parseLong(hit.getContent().getRelationshipId());
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

		// Date of now used to end components on the release branch
		Date now = new Date();

		// Promotion commit will be one second after original version commit.
		logger.info("Code system version commit head " + codeSystemVersionCommit.getHead().getTime());
		Date promotionCommitTime = new Date(codeSystemVersionCommit.getHeadTimestamp() + (ONE_SECOND_IN_MILLIS));

		// Revert commit will be one second after promotion commit.
		Date revertCommitTime = new Date(promotionCommitTime.getTime() + ONE_SECOND_IN_MILLIS);

		// Check there are no commits on the timeline where we are trying to write
		Branch existingBranchAtRevertCommit = branchService.findAtTimepointOrThrow(codeSystemPath, revertCommitTime);
		if (existingBranchAtRevertCommit.getHead().equals(promotionCommitTime)) {
			throw new IllegalStateException(format("There is already a commit on %s at %s, can not proceed.", codeSystemPath, promotionCommitTime.getTime()));
		}
		if (existingBranchAtRevertCommit.getHead().equals(revertCommitTime)) {
			throw new IllegalStateException(format("There is already a commit on %s at %s, can not proceed.", codeSystemPath, revertCommitTime.getTime()));
		}

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
			try (SearchHitsIterator<? extends DomainEntity> stream = elasticsearchTemplate.searchForStream(query, type)) {
				stream.forEachRemaining(hit -> entitiesToPromote.add(hit.getContent()));
			}

			Set<String> entityVersionsReplaced = releaseFixBranch.getVersionsReplaced(type);
			logger.info("Found {} {} to promote, {} versions replaced.", entitiesToPromote.size(), type.getSimpleName(), entityVersionsReplaced.size());

			if (!entitiesToPromote.isEmpty() || !entityVersionsReplaced.isEmpty()) {
				// Manually promote all entity types including the semantic index.
				// We do not want any post-commit hooks to run because they will not be able to deal with a new historic commit.
				// Post-commit hooks will not run because we will not be asking the branch service for a Commit object.
				ElasticsearchRepository typeRepository = domainEntityConfiguration.getAllTypeRepositoryMap().get(type);

				// Find existing versions of the same entities on the parent branch
				for (List<String> entityVersionsReplacedBatch : Lists.partition(new ArrayList<>(entityVersionsReplaced), 1_000)) {
					List<DomainEntity> existingEntitiesBatch = new ArrayList<>();
					Map<String, Date> existingEntitiesOriginalStartDate = new HashMap<>();
					try (SearchHitsIterator<? extends DomainEntity> existingEntityStream = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
							// Deleted or replaced component on fix branch
							.withQuery(termsQuery("_id", entityVersionsReplacedBatch))
							.withPageable(LARGE_PAGE).build(), type)) {
						existingEntityStream.forEachRemaining(hit -> {
							DomainEntity existingEntity = hit.getContent();
							if (existingEntity.getPath().equals(codeSystemPath)) {
								existingEntitiesBatch.add(existingEntity);
							} else {
								logger.error("Existing entity exists on a path which must be an ancestor of the codesystem branch. " +
												"Not promoting entity to avoid duplicate versions of {} {}. {} {}",
										existingEntity.getClass().getSimpleName(), existingEntity.getId(), existingEntity.getInternalId(), existingEntity.getPath());
							}
						});
					}

					if (existingEntitiesBatch.isEmpty()) {
						logger.info("No existing {} found on the code system path for this batch.", type.getSimpleName());
					} else {
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
				}

				for (List<DomainEntity> entitiesToPromoteBatch : partition(entitiesToPromote, 1_000)) {
					// End versions on the version branch
					for (DomainEntity entity : entitiesToPromoteBatch) {
						// End after set time period
						entity.setEnd(now);
					}
					logger.info("Ending {} {} on source branch with now {} end date.", entitiesToPromoteBatch.size(), type.getSimpleName(), now.getTime());
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
							if (component.getEffectiveTime() == null) {
								component.release(latestImportedVersion.getEffectiveDate());
							}
						}
					}
					logger.info("Promoting {} {} before revert commit.", entitiesToPromoteBatch.size(), type.getSimpleName());
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

		codeSystemService.clearCache();
	}

	public void cloneChildBranch(String sourceBranchPath, String destinationBranchPath) {
		String parentPath = PathUtil.getParentPath(sourceBranchPath);
		if (parentPath == null || !parentPath.equals(PathUtil.getParentPath(destinationBranchPath))) {
			throw new IllegalArgumentException("Source and destination branches must have a common parent branch.");
		}
		if (!branchService.findChildren(sourceBranchPath).isEmpty()) {
			throw new IllegalArgumentException("This operation only works on branches without children. The specified branch has children.");
		}
		if (branchService.exists(destinationBranchPath)) {
			throw new IllegalArgumentException("Destination branch already exists.");
		}

		List<Branch> sourceBranchCommits = branchService.findAllVersions(sourceBranchPath, LARGE_PAGE).getContent();
		int commitNumber = 0;
		for (Branch sourceBranchCommit : sourceBranchCommits) {
			commitNumber++;
			logger.info("Cloning branch {} to {}, commit {} of {}, timepoint {}:{}.", sourceBranchPath, destinationBranchPath, commitNumber, sourceBranchCommits.size(),
					sourceBranchCommit.getStart().getTime(), sourceBranchCommit.getStartDebugFormat());

			// Clone commit into new path
			sourceBranchCommit.setPath(destinationBranchPath);
			sourceBranchCommit.clearInternalId();
			branchRepository.save(sourceBranchCommit);

			// Clone commit content into new path
			Map<Class<? extends DomainEntity>, ElasticsearchRepository> allTypeRepositoryMap = domainEntityConfiguration.getAllTypeRepositoryMap();
			for (Map.Entry<Class<? extends DomainEntity>, ElasticsearchRepository> entry : allTypeRepositoryMap.entrySet()) {
				ElasticsearchRepository elasticsearchRepository = entry.getValue();
				List<DomainEntity> content = new ArrayList<>();
				try (SearchHitsIterator<? extends DomainEntity> searchHitsStream = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
						.withQuery(boolQuery()
								.must(termQuery("path", sourceBranchPath))
								.must(termQuery("start", sourceBranchCommit.getStart().getTime()))
						)
						.withPageable(LARGE_PAGE)
						.build(), entry.getKey())) {
					searchHitsStream.forEachRemaining(searchHit -> {
						DomainEntity domainEntity = searchHit.getContent();
						domainEntity.setPath(destinationBranchPath);
						domainEntity.clearInternalId();// ES will create a new document rather than updating.
						content.add(domainEntity);
						if (content.size() == 1_000) {
							logger.info("Cloning {} {}s", content.size(), entry.getKey().getSimpleName());
							elasticsearchRepository.saveAll(content);
							content.clear();
						}
					});
					if (!content.isEmpty()) {
						logger.info("Cloning {} {}s", content.size(), entry.getKey().getSimpleName());
						elasticsearchRepository.saveAll(content);
					}
				}
			}
		}
	}

	public void restoreReleasedStatus(String branchPath, Set<String> unbatchedConceptIds, boolean setDeletedComponentsToInactive) {
		final Pair<String, Optional<String>> latestReleaseAndDependantReleaseBranches = getLatestReleaseAndDependantReleaseBranches(branchPath);
		final String releaseBranch = latestReleaseAndDependantReleaseBranches.getFirst();
		final String dependantReleaseBranch = latestReleaseAndDependantReleaseBranches.getSecond().orElse(null);
		logger.info("Restoring components of {} concepts using release branch {}{}{}.", unbatchedConceptIds.size(), releaseBranch,
				dependantReleaseBranch != null ? " and dependant release branch " : "", dependantReleaseBranch != null ? dependantReleaseBranch : "");

		try (Commit commit = branchService.openCommit(branchPath)) {
			for (List<String> conceptIds : partition(unbatchedConceptIds, 1_000)) {

				Set<String> verifiedConceptIds = conceptIds.stream().filter(IdentifierService::isConceptId).collect(Collectors.toSet());

				final Set<String> descriptionIds = conceptIds.stream().filter(IdentifierService::isDescriptionId).collect(Collectors.toSet());
				if (!descriptionIds.isEmpty()) {
					logger.info("Looking up concept id for {} descriptions.", descriptionIds.size());
					final BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
					try (final SearchHitsIterator<Description> stream = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
							.withQuery(branchCriteria.getEntityBranchCriteria(Description.class).must(termsQuery(Description.Fields.DESCRIPTION_ID, descriptionIds)))
							.withFields(Description.Fields.DESCRIPTION_ID, Description.Fields.CONCEPT_ID)
							.build(), Description.class)) {
						stream.forEachRemaining(hit -> verifiedConceptIds.add(hit.getContent().getConceptId()));
					}
				}

				final List<Long> conceptIdsLongs = verifiedConceptIds.stream().map(Long::parseLong).collect(Collectors.toList());

				Map<String, Concept> conceptsToFix = conceptService.find(conceptIdsLongs, null, branchPath, LARGE_PAGE).stream()
						.collect(Collectors.toMap(Concept::getConceptId, Function.identity()));

				Map<String, Concept> releasedConcepts = conceptService.find(conceptIdsLongs, null, releaseBranch, LARGE_PAGE).stream()
						.collect(Collectors.toMap(Concept::getConceptId, Function.identity()));

				Map<String, Concept> dependantReleasedConcepts = dependantReleaseBranch != null ?
						conceptService.find(conceptIdsLongs, null, dependantReleaseBranch, LARGE_PAGE).stream()
								.collect(Collectors.toMap(Concept::getConceptId, Function.identity()))
						: new HashMap<>();

				for (String conceptId : verifiedConceptIds) {
					logger.info("Restoring components of {} on branch {}.", conceptId, branchPath);

					Concept conceptToFix = conceptsToFix.get(conceptId);
					Concept releasedConcept = releasedConcepts.get(conceptId);
					Concept dependantReleasedConcept = dependantReleasedConcepts.get(conceptId);

					if (releasedConcept == null && dependantReleasedConcept == null) {
						logger.warn("Concept {} not found on source branch so release status could not be restored.", conceptId);
						continue;
					}
					if (releasedConcept == null) {
						releasedConcept = dependantReleasedConcept;
						dependantReleasedConcept = new Concept();
					}
					if (dependantReleasedConcept == null) {
						dependantReleasedConcept = new Concept();
					}
					if (!conceptToFix.isReleased()) {
						conceptToFix.copyReleaseDetails(releasedConcept);
						conceptToFix.markChanged();
						conceptUpdateHelper.doSaveBatchComponents(Collections.singleton(conceptToFix), Concept.class, commit);
					}
					restoreComponentReleasedStatus(conceptToFix.getDescriptions(),
							resolveEffectiveComponents(releasedConcept.getDescriptions(), dependantReleasedConcept.getDescriptions()),
							commit, setDeletedComponentsToInactive);

					restoreComponentReleasedStatus(getAllRefsetMembers(conceptToFix.getDescriptions()),
							resolveEffectiveComponents(getAllRefsetMembers(releasedConcept.getDescriptions()), getAllRefsetMembers(dependantReleasedConcept.getDescriptions())),
							commit, setDeletedComponentsToInactive);

					restoreComponentReleasedStatus(conceptToFix.getRelationships(),
							resolveEffectiveComponents(releasedConcept.getRelationships(), dependantReleasedConcept.getRelationships()),
							commit, setDeletedComponentsToInactive);

					restoreComponentReleasedStatus(conceptToFix.getAllOwlAxiomMembers(),
							resolveEffectiveComponents(releasedConcept.getAllOwlAxiomMembers(), dependantReleasedConcept.getAllOwlAxiomMembers()),
							commit, setDeletedComponentsToInactive);
				}
			}

			commit.markSuccessful();
		}
	}

	private <T extends SnomedComponent> Set<T> resolveEffectiveComponents(Set<T> releasedComponents, Set<T> dependantComponents) {
		Map<String, T> effectiveComponents = releasedComponents.stream().collect(Collectors.toMap(T::getId, Function.identity()));
		if (dependantComponents != null) {
			for (T dependantComponent : dependantComponents) {
				final T current = effectiveComponents.get(dependantComponent.getId());
				if (current == null || current.getEffectiveTimeI() < dependantComponent.getEffectiveTimeI()) {
					effectiveComponents.put(dependantComponent.getId(), dependantComponent);
				}
			}
		}
		return new HashSet<>(effectiveComponents.values());
	}

	private Set<ReferenceSetMember> getAllRefsetMembers(Set<Description> descriptions) {
		Set<ReferenceSetMember> members = new HashSet<>();
		for (Description description : descriptions) {
			members.addAll(description.getLangRefsetMembers());
			Collection<ReferenceSetMember> inactivationIndicatorMembers = description.getInactivationIndicatorMembers();
			if (inactivationIndicatorMembers != null) {
				members.addAll(inactivationIndicatorMembers);
			}
			Collection<ReferenceSetMember> associationTargetMembers = description.getAssociationTargetMembers();
			if (associationTargetMembers != null) {
				members.addAll(associationTargetMembers);
			}
		}
		return members;
	}

	@SuppressWarnings("unchecked")
	private <T extends SnomedComponent> void restoreComponentReleasedStatus(Set<T> componentsToFix, Set<T> releasedComponents, Commit commit, boolean setDeletedComponentsToInactive) {

		Map<String, T> componentsToFixMap = componentsToFix.stream().collect(Collectors.toMap(SnomedComponent::getId, Function.identity()));
		if (releasedComponents.isEmpty()) {
			return;
		}
		Class<T> componentClass = (Class<T>) releasedComponents.iterator().next().getClass();

		Collection<T> componentsToSave = new HashSet<>();
		for (T releasedComponent : releasedComponents) {
			T componentToFix = componentsToFixMap.get(releasedComponent.getId());
			if (componentToFix == null) {
				// Released but must have been deleted, restore released version as inactive
				componentToFix = releasedComponent;
				if (setDeletedComponentsToInactive) {
					componentToFix.setActive(false);
					componentToFix.updateEffectiveTime();
				}
				componentToFix.markChanged();
				componentsToSave.add(componentToFix);
				System.out.println(format("Restoring deleted component %s %s.", componentClass.getSimpleName(), componentToFix.getId()));
			} else {
				String moduleId = componentToFix.getModuleId();
				if (!Concepts.CORE_MODULE.equals(moduleId) && !Concepts.MODEL_MODULE.equals(moduleId) && !releasedComponent.getModuleId().equals(moduleId)) {
					// Try restoring the previously released moduleId to see if that restores the effectiveTime
					componentToFix.setModuleId(releasedComponent.getModuleId());
					componentToFix.copyReleaseDetails(releasedComponent);
					componentToFix.updateEffectiveTime();
					if (componentToFix.getEffectiveTime() != null) {
						System.out.println(format("Setting previously released module restored the effectiveTime on %s %s.", componentClass.getSimpleName(), componentToFix.getId()));
						componentToFix.markChanged();
						componentsToSave.add(componentToFix);
					} else {
						// There is a change in this cycle, put the moduleId back
						componentToFix.setModuleId(moduleId);
					}
				}
				if (!componentToFix.isReleased()) {
					componentToFix.copyReleaseDetails(releasedComponent);
					componentToFix.updateEffectiveTime();
					componentToFix.markChanged();
					componentsToSave.add(componentToFix);
					System.out.println(format("Restoring missing released status of %s %s.", componentClass.getSimpleName(), componentToFix.getId()));
				}
			}
		}
		if (!componentsToSave.isEmpty()) {
			conceptUpdateHelper.doSaveBatchComponents(componentsToSave, componentClass, commit);
		}
	}

	public void cleanInferredRelationships(String branchPath) {
		logger.info("Cleaning up un-versioned inferred relationships on {}", branchPath);

		Pair<String, Optional<String>> latestReleaseAndDependantRelease = getLatestReleaseAndDependantReleaseBranches(branchPath);

		// Find all un-versioned inactive inferred relationships
		Set<Long> relationshipIds = new LongOpenHashSet();
		final BranchCriteria thisBranchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		try (final SearchHitsIterator<Relationship> relationships = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(thisBranchCriteria.getEntityBranchCriteria(Relationship.class))
						.must(termQuery(Relationship.Fields.ACTIVE, false))
						.must(termQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, Concepts.INFERRED_RELATIONSHIP))
						.mustNot(existsQuery(Relationship.Fields.EFFECTIVE_TIME)))
				.withPageable(LARGE_PAGE).build(), Relationship.class)) {
			relationships.forEachRemaining(hit -> relationshipIds.add(hit.getContent().getRelationshipIdAsLong()));
		}

		for (List<Long> relationshipBatch : partition(relationshipIds, 1_000)) {
			// Find previous version in this code system
			final Map<Long, Relationship> previousVersion = new Long2ObjectOpenHashMap<>();
			try (final SearchHitsIterator<Relationship> relationships = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(versionControlHelper.getBranchCriteria(latestReleaseAndDependantRelease.getFirst()).getEntityBranchCriteria(Relationship.class))
							.must(termQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, Concepts.INFERRED_RELATIONSHIP)))
					.withFilter(termsQuery(Relationship.Fields.RELATIONSHIP_ID, relationshipBatch))
					.withPageable(LARGE_PAGE).build(), Relationship.class)) {
				relationships.forEachRemaining(hit -> {
					final Relationship existingRelationship = hit.getContent();
					previousVersion.put(existingRelationship.getRelationshipIdAsLong(), existingRelationship);
				});
			}
			String dependantReleaseBranch = latestReleaseAndDependantRelease.getSecond().orElse(null);
			if (dependantReleaseBranch != null) {
				try (final SearchHitsIterator<Relationship> relationships = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
						.withQuery(boolQuery()
								.must(versionControlHelper.getBranchCriteria(dependantReleaseBranch).getEntityBranchCriteria(Relationship.class))
								.must(termQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, Concepts.INFERRED_RELATIONSHIP)))
						.withFilter(termsQuery(Relationship.Fields.RELATIONSHIP_ID, relationshipBatch))
						.withPageable(LARGE_PAGE).build(), Relationship.class)) {
					relationships.forEachRemaining(hit -> {
						final Relationship existingRelationship = hit.getContent();
						final Long id = existingRelationship.getRelationshipIdAsLong();
						if (!previousVersion.containsKey(id) || previousVersion.get(id).getEffectiveTimeI() < existingRelationship.getEffectiveTimeI()) {
							// Dependent version is newer
							previousVersion.put(id, existingRelationship);
						}
					});
				}
			}

			// Restore previous version of relationships that were already inactive
			final List<Relationship> toRestore = previousVersion.values().stream().filter(relationship -> !relationship.isActive()).collect(Collectors.toList());
			if (!toRestore.isEmpty()) {
				try (final Commit commit = branchService.openCommit(branchPath)) {
					toRestore.forEach(Relationship::markChanged);
					conceptUpdateHelper.doSaveBatchRelationships(toRestore, commit);
					commit.markSuccessful();
				}
			}

			// Delete relationships which are inactive and have no previous state
			final Set<Long> bornInactive = relationshipBatch.stream().filter(relationshipId -> !previousVersion.containsKey(relationshipId)).collect(Collectors.toSet());
			if (!bornInactive.isEmpty()) {
				relationshipService.deleteRelationships(bornInactive.stream().map(Object::toString).collect(Collectors.toSet()), branchPath, true);
			}
			logger.info("Cleaned up batch of {} of un-versioned inferred relationships on {}: " +
					"{} relationships replaced with the previously released version, " +
					"{} deleted due to born inactive.",	relationshipBatch.size(), branchPath, toRestore.size(), bornInactive.size());
		}
		logger.info("Completed clean up of un-versioned inferred relationships on {}", branchPath);
	}

	private Pair<String, Optional<String>> getLatestReleaseAndDependantReleaseBranches(String branchPath) {
		final CodeSystem closestCodeSystem = codeSystemService.findClosestCodeSystemUsingAnyBranch(branchPath, true);
		if (closestCodeSystem == null) {
			throw new IllegalStateException("No code system found.");
		}
		final CodeSystemVersion latestImportedVersion = codeSystemService.findLatestImportedVersion(closestCodeSystem.getShortName());
		if (latestImportedVersion == null) {
			throw new IllegalStateException("No code system version found.");
		}
		String latestReleaseBranch = latestImportedVersion.getBranchPath();

		String dependantReleaseBranch = null;
		if (closestCodeSystem.getDependantVersionEffectiveTime() != null) {
			final Optional<CodeSystem> parentCodeSystem = codeSystemService.findByBranchPath(PathUtil.getParentPath(closestCodeSystem.getBranchPath()));
			if (parentCodeSystem.isEmpty()) {
				throw new IllegalStateException("Dependant version set but parent code system not found.");
			}
			dependantReleaseBranch = codeSystemService.findVersion(parentCodeSystem.get().getShortName(), closestCodeSystem.getDependantVersionEffectiveTime()).getBranchPath();
		}
		return Pair.of(latestReleaseBranch, Optional.ofNullable(dependantReleaseBranch));
	}
}
