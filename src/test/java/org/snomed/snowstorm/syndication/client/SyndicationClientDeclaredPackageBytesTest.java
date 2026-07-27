package org.snomed.snowstorm.syndication.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyndicationClientDeclaredPackageBytesTest {

	@Test
	void returnsDefaultWhenLengthMissingOrBlank() {
		assertEquals(SyndicationClient.DEFAULT_RF2_PACKAGE_LENGTH_BYTES, SyndicationClient.parseDeclaredPackageBytes(null));
		assertEquals(SyndicationClient.DEFAULT_RF2_PACKAGE_LENGTH_BYTES, SyndicationClient.parseDeclaredPackageBytes(""));
		assertEquals(SyndicationClient.DEFAULT_RF2_PACKAGE_LENGTH_BYTES, SyndicationClient.parseDeclaredPackageBytes("   "));
	}

	@Test
	void parsesNumericLength() {
		assertEquals(563280356L, SyndicationClient.parseDeclaredPackageBytes("563280356"));
		assertEquals(563280356L, SyndicationClient.parseDeclaredPackageBytes("563,280,356"));
		assertEquals(563280356L, SyndicationClient.parseDeclaredPackageBytes(" 563280356 "));
	}

	@Test
	void returnsDefaultWhenLengthMalformed() {
		assertEquals(SyndicationClient.DEFAULT_RF2_PACKAGE_LENGTH_BYTES, SyndicationClient.parseDeclaredPackageBytes("unknown"));
		assertEquals(SyndicationClient.DEFAULT_RF2_PACKAGE_LENGTH_BYTES, SyndicationClient.parseDeclaredPackageBytes("560 MB"));
	}
}
