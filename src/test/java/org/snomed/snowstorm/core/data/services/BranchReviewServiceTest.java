package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import org.elasticsearch.ElasticsearchParseException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.domain.review.BranchReviewConceptChanges;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class BranchReviewServiceTest extends AbstractTest {

	@Autowired
	private BranchReviewService reviewService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	private Date setupStartTime;

	private static final Long[] EMPTY_ARRAY = new Long[]{};

	@Before
	public void setUp() throws Exception {
		setupStartTime = now();
		branchService.create("MAIN");
		createConcept("100", "MAIN");
		branchService.create("MAIN/A");
	}

	@Test
	public void testCreateConceptChangeReportOnBranchSinceTimepoint() throws Exception {
		try {
			// Assert report contains one new concept on MAIN since start of setup
			assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN", setupStartTime, now(), true),
					new Long[]{100L}, EMPTY_ARRAY, EMPTY_ARRAY);

			// Assert report contains no concepts on MAIN/A since start of setup
			assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN/A", setupStartTime, now(), false),
					EMPTY_ARRAY, EMPTY_ARRAY, EMPTY_ARRAY);

			final Date beforeSecondCreation = now();
			createConcept("200", "MAIN/A");

			// Assert report contains no new concepts on MAIN/A since start of setup
			assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN/A", setupStartTime, now(), false),
					new Long[]{200L}, EMPTY_ARRAY, EMPTY_ARRAY);

			// Assert report contains one new concept on MAIN/A since timeA
			assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN/A", beforeSecondCreation, now(), false),
					new Long[]{200L}, EMPTY_ARRAY, EMPTY_ARRAY);

			final Date beforeDeletion = now();

			// Delete concept 100 from MAIN
			conceptService.deleteConceptAndComponents("100", "MAIN", false);

			final Date afterDeletion = now();

			// Assert report contains one deleted concept on MAIN
			assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN", beforeDeletion, now(), true),
					EMPTY_ARRAY, EMPTY_ARRAY, new Long[]{100L});


			// Assert report contains no deleted concepts on MAIN before the deletion
			assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN", beforeSecondCreation, beforeDeletion, true),
					EMPTY_ARRAY, EMPTY_ARRAY, EMPTY_ARRAY);

			// Assert report contains no deleted concepts on MAIN after the deletion
			assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN", afterDeletion, now(), true),
					EMPTY_ARRAY, EMPTY_ARRAY, EMPTY_ARRAY);
		} catch (ElasticsearchParseException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testDescriptionUpdateOnSameBranchInChangeReport() throws Exception {
		final String path = "MAIN";
		createConcept("200", path);
		createConcept("300", path);

		Date start = now();

		// Nothing changed since start
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), true),
				EMPTY_ARRAY, EMPTY_ARRAY, EMPTY_ARRAY);

		final Concept concept = conceptService.find("100", path);
		concept.getDescriptions().iterator().next().setCaseSignificanceId(Concepts.ENTIRE_TERM_CASE_SENSITIVE);
		conceptService.update(concept, path);

		// Concept updated from description change
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), true),
				EMPTY_ARRAY, new Long[] {100L}, EMPTY_ARRAY);
	}

	@Test
	public void testDescriptionUpdateOnChildBranchInChangeReport() throws Exception {
		final String path = "MAIN/A";
		createConcept("200", path);
		createConcept("300", path);

		Date start = now();

		// Nothing changed since start
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), false),
				EMPTY_ARRAY, EMPTY_ARRAY, EMPTY_ARRAY);

		final Concept concept = conceptService.find("100", path);
		final Description description = concept.getDescriptions().iterator().next();
		description.setCaseSignificanceId(Concepts.ENTIRE_TERM_CASE_SENSITIVE);
		conceptService.update(concept, path);

		// Concept updated from description change
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), false),
				EMPTY_ARRAY, new Long[] {100L}, EMPTY_ARRAY);
	}

	@Test
	public void testLangRefsetUpdateOnChildBranchInChangeReport() throws Exception {
		final String path = "MAIN/A";

		Date start = now();

		// Nothing changed since start
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), false),
				EMPTY_ARRAY, EMPTY_ARRAY, EMPTY_ARRAY);

		final Concept concept = conceptService.find("100", path);
		final Description description = concept.getDescriptions().iterator().next();
		description.clearLanguageRefsetMembers();
		description.setAcceptabilityMap(Collections.singletonMap(Concepts.US_EN_LANG_REFSET,
				Concepts.descriptionAcceptabilityNames.get(Concepts.PREFERRED)));
		conceptService.update(concept, path);

		// Concept updated from lang refset change
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), false),
				EMPTY_ARRAY, new Long[] {100L}, EMPTY_ARRAY);
	}

	@Test
	public void testLangRefsetDeletionOnChildBranchInChangeReport() throws Exception {
		final String path = "MAIN/A";

		Date start = now();

		// Nothing changed since start
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), false),
				EMPTY_ARRAY, EMPTY_ARRAY, EMPTY_ARRAY);

		final Concept concept = conceptService.find("100", path);
		final Description description = concept.getDescriptions().iterator().next();
		description.clearLanguageRefsetMembers();
		description.setAcceptabilityMap(new HashMap<>());
		conceptService.update(concept, path);

		// Concept updated from lang refset change
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), false),
				EMPTY_ARRAY, new Long[] {100L}, EMPTY_ARRAY);
	}

	@Test
	public void testLangRefsetDeletionOnBranchInChangeReport() throws Exception {
		final String path = "MAIN";

		Date start = now();

		// Nothing changed since start
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), true),
				EMPTY_ARRAY, EMPTY_ARRAY, EMPTY_ARRAY);

		final Concept concept = conceptService.find("100", path);
		final Description next = concept.getDescriptions().iterator().next();
		next.clearLanguageRefsetMembers();
		next.setAcceptabilityMap(new HashMap<>());
		conceptService.update(concept, path);

		// Concept updated from lang refset change
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), true),
				EMPTY_ARRAY, new Long[] {100L}, EMPTY_ARRAY);
	}

	private Date now() {
		return new Date();
	}

	private void assertReportEquals(BranchReviewConceptChanges report, Long[] conceptsCreated, Long[] conceptsUpdated, Long[] conceptsDeleted) {
		Assert.assertArrayEquals("Concepts Created", conceptsCreated, report.getNewConcepts().toArray());
		Assert.assertArrayEquals("Concepts Updated", conceptsUpdated, report.getChangedConcepts().toArray());
		Assert.assertArrayEquals("Concepts Deleted", conceptsDeleted, report.getDeletedConcepts().toArray());
	}

	private void createConcept(String conceptId, String path) throws ServiceException {
		conceptService.create(
				new Concept(conceptId)
						.addDescription(
								new Description("Heart")
										.setAcceptabilityMap(Collections.singletonMap(Concepts.US_EN_LANG_REFSET,
												Concepts.descriptionAcceptabilityNames.get(Concepts.ACCEPTABLE)))
						)
						.addRelationship(
								new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)
						),
				path);
	}

}
