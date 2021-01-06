package org.snomed.snowstorm.core.data.services.classification;

import com.google.common.collect.Lists;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.util.concurrent.UncategorizedExecutionException;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.ihtsdo.otf.snomedboot.domain.rf2.RelationshipFieldIndexes;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.classification.Classification;
import org.snomed.snowstorm.core.data.domain.classification.ClassificationStatus;
import org.snomed.snowstorm.core.data.domain.classification.EquivalentConcepts;
import org.snomed.snowstorm.core.data.domain.classification.RelationshipChange;
import org.snomed.snowstorm.core.data.repositories.ClassificationRepository;
import org.snomed.snowstorm.core.data.repositories.classification.EquivalentConceptsRepository;
import org.snomed.snowstorm.core.data.repositories.classification.RelationshipChangeRepository;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.classification.pojo.ClassificationStatusResponse;
import org.snomed.snowstorm.core.data.services.classification.pojo.EquivalentConceptsResponse;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.export.ExportException;
import org.snomed.snowstorm.core.rf2.export.ExportService;
import org.snomed.snowstorm.core.util.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.*;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.snomed.snowstorm.core.data.domain.classification.ClassificationStatus.*;
import static org.snomed.snowstorm.core.data.services.ConceptService.DISABLE_CONTENT_AUTOMATIONS_METADATA_KEY;

@Service
public class ClassificationService {

	@Value("${classification-service.job.abort-after-minutes}")
	private int abortRemoteClassificationAfterMinutes;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMetadataHelper branchMetadataHelper;

	@Autowired
	private ClassificationRepository classificationRepository;

	@Autowired
	private RelationshipChangeRepository relationshipChangeRepository;

	@Autowired
	private EquivalentConceptsRepository equivalentConceptsRepository;

	@Autowired
	private RemoteClassificationServiceClient serviceClient;

	@Autowired
	private ExportService exportService;

	@Autowired
	private DescriptionService descriptionService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	private final List<Classification> classificationsInProgress;

	private Thread classificationStatusPollingThread;
	private boolean shutdownRequested;

	public static final int RESULT_PROCESSING_THREADS = 2;// Two threads is a good limit here. The processing is very Elasticsearch heavy while looking up inferred-not-stated values.
	private final ExecutorService classificationProcessingExecutor = Executors.newFixedThreadPool(RESULT_PROCESSING_THREADS);

	private static final int SECOND = 1000;

	private static final PageRequest PAGE_FIRST_1K = PageRequest.of(0, 1000);

	private final Logger logger = LoggerFactory.getLogger(getClass());
	private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");

	public ClassificationService() {
		classificationsInProgress = new ArrayList<>();
	}

