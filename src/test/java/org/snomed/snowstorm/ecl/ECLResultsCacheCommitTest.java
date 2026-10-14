package org.snomed.snowstorm.ecl;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.rest.pojo.SearchAfterPageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.ISA;
import static org.snomed.snowstorm.core.data.domain.Concepts.SNOMEDCT_ROOT;

class ECLResultsCacheCommitTest extends AbstractTest {

	private static final String DESCENDANTS_ECL = "< " + SNOMEDCT_ROOT;
	private static final PageRequest DESCENDANTS_PAGE = PageRequest.of(0, 10);

	@Autowired
	private ECLQueryService eclQueryService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@BeforeEach
	void enableEclCache() {
		// Other tests sharing the Spring context can leave the cache disabled.
		eclQueryService.setEclCacheEnabled(true);
		eclQueryService.clearCache();
	}

	@AfterEach
	void clearEclCache() {
		eclQueryService.clearCache();
	}

	@Test
	void commitResultsVisibleAtNewHead() throws ServiceException {
		conceptService.createUpdate(List.of(new Concept(SNOMEDCT_ROOT),
				new Concept("404684003").addAxiom(new Relationship(ISA, SNOMEDCT_ROOT))), MAIN);
		branchService.create("MAIN/A");
		assertEquals(1, selectDescendants(MAIN));
		assertEquals(1, selectDescendants("MAIN/A"));
		long branchASize = cachedSize("MAIN/A");

		conceptService.create(new Concept("71388002").addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)), MAIN);

		assertEquals(2, selectDescendants(MAIN));
		assertEquals(branchASize, cachedSize("MAIN/A"));
	}

	@Test
	void openCommitResultsNotCached() throws ServiceException {
		conceptService.createUpdate(List.of(new Concept(SNOMEDCT_ROOT),
				new Concept("404684003").addAxiom(new Relationship(ISA, SNOMEDCT_ROOT))), MAIN);
		Date head = versionControlHelper.getBranchCriteria(MAIN).getTimepoint();
		selectDescendants(MAIN);

		try (Commit commit = branchService.openCommit(MAIN)) {
			eclQueryService.selectConceptIds(DESCENDANTS_ECL, versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit), true, DESCENDANTS_PAGE);

			assertFalse(eclQueryService.getResultsCache().isCurrentTimepoint(MAIN, commit.getTimepoint()));
			assertNotNull(cachedDescendantsPage(MAIN, head));
		}
	}

	// FHIR expansions past the first large page re-fetch the same searchAfter pages, with server-generated tokens.
	@Test
	void searchAfterPagesCached() throws ServiceException {
		conceptService.createUpdate(List.of(new Concept(SNOMEDCT_ROOT),
				new Concept("404684003").addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)),
				new Concept("71388002").addAxiom(new Relationship(ISA, SNOMEDCT_ROOT))), MAIN);
		Sort byConceptId = Sort.by(QueryConcept.Fields.CONCEPT_ID);

		Page<Long> firstPage = eclQueryService.selectConceptIds(DESCENDANTS_ECL, versionControlHelper.getBranchCriteria(MAIN), true,
				SearchAfterPageRequest.of(new Object[]{0L}, 1, byConceptId));
		Page<Long> secondPage = eclQueryService.selectConceptIds(DESCENDANTS_ECL, versionControlHelper.getBranchCriteria(MAIN), true,
				SearchAfterPageRequest.of(new Object[]{firstPage.getContent().get(0)}, 1, byConceptId));
		Map<String, Long> before = eclQueryService.getResultsCache().getSummary();
		Page<Long> secondPageAgain = eclQueryService.selectConceptIds(DESCENDANTS_ECL, versionControlHelper.getBranchCriteria(MAIN), true,
				SearchAfterPageRequest.of(new Object[]{firstPage.getContent().get(0)}, 1, byConceptId));
		Map<String, Long> after = eclQueryService.getResultsCache().getSummary();

		assertNotEquals(firstPage.getContent(), secondPage.getContent());
		assertEquals(secondPage.getContent(), secondPageAgain.getContent());
		assertTrue(after.get("hits") > before.get("hits"));
		assertEquals(0, after.get("misses") - before.get("misses"));
	}

	private long selectDescendants(String path) {
		return eclQueryService.selectConceptIds(DESCENDANTS_ECL, versionControlHelper.getBranchCriteria(path), true, DESCENDANTS_PAGE)
				.getTotalElements();
	}

	private Page<Long> cachedDescendantsPage(String path, Date timepoint) {
		return eclQueryService.getResultsCache().get(path, timepoint, DESCENDANTS_ECL, true, DESCENDANTS_PAGE);
	}

	private long cachedSize(String path) {
		Map<String, Long> branchStats = eclQueryService.getResultsCacheStats().get(path);
		return branchStats != null ? branchStats.get("size") : 0;
	}
}
