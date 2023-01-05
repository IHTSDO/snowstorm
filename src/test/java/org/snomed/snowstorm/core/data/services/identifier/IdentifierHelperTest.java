package org.snomed.snowstorm.core.data.services.identifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdentifierHelperTest {

	@Test
	public void testGetNamespaceFromSCTID() {
		assertEquals(0, IdentifierHelper.getNamespaceFromSCTID("404684003"));
		assertEquals(0, IdentifierHelper.getNamespaceFromSCTID("2148514019"));
		assertEquals(1000202, IdentifierHelper.getNamespaceFromSCTID("17561000202107"));
		assertEquals(1000202, IdentifierHelper.getNamespaceFromSCTID("1240371000202113"));
		assertEquals(1000124, IdentifierHelper.getNamespaceFromSCTID("731000124108"));
		assertEquals(1000124, IdentifierHelper.getNamespaceFromSCTID("1621000124119"));
	}

}
