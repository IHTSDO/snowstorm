package org.snomed.snowstorm.core.data.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Axiom;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_DIALECTS;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class ConceptAttributeSortHelperTest {

	@Autowired
	private ConceptAttributeSortHelper conceptAttributeSortHelper;

	@BeforeEach
	void setup() {
		conceptAttributeSortHelper.getSubHierarchyToTopLevelTagCache().put("disorder", "finding");
		conceptAttributeSortHelper.getSubHierarchyToTopLevelTagCache().put("clinical_drug", "product");
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
		concept.getClassAxioms().forEach(a -> a.setAxiomId(UUID.randomUUID().toString()));
		conceptAttributeSortHelper.sortAttributes(Collections.singleton(concept));

		ArrayList<Axiom> axioms = new ArrayList<>(concept.getClassAxioms());
		assertEquals(2, axioms.size());
		assertEquals(7, axioms.get(0).getRelationships().size(),
				"Second axiom should have been sorted to the top because type 363714003 sorts higher than 363698007");
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

	@Test
	void sortConcreteAttributes() {
		Concept concept = new Concept()
				.addFSN("Product containing precisely nicotinic acid 500 milligram/1 each conventional release oral tablet (clinical drug)")
				.addAxiom(
						relationship(0, "116680003", "780007006", "Product containing only nicotinic acid in oral dose form (medicinal product form)"),
						relationship(0, "411116001", "421026006", "Conventional release oral tablet (dose form)"),
						relationship(0, "763032000", "732936001", "Tablet (unit of presentation)"),
						Relationship.newConcrete("1142139005", "#1"),// group 0
						relationship(1, "732943007", "273943001", "Nicotinic acid (substance)"),
						relationship(1, "762949000", "273943001", "Nicotinic acid (substance)"),
						Relationship.newConcrete("1142135004", "#500").setGroupId(1),
						relationship(1, "732945000", "258684004", "milligram (qualifier value)"),
						Relationship.newConcrete("1142136003", "#1").setGroupId(1),
						relationship(1, "732947008", "732936001", "Tablet (unit of presentation)")
				);
		concept.getClassAxioms().forEach(a -> a.setAxiomId(UUID.randomUUID().toString()));
		conceptAttributeSortHelper.sortAttributes(Collections.singleton(concept));

		ArrayList<Axiom> axioms = new ArrayList<>(concept.getClassAxioms());
		assertEquals(1, axioms.size());
		assertEquals(10, axioms.get(0).getRelationships().size());

		Axiom axiom = axioms.get(0);
		for (Relationship relationship : axiom.getRelationships()) {
			System.out.println(asString(relationship));
		}

		Iterator<Relationship> iterator = axiom.getRelationships().iterator();
		assertEquals("0, 116680003, Product containing only nicotinic acid in oral dose form (medicinal product form)", asString(iterator.next()));
		// Concrete attribute sorted to the top as per attribute order config
		assertEquals("0, 411116001, Conventional release oral tablet (dose form)", asString(iterator.next()));
		assertEquals("0, 763032000, Tablet (unit of presentation)", asString(iterator.next()));
		assertEquals("0, 1142139005, #1", asString(iterator.next()));

		// Assert attribute order within group 1 is sorted as per config
		assertEquals("1, 762949000, Nicotinic acid (substance)", asString(iterator.next()));
		assertEquals("1, 732943007, Nicotinic acid (substance)", asString(iterator.next()));
		assertEquals("1, 1142135004, #500", asString(iterator.next()));
		assertEquals("1, 732945000, milligram (qualifier value)", asString(iterator.next()));
		assertEquals("1, 1142136003, #1", asString(iterator.next()));
		assertEquals("1, 732947008, Tablet (unit of presentation)", asString(iterator.next()));
	}

	private String asString(Relationship relationship) {
		if (relationship.isConcrete()) {
			return relationship.getGroupId() + ", " + relationship.getTypeId() + ", " + relationship.getValue();
		} else {
			return relationship.getGroupId() + ", " + relationship.getTypeId() + ", " + relationship.getTargetFsn();
		}
	}

	public Relationship relationship(int groupId, String typeId, String destinationId, String destinationTerm) {
		return new Relationship(typeId, destinationId).setGroupId(groupId)
				.setTarget(new ConceptMini(destinationId, DEFAULT_LANGUAGE_DIALECTS).addFSN(destinationTerm));
	}
}
