package org.snomed.snowstorm.core.rf2.rf2import;

import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.factory.HistoryAwareComponentFactory;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ExecutorService;

import static org.snomed.snowstorm.core.rf2.RF2Type.FULL;

@Service
public class ImportService {

	private Map<String, ImportJob> importJobMap;

	private static final LoadingProfile DEFAULT_LOADING_PROFILE = LoadingProfile.complete.withFullRefsetMemberObjects();

	@Autowired
	private ConceptUpdateHelper conceptUpdateHelper;

	@Autowired
	private ReferenceSetMemberService memberService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMetadataHelper branchMetadataHelper;

	@Autowired
	private ExecutorService executorService;

	@Autowired
	private CodeSystemService codeSystemService;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public ImportService() {
		importJobMap = new HashMap<>();
	}

	public String createJob(RF2Type importType, String branchPath, boolean createCodeSystemVersion, boolean clearEffectiveTimes) {
		return createJob(new RF2ImportConfiguration(importType, branchPath)
				.setCreateCodeSystemVersion(createCodeSystemVersion)
				.setClearEffectiveTimes(clearEffectiveTimes));
	}

	public String createJob(RF2ImportConfiguration importConfiguration) {
		String id = UUID.randomUUID().toString();

		// Validate branch
		String branchPath = importConfiguration.getBranchPath();
		if (!branchService.exists(branchPath)) {
			throw new IllegalArgumentException(String.format("Branch %s does not exist.", branchPath));
		}

		if (importConfiguration.isCreateCodeSystemVersion()) {
			// Check there is a code system on this branch
			Optional<CodeSystem> optionalCodeSystem = codeSystemService.findAll().stream().filter(codeSystem -> codeSystem.getBranchPath().equals(branchPath)).findAny();
			if (!optionalCodeSystem.isPresent()) {
				throw new IllegalArgumentException(String.format("The %s option has been used but there is no codesystem on branchPath %s.", "createCodeSystemVersion", branchPath));
			}
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

			Integer maxEffectiveTime = null;
			switch (importType) {
				case DELTA: {
					// If we are not creating a new version copy the release fields from the existing components
					boolean copyReleaseFields = !job.isCreateCodeSystemVersion();
					ImportComponentFactoryImpl importComponentFactory = getImportComponentFactory(branchPath, patchReleaseVersion, copyReleaseFields, job.isClearEffectiveTimes());
					releaseImporter.loadDeltaReleaseFiles(releaseFileStream, loadingProfile, importComponentFactory);
					maxEffectiveTime = importComponentFactory.getMaxEffectiveTime();
					break;
				}
				case SNAPSHOT: {
					ImportComponentFactoryImpl importComponentFactory = getImportComponentFactory(branchPath, patchReleaseVersion, false, false);
					releaseImporter.loadSnapshotReleaseFiles(releaseFileStream, loadingProfile, importComponentFactory);
					maxEffectiveTime = importComponentFactory.getMaxEffectiveTime();
					break;
				}
				case FULL: {
					releaseImporter.loadFullReleaseFiles(releaseFileStream, loadingProfile, getFullImportComponentFactory(branchPath));
					break;
				}
			}

			if (job.isCreateCodeSystemVersion() && importType != FULL) {
				// Create Code System version if a code system exists on this path
				if (maxEffectiveTime != null) {
					codeSystemService.createVersionIfCodeSystemFoundOnPath(branchPath, maxEffectiveTime);
				}
			}

			job.setStatus(ImportJob.ImportStatus.COMPLETED);
			long seconds = (new Date().getTime() - start.getTime()) / 1_000;
			logger.info("Completed RF2 {} import on branch {} in {} seconds. ID {}", importType, branchPath, seconds, importId);
		} catch (Exception e) {
			logger.error("Failed RF2 {} import on branch {}. ID {}", importType, branchPath, importId, e);
			job.setStatus(ImportJob.ImportStatus.FAILED);
			throw e;
		}
	}

	private ImportComponentFactoryImpl getImportComponentFactory(String branchPath, Integer patchReleaseVersion, boolean copyReleaseFields, boolean clearEffectiveTimes) {
		return new ImportComponentFactoryImpl(conceptUpdateHelper, memberService, branchService, branchMetadataHelper, branchPath, patchReleaseVersion, copyReleaseFields, clearEffectiveTimes);
	}

	private HistoryAwareComponentFactory getFullImportComponentFactory(String branchPath) {
		return new FullImportComponentFactoryImpl(conceptUpdateHelper, memberService, branchService, branchMetadataHelper, codeSystemService, branchPath, null);
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
