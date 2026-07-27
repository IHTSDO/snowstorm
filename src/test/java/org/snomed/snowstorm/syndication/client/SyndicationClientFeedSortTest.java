package org.snomed.snowstorm.syndication.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyndicationClientFeedSortTest {

	@Test
	void sortFeedEntriesAllowsMissingContentItemVersion() {
		SyndicationFeedEntry incomplete = new SyndicationFeedEntry();
		SyndicationFeedEntry newer = versionedEntry("http://snomed.info/sct/900000000000207008/version/20250401");
		SyndicationFeedEntry older = versionedEntry("http://snomed.info/sct/900000000000207008/version/20250301");
		List<SyndicationFeedEntry> entries = new ArrayList<>(List.of(incomplete, older, newer));

		SyndicationClient.sortFeedEntries(entries);

		assertEquals(newer, entries.get(0));
		assertEquals(older, entries.get(1));
		assertEquals(incomplete, entries.get(2));
	}

	private static SyndicationFeedEntry versionedEntry(String contentItemVersion) {
		SyndicationFeedEntry entry = new SyndicationFeedEntry();
		entry.setContentItemVersion(contentItemVersion);
		return entry;
	}
}
