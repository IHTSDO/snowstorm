package org.ihtsdo.elasticsnomed.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import org.ihtsdo.elasticsnomed.TestConfig;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

import static io.kaicode.elasticvc.domain.Branch.BranchState.*;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class BranchServiceTest {

	@Autowired
	private BranchService branchService;

	@Test
	public void testCreateFindBranches() throws Exception {
		assertNull(branchService.findLatest("MAIN"));

		final Branch branch = branchService.create("MAIN");
		assertEquals(UP_TO_DATE, branch.getState());

		final Branch main = branchService.findLatest("MAIN");
		assertNotNull(main);
		assertNotNull(main.getInternalId());
		assertNotNull(main.getPath());
		assertNotNull(main.getBase());
		assertNotNull(main.getHead());
		assertEquals("MAIN", main.getPath());
		assertEquals(UP_TO_DATE, main.getState());

		assertNull(branchService.findLatest("MAIN/A"));
		branchService.create("MAIN/A");
		final Branch a = branchService.findLatest("MAIN/A");
		assertNotNull(a);
		assertEquals("MAIN/A", a.getPath());
		assertEquals(UP_TO_DATE, a.getState());

		assertNotNull(branchService.findLatest("MAIN"));
	}

	@Test
	public void testFindAll() {
		assertEquals(0, branchService.findAll().size());

		branchService.create("MAIN");
		assertEquals(1, branchService.findAll().size());

		branchService.create("MAIN/A");
		branchService.create("MAIN/A/AA");
		branchService.create("MAIN/C");
		branchService.create("MAIN/C/something");
		branchService.create("MAIN/C/something/thing");
		branchService.create("MAIN/B");

		final List<Branch> branches = branchService.findAll();
		assertEquals(7, branches.size());

		assertEquals("MAIN", branches.get(0).getPath());
		assertEquals("MAIN/A", branches.get(1).getPath());
		assertEquals("MAIN/C/something/thing", branches.get(6).getPath());
	}

	@Test
	public void testFindChildren() {
		assertEquals(0, branchService.findAll().size());

		branchService.create("MAIN");
		assertEquals(0, branchService.findChildren("MAIN").size());

		branchService.create("MAIN/A");
		branchService.create("MAIN/A/AA");
		branchService.create("MAIN/C");
		branchService.create("MAIN/C/something");
		branchService.create("MAIN/C/something/thing");
		branchService.create("MAIN/B");

		final List<Branch> mainChildren = branchService.findChildren("MAIN");
		assertEquals(6, mainChildren.size());

		assertEquals("MAIN/A", mainChildren.get(0).getPath());
		assertEquals("MAIN/C/something/thing", mainChildren.get(5).getPath());

		final List<Branch> cChildren = branchService.findChildren("MAIN/C");
		assertEquals(2, cChildren.size());
		assertEquals("MAIN/C/something", cChildren.get(0).getPath());
		assertEquals("MAIN/C/something/thing", cChildren.get(1).getPath());
	}

	@Test
	public void testBranchState() {
		branchService.create("MAIN");
		branchService.create("MAIN/A");
		branchService.create("MAIN/A/A1");
		branchService.create("MAIN/B");

		assertBranchState("MAIN", UP_TO_DATE);
		assertBranchState("MAIN/A", UP_TO_DATE);
		assertBranchState("MAIN/A/A1", UP_TO_DATE);
		assertBranchState("MAIN/B", UP_TO_DATE);

		makeEmptyCommit("MAIN/A");

		assertBranchState("MAIN", UP_TO_DATE);
		assertBranchState("MAIN/A", FORWARD);
		assertBranchState("MAIN/A/A1", BEHIND);
		assertBranchState("MAIN/B", UP_TO_DATE);

		makeEmptyCommit("MAIN");

		assertBranchState("MAIN", UP_TO_DATE);
		assertBranchState("MAIN/A", DIVERGED);
		assertBranchState("MAIN/A/A1", BEHIND);
		assertBranchState("MAIN/B", BEHIND);
	}

	@Test
	public void testExists() {
		branchService.create("MAIN");
		branchService.create("MAIN/AA");

		assertTrue(branchService.exists("MAIN"));
		assertTrue(branchService.exists("MAIN/AA"));
		assertFalse(branchService.exists("THING"));
		assertFalse(branchService.exists("MAIN/B"));
		assertFalse(branchService.exists("MAIN/AA/B"));
	}

	private void assertBranchState(String path, Branch.BranchState status) {
		assertEquals(status, branchService.findLatest(path).getState());
	}

	private void makeEmptyCommit(String path) {
		try (Commit commit = branchService.openCommit(path)) {
			commit.markSuccessful();
		}
	}

	@After
	public void tearDown() {
		branchService.deleteAll();
	}
}
