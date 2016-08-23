package com.kaicube.snomed.elasticsnomed.services;

import com.kaicube.snomed.elasticsnomed.Config;
import com.kaicube.snomed.elasticsnomed.TestConfig;
import com.kaicube.snomed.elasticsnomed.domain.Branch;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {Config.class, TestConfig.class})
public class BranchServiceTest {

	@Autowired
	private BranchService branchService;

	@Test
	public void testCreateFindBranches() throws Exception {
		Assert.assertNull(branchService.findLatest("MAIN"));

		branchService.create("MAIN");

		final Branch main = branchService.findLatest("MAIN");
		Assert.assertNotNull(main);
		Assert.assertNotNull(main.getFatPath());
		Assert.assertNotNull(main.getBase());
		Assert.assertNotNull(main.getHead());
		Assert.assertEquals("MAIN", main.getFatPath());

		Assert.assertNull(branchService.findLatest("MAIN/A"));
		branchService.create("MAIN/A");
		final Branch a = branchService.findLatest("MAIN/A");
		Assert.assertNotNull(a);
		Assert.assertEquals("MAIN/A", a.getFatPath());

		Assert.assertNotNull(branchService.findLatest("MAIN"));
	}

	@Test
	public void testFindAll() {
		Assert.assertEquals(0, branchService.findAll().size());

		branchService.create("MAIN");
		Assert.assertEquals(1, branchService.findAll().size());

		branchService.create("MAIN/A");
		branchService.create("MAIN/A/AA");
		branchService.create("MAIN/C");
		branchService.create("MAIN/C/something");
		branchService.create("MAIN/C/something/thing");
		branchService.create("MAIN/B");

		final List<Branch> branches = branchService.findAll();
		Assert.assertEquals(7, branches.size());

		Assert.assertEquals("MAIN", branches.get(0).getFatPath());
		Assert.assertEquals("MAIN/A", branches.get(1).getFatPath());
		Assert.assertEquals("MAIN/C/something/thing", branches.get(6).getFatPath());
	}

	@Test
	public void testFindChildren() {
		Assert.assertEquals(0, branchService.findAll().size());

		branchService.create("MAIN");
		Assert.assertEquals(0, branchService.findChildren("MAIN").size());

		branchService.create("MAIN/A");
		branchService.create("MAIN/A/AA");
		branchService.create("MAIN/C");
		branchService.create("MAIN/C/something");
		branchService.create("MAIN/C/something/thing");
		branchService.create("MAIN/B");

		final List<Branch> mainChildren = branchService.findChildren("MAIN");
		Assert.assertEquals(6, mainChildren.size());

		Assert.assertEquals("MAIN/A", mainChildren.get(0).getFatPath());
		Assert.assertEquals("MAIN/C/something/thing", mainChildren.get(5).getFatPath());

		final List<Branch> cChildren = branchService.findChildren("MAIN/C");
		Assert.assertEquals(2, cChildren.size());
		Assert.assertEquals("MAIN/C/something", cChildren.get(0).getFatPath());
		Assert.assertEquals("MAIN/C/something/thing", cChildren.get(1).getFatPath());
	}

	@After
	public void tearDown() {
		branchService.deleteAll();
	}
}