package org.ihtsdo.elasticsnomed.core.data.services.classification;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import org.elasticsearch.common.Strings;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.ihtsdo.elasticsnomed.core.data.domain.classification.Classification;
import org.ihtsdo.elasticsnomed.core.data.domain.classification.EquivalentConcepts;
import org.ihtsdo.elasticsnomed.core.data.domain.classification.RelationshipChange;
import org.ihtsdo.elasticsnomed.core.data.repositories.classification.EquivalentConceptsRepository;
import org.ihtsdo.elasticsnomed.core.data.repositories.classification.RelationshipChangeRepository;
import org.ihtsdo.elasticsnomed.core.data.repositories.ClassificationRepository;
import org.ihtsdo.elasticsnomed.core.data.services.BranchMetadataKeys;
import org.ihtsdo.elasticsnomed.core.data.services.NotFoundException;
import org.ihtsdo.elasticsnomed.core.data.services.ServiceException;
import org.ihtsdo.elasticsnomed.core.data.services.classification.pojo.ClassificationStatusResponse;
import org.ihtsdo.elasticsnomed.core.rf2.RF2Type;
import org.ihtsdo.elasticsnomed.core.rf2.export.ExportException;
import org.ihtsdo.elasticsnomed.core.rf2.export.ExportService;
import org.ihtsdo.elasticsnomed.core.util.DateUtil;
import org.ihtsdo.otf.snomedboot.domain.rf2.RelationshipFieldIndexes;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.GetQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import javax.annotation.PostConstruct;
import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Service
public class ClassificationService {

	@Value("${classification-service.job.abort-after-minutes}")
	private int abortRemoteClassificationAfterMinutes;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private BranchService branchService;

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

	private final List<Classification> classificationsInProgress;

	private Thread classificationStatusPollingThread;

	private static final PageRequest PAGE_FIRST_1K = new PageRequest(0, 1000);

	private Logger logger = LoggerFactory.getLogger(getClass());

	public ClassificationService() {
		classificationsInProgress = new ArrayList<>();
	}

	@PostConstruct
	private void init() {
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(termsQuery(Classification.Fields.STATUS, Classification.Status.SCHEDULED, Classification.Status.RUNNING))
				.withPageable(PAGE_FIRST_1K);

		// Mark running classifications as failed. This could be improved in the future.
		final long[] failedCount = {0};
		try (CloseableIterator<Classification> runningClassifications = elasticsearchOperations.stream(queryBuilder.build(), Classification.class)) {
			runningClassifications.forEachRemaining(classification -> {
				classification.setStatus(Classification.Status.FAILED);
				classification.setErrorMessage("Termserver restarted.");
				classificationRepository.save(classification);
				failedCount[0]++;
			});
		}
		if (failedCount[0] > 0) {
			logger.info("{} currently running classifications marked as failed due to restart.", failedCount[0]);
		}

		// Start thread to continuously fetch the status of remote classifications
		// Copy the in-progress list to avoid long synchronized block
		classificationStatusPollingThread = new Thread(() -> {
			List<Classification> classificationsToCheck = new ArrayList<>();
			try {
				while (true) {
					try {
						// Copy the in-progress list to avoid long synchronized block
						synchronized (classificationsInProgress) {
							classificationsToCheck.addAll(classificationsInProgress);
						}
						Date remoteClassificationCutoffTime = DateUtil.newDatePlus(Calendar.MINUTE, -abortRemoteClassificationAfterMinutes);
						for (Classification classification : classificationsToCheck) {
							ClassificationStatusResponse statusResponse = serviceClient.getStatus(classification.getId());
							Classification.Status latestStatus = statusResponse.getStatus();
							if (latestStatus == Classification.Status.FAILED) {
								classification.setErrorMessage(statusResponse.getErrorMessage());
								logger.warn("Remote classification failed with message:{}, developerMessage:{}",
										statusResponse.getErrorMessage(), statusResponse.getDeveloperMessage());
							}
							else if (classification.getCreationDate().before(remoteClassificationCutoffTime)) {
								latestStatus = Classification.Status.FAILED;
								classification.setErrorMessage("Remote service taking too long.");
							}
							if (classification.getStatus() != latestStatus) {

								if (latestStatus == Classification.Status.COMPLETED) {
									try {
										downloadRemoteResults(classification.getId());
									} catch (IOException e) {
										latestStatus = Classification.Status.FAILED;
										String message = "Failed to capture remote classification results.";
										classification.setErrorMessage(message);
										logger.error(message, e);
									}
								}

								classification.setStatus(latestStatus);
								classificationRepository.save(classification);
							}
							if (latestStatus != Classification.Status.SCHEDULED && latestStatus != Classification.Status.RUNNING) {
								synchronized (classificationsInProgress) {
									classificationsInProgress.remove(classification);
								}
							}
						}
						classificationsToCheck.clear();
						Thread.sleep(500);

					} catch (HttpClientErrorException e) {
						int coolOffSeconds = 30;
						logger.warn("Problem with classification-service communication. Trying again in {} seconds.", coolOffSeconds, e);
						// Let's wait a while before trying again
						Thread.sleep(coolOffSeconds * 1000);
					}
				}
			} catch (InterruptedException e) {
				logger.info("Classification status polling thread interrupted.");
			} finally {
				logger.info("Classification status polling thread stopped.");
			}
		});
		classificationStatusPollingThread.setName("classification-status-polling");
		classificationStatusPollingThread.start();
	}

