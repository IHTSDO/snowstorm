package org.ihtsdo.elasticsnomed.core.data.services.identifier;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class IdentifierServiceTest {
	
	@Autowired 
	IdentifierService service;


	@Test
	public void testGetHackId() {
		final String id = service.getHackId();
		Assert.assertEquals(15, id.length());
		Assert.assertFalse(id.contains("."));
	}

	@Test
	public void testIsConceptId() {
		Assert.assertFalse(IdentifierService.isConceptId(null));
		Assert.assertFalse(IdentifierService.isConceptId(""));
		Assert.assertFalse(IdentifierService.isConceptId("123123"));
		Assert.assertFalse(IdentifierService.isConceptId("123120"));
		Assert.assertTrue(IdentifierService.isConceptId("1234101"));
		Assert.assertTrue(IdentifierService.isConceptId("1234001"));
		Assert.assertFalse(IdentifierService.isConceptId("101"));
		Assert.assertFalse(IdentifierService.isConceptId("a123101"));
		Assert.assertFalse(IdentifierService.isConceptId("12 3101"));
	}
}
