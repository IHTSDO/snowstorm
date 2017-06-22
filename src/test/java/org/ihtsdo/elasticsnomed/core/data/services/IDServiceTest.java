package org.ihtsdo.elasticsnomed.core.data.services;

import org.junit.Assert;
import org.junit.Test;

public class IDServiceTest {

	@Test
	public void testGetHackId() {
		final String id = IDService.getHackId();
		Assert.assertEquals(15, id.length());
		Assert.assertFalse(id.contains("."));
	}

	@Test
	public void testIsConceptId() {
		Assert.assertFalse(IDService.isConceptId(null));
		Assert.assertFalse(IDService.isConceptId(""));
		Assert.assertFalse(IDService.isConceptId("123123"));
		Assert.assertFalse(IDService.isConceptId("123120"));
		Assert.assertTrue(IDService.isConceptId("1234101"));
		Assert.assertTrue(IDService.isConceptId("1234001"));
		Assert.assertFalse(IDService.isConceptId("101"));
		Assert.assertFalse(IDService.isConceptId("a123101"));
		Assert.assertFalse(IDService.isConceptId("12 3101"));
	}

}
