package com.kaicube.snomed.elasticsnomed.rest;

import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BranchPathUrlRewriteFilterTest {

	private BranchPathUrlRewriteFilter filter;

	@Before
	public void setup() {
		filter = new BranchPathUrlRewriteFilter(Lists.newArrayList("/branches/(.*)/children", "/branches/(.*)", "/browser/(.*)/concepts"));
	}

	@Test
	public void testRewriteUri() throws Exception {
		Assert.assertEquals("/branches/MAIN", filter.rewriteUri("/branches/MAIN"));

		Assert.assertEquals("/branches/MAIN|PROJECT-A", filter.rewriteUri("/branches/MAIN/PROJECT-A"));
		Assert.assertEquals("/branches/MAIN|PROJECT-A/children", filter.rewriteUri("/branches/MAIN/PROJECT-A/children"));

		Assert.assertEquals("/browser/MAIN/concepts", filter.rewriteUri("/browser/MAIN/concepts"));
		Assert.assertEquals("/browser/MAIN|PROJECT-A|TASK-A/concepts", filter.rewriteUri("/browser/MAIN/PROJECT-A/TASK-A/concepts"));
	}
}
