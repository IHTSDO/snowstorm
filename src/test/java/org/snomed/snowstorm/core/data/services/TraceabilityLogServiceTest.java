package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class TraceabilityLogServiceTest {

	private TraceabilityLogService traceabilityLogService;

	@Before
	public void setUp() {
		traceabilityLogService = new TraceabilityLogService();
	}

	@Test
	public void createCommitCommentRebase() {
		Commit commit = new Commit(new Branch("MAIN/A"), Commit.CommitType.REBASE, null, null);
		commit.setSourceBranchPath("MAIN");
		assertEquals("System performed merge of MAIN to MAIN/A", traceabilityLogService.createCommitComment(commit, Collections.emptySet()));
	}

	@Test
	public void createCommitCommentPromotion() {
		Commit commit = new Commit(new Branch("MAIN"), Commit.CommitType.PROMOTION, null, null);
		commit.setSourceBranchPath("MAIN/A");
		assertEquals("System performed merge of MAIN/A to MAIN", traceabilityLogService.createCommitComment(commit, Collections.emptySet()));
	}
}
