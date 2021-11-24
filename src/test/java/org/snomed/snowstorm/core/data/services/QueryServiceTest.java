package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.lang.Long.parseLong;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

	private static final PageRequest PAGE_REQUEST = PageRequest.of(0, 50);
	public static final String PATH = "MAIN";
	public static final int TEST_ET = 20210131;
	private final static String MODEL_COMPONENT = "900000000000441003";
	private final static String BODY_STRUCTURE = "123037004";
	private final static String LATERALITY = "272741003";
	private final static String RIGHT = "24028007";
	private final static String PULMONARY_VALVE_STRUCTURE = "39057004";
	private final static String DISORDER = "64572001";
	private final static String PENTALOGY_OF_FALLOT = "204306007";
	private final static String ASSOCIATED_MORPHOLOGY = "116676008";
	private final static String STENOSIS = "415582006";
	private final static String HYPERTROPHY = "56246009";
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
				.definitionStatusFilter(Concepts.SUFFICIENTLY_DEFINED);
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
	public void testModuleFilter() {
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
		List<Concept> allConcepts = new ArrayList<>();
		allConcepts.add(new Concept(ISA).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(MODEL_COMPONENT).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(CONCEPT_MODEL_ATTRIBUTE).addRelationship(new Relationship(ISA, MODEL_COMPONENT)));
		allConcepts.add(new Concept(CONCEPT_MODEL_OBJECT_ATTRIBUTE).addRelationship(new Relationship(ISA, CONCEPT_MODEL_ATTRIBUTE)));
		allConcepts.add(new Concept(FINDING_SITE).addRelationship(new Relationship(ISA, CONCEPT_MODEL_OBJECT_ATTRIBUTE)));
		allConcepts.add(new Concept(BODY_STRUCTURE).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(CLINICAL_FINDING).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(DISORDER).addRelationship(new Relationship(ISA, CLINICAL_FINDING)));
		allConcepts.add(new Concept(ASSOCIATED_MORPHOLOGY).addRelationship(new Relationship(ISA, CONCEPT_MODEL_OBJECT_ATTRIBUTE)));

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

		QueryService.ConceptQueryBuilder queryBuilder = service.createQueryBuilder(false).activeFilter(true);
		// Dot notation
		String ecl = "( *: 116676008 |Associated morphology (attribute)|= 415582006 |Stenosis (morphologic abnormality)|). 363698007 |Finding site (attribute)|";
		queryBuilder.ecl(ecl);
		List<ConceptMini> results = service.search(queryBuilder, PATH, PAGE_REQUEST).getContent();
		System.out.println("Dot notation query results:");
		results.forEach(ConceptMini::getFsnTerm);
		assertEquals(2, results.size());

		// Add term filtering
		queryBuilder.descriptionTerm("structure");

		results = service.search(queryBuilder, PATH, PAGE_REQUEST).getContent();
		System.out.println("Dot notation query results with term filter:");
		results.forEach(ConceptMini::getFsnTerm);
		assertEquals(2, results.size());

		// Reverse flag
		ecl = "* : R 363698007 |Finding site (attribute)| = (* : 116676008 |Associated morphology (attribute)| = 415582006 |Stenosis (morphologic abnormality)|)";
		queryBuilder.ecl(ecl);
		results = service.search(queryBuilder, PATH, PAGE_REQUEST).getContent();
		System.out.println("Reverse flag query results:");
		results.forEach(ConceptMini::getFsnTerm);
		assertEquals(2, results.size());
	}

}
