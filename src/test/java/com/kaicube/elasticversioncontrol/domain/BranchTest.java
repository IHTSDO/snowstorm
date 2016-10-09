package com.kaicube.elasticversioncontrol.domain;

import org.junit.Assert;
import org.junit.Test;

public class BranchTest {

	@Test
	public void testIsParent() {
		Assert.assertTrue(new Branch("MAIN").isParent(new Branch("MAIN/A")));
		Assert.assertTrue(new Branch("MAIN/A").isParent(new Branch("MAIN/A/B")));

		Assert.assertFalse(new Branch("MAIN").isParent(new Branch("MAIN")));
		Assert.assertFalse(new Branch("MAIN/A").isParent(new Branch("MAIN/A")));
		Assert.assertFalse(new Branch("MAIN").isParent(new Branch("MAIN/A/B")));
		Assert.assertFalse(new Branch("MAIN/A").isParent(new Branch("MAIN")));
		Assert.assertFalse(new Branch("MAIN/A").isParent(new Branch("MAIN/B")));
	}

}
