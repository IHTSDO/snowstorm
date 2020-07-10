package org.snomed.snowstorm.core.data.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_DIALECTS;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class ConceptAttributeSortHelperTest {

	@Autowired
	private ConceptAttributeSortHelper conceptAttributeSortHelper;

	@BeforeEach
	void setup() {
		conceptAttributeSortHelper.getSubHierarchyToTopLevelTagCache().put("disorder", "finding");
	}

	@Test
	void sortAttributes() {
		Concept concept = new Concept()
				.addFSN("Contracture of knee joint (disorder)")
				.addAxiom(
						relationship(0, "116680003", "298325004", "Finding of movement (finding)"),
						relationship(0, "116680003", "64572001", "Disease (disorder)"),
						relationship(1, "116676008", "57048009", "Contracture (morphologic abnormality)"),
						relationship(1, "363698007", "72696002", "Knee region structure (body structure)")
				)
				.addAxiom(
						relationship(0, "116680003", "298325004", "Finding of movement (finding)"),
						relationship(0, "116680003", "64572001", "Disease (disorder)"),
						relationship(1, "116676008", "57048009", "Contracture (morphologic abnormality)"),
						relationship(1, "363698007", "72696002", "Knee region structure (body structure)"),
						relationship(2, "363713009", "1250004", "Decreased (qualifier value)"),
						relationship(2, "363714003", "299332000", "Knee joint - range of movement (observable entity)"),
						relationship(3, "363698007", "10200004", "Liver structure (body structure)")
				);
		conceptAttributeSortHelper.sortAttributes(Collections.singleton(concept));

		ArrayList<Axiom> axioms = new ArrayList<>(concept.getClassAxioms());
		assertEquals(2, axioms.size());
		assertEquals("Second axiom should have been sorted to the top because type 363714003 sorts higher than 363698007", 7, axioms.get(0).getRelationships().size());
		assertEquals(7, axioms.get(0).getRelationships().size());
		assertEquals(4, axioms.get(1).getRelationships().size());

		Axiom axiom = axioms.get(0);
		for (Relationship relationship : axiom.getRelationships()) {
			System.out.println(asString(relationship));
		}

		Iterator<Relationship> iterator = axiom.getRelationships().iterator();

		// Assert that is a attributes are sorted alphabetically
		assertEquals("0, 116680003, Disease (disorder)", asString(iterator.next()));
		assertEquals("0, 116680003, Finding of movement (finding)", asString(iterator.next()));

		// Assert that self grouped attributes should be sorted after is-a relationships
		assertEquals("3, 363698007, Liver structure (body structure)", asString(iterator.next()));

		// Assert that groups have swapped position because type 363714003 sorts higher than 363698007.
		assertEquals("2, 363714003, Knee joint - range of movement (observable entity)", asString(iterator.next()));
		assertEquals("2, 363713009, Decreased (qualifier value)", asString(iterator.next()));
		assertEquals("1, 363698007, Knee region structure (body structure)", asString(iterator.next()));
		assertEquals("1, 116676008, Contracture (morphologic abnormality)", asString(iterator.next()));
	}

	private String asString(Relationship relationship) {
		return relationship.getGroupId() + ", " + relationship.getTypeId() + ", " + relationship.getTargetFsn();
	}

	public Relationship relationship(int groupId, String typeId, String destinationId, String destinationTerm) {
		return new Relationship(typeId, destinationId).setGroupId(groupId)
				.setTarget(new ConceptMini(destinationId, DEFAULT_LANGUAGE_DIALECTS).addFSN(destinationTerm));
	}
}
