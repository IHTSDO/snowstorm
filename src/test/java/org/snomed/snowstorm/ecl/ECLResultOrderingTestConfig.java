package org.snomed.snowstorm.ecl;

import jakarta.annotation.PostConstruct;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.ServiceException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.snomed.otf.owltoolkit.constants.Concepts.LATERALITY;
import static org.snomed.snowstorm.AbstractTest.MAIN;
import static org.snomed.snowstorm.TestConcepts.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.HEART_STRUCTURE;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

/**
 * Fixture for {@link ECLResultOrderingTest}.
 *
 * Reference set members are created in ascending concept id order, the opposite of the descending order ECL is
 * required to return. Elasticsearch tends to return documents in storage order when a query does not sort, so a
 * result which simply echoes that order will fail the ordering assertions rather than passing by luck.
 */
public class ECLResultOrderingTestConfig extends ECLQueryTestConfig {

	/**
	 * Reference set which is <em>not</em> covered by ecl.concepts-lookup.refset.ids, so member of queries against it
	 * are answered by streaming the member index. This is the path reported in BROWSE-863.
	 */
	static final String REFSET_WITHOUT_LOOKUP = REFSET_MRCM_ATTRIBUTE_DOMAIN;

	/**
	 * Reference set which <em>is</em> covered by ecl.concepts-lookup.refset.ids, so member of queries against it can
	 * be answered from a referenced concepts lookup instead.
	 */
	static final String REFSET_WITH_LOOKUP = REFSET_SIMPLE;

	static final List<String> MEMBER_IDS_ASCENDING = List.of(
			RIGHT_FOOT,						// 7769000
			LEFT_FOOT,						// 22335008
			PULMONARY_VALVE_STRUCTURE,		// 39057004
			SKIN_STRUCTURE,					// 39937001
			HEMORRHAGE,						// 50960005
			THORACIC_STRUCTURE,				// 51185008
			RIGHT_VENTRICULAR_STRUCTURE,	// 53085002
			HYPERTROPHY,					// 56246009
			HEART_STRUCTURE,				// 80891009
			STENOSIS);						// 415582006

	@PostConstruct
	public void beforeAll() throws ServiceException, InterruptedException {

		deleteAll();

		List<Concept> allConcepts = new ArrayList<>();
		allConcepts.add(new Concept(SNOMEDCT_ROOT));
		allConcepts.add(new Concept(ISA).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(BODY_STRUCTURE).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(REFSET_WITHOUT_LOOKUP).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(REFSET_WITH_LOOKUP).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));

		// Attribute model, so that the refined member of query has a real attribute to match on
		allConcepts.add(new Concept(MODEL_COMPONENT).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(CONCEPT_MODEL_ATTRIBUTE).addRelationship(new Relationship(ISA, MODEL_COMPONENT)));
		allConcepts.add(new Concept(CONCEPT_MODEL_OBJECT_ATTRIBUTE).addRelationship(new Relationship(ISA, CONCEPT_MODEL_ATTRIBUTE)));
		allConcepts.add(new Concept(LATERALITY).addRelationship(new Relationship(ISA, CONCEPT_MODEL_OBJECT_ATTRIBUTE)));
		allConcepts.add(new Concept(RIGHT).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));

		for (String conceptId : MEMBER_IDS_ASCENDING) {
			allConcepts.add(new Concept(conceptId)
					.addRelationship(new Relationship(ISA, BODY_STRUCTURE))
					.addRelationship(new Relationship(LATERALITY, RIGHT)));
		}

		branchService.create(MAIN);
		conceptService.batchCreate(allConcepts, MAIN);

		// LinkedHashSet so the members are written in the ascending order declared above
		memberService.createMembers(MAIN, newMembers(REFSET_WITHOUT_LOOKUP));
		memberService.createMembers(MAIN, newMembers(REFSET_WITH_LOOKUP));
	}

	private Set<ReferenceSetMember> newMembers(String refsetId) {
		Set<ReferenceSetMember> members = new LinkedHashSet<>();
		for (String conceptId : MEMBER_IDS_ASCENDING) {
			members.add(new ReferenceSetMember(Concepts.CORE_MODULE, refsetId, conceptId));
		}
		return members;
	}
}
