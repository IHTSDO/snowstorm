package org.ihtsdo.elasticsnomed.ecl;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.TestConfig;
import org.ihtsdo.elasticsnomed.core.data.domain.Concept;
import org.ihtsdo.elasticsnomed.core.data.domain.Concepts;
import org.ihtsdo.elasticsnomed.core.data.domain.Relationship;
import org.ihtsdo.elasticsnomed.core.data.services.ConceptService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class ECLQueryServiceTest {

	@Autowired
	private ECLQueryService eclQueryService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	private QueryBuilder branchCriteria;
	private static final String MAIN = "MAIN";

	@Before
	public void setup() {
		branchService.create(MAIN);

		List<Concept> concepts = new ArrayList<>();

		concepts.add(new Concept(Concepts.SNOMEDCT_ROOT));
		concepts.add(new Concept(Concepts.CLINICAL_FINDING).addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)));

		conceptService.create(concepts, MAIN);

		branchCriteria = versionControlHelper.getBranchCriteria(MAIN);
	}

	@Test
	public void selectByDescendantAndAncestorOperators() throws Exception {
		boolean stated = true;
		assertEquals(
				Sets.newHashSet(Concepts.SNOMEDCT_ROOT),
				strings(eclQueryService.selectConceptIds(Concepts.SNOMEDCT_ROOT, branchCriteria, MAIN, stated)));

		assertEquals(
				Sets.newHashSet(Concepts.CLINICAL_FINDING),
				strings(eclQueryService.selectConceptIds("<" + Concepts.SNOMEDCT_ROOT, branchCriteria, MAIN, stated)));

		assertEquals(
				Sets.newHashSet(Concepts.SNOMEDCT_ROOT, Concepts.CLINICAL_FINDING),
				strings(eclQueryService.selectConceptIds("<<" + Concepts.SNOMEDCT_ROOT, branchCriteria, MAIN, stated)));

		assertEquals(
				Sets.newHashSet(Concepts.SNOMEDCT_ROOT),
				strings(eclQueryService.selectConceptIds(">" + Concepts.CLINICAL_FINDING, branchCriteria, MAIN, stated)));

		assertEquals(
				Sets.newHashSet(Concepts.SNOMEDCT_ROOT, Concepts.CLINICAL_FINDING),
				strings(eclQueryService.selectConceptIds(">>" + Concepts.CLINICAL_FINDING, branchCriteria, MAIN, stated)));
	}

	private Set<String> strings(Collection<Long> ids) {
		return ids.stream().map(Object::toString).collect(Collectors.toSet());
	}

	@After
	public void tearDown() {
		conceptService.deleteAll();
		branchService.deleteAll();
	}

}
