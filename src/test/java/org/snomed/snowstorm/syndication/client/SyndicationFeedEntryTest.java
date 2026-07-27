package org.snomed.snowstorm.syndication.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyndicationFeedEntryTest {

	@Test
	void getTitleCleaned() {
		SyndicationFeedEntry entry = new SyndicationFeedEntry();
		entry.setTitle("SNOMED CT International Edition-March 2025 v1.0");
		assertEquals("SNOMED CT International Edition", entry.getTitleCleaned());

		entry.setTitle("No hyphen title");
		assertEquals("No hyphen title", entry.getTitleCleaned());

		entry.setTitle(null);
		assertEquals("", entry.getTitleCleaned());
	}
}
