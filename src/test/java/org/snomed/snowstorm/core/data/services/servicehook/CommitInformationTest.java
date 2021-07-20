package org.snomed.snowstorm.core.data.services.servicehook;

import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommitInformationTest {

	private static Commit buildCommit(String branchPath, String commitSourceBranchPath, Commit.CommitType commitType) {
		Commit commit = new Commit(new Branch(branchPath), commitType, null, null);
		commit.setSourceBranchPath(commitSourceBranchPath);
		return commit;
	}

	@Test
	void commitInformation_ShouldHaveCorrectData_WhenCommitTypeIsContent() {
		// given
		Commit commit = new Commit(new Branch("MAIN"), Commit.CommitType.CONTENT, null, null);

		// when
		CommitInformation commitInformation = new CommitInformation(commit);

		// then
		assertEquals("MAIN", commitInformation.getSourceBranchPath());
		assertNull(commitInformation.getTargetBranchPath());
	}

	@Test
	void commitInformation_ShouldHaveCorrectData_WhenCommitTypeIsRebase() {
		// given
		Commit commit = buildCommit("MAIN/ProjectA", "MAIN", Commit.CommitType.REBASE);

		// when
		CommitInformation commitInformation = new CommitInformation(commit);

		// then
		assertEquals(Commit.CommitType.REBASE, commitInformation.getCommitType());
		assertEquals("MAIN", commitInformation.getSourceBranchPath());
		assertEquals("MAIN/ProjectA", commitInformation.getTargetBranchPath());
	}

	@Test
	void commitInformation_ShouldHaveCorrectData_WhenCommitTypeIsPromotion() {
		// given
		Commit commit = buildCommit("MAIN", "MAIN/ProjectA", Commit.CommitType.PROMOTION);

		// when
		CommitInformation commitInformation = new CommitInformation(commit);

		// then
		assertEquals(Commit.CommitType.PROMOTION, commitInformation.getCommitType());
		assertEquals("MAIN/ProjectA", commitInformation.getSourceBranchPath());
		assertEquals("MAIN", commitInformation.getTargetBranchPath());
	}
}
