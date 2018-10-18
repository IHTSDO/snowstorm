package org.snomed.snowstorm.core.rf2.rf2import;

import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.factory.HistoryAwareComponentFactory;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.NotFoundException;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

@Service
public class ImportService {

	private Map<String, ImportJob> importJobMap;

	private static final LoadingProfile DEFAULT_LOADING_PROFILE = LoadingProfile.complete.withFullRefsetMemberObjects();

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ReferenceSetMemberService memberService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ExecutorService executorService;

	@Autowired
	private CodeSystemService codeSystemService;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public ImportService() {
		importJobMap = new HashMap<>();
	}

	public String createJob(RF2Type importType, String branchPath) {
		return createJob(new RF2ImportConfiguration(importType, branchPath));
	}

	public String createJob(RF2ImportConfiguration importConfiguration) {
		String id = UUID.randomUUID().toString();
		if (!branchService.exists(importConfiguration.getBranchPath())) {
			throw new IllegalArgumentException("Branch does not exist.");
		}
		importJobMap.put(id, new ImportJob(importConfiguration));
		return id;
	}

	public void importArchive(String importId, InputStream releaseFileStream) throws ReleaseImportException {
		ImportJob job = getJob(importId);
		if (job.getStatus() != ImportJob.ImportStatus.WAITING_FOR_FILE) {
			throw new IllegalStateException("Import Job must be in state " + ImportJob.ImportStatus.WAITING_FOR_FILE);
		}
		RF2Type importType = job.getType();
		String branchPath = job.getBranchPath();
		Integer patchReleaseVersion = job.getPatchReleaseVersion();

		try {
			Date start = new Date();
			logger.info("Starting RF2 {}{} import on branch {}. ID {}", importType, patchReleaseVersion != null ? " RELEASE PATCH on effectiveTime " + patchReleaseVersion : "", branchPath, importId);

			ReleaseImporter releaseImporter = new ReleaseImporter();
			job.setStatus(ImportJob.ImportStatus.RUNNING);
			LoadingProfile loadingProfile = DEFAULT_LOADING_PROFILE
					.withModuleIds(job.getModuleIds().toArray(new String[]{}));

			switch (importType) {
				case DELTA:
					releaseImporter.loadDeltaReleaseFiles(releaseFileStream, loadingProfile, getImportComponentFactory(branchPath, patchReleaseVersion));
					break;
				case SNAPSHOT:

					ImportComponentFactoryImpl importComponentFactory = getImportComponentFactory(branchPath, patchReleaseVersion);
					releaseImporter.loadSnapshotReleaseFiles(releaseFileStream, loadingProfile, importComponentFactory);

					// Create Code System version of this snapshot content (if a code system exists on this path)
					Integer maxEffectiveTime = importComponentFactory.getMaxEffectiveTime();
					if (maxEffectiveTime != null) {
						codeSystemService.createVersionIfCodeSystemFoundOnPath(branchPath, maxEffectiveTime, "RF2 Snapshot Import");
					}

					break;
				case FULL:
					releaseImporter.loadFullReleaseFiles(releaseFileStream, loadingProfile, getFullImportComponentFactory(branchPath));
					break;
			}
			job.setStatus(ImportJob.ImportStatus.COMPLETED);
			long seconds = new Date().getTime() - start.getTime() / 1000;
			logger.info("Completed RF2 {} import on branch {} in {} seconds. ID {}", importType, branchPath, seconds, importId);
		} catch (Exception e) {
			logger.error("Failed RF2 {} import on branch {}. ID {}", importType, branchPath, importId, e);
			job.setStatus(ImportJob.ImportStatus.FAILED);
			throw e;
		}
	}

	private ImportComponentFactoryImpl getImportComponentFactory(String branchPath, Integer patchReleaseVersion) {
		return new ImportComponentFactoryImpl(conceptService, memberService, branchService, branchPath, patchReleaseVersion);
	}

	private HistoryAwareComponentFactory getFullImportComponentFactory(String branchPath) {
		return new FullImportComponentFactoryImpl(conceptService, memberService, branchService, codeSystemService, branchPath, null);
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
