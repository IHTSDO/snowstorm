package org.ihtsdo.elasticsnomed;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Commit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TestUtil {

	@Autowired
	private BranchService branchService;

	public void createBranchAndParents(String branchPath) {
		String[] parts = branchPath.split("/");
		String path = "";
		for (String part : parts) {
			if (!path.isEmpty()) {
				path += "/";
			}
			path += part;
			branchService.create(path);
		}
	}

	public void emptyCommit(String branchPath) {
		Commit commit = branchService.openCommit(branchPath);
		branchService.completeCommit(commit);
	}
}
