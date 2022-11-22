package org.snomed.snowstorm.ecl;

import com.google.common.collect.Sets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collection;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.snomed.snowstorm.TestConcepts.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.CLINICAL_FINDING;
import static org.snomed.snowstorm.core.data.domain.Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN;

/**
 * In this test suite we run all the same ECL query tests again against the stated form
 * but the data is set up using axioms without any stated relationships.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ECLQueryServiceStatedAxiomTestConfig.class)
class ECLQueryServiceStatedAxiomTest extends AbstractECLQueryServiceTest {

	@BeforeEach
	void setup() {
		branchCriteria = versionControlHelper.getBranchCriteria(MAIN);
		allConceptIds = eclQueryService.selectConceptIds("*", branchCriteria, true, PageRequest.of(0, 1000))
				.getContent().stream().map(Object::toString).collect(Collectors.toSet());
	}

	@Test
	// In axioms all non is-a attributes in group 0 become self grouped unless the MRCM Attribute Domain reference set explicitly states that they should never be grouped
	void attributeGroupCardinality() {
		assertEquals(
				"Match clinical finding with at least one grouped finding site attributes.",
				Sets.newHashSet(PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING, BLEEDING_SKIN),
				strings(selectConceptIds("<404684003 |Clinical finding|: { 363698007 |Finding site| = * }")));

		assertEquals(
				"Match clinical finding with zero grouped finding site attributes.",
				Sets.newHashSet(DISORDER, BLEEDING),
				strings(selectConceptIds("<404684003 |Clinical finding|: [0..0]{ 363698007 |Finding site| = * }")));

		assertEquals(
				"Match clinical finding with one grouped finding site attributes.",
				Sets.newHashSet(BLEEDING_SKIN),
				strings(selectConceptIds("<404684003 |Clinical finding|: [1..1]{ 363698007 |Finding site| = * }")));

		assertEquals(
				"Match clinical finding with one or two grouped finding site attributes.",
				Sets.newHashSet(PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING, BLEEDING_SKIN),
				strings(selectConceptIds("<404684003 |Clinical finding|: [1..2]{ 363698007 |Finding site| = * }")));

		assertEquals(
				"Match clinical finding with three or more grouped finding site attributes.",
				Sets.newHashSet(),
				strings(selectConceptIds("<404684003 |Clinical finding|: [3..*]{ 363698007 |Finding site| = * }")));
	}
	
	@Test
	void attributeGroupDisjunction() {
		//This test has been overridden from the base class because the serialisation of axioms into the axiom reference
		//set causes attributes to be grouped where this is required by the MRCM.
		//As such, Bleeding and Bleeding skin - which are created with ungrouped attributes above, will
		//match this ECL as those attributes become grouped when expressed in axioms.
		assertEquals(
				"Match clinical finding with at least one grouped finding site attributes.",
				Sets.newHashSet(PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING, BLEEDING, BLEEDING_SKIN),
				strings(selectConceptIds("<404684003 |Clinical finding|: { 363698007 |Finding site| = * } OR { 116676008 |Associated morphology| = * }")));
	}

	@Test
	void selectMemberOfReferenceSet() {
		// Member of x
		assertEquals(
				Sets.newHashSet(CLINICAL_FINDING, BODY_STRUCTURE),
				strings(selectConceptIds("^" + REFSET_MRCM_ATTRIBUTE_DOMAIN))
		);

		// Member of any reference set
		// All concepts with axioms are members
		assertEquals(allConceptIds.stream().filter(id -> !id.equals(Concepts.SNOMEDCT_ROOT)).collect(Collectors.toSet()), strings(selectConceptIds("^*")));
	}

	protected Collection<Long> selectConceptIds(String ecl) {
		return selectConceptIds(ecl, null);
	}

	protected Collection<Long> selectConceptIds(String ecl, PageRequest pageRequest) {
		boolean stated = true;
		return eclQueryService.selectConceptIds(ecl, branchCriteria, stated, pageRequest).getContent();
	}

}
