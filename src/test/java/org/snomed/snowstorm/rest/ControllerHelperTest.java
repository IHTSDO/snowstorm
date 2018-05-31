package org.snomed.snowstorm.rest;

import org.apache.commons.lang.IncompleteArgumentException;
import org.junit.Test;
import org.springframework.data.domain.PageRequest;

import static org.junit.Assert.*;

public class ControllerHelperTest {
	@Test
	public void getPageRequestZero() throws Exception {
		PageRequest pageRequest = ControllerHelper.getPageRequest(0, 100);
		assertEquals(0, pageRequest.getPageNumber());
		assertEquals(100, pageRequest.getPageSize());
	}

	@Test
	public void getPageRequestOne() throws Exception {
		PageRequest pageRequest = ControllerHelper.getPageRequest(100, 100);
		assertEquals(1, pageRequest.getPageNumber());
		assertEquals(100, pageRequest.getPageSize());
	}

	@Test
	public void getPageRequestTwo() throws Exception {
		PageRequest pageRequest = ControllerHelper.getPageRequest(200, 100);
		assertEquals(2, pageRequest.getPageNumber());
		assertEquals(100, pageRequest.getPageSize());
	}

	@Test(expected = IllegalArgumentException.class)
	public void getPageRequestTwoAndAHalf() throws Exception {
		ControllerHelper.getPageRequest(250, 100);
	}

}
