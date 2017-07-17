package org.ihtsdo.elasticsnomed;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Commit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TestUtil {

	@Autowired
	private BranchService branchService;

	public void emptyCommit(String branchPath) {
		try (Commit commit = branchService.openCommit(branchPath)) {
			commit.markSuccessful();
		}
	}
}
