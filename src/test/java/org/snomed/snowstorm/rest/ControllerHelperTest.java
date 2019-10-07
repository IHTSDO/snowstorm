package org.snomed.snowstorm.rest;

import org.junit.Test;
import org.springframework.data.domain.PageRequest;

import static org.junit.Assert.assertEquals;

public class ControllerHelperTest {
	@Test
	public void getPageRequestZero() {
		PageRequest pageRequest = ControllerHelper.getPageRequest(0, 100);
		assertEquals(0, pageRequest.getPageNumber());
		assertEquals(100, pageRequest.getPageSize());
	}

	@Test
	public void getPageRequestOne() {
		PageRequest pageRequest = ControllerHelper.getPageRequest(100, 100);
		assertEquals(1, pageRequest.getPageNumber());
		assertEquals(100, pageRequest.getPageSize());
	}

	@Test
	public void getPageRequestTwo() {
		PageRequest pageRequest = ControllerHelper.getPageRequest(200, 100);
		assertEquals(2, pageRequest.getPageNumber());
		assertEquals(100, pageRequest.getPageSize());
	}

	@Test(expected = IllegalArgumentException.class)
	public void getPageRequestTwoAndAHalf() {
		ControllerHelper.getPageRequest(250, 100);
	}

}
