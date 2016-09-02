package com.kaicube.snomed.elasticsnomed.services;

import org.junit.Assert;
import org.junit.Test;

public class IDServiceTest {

	@Test
	public void test() {
		final String id = IDService.getHackId();
		Assert.assertEquals(9, id.length());
		Assert.assertFalse(id.contains("."));
	}

}
