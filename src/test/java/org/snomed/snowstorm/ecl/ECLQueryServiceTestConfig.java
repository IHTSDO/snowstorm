package org.snomed.snowstorm.ecl;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import org.elasticsearch.search.sort.SortBuilders;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.classification.ClassificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.VersionControlHelper.LARGE_PAGE;
import static org.junit.Assert.assertEquals;
import static org.snomed.snowstorm.AbstractTest.MAIN;
import static org.snomed.snowstorm.TestConcepts.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.HEART_STRUCTURE;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

public class ECLQueryServiceTestConfig extends TestConfig {

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ReferenceSetMemberService memberService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private ClassificationService classificationService;

	@Autowired
	private PermissionService permissionService;

	@Autowired
	private ElasticsearchRestTemplate elasticsearchTemplate;

	@PostConstruct
	public void beforeAll() throws ServiceException {
		List<Concept> allConcepts = new ArrayList<>();

		allConcepts.add(new Concept(SNOMEDCT_ROOT));
		allConcepts.add(new Concept(ISA).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(MODEL_COMPONENT).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(CONCEPT_MODEL_ATTRIBUTE).addRelationship(new Relationship(ISA, MODEL_COMPONENT)));
		allConcepts.add(new Concept(CONCEPT_MODEL_OBJECT_ATTRIBUTE).addRelationship(new Relationship(ISA, CONCEPT_MODEL_ATTRIBUTE)));
		allConcepts.add(new Concept(Concepts.FINDING_SITE).addRelationship(new Relationship(ISA, CONCEPT_MODEL_OBJECT_ATTRIBUTE)));
		allConcepts.add(new Concept(ASSOCIATED_MORPHOLOGY).addRelationship(new Relationship(ISA, CONCEPT_MODEL_OBJECT_ATTRIBUTE)));
		allConcepts.add(new Concept(PROCEDURE_SITE).addRelationship(new Relationship(ISA, CONCEPT_MODEL_OBJECT_ATTRIBUTE)));
		allConcepts.add(new Concept(PROCEDURE_SITE_DIRECT).addRelationship(new Relationship(ISA, PROCEDURE_SITE)));
		allConcepts.add(new Concept(LATERALITY).addRelationship(new Relationship(ISA, CONCEPT_MODEL_OBJECT_ATTRIBUTE)));
		allConcepts.add(new Concept(RIGHT).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));

