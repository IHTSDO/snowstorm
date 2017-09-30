package org.ihtsdo.elasticsnomed.core.rf2.rf2import;

import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.elasticsnomed.core.data.services.CodeSystemService;
import org.ihtsdo.elasticsnomed.core.data.services.ConceptService;
import org.ihtsdo.otf.snomedboot.factory.HistoryAwareComponentFactory;

public class FullImportComponentFactoryImpl extends ImportComponentFactoryImpl implements HistoryAwareComponentFactory {

	private String basePath;
	private final CodeSystemService codeSystemService;
	private final String stopImportAfterEffectiveTime;

	public FullImportComponentFactoryImpl(ConceptService conceptService, BranchService branchService, CodeSystemService codeSystemService,
										  String path, String stopImportAfterEffectiveTime) {
		super(conceptService, branchService, path);
		this.basePath = path;
		this.stopImportAfterEffectiveTime = stopImportAfterEffectiveTime;
		this.codeSystemService = codeSystemService;
	}

	@Override
	public void loadingReleaseDeltaStarting(String releaseDate) {
		setCommit(getBranchService().openCommit(basePath));
	}

	@Override
	public void loadingReleaseDeltaFinished(String releaseDate) {
		completeImportCommit();

		// Create codesystem version if there is one on this path
		codeSystemService.createVersionIfCodeSystemFoundOnPath(basePath, releaseDate, "RF2 Full Import");

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
