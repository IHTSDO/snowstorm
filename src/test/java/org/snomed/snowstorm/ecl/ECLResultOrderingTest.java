package org.snomed.snowstorm.ecl;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.util.SearchAfterPage;
import org.snomed.snowstorm.rest.pojo.SearchAfterPageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.kaicode.elasticvc.domain.Branch.MAIN;
import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.otf.owltoolkit.constants.Concepts.LATERALITY;
import static org.snomed.snowstorm.TestConcepts.*;
import static org.snomed.snowstorm.ecl.ECLResultOrderingTestConfig.*;

/**
 * BROWSE-863. ECL concept results must be ordered by concept id descending.
 *
 * Member of queries used to return concepts in whatever order Elasticsearch held them, which differs between the
 * primary and replica copies of a shard. That made searchAfter paging unsound, because it resumes by locating the
 * previous page's last id in the result list: when the order changed between requests concepts were silently
 * skipped or repeated, the latter looping forever.
 *
 * These assertions check the results are sorted, not merely that repeated calls agree. A single node test cluster
 * has no replica to diverge from, so a stability assertion would pass even with the defect present.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {TestConfig.class, ECLResultOrderingTestConfig.class})
class ECLResultOrderingTest {

	@Autowired
	private ECLQueryService eclQueryService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	private static final PageRequest LARGE_PAGE = PageRequest.of(0, 10_000);

	private BranchCriteria branchCriteria;

	@BeforeEach
	void setup() {
		branchCriteria = versionControlHelper.getBranchCriteria(MAIN);
	}

	@Test
	void memberOfIsSortedWhenAnsweredByTheMemberIndex() {
		assertEquals(descending(MEMBER_IDS_ASCENDING), selectConceptIds("^" + REFSET_WITHOUT_LOOKUP));
	}

	@Test
	void memberOfIsSortedWhenAnsweredByAConceptsLookup() {
		assertEquals(descending(MEMBER_IDS_ASCENDING), selectConceptIds("^" + REFSET_WITH_LOOKUP));
	}

	@Test
	void memberOfWithExclusionIsSorted() {
		List<Long> expected = descending(MEMBER_IDS_ASCENDING);
		expected.remove(Long.valueOf(HEART_STRUCTURE));

		assertEquals(expected, selectConceptIds("^" + REFSET_WITHOUT_LOOKUP + " MINUS " + HEART_STRUCTURE));
	}

	@Test
	void memberOfWithConjunctionIsSorted() {
		// Every member is a descendant of body structure, so the conjunction selects the whole reference set
		assertEquals(
				descending(MEMBER_IDS_ASCENDING),
				selectConceptIds("^" + REFSET_WITHOUT_LOOKUP + " AND <" + BODY_STRUCTURE));
	}

	@Test
	void memberOfWithDisjunctionIsSorted() {
		Set<String> expectedIds = new HashSet<>(MEMBER_IDS_ASCENDING);
		expectedIds.add(BODY_STRUCTURE);

		assertEquals(
				descending(expectedIds),
				selectConceptIds("^" + REFSET_WITHOUT_LOOKUP + " OR " + BODY_STRUCTURE));
	}

	@Test
	void memberOfWithConceptFilterIsSorted() {
		// A concept filter forces the prefetch path and rebuilds the id set, so it must not lose the ordering
		assertEquals(
				descending(MEMBER_IDS_ASCENDING),
				selectConceptIds("^" + REFSET_WITHOUT_LOOKUP + " {{ C active = true }}"));
	}

	/**
	 * The description filter path streams descriptions with no sort of its own, so without the sort applied to the
	 * prefetched ids these results have no defined order at all.
	 */
	@Test
	void memberOfWithDescriptionFilterIsSorted() {
		assertEquals(
				descending(MEMBER_IDS_ASCENDING),
				selectConceptIds("^" + REFSET_WITHOUT_LOOKUP + " {{ term = \"Orderable\" }}"));
	}

	@Test
	void refinedMemberOfIsSorted() {
		assertEquals(
				descending(MEMBER_IDS_ASCENDING),
				selectConceptIds("^" + REFSET_WITHOUT_LOOKUP + " : " + LATERALITY + " = " + RIGHT));
	}

	/**
	 * The symptom reported in BROWSE-863: the same query returned a different first and last concept depending on
	 * the requested limit.
	 */
	@Test
	void everyPageSizeReturnsAPrefixOfTheSameOrdering() {
		List<Long> expected = descending(MEMBER_IDS_ASCENDING);

		for (int limit : new int[]{1, 2, 3, 5, 7, 10, 100}) {
			List<Long> page = selectConceptIds("^" + REFSET_WITHOUT_LOOKUP, PageRequest.of(0, limit));
			assertEquals(expected.subList(0, Math.min(limit, expected.size())), page,
					"First page of size " + limit + " should be the start of the full ordering");
		}
	}

	@Test
	void searchAfterPagingVisitsEveryConceptExactlyOnce() {
		List<Long> expected = descending(MEMBER_IDS_ASCENDING);
		int pageSize = 3;

		List<Long> paged = new ArrayList<>();
		Object[] searchAfter = null;
		// Bounded so a regression fails the test rather than looping forever, which is the reported symptom
		int maxRequests = (expected.size() / pageSize) + 5;

		for (int request = 0; request < maxRequests; request++) {
			PageRequest pageRequest = searchAfter == null
					? PageRequest.of(0, pageSize, conceptIdDescending())
					: SearchAfterPageRequest.of(searchAfter, pageSize, conceptIdDescending());

			Page<Long> page = eclQueryService.selectConceptIds("^" + REFSET_WITHOUT_LOOKUP, branchCriteria, false, pageRequest);
			if (!page.hasContent()) {
				break;
			}
			paged.addAll(page.getContent());
			searchAfter = ((SearchAfterPage<Long>) page).getSearchAfter();
		}

		assertEquals(expected, paged, "searchAfter paging should return the full result set in order");
		assertEquals(new HashSet<>(paged).size(), paged.size(), "searchAfter paging should not repeat concepts");
	}

	private Sort conceptIdDescending() {
		return Sort.by(QueryConcept.Fields.CONCEPT_ID).descending();
	}

	private List<Long> selectConceptIds(String ecl) {
		return selectConceptIds(ecl, LARGE_PAGE);
	}

	private List<Long> selectConceptIds(String ecl, PageRequest pageRequest) {
		return new ArrayList<>(eclQueryService.selectConceptIds(ecl, branchCriteria, false, pageRequest).getContent());
	}

	private List<Long> descending(Iterable<String> conceptIds) {
		List<Long> sorted = new ArrayList<>();
		conceptIds.forEach(conceptId -> sorted.add(Long.valueOf(conceptId)));
		sorted.sort(Comparator.reverseOrder());
		return sorted;
	}
}