		allConcepts.add(new Concept(BODY_STRUCTURE).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(HEART_STRUCTURE).addRelationship(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(SKIN_STRUCTURE).addRelationship(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(THORACIC_STRUCTURE).addRelationship(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(PULMONARY_VALVE_STRUCTURE).addRelationship(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(RIGHT_VENTRICULAR_STRUCTURE)
				.addRelationship(new Relationship(ISA, BODY_STRUCTURE))
				.addRelationship(new Relationship(LATERALITY, RIGHT))
		);
		allConcepts.add(new Concept(LEFT_FOOT).addRelationship(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(RIGHT_FOOT).addRelationship(new Relationship(ISA, BODY_STRUCTURE)));

		allConcepts.add(new Concept(STENOSIS).addRelationship(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(HYPERTROPHY).addRelationship(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(HEMORRHAGE).addRelationship(new Relationship(ISA, BODY_STRUCTURE)));

		allConcepts.add(new Concept(CLINICAL_FINDING).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(DISORDER).addRelationship(new Relationship(ISA, CLINICAL_FINDING)));
		allConcepts.add(new Concept(BLEEDING)
				.addRelationship(new Relationship(ISA, CLINICAL_FINDING))
				.addRelationship(new Relationship(ASSOCIATED_MORPHOLOGY, HEMORRHAGE))
		);
		allConcepts.add(new Concept(BLEEDING_SKIN)
				.addRelationship(new Relationship(ISA, BLEEDING))
				.addRelationship(new Relationship(ASSOCIATED_MORPHOLOGY, HEMORRHAGE))
				.addRelationship(new Relationship(FINDING_SITE, SKIN_STRUCTURE))
		);
		/*
			 <  404684003 |Clinical finding| :
				{  363698007 |Finding site|  = <<  39057004 |Pulmonary valve structure| ,
					 116676008 |Associated morphology|  = <<  415582006 |Stenosis| },
				{  363698007 |Finding site|  = <<  53085002 |Right ventricular structure| ,
					 116676008 |Associated morphology|  = <<  56246009 |Hypertrophy| }
		 */

		allConcepts.add(new Concept(PENTALOGY_OF_FALLOT)
				.addRelationship(new Relationship(ISA, DISORDER))
				.addRelationship(new Relationship(FINDING_SITE, PULMONARY_VALVE_STRUCTURE).setGroupId(1))
				.addRelationship(new Relationship(ASSOCIATED_MORPHOLOGY, STENOSIS).setGroupId(1))
				.addRelationship(new Relationship(FINDING_SITE, RIGHT_VENTRICULAR_STRUCTURE).setGroupId(2))
				.addRelationship(new Relationship(ASSOCIATED_MORPHOLOGY, HYPERTROPHY).setGroupId(2))
		);
		allConcepts.add(new Concept(PENTALOGY_OF_FALLOT_INCORRECT_GROUPING)
				.addRelationship(new Relationship(ISA, DISORDER))
				.addRelationship(new Relationship(FINDING_SITE, PULMONARY_VALVE_STRUCTURE).setGroupId(1))
				.addRelationship(new Relationship(ASSOCIATED_MORPHOLOGY, STENOSIS).setGroupId(2))// <-- was group 1
				.addRelationship(new Relationship(FINDING_SITE, RIGHT_VENTRICULAR_STRUCTURE).setGroupId(2))
				.addRelationship(new Relationship(ASSOCIATED_MORPHOLOGY, HYPERTROPHY).setGroupId(1))// <-- was group 2
		);

		allConcepts.add(new Concept(PROCEDURE).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(OPERATION_ON_HEART)
				.addRelationship(new Relationship(ISA, PROCEDURE))
				.addRelationship(new Relationship(PROCEDURE_SITE, HEART_STRUCTURE))
		);
		allConcepts.add(new Concept(CHEST_IMAGING)
				.addRelationship(new Relationship(ISA, PROCEDURE))
				.addRelationship(new Relationship(PROCEDURE_SITE_DIRECT, THORACIC_STRUCTURE))
		);
		allConcepts.add(new Concept(AMPUTATION_FOOT_LEFT)
				.addRelationship(new Relationship(ISA, PROCEDURE))
				.addRelationship(new Relationship(PROCEDURE_SITE, LEFT_FOOT).setGroupId(1))
		);
		allConcepts.add(new Concept(AMPUTATION_FOOT_RIGHT)
				.addRelationship(new Relationship(ISA, PROCEDURE))
				.addRelationship(new Relationship(PROCEDURE_SITE, RIGHT_FOOT).setGroupId(1))
		);
		allConcepts.add(new Concept(AMPUTATION_FOOT_BILATERAL)
				.addRelationship(new Relationship(ISA, PROCEDURE))
				.addRelationship(new Relationship(PROCEDURE_SITE, LEFT_FOOT).setGroupId(1))
				.addRelationship(new Relationship(PROCEDURE_SITE, RIGHT_FOOT).setGroupId(2))
		);


		branchService.create(MAIN);
		conceptService.batchCreate(allConcepts, MAIN);

		memberService.createMembers(MAIN, Sets.newHashSet(
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN, CLINICAL_FINDING),
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN, BODY_STRUCTURE)));

		memberService.createMembers(MAIN, Sets.newHashSet(
				new ReferenceSetMember(Concepts.CORE_MODULE, REFSET_SIMPLE, BODY_STRUCTURE)));

		List<QueryConcept> queryConcepts = elasticsearchTemplate.search(
						new NativeSearchQueryBuilder()
								.withSort(SortBuilders.fieldSort(QueryConcept.Fields.CONCEPT_ID))
								.withPageable(LARGE_PAGE).build(), QueryConcept.class)
				.stream().map(SearchHit::getContent).collect(Collectors.toList());
		assertEquals(34, queryConcepts.size());
	}

	@PreDestroy
	public void tearDown() throws InterruptedException {
		branchService.deleteAll();
		conceptService.deleteAll();
		codeSystemService.deleteAll();
		classificationService.deleteAll();
		permissionService.deleteAll();
	}

}