	@PostConstruct
	private void init() throws ServiceException {

		try {
			if (!elasticsearchOperations.indexOps(Concept.class).exists()) {
				throw new StartupException("Elasticsearch Concept index does not exist.");
			}
		} catch (UncategorizedExecutionException e) {
			throw new StartupException("Not able to connect to Elasticsearch. " +
					"Check that Elasticsearch is running and that you have the right version installed.", e);
		}

		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(termsQuery(Classification.Fields.STATUS, ClassificationStatus.SCHEDULED.name(), ClassificationStatus.RUNNING.name()))
				.withPageable(PAGE_FIRST_1K);

		// Mark running classifications as failed. This could be improved in the future.
		final long[] failedCount = {0};
		try (SearchHitsIterator<Classification> runningClassifications = elasticsearchOperations.searchForStream(queryBuilder.build(), Classification.class)) {
			runningClassifications.forEachRemaining(hit -> {
				Classification classification = hit.getContent();
				classification.setStatus(ClassificationStatus.FAILED);
				classification.setErrorMessage("Termserver restarted.");
				classificationRepository.save(classification);
				failedCount[0]++;
			});
		}
		if (failedCount[0] > 0) {
			logger.info("{} currently running classifications marked as failed due to restart.", failedCount[0]);
		}

		// Start thread to continuously fetch the status of remote classifications
		classificationStatusPollingThread = new Thread(() -> {
			try {
				while (!shutdownRequested) {
					try {
						// Copy the in-progress list to avoid long synchronized block
						List<Classification> classificationsToCheck;
						synchronized (classificationsInProgress) {
							classificationsToCheck = new ArrayList<>(classificationsInProgress);
						}
						Date remoteClassificationCutoffTime = DateUtil.newDatePlus(Calendar.MINUTE, -abortRemoteClassificationAfterMinutes);
						for (Classification classification : classificationsToCheck) {
							ClassificationStatusResponse statusResponse = serviceClient.getStatus(classification.getId());
							ClassificationStatus newStatus = statusResponse.getStatus();
							boolean timeout = false;

							if (classification.getStatus() == newStatus) {
								// No status change
								// Check for timeout
								if (classification.getCreationDate().before(remoteClassificationCutoffTime)) {
									timeout = true;
								} else {
									// Nothing to do
									continue;
								}
							}

							if (timeout || classification.getStatus() != newStatus) {
								// Status change to process

								// Stop polling if no longer needed
								if (newStatus != ClassificationStatus.SCHEDULED && newStatus != ClassificationStatus.RUNNING) {
									synchronized (classificationsInProgress) {
										classificationsInProgress.remove(classification);
									}
								}

								if (newStatus == ClassificationStatus.FAILED) {
									classification.setErrorMessage(statusResponse.getErrorMessage());
									logger.warn("Remote classification failed with message:{}, developerMessage:{}",
											statusResponse.getErrorMessage(), statusResponse.getDeveloperMessage());
								} else if (timeout) {
									newStatus = ClassificationStatus.FAILED;
									classification.setErrorMessage("Remote service taking too long.");
								}

								final ClassificationStatus newStatusFinal = newStatus;
								classificationProcessingExecutor.submit(() -> {

									classification.setStatus(newStatusFinal);

									if (newStatusFinal == COMPLETED) {
										try {
											logger.info("Classification {} remote step complete after {} seconds. Processing results...", classification.getId(), getSecondsSince(classification.getCreationDate()));

											downloadRemoteResults(classification);

											Boolean inferredRelationshipChangesFound = doGetRelationshipChanges(classification.getPath(), classification.getId(),
													Config.DEFAULT_LANGUAGE_DIALECTS, PageRequest.of(0, 1), false, null).getTotalElements() > 0;

											Boolean equivalentConceptsFound = doGetEquivalentConcepts(classification.getPath(), classification.getId(),
													Config.DEFAULT_LANGUAGE_DIALECTS, PageRequest.of(0, 1)).getTotalElements() > 0;

											classification.setInferredRelationshipChangesFound(inferredRelationshipChangesFound);
											classification.setEquivalentConceptsFound(equivalentConceptsFound);
											classification.setCompletionDate(new Date());

										} catch (IOException | ElasticsearchException e) {
											classification.setStatus(ClassificationStatus.FAILED);
											String message = "Failed to capture remote classification results.";
											classification.setErrorMessage(message);
											logger.error(message, e);
										}
									}

									classificationRepository.save(classification);
									logger.info("Classification {} {} after {} seconds.", classification.getId(), classification.getStatus(), getSecondsSince(classification.getCreationDate()));
								});
							}
							if (shutdownRequested) {
								break;
							}
						}
						classificationsToCheck.clear();
						Thread.sleep(SECOND);

					} catch (RestClientException e) {
						int coolOffSeconds = 30;
						logger.warn("Problem with classification-service communication. Trying again in {} seconds.", coolOffSeconds, e);
						// Let's wait a while before trying again
						Thread.sleep(coolOffSeconds * SECOND);
					}
				}
			} catch (InterruptedException e) {
				logger.info("Classification status polling thread interrupted.");
			} catch (Exception e) {
				logger.error("Unexpected exception in classification status polling thread.", e);
			} finally {
				logger.info("Classification status polling thread stopped.");
			}
		});
		classificationStatusPollingThread.setName("classification-status-polling");
		classificationStatusPollingThread.start();
	}

	private long getSecondsSince(Date saveDate) {
		return (new Date().getTime() - saveDate.getTime()) / 1_000;
	}

