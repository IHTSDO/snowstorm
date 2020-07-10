package org.snomed.snowstorm.rest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import static org.junit.Assert.assertEquals;

class ControllerHelperTest {
	@Test
	void getPageRequestZero() {
		PageRequest pageRequest = ControllerHelper.getPageRequest(0, 100);
		assertEquals(0, pageRequest.getPageNumber());
		assertEquals(100, pageRequest.getPageSize());
	}

	@Test
	void getPageRequestOne() {
		PageRequest pageRequest = ControllerHelper.getPageRequest(100, 100);
		assertEquals(1, pageRequest.getPageNumber());
		assertEquals(100, pageRequest.getPageSize());
	}

	@Test
	void getPageRequestTwo() {
		PageRequest pageRequest = ControllerHelper.getPageRequest(200, 100);
		assertEquals(2, pageRequest.getPageNumber());
		assertEquals(100, pageRequest.getPageSize());
	}

	@Test
	void getPageRequestTwoAndAHalf() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			ControllerHelper.getPageRequest(250, 100);
		});
	}

}
