package org.ihtsdo.elasticsnomed.rf2import;

import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.elasticsnomed.services.ConceptService;
import org.ihtsdo.otf.snomedboot.factory.HistoryAwareComponentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FullImportComponentFactoryImpl extends ImportComponentFactoryImpl implements HistoryAwareComponentFactory {

	private String basePath;
	private final String stopImportAfterEffectiveTime;
	private Logger logger = LoggerFactory.getLogger(getClass());

	public FullImportComponentFactoryImpl(ConceptService conceptService, BranchService branchService, String path, String stopImportAfterEffectiveTime) {
		super(conceptService, branchService, path);
		this.basePath = path;
		this.stopImportAfterEffectiveTime = stopImportAfterEffectiveTime;
	}

	@Override
	public void loadingReleaseDeltaStarting(String releaseDate) {
		setCommit(getBranchService().openCommit(basePath));
	}

	@Override
	public void loadingReleaseDeltaFinished(String releaseDate) {
		completeImportCommit();
		String releaseBranchPath = basePath + "/" + releaseDate;
		logger.info("Creating release branch {}", releaseBranchPath);
		getBranchService().create(releaseBranchPath);
		if (stopImportAfterEffectiveTime != null && stopImportAfterEffectiveTime.equals(releaseDate)) {
			throw new RuntimeException("Stopping import here after " + stopImportAfterEffectiveTime);
		}
	}

	@Override
	public void loadingComponentsStarting() {
	}

	@Override
	public void loadingComponentsCompleted() {
	}
}