	@PreDestroy
	public void shutdownPolling() {
		shutdownRequested = true;
		classificationProcessingExecutor.shutdown();
	}

	public Page<Classification> findClassifications(String path) {
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(termQuery(Classification.Fields.PATH, path))
				.withSort(SortBuilders.fieldSort(Classification.Fields.CREATION_DATE).order(SortOrder.ASC))
				.withPageable(PAGE_FIRST_1K);
		SearchHits<Classification> searchHits = elasticsearchOperations.search(queryBuilder.build(), Classification.class);
		List<Classification>classifications = searchHits.stream().map(SearchHit::getContent).collect(Collectors.toList());
		updateStatusIfStale(classifications, path);
		return new PageImpl<>(classifications, PAGE_FIRST_1K, searchHits.getTotalHits());
	}

	public Classification findClassification(String path, String classificationId) {
		Classification classification = elasticsearchOperations.get(classificationId, Classification.class);
		if (classification == null || !path.equals(classification.getPath())) {
			throw new NotFoundException("Classification not found on branch.");
		}
		updateStatusIfStale(Collections.singleton(classification), path);
		return classification;
	}

	// Set status to stale if the branch has moved on
	private void updateStatusIfStale(Iterable<Classification> classifications, String path) {
		if (classifications != null) {
			Branch branch = branchService.findBranchOrThrow(path);
			classifications.forEach(classification -> {
				if (classification.getStatus() == COMPLETED && !branch.getHead().equals(classification.getLastCommitDate())) {
					classification.setStatus(STALE);
				}
			});
		}
	}

	public Classification createClassification(String path, String reasonerId) throws ServiceException {
		Branch branch = branchService.findBranchOrThrow(path);

		Classification classification = new Classification();
		classification.setPath(path);
		classification.setReasonerId(reasonerId);
		classification.setUserId(SecurityUtil.getUsername());
		classification.setCreationDate(new Date());
		classification.setLastCommitDate(branch.getHead());

		Branch branchWithInheritedMetadata = branchService.findBranchOrThrow(path, true);
		Map<String, String> metadata = branchWithInheritedMetadata.getMetadata();
		String previousPackage = metadata != null ? metadata.get(BranchMetadataKeys.PREVIOUS_PACKAGE) : null;
		String dependencyPackage = metadata != null ? metadata.get(BranchMetadataKeys.DEPENDENCY_PACKAGE) : null;
		if (Strings.isNullOrEmpty(previousPackage) && Strings.isNullOrEmpty(dependencyPackage)) {
			throw new IllegalStateException("Missing branch metadata for " + BranchMetadataKeys.PREVIOUS_PACKAGE + " or " + BranchMetadataKeys.DEPENDENCY_PACKAGE);
		}

		try {
			File deltaExport = exportService.exportRF2ArchiveFile(path, SIMPLE_DATE_FORMAT.format(new Date()), RF2Type.DELTA, true);
			String remoteClassificationId = serviceClient.createClassification(previousPackage, dependencyPackage, deltaExport, path, reasonerId);
			classification.setId(remoteClassificationId);
			classification.setStatus(ClassificationStatus.SCHEDULED);
			classificationRepository.save(classification);
			synchronized (classificationsInProgress) {
				classificationsInProgress.add(classification);
			}
		} catch (RestClientException | ExportException e) {
			throw new ServiceException("Failed to create classification.", e);
		}

		return classification;
	}

