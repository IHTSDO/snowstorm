package org.snomed.snowstorm.rest.converter;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class SearchAfterHelperTests {

	private String FOO = "foo";
	private String BAR = "bar";
	private String [] testArray;
	private String expectedToken = "WyJmb28iLCJiYXIiXQ==";

	@Before 
	public void before() {
		testArray = new String[] { FOO, BAR };
	}

	@Test
	public void toSearchAfterToken() {
		String token = SearchAfterHelper.toSearchAfterToken(testArray);
		assertEquals(expectedToken, token);
	}

	@Test
	public void fromSearchAfterToken() {
		Object[] arr = SearchAfterHelper.fromSearchAfterToken(expectedToken);
		assertEquals(FOO, arr[0]);
		assertEquals(BAR, arr[1]);
	}

	@Test
	public void testConvertInt() {
		Object[] before = new Object[]{123, 456};
		String token = SearchAfterHelper.toSearchAfterToken(before);
		Object[] after = SearchAfterHelper.fromSearchAfterToken(token);
		assertArrayEquals(before, after);
	}

	@Test
	public void testConvertLongFails() {
		Object[] before = new Object[]{123L, 456L};
		String token = SearchAfterHelper.toSearchAfterToken(before);
		Object[] after = SearchAfterHelper.fromSearchAfterToken(token);
		assertFalse("Conversion from Long to token and back fails.", Arrays.equals(before, after));
	}
}
