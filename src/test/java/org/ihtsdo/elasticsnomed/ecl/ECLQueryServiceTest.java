package org.ihtsdo.elasticsnomed.ecl;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.TestConfig;
import org.ihtsdo.elasticsnomed.core.data.domain.Concept;
import org.ihtsdo.elasticsnomed.core.data.domain.Concepts;
import org.ihtsdo.elasticsnomed.core.data.domain.QueryConcept;
import org.ihtsdo.elasticsnomed.core.data.domain.Relationship;
import org.ihtsdo.elasticsnomed.core.data.services.ConceptService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.ihtsdo.elasticsnomed.core.data.domain.Concepts.CLINICAL_FINDING;
import static org.ihtsdo.elasticsnomed.core.data.domain.Concepts.SNOMEDCT_ROOT;
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

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	private QueryBuilder branchCriteria;
	private static final String MAIN = "MAIN";
	private static final String BLEEDING = "131148009";
	private static final String ASSOCIATED_MORPHOLOGY = "116676008";
	private static final String HEMORRHAGE = "50960005";
	private static final String DISEASE = "64572001";

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
				Sets.newHashSet(CLINICAL_FINDING, BLEEDING, DISEASE, ASSOCIATED_MORPHOLOGY),
				strings(eclQueryService.selectConceptIds("<" + SNOMEDCT_ROOT, branchCriteria, MAIN, stated)));

		assertEquals(
				Sets.newHashSet(SNOMEDCT_ROOT, CLINICAL_FINDING, BLEEDING, DISEASE, ASSOCIATED_MORPHOLOGY),
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
				Sets.newHashSet(CLINICAL_FINDING, ASSOCIATED_MORPHOLOGY),
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

		List<QueryConcept> queryConcepts = elasticsearchOperations.queryForList(new NativeSearchQueryBuilder()
				.withQuery(boolQuery().must(termQuery("attr." + ASSOCIATED_MORPHOLOGY, HEMORRHAGE))).build(), QueryConcept.class);

		assertEquals(1, queryConcepts.size());
		assertEquals(BLEEDING, queryConcepts.get(0).getConceptId().toString());

		List<QueryConcept> queryConcepts2 = elasticsearchOperations.queryForList(new NativeSearchQueryBuilder()
				.withQuery(boolQuery().must(existsQuery("attr." + ASSOCIATED_MORPHOLOGY))).build(), QueryConcept.class);

		assertEquals(1, queryConcepts2.size());
		assertEquals(BLEEDING, queryConcepts2.get(0).getConceptId().toString());

		boolean stated = true;
		assertEquals(
				Sets.newHashSet(),
				strings(eclQueryService.selectConceptIds("*:" + CLINICAL_FINDING + "=*", branchCriteria, MAIN, stated)));

		assertEquals(
				Sets.newHashSet(BLEEDING),
				strings(eclQueryService.selectConceptIds("*:" + ASSOCIATED_MORPHOLOGY + "=*", branchCriteria, MAIN, stated)));

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
