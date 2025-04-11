package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Lists;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Commit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.snomed.snowstorm.rest.pojo.SearchAfterPageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static java.lang.Long.parseLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class QueryServiceTest extends AbstractTest {

	@Autowired
	private QueryService service;

	@Autowired
	private ConceptService conceptService;
	
	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	private static final PageRequest PAGE_REQUEST = PageRequest.of(0, 50);
	public static final String PATH = "MAIN";
	public static final int TEST_ET = 20210131;
	private static final String MODEL_COMPONENT = "900000000000441003";
	private static final String BODY_STRUCTURE = "123037004";
	private static final String LATERALITY = "272741003";
	private static final String RIGHT = "24028007";
	private static final String PULMONARY_VALVE_STRUCTURE = "39057004";
	private static final String DISORDER = "64572001";
	private static final String PENTALOGY_OF_FALLOT = "204306007";
	private static final String ASSOCIATED_MORPHOLOGY = "116676008";
	private static final String STENOSIS = "415582006";
	private static final String HYPERTROPHY = "56246009";
	private static final String RIGHT_VENTRICULAR_STRUCTURE = "53085002";

	private Concept root;
	private Concept pizza_2;
	private Concept cheesePizza_3;
	private Concept reallyCheesyPizza_4;
	private Concept reallyCheesyPizza_5;
	private Concept inactivePizza_6;



	@BeforeEach
	void setup() throws ServiceException {
		root = new Concept(SNOMEDCT_ROOT).setModuleId(MODEL_MODULE);
		pizza_2 = new Concept("100002").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)).addFSN("Pizza");
		cheesePizza_3 = new Concept("100005").addRelationship(new Relationship(ISA, pizza_2.getId())).addFSN("Cheese Pizza");
		reallyCheesyPizza_4 = new Concept("100008").addRelationship(new Relationship(ISA, cheesePizza_3.getId())).addFSN("Really Cheesy Pizza");
		reallyCheesyPizza_5 = new Concept("100003").addRelationship(new Relationship(ISA, reallyCheesyPizza_4.getId())).addFSN("So Cheesy Pizza")
				.addDescription(new Description("Cheesy Pizza"));
		inactivePizza_6 = new Concept("100006")
				.addRelationship(new Relationship(ISA, reallyCheesyPizza_4.getId()).setActive(false))
				.addFSN("Inactive Pizza")
				.addDescription(new Description("additional pizza"))
				.setActive(false);
		conceptService.batchCreate(Lists.newArrayList(root, pizza_2, cheesePizza_3, reallyCheesyPizza_4, reallyCheesyPizza_5, inactivePizza_6), PATH);
		CodeSystem codeSystem = new CodeSystem("SNOMEDCT-TEST", PATH);
		codeSystemService.createCodeSystem(codeSystem);
		codeSystemService.createVersion(codeSystem, TEST_ET, "Unit Test Version");
		
	}

	@Test
	void testSearchResultOrdering() {
		List<ConceptMini> matches = service.search(service.createQueryBuilder(false).activeFilter(true).descriptionTerm("Piz"), PATH, PAGE_REQUEST).getContent();
		assertEquals(4, matches.size());
		assertEquals("Pizza", matches.get(0).getFsnTerm());
		assertEquals("Cheese Pizza", matches.get(1).getFsnTerm());
		assertEquals("So Cheesy Pizza", matches.get(2).getFsnTerm());
		assertEquals("Really Cheesy Pizza", matches.get(3).getFsnTerm());

		matches = service.search(service.createQueryBuilder(false).ecl("<" + SNOMEDCT_ROOT).descriptionTerm("Piz"), PATH, PAGE_REQUEST).getContent();
		assertEquals(4, matches.size());
		assertEquals("Pizza", matches.get(0).getFsnTerm());
		assertEquals("Cheese Pizza", matches.get(1).getFsnTerm());
		assertEquals("So Cheesy Pizza", matches.get(2).getFsnTerm());
		assertEquals("Really Cheesy Pizza", matches.get(3).getFsnTerm());

		matches = service.search(service.createQueryBuilder(false).ecl("<" + pizza_2.getConceptId()).descriptionTerm("Piz"), PATH, PAGE_REQUEST).getContent();
		assertEquals(3, matches.size());
		assertEquals("Cheese Pizza", matches.get(0).getFsnTerm());
		assertEquals("So Cheesy Pizza", matches.get(1).getFsnTerm());
		assertEquals("Really Cheesy Pizza", matches.get(2).getFsnTerm());

		matches = service.search(service.createQueryBuilder(false).ecl("<" + pizza_2.getConceptId()).descriptionTerm("Cheesy"), PATH, PAGE_REQUEST).getContent();
		assertEquals(2, matches.size());
		assertEquals("So Cheesy Pizza", matches.get(0).getFsnTerm());
		assertEquals("Really Cheesy Pizza", matches.get(1).getFsnTerm());
	}

	@Test
	void testFindInactiveConcept() {
		Set<String> inactiveConceptId = Collections.singleton(inactivePizza_6.getId());
		List<ConceptMini> content = service.search(service.createQueryBuilder(false).conceptIds(inactiveConceptId), PATH, PAGE_REQUEST).getContent();
		assertEquals(1, content.size());
		assertEquals("Inactive Pizza", content.get(0).getFsnTerm());

		assertEquals(1, service.search(service.createQueryBuilder(false).descriptionTerm("Inacti").definitionStatusFilter(Concepts.PRIMITIVE).conceptIds(inactiveConceptId), PATH, PAGE_REQUEST).getContent().size());
		assertEquals(0, service.search(service.createQueryBuilder(false).descriptionTerm("Not").definitionStatusFilter(Concepts.PRIMITIVE).conceptIds(inactiveConceptId), PATH, PAGE_REQUEST).getContent().size());
		assertEquals(0, service.search(service.createQueryBuilder(false).definitionStatusFilter(Concepts.FULLY_DEFINED).conceptIds(inactiveConceptId), PATH, PAGE_REQUEST).getContent().size());
	}


	@Test
	void testFindConceptsByTerm() {

		Page<ConceptMini> activeSearch = service.search(service.createQueryBuilder(false).descriptionTerm("pizza").activeFilter(true), PATH, PAGE_REQUEST);
		assertEquals(4, activeSearch.getNumberOfElements());

		Page<ConceptMini> inactiveSearch = service.search(service.createQueryBuilder(false).descriptionTerm("pizza").activeFilter(false), PATH, PAGE_REQUEST);
		assertEquals(1, inactiveSearch.getNumberOfElements());

		Page<ConceptMini> page = service.search(service.createQueryBuilder(false).descriptionTerm("pizza"), PATH, PAGE_REQUEST);
		assertEquals(5, page.getNumberOfElements());
	}

	@Test
	void testFindConceptsByTermUsingConceptId() {
		Page<ConceptMini> activeSearch = service.search(service.createQueryBuilder(false).descriptionTerm("100003").activeFilter(true), PATH, PAGE_REQUEST);
		assertEquals(1, activeSearch.getNumberOfElements());
	}

	@Test
	void testDefinitionStatusFilter() {
		QueryService.ConceptQueryBuilder query = service.createQueryBuilder(false)
				.ecl(pizza_2.getConceptId())
				.definitionStatusFilter(Concepts.DEFINED);
		assertEquals(0, service.search(query, PATH, PAGE_REQUEST).getTotalElements());
		QueryService.ConceptQueryBuilder query2 = service.createQueryBuilder(false)
				.ecl(pizza_2.getConceptId())
				.definitionStatusFilter(Concepts.PRIMITIVE);
		assertEquals(1, service.search(query2, PATH, PAGE_REQUEST).getTotalElements());
	}
	
	@Test
	void testEffectiveTimeFilter() {
		QueryService.ConceptQueryBuilder query = service.createQueryBuilder(false)
				.ecl(pizza_2.getConceptId())
				.effectiveTime(20210131);
		assertEquals(1, service.search(query, PATH, PAGE_REQUEST).getTotalElements());
		QueryService.ConceptQueryBuilder query2 = service.createQueryBuilder(false)
				.ecl(pizza_2.getConceptId())
				.effectiveTime(10661014);
		assertEquals(0, service.search(query2, PATH, PAGE_REQUEST).getTotalElements());
		QueryService.ConceptQueryBuilder query3 = service.createQueryBuilder(false)
				.effectiveTime(20210131);
		assertEquals(6, service.search(query3, PATH, PAGE_REQUEST).getTotalElements());

	}
	
	@Test
	void testNullEffectiveTimeFilter() {
		QueryService.ConceptQueryBuilder query = service.createQueryBuilder(false)
				.ecl(pizza_2.getConceptId())
				.isNullEffectiveTime(true);
		assertEquals(0, service.search(query, PATH, PAGE_REQUEST).getTotalElements());
		QueryService.ConceptQueryBuilder query2 = service.createQueryBuilder(false)
				.ecl(pizza_2.getConceptId())
				.isNullEffectiveTime(false);
		assertEquals(1, service.search(query2, PATH, PAGE_REQUEST).getTotalElements());
	}
	
	@Test
	void testIsPublishedFilter() {
		//We've versioned this content so there will be no un-released content
		QueryService.ConceptQueryBuilder query = service.createQueryBuilder(false)
				.ecl(pizza_2.getConceptId())
				.isReleased(true);
		assertEquals(1, service.search(query, PATH, PAGE_REQUEST).getTotalElements());
		QueryService.ConceptQueryBuilder query2 = service.createQueryBuilder(false)
				.ecl(pizza_2.getConceptId())
				.isReleased(false);
		assertEquals(0, service.search(query2, PATH, PAGE_REQUEST).getTotalElements());
	}
	
	@Test
	void testFilterComboWithoutECL() {
		//We've versioned this content so there will be no un-released content
		QueryService.ConceptQueryBuilder query = service.createQueryBuilder(false)
				.effectiveTime(TEST_ET)
				.isReleased(true)
				.isNullEffectiveTime(false);
		assertEquals(6, service.search(query, PATH, PAGE_REQUEST).getTotalElements());
		QueryService.ConceptQueryBuilder query2 = service.createQueryBuilder(false)
				.effectiveTime(TEST_ET)
				.isReleased(true)
				.isNullEffectiveTime(true); //Contradiction shoudl force no results
 		assertEquals(0, service.search(query2, PATH, PAGE_REQUEST).getTotalElements());
	}

	@Test
	void testPagination() {
		QueryService.ConceptQueryBuilder queryBuilder = service.createQueryBuilder(false).activeFilter(true);
		Page<ConceptMini> page = service.search(queryBuilder, PATH, PageRequest.of(0, 2));
		assertEquals(5, page.getTotalElements());
	}

	@Test
	void testModuleFilter() {
		QueryService.ConceptQueryBuilder queryBuilder = service.createQueryBuilder(false).activeFilter(true);
		assertEquals(5, service.search(queryBuilder, PATH, PAGE_REQUEST).getTotalElements());

		queryBuilder.module(parseLong(MODEL_MODULE));
		assertEquals(1, service.search(queryBuilder, PATH, PAGE_REQUEST).getTotalElements());

		QueryService.ConceptQueryBuilder eclQueryBuilder = service.createQueryBuilder(false).ecl("<<" + SNOMEDCT_ROOT);
		assertEquals(5, service.search(eclQueryBuilder, PATH, PAGE_REQUEST).getTotalElements());

		eclQueryBuilder.module(parseLong(MODEL_MODULE));
		assertEquals(1, service.search(eclQueryBuilder, PATH, PAGE_REQUEST).getTotalElements());

		eclQueryBuilder.module(parseLong(CORE_MODULE));
		assertEquals(4, service.search(eclQueryBuilder, PATH, PAGE_REQUEST).getTotalElements());
	}

	@Test
	void testDotNotationEclQuery() throws ServiceException {
		createConcepts();

		QueryService.ConceptQueryBuilder queryBuilder = service.createQueryBuilder(false).activeFilter(true);
		// Dot notation
		String ecl = "( *: 116676008 |Associated morphology (attribute)|= 415582006 |Stenosis (morphologic abnormality)|). 363698007 |Finding site (attribute)|";
		queryBuilder.ecl(ecl);
		List<ConceptMini> results = service.search(queryBuilder, PATH, PAGE_REQUEST).getContent();
		assertEquals(2, results.size());

		// Add term filtering
		queryBuilder.descriptionTerm("structure");

		results = service.search(queryBuilder, PATH, PAGE_REQUEST).getContent();
		assertEquals(2, results.size());

		// Reverse flag
		ecl = "* : R 363698007 |Finding site (attribute)| = (* : 116676008 |Associated morphology (attribute)| = 415582006 |Stenosis (morphologic abnormality)|)";
		queryBuilder.ecl(ecl);
		results = service.search(queryBuilder, PATH, PAGE_REQUEST).getContent();
		assertEquals(2, results.size());
	}

	@Test
	void testDotNotationEclQueryWithSearchAfter() throws ServiceException {
		createConcepts();

		QueryService.ConceptQueryBuilder queryBuilder = service.createQueryBuilder(false).activeFilter(true);
		String ecl = "( *: 116676008 |Associated morphology (attribute)|= 415582006 |Stenosis (morphologic abnormality)|). 363698007 |Finding site (attribute)|";
		queryBuilder.ecl(ecl);

		Sort sort = Sort.sort(QueryConcept.class).by(QueryConcept::getConceptIdL).descending();

		ItemsPage<ConceptMini> resultsPageOne = new ItemsPage<>(service.search(queryBuilder, PATH, SearchAfterPageRequest.of(0, 1, sort)));
		assertEquals(1, resultsPageOne.getItems().size());
		ConceptMini conceptPageOne = resultsPageOne.getItems().stream().toList().get(0);
		assertEquals(RIGHT_VENTRICULAR_STRUCTURE, conceptPageOne.getConceptId());

		ItemsPage<ConceptMini> resultsPageTwo = new ItemsPage<>(service.search(queryBuilder, PATH, SearchAfterPageRequest.of(resultsPageOne.getSearchAfterArray(), 1, sort)));
		assertEquals(1, resultsPageTwo.getItems().size());
		ConceptMini conceptPageTwo = resultsPageTwo.getItems().stream().toList().get(0);
		assertEquals(PULMONARY_VALVE_STRUCTURE, conceptPageTwo.getConceptId());

		assertNotEquals(resultsPageOne.getSearchAfter(), resultsPageTwo.getSearchAfter());
	}

	@Test
	void testNestedMemberOfQueries()throws ServiceException {
		createConcepts();
		createMembers(MAIN, REFSET_SIMPLE, List.of(STENOSIS, HYPERTROPHY));
		QueryService.ConceptQueryBuilder queryBuilder = service.createQueryBuilder(false).activeFilter(true);
		String ecl = "^ 446609009";
		queryBuilder.ecl(ecl);
		List<ConceptMini> results = service.search(queryBuilder, PATH, PAGE_REQUEST).getContent();
		assertEquals(2, results.size());

		ecl = "(^ 446609009)";
		queryBuilder.ecl(ecl);
		results = service.search(queryBuilder, PATH, PAGE_REQUEST).getContent();
		assertEquals(2, results.size());

		ecl = "^(<<900000000000455006)";
		queryBuilder.ecl(ecl);
		results = service.search(queryBuilder, PATH, PAGE_REQUEST).getContent();
		assertEquals(2, results.size());
	}

	@Test
	void testRefinedExpressionConstraintWithMemberOf()throws ServiceException {
		createConcepts();
		createMembers(MAIN, REFSET_SIMPLE, List.of(STENOSIS, HYPERTROPHY));
		QueryService.ConceptQueryBuilder queryBuilder = service.createQueryBuilder(false).activeFilter(true);
		String ecl = "^446609009:116676008 |Associated morphology (attribute)|=*";
		queryBuilder.ecl(ecl);
		List<ConceptMini> results = service.search(queryBuilder, PATH, PAGE_REQUEST).getContent();
		assertEquals(0, results.size());

		createMembers(MAIN, REFSET_SIMPLE, List.of(PENDING_MOVE, PENTALOGY_OF_FALLOT));
		results = service.search(queryBuilder, PATH, PAGE_REQUEST).getContent();
		assertEquals(1, results.size());
		assertEquals(PENTALOGY_OF_FALLOT, results.get(0).getConceptId());
	}


	@Test
	void testDotNotationEclWithMemberOf() throws ServiceException {
		createConcepts();
		createMembers(MAIN, REFSET_SIMPLE, List.of(STENOSIS, HYPERTROPHY));
		QueryService.ConceptQueryBuilder queryBuilder = service.createQueryBuilder(false).activeFilter(true);
		String ecl = "( *: 116676008 |Associated morphology (attribute)|= ^446609009).363698007|Finding site (attribute)|";
		queryBuilder.ecl(ecl);
		List<ConceptMini> results = service.search(queryBuilder, PATH, PAGE_REQUEST).getContent();
		assertEquals(2, results.size());
		assertEquals(RIGHT_VENTRICULAR_STRUCTURE, results.get(0).getConceptId());
		assertEquals(PULMONARY_VALVE_STRUCTURE, results.get(1).getConceptId());

		createMembers(MAIN, REFSET_SIMPLE, List.of(PENDING_MOVE, PENTALOGY_OF_FALLOT));
		ecl = "^446609009.116676008 |Associated morphology (attribute)|";
		queryBuilder.ecl(ecl);
		results = service.search(queryBuilder, PATH, PAGE_REQUEST).getContent();
		assertEquals(2, results.size());
		assertEquals(STENOSIS, results.get(0).getConceptId());
		assertEquals(HYPERTROPHY, results.get(1).getConceptId());
	}

	@Test
	void testDotNotationEclWithMemberOfForStatedView() throws ServiceException {
		createConcepts();
		createMembers(MAIN, REFSET_SIMPLE, List.of(STENOSIS, HYPERTROPHY));
		QueryService.ConceptQueryBuilder queryBuilder = service.createQueryBuilder(true).activeFilter(true);
		createMembers(MAIN, REFSET_SIMPLE, List.of(PENDING_MOVE, PENTALOGY_OF_FALLOT));
		String ecl = "^446609009.116676008 |Associated morphology (attribute)|";
		queryBuilder.ecl(ecl);
		List<ConceptMini> results = service.search(queryBuilder, PATH, PAGE_REQUEST).getContent();
		assertEquals(0, results.size());
	}

	private void createConcepts() throws ServiceException {
		List<Concept> allConcepts = new ArrayList<>();
		allConcepts.add(new Concept(ISA).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(REFSET_SIMPLE).addRelationship(new Relationship(ISA, REFSET)));
		allConcepts.add(new Concept(REFSET).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(MODEL_COMPONENT).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(CONCEPT_MODEL_ATTRIBUTE).addRelationship(new Relationship(ISA, MODEL_COMPONENT)));
		allConcepts.add(new Concept(CONCEPT_MODEL_OBJECT_ATTRIBUTE).addRelationship(new Relationship(ISA, CONCEPT_MODEL_ATTRIBUTE)));
		allConcepts.add(new Concept(FINDING_SITE).addRelationship(new Relationship(ISA, CONCEPT_MODEL_OBJECT_ATTRIBUTE)));
		allConcepts.add(new Concept(ASSOCIATED_MORPHOLOGY).addRelationship(new Relationship(ISA, CONCEPT_MODEL_OBJECT_ATTRIBUTE)));
		allConcepts.add(new Concept(BODY_STRUCTURE).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(CLINICAL_FINDING).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(DISORDER).addRelationship(new Relationship(ISA, CLINICAL_FINDING)));

		allConcepts.add(new Concept(RIGHT_VENTRICULAR_STRUCTURE)
				.addFSN("Right cardiac ventricular structure (body structure)")
				.addRelationship(new Relationship(ISA, BODY_STRUCTURE))
				.addRelationship(new Relationship(LATERALITY, RIGHT))
		);
		allConcepts.add(new Concept(PULMONARY_VALVE_STRUCTURE)
				.addFSN("Pulmonary valve structure (body structure)")
				.addRelationship(new Relationship(ISA, BODY_STRUCTURE)));

		allConcepts.add(new Concept(STENOSIS).addRelationship(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(HYPERTROPHY).addRelationship(new Relationship(ISA, BODY_STRUCTURE)));

		allConcepts.add(new Concept(PENTALOGY_OF_FALLOT)
				.addRelationship(new Relationship(ISA, DISORDER))
				.addRelationship(new Relationship(FINDING_SITE, PULMONARY_VALVE_STRUCTURE).setGroupId(1))
				.addRelationship(new Relationship(ASSOCIATED_MORPHOLOGY, STENOSIS).setGroupId(1))
				.addRelationship(new Relationship(FINDING_SITE, RIGHT_VENTRICULAR_STRUCTURE).setGroupId(2))
				.addRelationship(new Relationship(ASSOCIATED_MORPHOLOGY, HYPERTROPHY).setGroupId(2))
		);

		conceptService.batchCreate(allConcepts, MAIN);
	}

	private void createMembers(String branchPath, String refsetId, List<String> referencedConcepts) {
		Set<ReferenceSetMember> members = new HashSet<>();
		for (String conceptId : referencedConcepts) {
			ReferenceSetMember member = new ReferenceSetMember();
			member.setRefsetId(refsetId);
			member.setReferencedComponentId(conceptId);
			member.setModuleId(Concepts.CORE_MODULE);
			member.setPath(branchPath);
			members.add(member);
		}
		try (Commit commit = branchService.openCommit(branchPath, "Testing")) {
			referenceSetMemberService.doSaveBatchMembers(members, commit);
			commit.markSuccessful();
		}
		referenceSetMemberService.createMembers(branchPath, members);
	}
}
