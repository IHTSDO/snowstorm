package com.kaicube.snomed.elasticsnomed.rf2import;

import com.kaicube.snomed.elasticsnomed.services.BranchService;
import com.kaicube.snomed.elasticsnomed.services.ConceptService;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.domain.ConceptConstants;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.springframework.beans.factory.annotation.Autowired;

public class ImportService {

	public static final LoadingProfile DEFAULT_LOADING_PROFILE = LoadingProfile.complete.withoutAllRefsets()
			.withRefset(ConceptConstants.US_EN_LANGUAGE_REFERENCE_SET)
			.withRefset(ConceptConstants.GB_EN_LANGUAGE_REFERENCE_SET);

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	public void importFull(String releaseDirPath, String branchPath) throws ReleaseImportException {
		importFull(releaseDirPath, branchPath, null);
	}

	public void importFull(String releaseDirPath, String branchPath, String stopImportAfterEffectiveTime) throws ReleaseImportException {
		conceptService.deleteAll();
		branchService.deleteAll();
		branchService.create("MAIN");
		FullImportComponentFactoryImpl componentFactory = new FullImportComponentFactoryImpl(conceptService, branchService, branchPath, stopImportAfterEffectiveTime);
		ReleaseImporter releaseImporter = new ReleaseImporter();
		releaseImporter.loadFullReleaseFiles(releaseDirPath, DEFAULT_LOADING_PROFILE, componentFactory);
	}

	public void importSnapshot(String releaseDirPath, String branchPath) throws ReleaseImportException {
		ImportComponentFactoryImpl componentFactory = new ImportComponentFactoryImpl(conceptService, branchService, branchPath);
		ReleaseImporter releaseImporter = new ReleaseImporter();
		releaseImporter.loadSnapshotReleaseFiles(releaseDirPath, DEFAULT_LOADING_PROFILE, componentFactory);
	}
}
