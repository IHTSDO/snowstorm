package org.snomed.snowstorm.core.rf2.rf2import;

import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.otf.snomedboot.factory.HistoryAwareComponentFactory;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;

public class FullImportComponentFactoryImpl extends ImportComponentFactoryImpl implements HistoryAwareComponentFactory {

	private String basePath;
	private final CodeSystemService codeSystemService;
	private final String stopImportAfterEffectiveTime;

	public FullImportComponentFactoryImpl(ConceptService conceptService, ReferenceSetMemberService memberService, BranchService branchService, CodeSystemService codeSystemService,
										  String path, String stopImportAfterEffectiveTime) {
		super(conceptService, memberService, branchService, path);
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
		int effectiveDate = Integer.parseInt(releaseDate);
		codeSystemService.createVersionIfCodeSystemFoundOnPath(basePath, effectiveDate, "RF2 Full Import");

		if (stopImportAfterEffectiveTime != null && stopImportAfterEffectiveTime.equals(releaseDate)) {
			throw new RuntimeException("Stopping import here after " + stopImportAfterEffectiveTime);
		}

		// Prepare to load another release
		coreComponentsFlushed = false;
	}

	@Override
	public void loadingComponentsStarting() {
	}

	@Override
	public void loadingComponentsCompleted() {
	}
}
