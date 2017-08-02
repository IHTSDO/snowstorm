package org.ihtsdo.elasticsnomed.core.rf2.rf2import;

import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.elasticsnomed.core.data.domain.Concepts;
import org.ihtsdo.elasticsnomed.core.data.services.ConceptService;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.domain.ConceptConstants;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ImportService {

	public static final LoadingProfile DEFAULT_LOADING_PROFILE = LoadingProfile.complete.withoutAllRefsets()
			.withFullRefsetMemberObjects()
			.withRefset(ConceptConstants.US_EN_LANGUAGE_REFERENCE_SET)
			.withRefset(ConceptConstants.GB_EN_LANGUAGE_REFERENCE_SET)
			.withRefset(Concepts.CONCEPT_INACTIVATION_INDICATOR_REFERENCE_SET)
			.withRefset(Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET)
			.withRefsets(Concepts.historicalAssociationNames.keySet().toArray(new String[Concepts.historicalAssociationNames.size()]));

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public void importFull(String releaseDirPath, String branchPath) throws ReleaseImportException {
		importFull(releaseDirPath, branchPath, null);
	}

	public void importFull(String releaseDirPath, String branchPath, String stopImportAfterEffectiveTime) throws ReleaseImportException {
		logger.info("Starting RF2 import on branch {}", branchPath);
		FullImportComponentFactoryImpl componentFactory = new FullImportComponentFactoryImpl(conceptService, branchService, branchPath, stopImportAfterEffectiveTime);
		ReleaseImporter releaseImporter = new ReleaseImporter();
		releaseImporter.loadFullReleaseFiles(releaseDirPath, DEFAULT_LOADING_PROFILE, componentFactory);
		logger.info("Completed RF2 import on branch {}", branchPath);
	}

	public void importSnapshot(String releaseDirPath, String branchPath) throws ReleaseImportException {
		logger.info("Starting RF2 import on branch {}", branchPath);
		ImportComponentFactoryImpl componentFactory = new ImportComponentFactoryImpl(conceptService, branchService, branchPath);
		ReleaseImporter releaseImporter = new ReleaseImporter();
		releaseImporter.loadSnapshotReleaseFiles(releaseDirPath, DEFAULT_LOADING_PROFILE, componentFactory);
		logger.info("Completed RF2 import on branch {}", branchPath);
	}
}
