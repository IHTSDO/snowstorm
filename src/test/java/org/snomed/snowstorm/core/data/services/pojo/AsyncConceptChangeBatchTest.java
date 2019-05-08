package org.snomed.snowstorm.core.data.services.pojo;

import org.junit.Test;

import static org.junit.Assert.*;

public class AsyncConceptChangeBatchTest {

	@Test
	public void getSecondsDuration() throws InterruptedException {
		AsyncConceptChangeBatch batch = new AsyncConceptChangeBatch();
		Thread.sleep(2_000);
		assertNull(batch.getSecondsDuration());
		batch.setStatus(AsyncConceptChangeBatch.Status.COMPLETED);
		String secondsFloat = batch.getSecondsDuration().toString();
		System.out.println(secondsFloat);
		assertTrue(secondsFloat.matches("2\\.[0-9]{1,3}"));
	}
}
