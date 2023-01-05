package org.snomed.snowstorm.core.data.services.postcoordination;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.snomed.snowstorm.mrcm.MRCMService;
import org.snomed.snowstorm.mrcm.model.MRCM;

public class ExpressionContext {

	private final VersionControlHelper versionControlHelper;
	private final MRCMService mrcmService;
	private final String branch;
	private final TimerUtil timer;
	private final BranchService branchService;

	private BranchCriteria branchCriteria;
	private BranchCriteria dependantReleaseBranchCriteria;
	private MRCM mrcm;

	public ExpressionContext(String branch, BranchService branchService, VersionControlHelper versionControlHelper, MRCMService mrcmService, TimerUtil timer) {
		this.branch = branch;
		this.branchService = branchService;
		this.versionControlHelper = versionControlHelper;
		this.mrcmService = mrcmService;
		this.timer = timer;
	}

	public BranchCriteria getBranchCriteria() {
		if (branchCriteria == null) {
			branchCriteria = versionControlHelper.getBranchCriteria(branch);
		}
		return branchCriteria;
	}

	public BranchCriteria getDependantReleaseBranchCriteria() {
		if (dependantReleaseBranchCriteria == null) {
			Branch latest = branchService.findLatest(branch);
			dependantReleaseBranchCriteria = versionControlHelper.getBranchCriteriaAtTimepoint(PathUtil.getParentPath(branch), latest.getBase());
		}
		return dependantReleaseBranchCriteria;
	}

	public MRCM getBranchMRCM() throws ServiceException {
		if (mrcm == null) {
			mrcm = mrcmService.loadActiveMRCMFromCache(branch);
		}
		return mrcm;
	}

	public String getBranch() {
		return branch;
	}

	public TimerUtil getTimer() {
		return timer;
	}
}