	public Page<Classification> findClassifications(String path) {
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(termQuery(Classification.Fields.PATH, path))
				.withSort(SortBuilders.fieldSort(Classification.Fields.CREATION_DATE).order(SortOrder.DESC))
				.withPageable(PAGE_FIRST_1K);
		return elasticsearchOperations.queryForPage(queryBuilder.build(), Classification.class);
	}

	public Classification findClassification(String path, String classificationId) {
		GetQuery getQuery = new GetQuery();
		getQuery.setId(classificationId);
		Classification classification = elasticsearchOperations.queryForObject(getQuery, Classification.class);
		if (classification == null || !path.equals(classification.getPath())) {
			throw new NotFoundException("Classification not found on branch.");
		}
		return path.equals(classification.getPath()) ? classification : null;
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
		String previousPackage = metadata != null ? metadata.get(BranchMetadataKeys.CLASSIFICATION_PREVIOUS_PACKAGE) : null;
		if (Strings.isNullOrEmpty(previousPackage)) {
			throw new IllegalStateException("Missing branch metadata for " + BranchMetadataKeys.CLASSIFICATION_PREVIOUS_PACKAGE);
		}

		try {
			File deltaExport = exportService.exportRF2ArchiveFile(path, "", RF2Type.DELTA, true);
			String remoteClassificationId = serviceClient.createClassification(previousPackage, deltaExport, path, reasonerId);
			classification.setId(remoteClassificationId);
			classification.setStatus(Classification.Status.SCHEDULED);
			classificationRepository.save(classification);
			synchronized (classificationsInProgress) {
				classificationsInProgress.add(classification);
			}
		} catch (RestClientException | ExportException e) {
			throw new ServiceException("Failed to create classification.", e);
		}

		return classification;
	}

	private void downloadRemoteResults(String classificationId) throws IOException {
		logger.info("Downloading remote classification results for {}", classificationId);
		try (ZipInputStream rf2ResultsZipStream = new ZipInputStream(serviceClient.downloadRf2Results(classificationId))) {
			ZipEntry zipEntry;
			while ((zipEntry = rf2ResultsZipStream.getNextEntry()) != null) {
				if (zipEntry.getName().contains("sct2_Relationship_Delta")) {
					saveRelationshipChanges(classificationId, rf2ResultsZipStream);
				}
				if (zipEntry.getName().contains("der2_sRefset_EquivalentConceptSimpleMapDelta")) {
					saveEquivalentConcepts(classificationId, rf2ResultsZipStream);
				}
			}
		}
	}

	private void saveRelationshipChanges(String classificationId, InputStream rf2Stream) throws IOException {
		// Leave the stream open after use.
		BufferedReader reader = new BufferedReader(new InputStreamReader(rf2Stream));

		@SuppressWarnings("UnusedAssignment")
		String line = reader.readLine(); // Read and discard header line

		List<RelationshipChange> relationshipChanges = new ArrayList<>();
		while ((line = reader.readLine()) != null) {
			String[] values = line.split("\\t");
			// Header id	effectiveTime	active	moduleId	sourceId	destinationId	relationshipGroup	typeId	characteristicTypeId	modifierId
			relationshipChanges.add(new RelationshipChange(
					classificationId,
					values[RelationshipFieldIndexes.id],
					"1".equals(values[RelationshipFieldIndexes.active]),
					values[RelationshipFieldIndexes.sourceId],
					values[RelationshipFieldIndexes.destinationId],
					values[RelationshipFieldIndexes.relationshipGroup],
					values[RelationshipFieldIndexes.typeId],
					values[RelationshipFieldIndexes.modifierId]));
		}
		if (!relationshipChanges.isEmpty()) {
			logger.info("Saving {} classification relationship changes", relationshipChanges.size());
			relationshipChangeRepository.save(relationshipChanges);
		}
	}

	private void saveEquivalentConcepts(String classificationId, InputStream rf2Stream) throws IOException {
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
			equivalentConceptsRepository.save(equivalentConceptsMap.values());
		}
	}

	public void saveClassificationResultsToBranch(String path, String classificationId) {
		// TODO: implement this
	}

}
