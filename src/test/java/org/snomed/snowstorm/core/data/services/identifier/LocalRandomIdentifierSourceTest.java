package org.snomed.snowstorm.core.data.services.identifier;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class LocalRandomIdentifierSourceTest extends AbstractTest {

	@Autowired
	private LocalRandomIdentifierSource identifierSource;

	@Autowired
	private ConceptService conceptService;

	@Test
	public void testReserveIds() {
		List<Long> longs = identifierSource.reserveIds(0, "10", 20);
		assertEquals(20, longs.size());
		assertEquals(11, longs.get(0).toString().length());
	}

	@Test
	public void testClashDetection() throws ServiceException {
		LocalRandomIdentifierSource.ItemIdProvider originalItemIdProvider = identifierSource.getItemIdProvider();
		try {
			// Replace random ids with sequential based on 100
			// in order to simulate generating an identifier which is already used.
			AtomicInteger itemId = new AtomicInteger(100);
			identifierSource.setItemIdProvider(() -> itemId.getAndIncrement() + "");

			// We now know which identifiers will be generated
			List<Long> longs = identifierSource.reserveIds(0, "10", 3);
			System.out.println(longs);
			assertEquals("100108", longs.get(0).toString());
			assertEquals("101107", longs.get(1).toString());
			assertEquals("102104", longs.get(2).toString());

			// And can reset the counter
			itemId.set(100);
			longs = identifierSource.reserveIds(0, "10", 3);
			System.out.println(longs);
			assertEquals("100108", longs.get(0).toString());
			assertEquals("101107", longs.get(1).toString());
			assertEquals("102104", longs.get(2).toString());

			// Create a concept with one of these identifiers
			conceptService.create(new Concept("101107").addFSN("Test"), "MAIN");

			// Reset counter and generated again..
			itemId.set(100);
			longs = identifierSource.reserveIds(0, "10", 3);
			System.out.println(longs);
			assertEquals("100108", longs.get(0).toString());
			// We now don't get 101107 generated again even though the counter was reset
			// because this number is already in the store.
			assertEquals("102104", longs.get(1).toString());
			assertEquals("103105", longs.get(2).toString());
		} finally {
			identifierSource.setItemIdProvider(originalItemIdProvider);
		}
	}
}
