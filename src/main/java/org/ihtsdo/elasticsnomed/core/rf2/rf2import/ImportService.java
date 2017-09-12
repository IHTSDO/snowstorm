package org.ihtsdo.elasticsnomed.core.rf2.rf2import;

import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.elasticsnomed.core.data.domain.Concepts;
import org.ihtsdo.elasticsnomed.core.data.services.ConceptService;
import org.ihtsdo.elasticsnomed.core.data.services.NotFoundException;
import org.ihtsdo.elasticsnomed.core.rf2.RF2Type;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.domain.ConceptConstants;
import org.ihtsdo.otf.snomedboot.factory.HistoryAwareComponentFactory;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class ImportService {

	private Map<String, ImportJob> importJobMap;

	private static final LoadingProfile DEFAULT_LOADING_PROFILE = LoadingProfile.complete.withoutAllRefsets()
			.withFullRefsetMemberObjects()
			.withRefset(ConceptConstants.US_EN_LANGUAGE_REFERENCE_SET)
			.withRefset(ConceptConstants.GB_EN_LANGUAGE_REFERENCE_SET)
			.withRefset(Concepts.CONCEPT_INACTIVATION_INDICATOR_REFERENCE_SET)
			.withRefset(Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET)
			.withRefsets(Concepts.historicalAssociationNames.keySet().toArray(new String[Concepts.historicalAssociationNames.size()]))
			.withRefsets(Concepts.MRCM_INTERNATIONAL_REFSETS.toArray(new String[Concepts.MRCM_INTERNATIONAL_REFSETS.size()]));

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ExecutorService executorService;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public ImportService() {
		importJobMap = new HashMap<>();
	}

	public String createJob(RF2Type type, String branchPath) {
		String id = UUID.randomUUID().toString();
		importJobMap.put(id, new ImportJob(type, branchPath));
		return id;
	}

	public void importArchive(String importId, InputStream releaseFileStream) throws ReleaseImportException {
		ImportJob job = getJob(importId);
		if (job.getStatus() != ImportJob.ImportStatus.WAITING_FOR_FILE) {
			throw new IllegalStateException("Import Job must be in state " + ImportJob.ImportStatus.WAITING_FOR_FILE);
		}
		RF2Type importType = job.getType();
		String branchPath = job.getBranchPath();

		try {
			logger.info("Starting RF2 {} import on branch {}. ID {}", importType, branchPath, importId);

			ReleaseImporter releaseImporter = new ReleaseImporter();
			job.setStatus(ImportJob.ImportStatus.RUNNING);
			switch (importType) {
				case DELTA:
					releaseImporter.loadDeltaReleaseFiles(releaseFileStream, DEFAULT_LOADING_PROFILE, getImportComponentFactory(branchPath));
					break;
				case SNAPSHOT:
					releaseImporter.loadSnapshotReleaseFiles(releaseFileStream, DEFAULT_LOADING_PROFILE, getImportComponentFactory(branchPath));
					break;
				case FULL:
					releaseImporter.loadFullReleaseFiles(releaseFileStream, DEFAULT_LOADING_PROFILE, getFullImportComponentFactory(branchPath));
					break;
			}
			job.setStatus(ImportJob.ImportStatus.COMPLETED);
			logger.info("Completed RF2 {} import on branch {}. ID {}", importType, branchPath, importId);
		} catch (ReleaseImportException e) {
			logger.error("Failed RF2 {} import on branch {}. ID {}", importType, branchPath, importId, e);
			job.setStatus(ImportJob.ImportStatus.FAILED);
			throw e;
		}
	}

	private ImportComponentFactoryImpl getImportComponentFactory(String branchPath) {
		return new ImportComponentFactoryImpl(conceptService, branchService, branchPath);
	}

	private HistoryAwareComponentFactory getFullImportComponentFactory(String branchPath) {
		return new FullImportComponentFactoryImpl(conceptService, branchService, branchPath, null);
	}

	public void importArchiveAsync(String importId, InputStream releaseFileStream) {
		executorService.submit(() -> {
			try {
				importArchive(importId, releaseFileStream);
			} catch (ReleaseImportException e) {
				// Swallow exception - already logged and this is an async method
			}
		});
	}

	public ImportJob getImportJobOrThrow(@PathVariable String importId) {
		ImportJob importJob = getJob(importId);
		if (importJob == null) {
			throw new NotFoundException("Import job not found.");
		}
		return importJob;
	}

	private ImportJob getJob(String importId) {
		return importJobMap.get(importId);
	}
}
