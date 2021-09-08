package org.snomed.snowstorm.core.rf2.rf2import;

import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.otf.snomedboot.factory.HistoryAwareComponentFactory;
import org.snomed.snowstorm.core.data.services.BranchMetadataHelper;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptUpdateHelper;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;

public class FullImportComponentFactoryImpl extends ImportComponentFactoryImpl implements HistoryAwareComponentFactory {

	private final String basePath;
	private final BranchMetadataHelper branchMetadataHelper;
	private final CodeSystemService codeSystemService;
	private final String stopImportAfterEffectiveTime;

	FullImportComponentFactoryImpl(ConceptUpdateHelper conceptUpdateHelper, ReferenceSetMemberService memberService, BranchService branchService,
			BranchMetadataHelper branchMetadataHelper, CodeSystemService codeSystemService, String path, String stopImportAfterEffectiveTime) {
		super(conceptUpdateHelper, memberService, branchService, branchMetadataHelper, path, null, false, false);
		this.branchMetadataHelper = branchMetadataHelper;
		this.basePath = path;
		this.stopImportAfterEffectiveTime = stopImportAfterEffectiveTime;
		this.codeSystemService = codeSystemService;

		// There is no need to skip these stated relationships when importing the full
		// because they will first be imported as stated then changed to inferred at a later effectiveTime.
		super.statedRelationshipsToSkip = null;
	}

	@Override
	public void loadingReleaseDeltaStarting(String releaseDate) {
		setCommit(getBranchService().openCommit(basePath, branchMetadataHelper.getBranchLockMetadata("Loading components from RF2 Delta import.")));
	}

	@Override
	public void loadingReleaseDeltaFinished(String releaseDate) {
		completeImportCommit();

		// Create codesystem version if there is one on this path
		int effectiveDate = Integer.parseInt(releaseDate);
		codeSystemService.createVersionIfCodeSystemFoundOnPath(basePath, effectiveDate, false);

		if (stopImportAfterEffectiveTime != null && stopImportAfterEffectiveTime.equals(releaseDate)) {
			throw new RuntimeException("Stopping import here after " + stopImportAfterEffectiveTime);
		}

		// Prepare to load another release
		coreComponentsFlushed = false;
	}

	@Override
	public void loadingComponentsStarting() {
		// Nothing to do
	}

	@Override
	public void loadingComponentsCompleted() {
		// Nothing to do
	}
}
