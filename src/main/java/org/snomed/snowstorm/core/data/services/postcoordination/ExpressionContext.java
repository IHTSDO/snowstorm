package org.snomed.snowstorm.core.data.services.postcoordination;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.mrcm.MRCMService;
import org.snomed.snowstorm.mrcm.model.MRCM;

public class ExpressionContext {

	private final VersionControlHelper versionControlHelper;
	private final MRCMService mrcmService;
	private final String branch;

	private BranchCriteria branchCriteria;
	private MRCM mrcm;

	public ExpressionContext(String branch, VersionControlHelper versionControlHelper, MRCMService mrcmService) {
		this.branch = branch;
		this.versionControlHelper = versionControlHelper;
		this.mrcmService = mrcmService;
	}

	public BranchCriteria getBranchCriteria() {
		if (branchCriteria == null) {
			branchCriteria = versionControlHelper.getBranchCriteria(branch);
		}
		return branchCriteria;
	}

	public MRCM getBranchMRCM() throws ServiceException {
		if (mrcm == null) {
			mrcm = mrcmService.loadActiveMRCM(branch, getBranchCriteria());
		}
		return mrcm;
	}

	public String getBranch() {
		return branch;
	}
}
