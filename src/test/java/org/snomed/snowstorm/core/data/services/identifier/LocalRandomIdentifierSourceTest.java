package org.snomed.snowstorm.core.data.services.identifier;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class LocalRandomIdentifierSourceTest extends AbstractTest {

	@Autowired
	private LocalRandomIdentifierSource identifierSource;

	@Autowired
	private ConceptService conceptService;

	@Test
	void testReserveIds() {
		List<Long> longs = identifierSource.reserveIds(0, "10", 20);
		assertEquals(20, longs.size());
		assertEquals(11, longs.get(0).toString().length());
	}

	@Test
	void testClashDetection() throws ServiceException {
		LocalRandomIdentifierSource.ItemIdProvider originalItemIdProvider = identifierSource.getItemIdProvider();
		try {
			// Replace random ids with sequential based on 100
			// in order to simulate generating an identifier which is already used.
			AtomicInteger itemId = new AtomicInteger(100);
			identifierSource.setItemIdProvider(() -> itemId.getAndIncrement() + "");

			// We now know which identifiers will be generated
			List<Long> longs = identifierSource.reserveIds(0, "10", 3);
			assertEquals("100108", longs.get(0).toString());
			assertEquals("101107", longs.get(1).toString());
			assertEquals("102104", longs.get(2).toString());

			// And can reset the counter
			itemId.set(100);
			longs = identifierSource.reserveIds(0, "10", 3);
			assertEquals("100108", longs.get(0).toString());
			assertEquals("101107", longs.get(1).toString());
			assertEquals("102104", longs.get(2).toString());

			// Create a concept with one of these identifiers
			conceptService.create(new Concept("101107").addFSN("Test"), "MAIN");

			// Reset counter and generated again..
			itemId.set(100);
			longs = identifierSource.reserveIds(0, "10", 3);
			assertEquals(3, longs.size());
			assertEquals("100108", longs.get(0).toString());
			// We now don't get 101107 generated again even though the counter was reset
			// because this number is already in the store.
			assertEquals("102104", longs.get(1).toString());
			assertEquals("103105", longs.get(2).toString());
		} finally {
			identifierSource.setItemIdProvider(originalItemIdProvider);
		}
	}

	@Test
	void testGenerate10KWithClashDetection() throws ServiceException {
		LocalRandomIdentifierSource.ItemIdProvider originalItemIdProvider = identifierSource.getItemIdProvider();
		try {
			// Replace random ids with sequential based on 100
			// in order to simulate generating an identifier which is already used.
			AtomicInteger itemId = new AtomicInteger(100);
			identifierSource.setItemIdProvider(() -> itemId.getAndIncrement() + "");

			// We now know which identifiers will be generated
			int quantity = 5_512;
			List<Long> longs = identifierSource.reserveIds(0, "10", quantity);
			assertEquals(quantity, longs.size());
			assertEquals("100108", longs.get(0).toString());
			int offset = quantity - 1;
			assertEquals("5611103", longs.get(offset).toString());

			// Create a concept with one of these identifiers
			Long id = longs.get(4_000);
			conceptService.create(new Concept(id.toString()).addFSN("Test"), "MAIN");

			// Reset counter and generated again..
			itemId.set(100);
			longs = identifierSource.reserveIds(0, "10", 10_000);
			assertEquals(10_000, new LongOpenHashSet(longs).size());
			assertEquals("100108", longs.get(0).toString());
			assertNotEquals("5611103", longs.get(offset).toString());
		} finally {
			identifierSource.setItemIdProvider(originalItemIdProvider);
		}
	}
}
