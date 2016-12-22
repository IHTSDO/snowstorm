package org.ihtsdo.elasticsnomed.services;

import org.junit.Assert;
import org.junit.Test;

public class IDServiceTest {

	@Test
	public void testGetHackId() {
		final String id = IDService.getHackId();
		Assert.assertEquals(9, id.length());
		Assert.assertFalse(id.contains("."));
	}

	@Test
	public void testIsConceptId() {
		Assert.assertFalse(IDService.isConceptId(null));
		Assert.assertFalse(IDService.isConceptId(""));
		Assert.assertFalse(IDService.isConceptId("123123"));
		Assert.assertFalse(IDService.isConceptId("123120"));
		Assert.assertTrue(IDService.isConceptId("123101"));
		Assert.assertTrue(IDService.isConceptId("123001"));
		Assert.assertFalse(IDService.isConceptId("101"));
	}

}
