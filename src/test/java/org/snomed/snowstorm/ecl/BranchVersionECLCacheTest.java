package org.snomed.snowstorm.ecl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BranchVersionECLCacheTest {

	@Test
	void test() {
		assertEquals("123,456", BranchVersionECLCache.normaliseEclString("123 AND 456"));
		assertEquals(">987840791000119102,>969688801000119108", BranchVersionECLCache.normaliseEclString(
				"> 987840791000119102 | Adenosine deaminase 2 deficiency (disorder) |  AND > 969688801000119108 | Acute left-sided ulcerative colitis (disorder) |"));
		assertEquals(">987840791000119102,>969688801000119108", BranchVersionECLCache.normaliseEclString(
				">987840791000119102, >969688801000119108 |Wrong term here|"));
	}

}
