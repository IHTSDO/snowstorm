package org.snomed.snowstorm.rest.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

class SearchAfterHelperTest {

	private String FOO = "foo";
	private String BAR = "bar";
	private String [] testArray;
	private String expectedToken = "WyJmb28iLCJiYXIiXQ==";

	@BeforeEach
	void before() {
		testArray = new String[] { FOO, BAR };
	}

	@Test
	void toSearchAfterToken() {
		String token = SearchAfterHelper.toSearchAfterToken(testArray);
		assertEquals(expectedToken, token);
	}

	@Test
	void fromSearchAfterToken() {
		Object[] arr = SearchAfterHelper.fromSearchAfterToken(expectedToken);
		assertEquals(FOO, arr[0]);
		assertEquals(BAR, arr[1]);
	}

	@Test
	void testConvertInt() {
		Object[] before = new Object[]{123, 456};
		String token = SearchAfterHelper.toSearchAfterToken(before);
		Object[] after = SearchAfterHelper.fromSearchAfterToken(token);
		assertArrayEquals(before, after);
	}

	@Test
	void testConvertLongFails() {
		Object[] before = new Object[]{123L, 456L};
		String token = SearchAfterHelper.toSearchAfterToken(before);
		Object[] after = SearchAfterHelper.fromSearchAfterToken(token);
		assertFalse("Conversion from Long to token and back fails.", Arrays.equals(before, after));
	}
}