	@Async
	public void saveClassificationResultsToBranch(String path, String classificationId, SecurityContext securityContext) {
		try {
			SecurityContextHolder.setContext(securityContext);
			Classification classification = classificationSaveStatusCheck(path, classificationId);

			if (classification.getInferredRelationshipChangesFound()) {
				classification.setStatus(SAVING_IN_PROGRESS);
				classificationRepository.save(classification);

				try {
					// Commit in auto-close try block like this will roll back if an exception is thrown
					try (Commit commit = branchService.openCommit(path, branchMetadataHelper.getBranchLockMetadata("Saving classification " + classification.getId()))) {
						commit.getBranch().getMetadata().put(DISABLE_CONTENT_AUTOMATIONS_METADATA_KEY, "true");

						NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
								.withQuery(termQuery("classificationId", classificationId))
								.withSort(new FieldSortBuilder(RelationshipChange.Fields.SOURCE_ID))
								.withSort(new FieldSortBuilder(RelationshipChange.Fields.GROUP))
								.withSort(new FieldSortBuilder("_id"))// This gives a guaranteed sort order for a reliable stateless stream
								.withPageable(LARGE_PAGE);
						try (SearchHitsIterator<RelationshipChange> relationshipChangeStream = elasticsearchOperations.searchForStream(queryBuilder.build(), RelationshipChange.class)) {
							while (relationshipChangeStream.hasNext()) {
								List<RelationshipChange> changesBatch = new ArrayList<>();
								int i = 0;
								while (i++ < 10_000 && relationshipChangeStream.hasNext()) {
									changesBatch.add(relationshipChangeStream.next().getContent());
								}

								// Group changes by concept
								Map<Long, List<RelationshipChange>> conceptToChangeMap = new Long2ObjectOpenHashMap<>();
								for (RelationshipChange relationshipChange : changesBatch) {
									conceptToChangeMap.computeIfAbsent(parseLong(relationshipChange.getSourceId()), conceptId -> new ArrayList<>()).add(relationshipChange);
								}

								// Load concepts
								Collection<Concept> concepts = conceptService.find(path, conceptToChangeMap.keySet(), Config.DEFAULT_LANGUAGE_DIALECTS);

								// Apply changes to concepts
								for (Concept concept : concepts) {
									List<RelationshipChange> relationshipChanges = conceptToChangeMap.get(concept.getConceptIdAsLong());
									applyRelationshipChangesToConcept(concept, relationshipChanges, false);
								}

								// Update concepts
								conceptService.updateWithinCommit(concepts, commit);
							}
						}

						commit.getBranch().getMetadata().remove(DISABLE_CONTENT_AUTOMATIONS_METADATA_KEY);
						commit.markSuccessful();
						classification.setStatus(SAVED);
						classification.setSaveDate(new Date());
					}
				} catch (ServiceException e) {
					classification.setStatus(SAVE_FAILED);
					logger.error("Classification save failed {} {}", classification.getPath(), classificationId, e);
				}
			} else {
				classification.setStatus(SAVED);
			}

			classificationRepository.save(classification);
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	public Classification classificationSaveStatusCheck(String path, String classificationId) {

		// Check completed
		Classification classification = findClassification(path, classificationId);
		if (classification.getStatus() != COMPLETED) {
			throw new IllegalStateException("Classification status must be " + COMPLETED.toString() + " in order to save results.");
		}

		// Check not stale
		updateStatusIfStale(Collections.singleton(classification), path);
		if (classification.getStatus() == STALE) {
			throw new IllegalStateException("Classification is stale.");
		}

		return classification;
	}

	private void applyRelationshipChangesToConcept(Concept concept, List<RelationshipChange> relationshipChanges, boolean copyDescriptions) throws ServiceException {
		for (RelationshipChange relationshipChange : relationshipChanges) {
			Relationship relationship = null;
			switch (relationshipChange.getChangeNature()) {
				case INFERRED:
					if (Strings.isNullOrEmpty(relationshipChange.getRelationshipId())) {
						// Newly inferred relationship
						relationship = new Relationship(
								null,
								null,
								true,
								concept.getModuleId(),
								null,
								relationshipChange.getDestinationId(),
								relationshipChange.getGroup(),
								relationshipChange.getTypeId(),
								relationshipChange.getCharacteristicTypeId(),
								relationshipChange.getModifierId());

						concept.addRelationship(relationship);
					} else {
						// Existing relationship change - could be a reactivation or group change
						relationship = concept.getRelationship(relationshipChange.getRelationshipId());
						if (relationship == null) {
							throw new ServiceException(String.format("Relationship %s not found within Concept %s so can not apply update.", relationshipChange.getRelationshipId(), concept.getConceptId()));
						}
						relationship.setActive(true);
						relationship.setGroupId(relationshipChange.getGroup());
					}
					break;
				case REDUNDANT:
					int before = concept.getRelationships().size();
					if (!concept.getRelationships().remove(new Relationship(relationshipChange.getRelationshipId())) || concept.getRelationships().size() == before) {
						throw new ServiceException(String.format("Failed to remove relationship %s from concept %s.", relationshipChange.getRelationshipId(), concept.getConceptId()));
					}
					break;
			}
			if (copyDescriptions && relationship != null) {
				relationship.setSource(relationshipChange.getSource());
				relationship.setType(relationshipChange.getType());
				relationship.setTarget(relationshipChange.getDestination());
			}
		}
	}

	public Page<RelationshipChange> getRelationshipChanges(String path, String classificationId, List<LanguageDialect> languageDialects, PageRequest pageRequest) {
		checkClassificationHasResults(path, classificationId);
		return doGetRelationshipChanges(path, classificationId, languageDialects, pageRequest, true, null);
	}

	private Page<RelationshipChange> doGetRelationshipChanges(String path, String classificationId, List<LanguageDialect> languageDialects, PageRequest pageRequest, boolean fetchDescriptions, String sourceIdFilter) {

		Page<RelationshipChange> relationshipChanges =
				sourceIdFilter != null ?
						relationshipChangeRepository.findByClassificationIdAndSourceId(classificationId, sourceIdFilter, pageRequest)
						: relationshipChangeRepository.findByClassificationId(classificationId, pageRequest);

		Map<String, ConceptMini> conceptMiniMap = new HashMap<>();
		for (RelationshipChange relationshipChange : relationshipChanges) {
			if (fetchDescriptions) {
				relationshipChange.setSource(conceptMiniMap.computeIfAbsent(relationshipChange.getSourceId(), conceptId -> new ConceptMini(conceptId, languageDialects)));
				relationshipChange.setDestination(conceptMiniMap.computeIfAbsent(relationshipChange.getDestinationId(), conceptId -> new ConceptMini(conceptId, languageDialects)));
				relationshipChange.setType(conceptMiniMap.computeIfAbsent(relationshipChange.getTypeId(), conceptId -> new ConceptMini(conceptId, languageDialects)));
			}
		}
		if (fetchDescriptions) {
			descriptionService.joinActiveDescriptions(path, conceptMiniMap);
		}

		return relationshipChanges;
	}

	public Page<EquivalentConceptsResponse> getEquivalentConcepts(String path, String classificationId, List<LanguageDialect> languageDialects, PageRequest pageRequest) {
		checkClassificationHasResults(path, classificationId);
		return doGetEquivalentConcepts(path, classificationId, languageDialects, pageRequest);
	}

	public Concept getConceptPreview(String path, String classificationId, String conceptId, List<LanguageDialect> languageDialects) throws ServiceException {
		checkClassificationHasResults(path, classificationId);

		Concept concept = conceptService.find(conceptId, languageDialects, path);
		Page<RelationshipChange> conceptRelationshipChanges = doGetRelationshipChanges(path, classificationId, languageDialects, LARGE_PAGE, true, conceptId);
		applyRelationshipChangesToConcept(concept, conceptRelationshipChanges.getContent(), true);

		return concept;
	}

	private Page<EquivalentConceptsResponse> doGetEquivalentConcepts(String path, String classificationId, List<LanguageDialect> languageDialects, PageRequest pageRequest) {
		Page<EquivalentConcepts> relationshipChanges = equivalentConceptsRepository.findByClassificationId(classificationId, pageRequest);
		if (relationshipChanges.getTotalElements() == 0) {
			return new PageImpl<>(Collections.emptyList());
		}

		Set<String> conceptIds = relationshipChanges.getContent().stream().map(EquivalentConcepts::getConceptIds).flatMap(Collection::stream).collect(Collectors.toSet());
		Map<String, ConceptMini> conceptMiniMap = conceptService.findConceptMinis(path, conceptIds, languageDialects).getResultsMap();
		List<EquivalentConceptsResponse> responseContent = new ArrayList<>();
		for (EquivalentConcepts equivalentConcepts : relationshipChanges.getContent()) {
			HashSet<ConceptMini> concepts = new HashSet<>();
			responseContent.add(new EquivalentConceptsResponse(concepts));
			for (String conceptId : equivalentConcepts.getConceptIds()) {
				concepts.add(conceptMiniMap.get(conceptId));
			}
		}

		return new PageImpl<>(responseContent, pageRequest, relationshipChanges.getTotalElements());
	}

	private void checkClassificationHasResults(String path, String classificationId) {
		Classification classification = findClassification(path, classificationId);
		if (!classification.getStatus().isResultsAvailable()) {
			throw new IllegalStateException("This classification has no results yet.");
		}
	}

	private void downloadRemoteResults(Classification classification) throws IOException, ElasticsearchException {
		logger.info("Downloading remote classification results for {}", classification.getId());
		try (ZipInputStream rf2ResultsZipStream = new ZipInputStream(serviceClient.downloadRf2Results(classification.getId()))) {
			ZipEntry zipEntry;
			while ((zipEntry = rf2ResultsZipStream.getNextEntry()) != null) {
				if (zipEntry.getName().contains("sct2_Relationship_Delta")) {
					saveRelationshipChanges(classification, rf2ResultsZipStream);
				}
				if (zipEntry.getName().contains("der2_sRefset_EquivalentConceptSimpleMapDelta")) {
					saveEquivalentConcepts(classification.getId(), rf2ResultsZipStream);
				}
			}
		}
	}

	void saveRelationshipChanges(Classification classification, InputStream rf2Stream) throws IOException, ElasticsearchException {
		// Leave the stream open after use.
		BufferedReader reader = new BufferedReader(new InputStreamReader(rf2Stream));

		reader.readLine(); // Read and discard header line

		List<RelationshipChange> relationshipChanges = new ArrayList<>();
		String line;
		long activeRows = 0;
		boolean active;
		while ((line = reader.readLine()) != null) {
			String[] values = line.split("\\t");
			// Header id	effectiveTime	active	moduleId	sourceId	destinationId	relationshipGroup	typeId	characteristicTypeId	modifierId
			active = "1".equals(values[RelationshipFieldIndexes.active]);
			relationshipChanges.add(new RelationshipChange(
					classification.getId(),
					values[RelationshipFieldIndexes.id],
					active,
					values[RelationshipFieldIndexes.sourceId],
					values[RelationshipFieldIndexes.destinationId],
					Integer.parseInt(values[RelationshipFieldIndexes.relationshipGroup]),
					values[RelationshipFieldIndexes.typeId],
					values[RelationshipFieldIndexes.modifierId],
					false));
			if (active) {
				activeRows++;
			}
		}

		// - Mark inferred not previously stated changes -
		// Build query to find concepts in the stated semantic index which do not contain the inferred parents or attributes
		NumberFormat numberFormat = NumberFormat.getIntegerInstance();
		if (activeRows > 0) {
			logger.info("Looking up 'inferred not previously stated' values for {} active inferred relationship changes for classification {}.",
					numberFormat.format(activeRows), classification.getId());
		}
		long rowsProcessed = 0;
		Map<Long, List<RelationshipChange>> activeConceptChanges = new HashMap<>();

		// The max clauses for bool query in elastic search by default is 1024
		for (List<RelationshipChange> relationshipChangePartition : Lists.partition(relationshipChanges, 1000)) {
			BoolQueryBuilder allConceptsQuery = boolQuery();
			for (RelationshipChange relationshipChange : relationshipChangePartition) {
				if (relationshipChange.isActive()) {
					Long sourceId = parseLong(relationshipChange.getSourceId());
					BoolQueryBuilder conceptQuery = boolQuery()
							.must(termQuery(QueryConcept.Fields.CONCEPT_ID, sourceId));
					if (relationshipChange.getTypeId().equals(Concepts.ISA)) {
						conceptQuery.mustNot(termQuery(QueryConcept.Fields.PARENTS, relationshipChange.getDestinationId()));
					} else {
						conceptQuery.mustNot(termQuery(QueryConcept.Fields.ATTR + "." + relationshipChange.getTypeId(), relationshipChange.getDestinationId()));
					}
					allConceptsQuery.should(conceptQuery);
					activeConceptChanges.computeIfAbsent(sourceId, id -> new ArrayList<>()).add(relationshipChange);
					rowsProcessed++;
					if (rowsProcessed % 1_000 == 0) {
						logger.info("Processing row {} of {} for classification {}", numberFormat.format(rowsProcessed), numberFormat.format(activeRows), classification.getId());
					}
				}
			}

			// make sure the should clause is not empty before running semantic index search
			if (allConceptsQuery.should().isEmpty()) {
				continue;
			}

			BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(classification.getPath());
			try (SearchHitsIterator<QueryConcept> semanticIndexConcepts = elasticsearchOperations.searchForStream(
					new NativeSearchQueryBuilder()
							.withQuery(boolQuery()
									.must(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
									.must(termQuery(QueryConcept.Fields.STATED, true)))
							.withFilter(allConceptsQuery)
							.withPageable(LARGE_PAGE).build(),
					QueryConcept.class)) {

				semanticIndexConcepts.forEachRemaining(hit -> {
					// One or more inferred attributes or parents do not exist on this stated semanticIndexConcept
					List<RelationshipChange> conceptChanges = activeConceptChanges.get(hit.getContent().getConceptIdL());
					if (conceptChanges != null) {
						Map<String, Set<Object>> conceptAttributes = hit.getContent().getAttr();
						for (RelationshipChange relationshipChange : conceptChanges) {
							if (relationshipChange.getTypeId().equals(Concepts.ISA)) {
								if (!hit.getContent().getParents().contains(parseLong(relationshipChange.getDestinationId()))) {
									relationshipChange.setInferredNotStated(true);
								}
							} else {
								if (!conceptAttributes.getOrDefault(relationshipChange.getTypeId(), Collections.emptySet()).contains(relationshipChange.getDestinationId())) {
									relationshipChange.setInferredNotStated(true);
								}
							}
						}
					}
				});
			}
		}

		if (!relationshipChanges.isEmpty()) {
			logger.info("Saving {} classification relationship changes total.", numberFormat.format(relationshipChanges.size()));
			int chunkSize = 10_000;
			List<List<RelationshipChange>> partition = Lists.partition(relationshipChanges, chunkSize);
			for (List<RelationshipChange> changes : partition) {
				if (relationshipChanges.size() > chunkSize) {
					logger.info("Saving batch of {} classification relationship changes.", numberFormat.format(changes.size()));
				}
				relationshipChangeRepository.saveAll(changes);
			}
		}
	}

	private void saveEquivalentConcepts(String classificationId, InputStream rf2Stream) throws IOException, ElasticsearchException {
		// Leave the stream open after use.
		BufferedReader reader = new BufferedReader(new InputStreamReader(rf2Stream));

		@SuppressWarnings("UnusedAssignment")
		String line = reader.readLine(); // Read and discard header line

		Map<String, EquivalentConcepts> equivalentConceptsMap = new HashMap<>();
		while ((line = reader.readLine()) != null) {
			String[] values = line.split("\\t");
			// 0	1				2		3			4			5						6
			// id	effectiveTime	active	moduleId	refsetId	referencedComponentId	mapTarget
			String setId = values[6];
			String conceptIdInSet = values[5];
			EquivalentConcepts equivalentConcepts = equivalentConceptsMap.computeIfAbsent(setId, s -> new EquivalentConcepts(classificationId));
			equivalentConcepts.addConceptId(conceptIdInSet);
		}
		if (!equivalentConceptsMap.isEmpty()) {
			logger.info("Saving {} classification equivalent concept sets", equivalentConceptsMap.size());
			List<List<EquivalentConcepts>> partition = Lists.partition(new ArrayList<>(equivalentConceptsMap.values()), 10_000);
			for (List<EquivalentConcepts> equivalentConcepts : partition) {
				equivalentConceptsRepository.saveAll(equivalentConcepts);
			}
		}
	}

	public void deleteAll() {
		classificationRepository.deleteAll();
		relationshipChangeRepository.deleteAll();
		equivalentConceptsRepository.deleteAll();
	}
}
