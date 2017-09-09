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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.ihtsdo.elasticsnomed.core.data.domain.Concepts.CLINICAL_FINDING;
import static org.ihtsdo.elasticsnomed.core.data.domain.Concepts.SNOMEDCT_ROOT;
import static org.junit.Assert.assertEquals;

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
	private static final String BLEEDING = "131148009";
	private static final String ASSOCIATED_MORPHOLOGY = "116676008";
	private static final String HEMORRHAGE = "50960005";
	private static final String DISEASE = "64572001";
	private static final String NON_EXISTENT_CONCEPT = "12345001";

	@Before
	public void setup() {
		branchService.create(MAIN);

		List<Concept> concepts = new ArrayList<>();

		concepts.add(new Concept(SNOMEDCT_ROOT));
		concepts.add(new Concept(CLINICAL_FINDING).addRelationship(new Relationship(Concepts.ISA, SNOMEDCT_ROOT)));
		concepts.add(new Concept(BLEEDING)
				.addRelationship(new Relationship(Concepts.ISA, CLINICAL_FINDING))
				.addRelationship(new Relationship(ASSOCIATED_MORPHOLOGY, HEMORRHAGE))
		);
		concepts.add(new Concept(DISEASE).addRelationship(new Relationship(Concepts.ISA, CLINICAL_FINDING)));
		concepts.add(new Concept(ASSOCIATED_MORPHOLOGY).addRelationship(new Relationship(Concepts.ISA, SNOMEDCT_ROOT)));
		concepts.add(new Concept(HEMORRHAGE).addRelationship(new Relationship(Concepts.ISA, SNOMEDCT_ROOT)));

		conceptService.create(concepts, MAIN);

		branchCriteria = versionControlHelper.getBranchCriteria(MAIN);
	}

	@Test
	public void selectByDescendantAndAncestorOperators() throws Exception {
		boolean stated = true;
		assertEquals(
				Sets.newHashSet(SNOMEDCT_ROOT),
				strings(eclQueryService.selectConceptIds(SNOMEDCT_ROOT, branchCriteria, MAIN, stated)));

		assertEquals(
				Sets.newHashSet(CLINICAL_FINDING, BLEEDING, DISEASE, ASSOCIATED_MORPHOLOGY, HEMORRHAGE),
				strings(eclQueryService.selectConceptIds("<" + SNOMEDCT_ROOT, branchCriteria, MAIN, stated)));

		assertEquals(
				Sets.newHashSet(SNOMEDCT_ROOT, CLINICAL_FINDING, BLEEDING, DISEASE, ASSOCIATED_MORPHOLOGY, HEMORRHAGE),
				strings(eclQueryService.selectConceptIds("<<" + SNOMEDCT_ROOT, branchCriteria, MAIN, stated)));

		assertEquals(
				Sets.newHashSet(SNOMEDCT_ROOT),
				strings(eclQueryService.selectConceptIds(">" + CLINICAL_FINDING, branchCriteria, MAIN, stated)));

		assertEquals(
				Sets.newHashSet(SNOMEDCT_ROOT, CLINICAL_FINDING),
				strings(eclQueryService.selectConceptIds(">>" + CLINICAL_FINDING, branchCriteria, MAIN, stated)));

		assertEquals(
				Sets.newHashSet(SNOMEDCT_ROOT, CLINICAL_FINDING, BLEEDING),
				strings(eclQueryService.selectConceptIds(">>" + BLEEDING, branchCriteria, MAIN, stated)));
	}

	@Test
	public void selectParents() throws Exception {
		boolean stated = true;
		assertEquals(
				Sets.newHashSet(),
				strings(eclQueryService.selectConceptIds(">!" + SNOMEDCT_ROOT, branchCriteria, MAIN, stated)));

		assertEquals(
				Sets.newHashSet(SNOMEDCT_ROOT),
				strings(eclQueryService.selectConceptIds(">!" + CLINICAL_FINDING, branchCriteria, MAIN, stated)));

		assertEquals(
				Sets.newHashSet(CLINICAL_FINDING),
				strings(eclQueryService.selectConceptIds(">!" + BLEEDING, branchCriteria, MAIN, stated)));
	}

	@Test
	public void selectChildren() throws Exception {
		boolean stated = true;
		assertEquals(
				Sets.newHashSet(CLINICAL_FINDING, ASSOCIATED_MORPHOLOGY, HEMORRHAGE),
				strings(eclQueryService.selectConceptIds("<!" + SNOMEDCT_ROOT, branchCriteria, MAIN, stated)));

		assertEquals(
				Sets.newHashSet(BLEEDING, DISEASE),
				strings(eclQueryService.selectConceptIds("<!" + CLINICAL_FINDING, branchCriteria, MAIN, stated)));

		assertEquals(
				Sets.newHashSet(),
				strings(eclQueryService.selectConceptIds("<!" + BLEEDING, branchCriteria, MAIN, stated)));
	}

	@Test
	public void selectByAttributeType() throws Exception {
		boolean stated = true;
		assertEquals(
				Sets.newHashSet(),
				strings(eclQueryService.selectConceptIds("*:" + CLINICAL_FINDING + "=*", branchCriteria, MAIN, stated)));

		assertEquals(
				Sets.newHashSet(BLEEDING),
				strings(eclQueryService.selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "=*", branchCriteria, MAIN, stated)));

		assertEquals(
				Sets.newHashSet(BLEEDING),
				strings(eclQueryService.selectConceptIds(BLEEDING +":" + ASSOCIATED_MORPHOLOGY + "=*", branchCriteria, MAIN, stated)));

		assertEquals(
				Sets.newHashSet(),
				strings(eclQueryService.selectConceptIds(BLEEDING +":" + NON_EXISTENT_CONCEPT + "=*", branchCriteria, MAIN, stated)));
	}

	@Test
	public void selectByAttributeValue() throws Exception {
		boolean stated = true;
		assertEquals(
				Sets.newHashSet(BLEEDING),
				strings(eclQueryService.selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "=" + HEMORRHAGE, branchCriteria, MAIN, stated)));

		assertEquals(
				Sets.newHashSet(),
				strings(eclQueryService.selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "!=" + HEMORRHAGE, branchCriteria, MAIN, stated)));

		assertEquals(
				Sets.newHashSet(BLEEDING),
				strings(eclQueryService.selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "=<<" + SNOMEDCT_ROOT, branchCriteria, MAIN, stated)));

		assertEquals(
				Sets.newHashSet(),
				strings(eclQueryService.selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "=" + CLINICAL_FINDING, branchCriteria, MAIN, stated)));

		assertEquals(
				Sets.newHashSet(),
				strings(eclQueryService.selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "=" + NON_EXISTENT_CONCEPT, branchCriteria, MAIN, stated)));
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
