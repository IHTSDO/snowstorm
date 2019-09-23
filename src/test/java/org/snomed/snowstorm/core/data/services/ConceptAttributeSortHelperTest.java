package org.snomed.snowstorm.core.data.services;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class ConceptAttributeSortHelperTest {

	@Autowired
	private ConceptAttributeSortHelper conceptAttributeSortHelper;

	private static final List<String> EN = Collections.singletonList("en");

	@Before
	public void setup() {
		conceptAttributeSortHelper.getSubHierarchyToTopLevelTagCache().put("disorder", "finding");
	}

	@Test
	public void sortAttributes() {
		Concept concept = new Concept()
				.addDescription(new Description("Contracture of knee joint (disorder)").setTypeId(Concepts.FSN))
				.addAxiom(
						relationship(0, "116680003", "298325004", "Finding of movement (finding)"),
						relationship(0, "116680003", "64572001", "Disease (disorder)"),
						relationship(1, "116676008", "57048009", "Contracture (morphologic abnormality)"),
						relationship(1, "363698007", "72696002", "Knee region structure (body structure)"),
						relationship(2, "363713009", "1250004", "Decreased (qualifier value)"),
						relationship(2, "363714003", "299332000", "Knee joint - range of movement (observable entity)")
				);
		conceptAttributeSortHelper.sortAttributes(Collections.singleton(concept));

		for (Relationship relationship : concept.getClassAxioms().iterator().next().getRelationships()) {
			System.out.println(asString(relationship));
		}

		Iterator<Relationship> iterator = concept.getClassAxioms().iterator().next().getRelationships().iterator();

		// Assert that is a attributes are sorted alphabetically
		assertEquals("0, 116680003, Disease (disorder)", asString(iterator.next()));
		assertEquals("0, 116680003, Finding of movement (finding)", asString(iterator.next()));

		// Assert that group ids have been switched because type 363714003 sorts higher than 363698007.
		assertEquals("1, 363714003, Knee joint - range of movement (observable entity)", asString(iterator.next()));
		assertEquals("1, 363713009, Decreased (qualifier value)", asString(iterator.next()));
		assertEquals("2, 363698007, Knee region structure (body structure)", asString(iterator.next()));
		assertEquals("2, 116676008, Contracture (morphologic abnormality)", asString(iterator.next()));
	}

	private String asString(Relationship relationship) {
		return relationship.getGroupId() + ", " + relationship.getTypeId() + ", " + relationship.getTargetFsn();
	}

	public Relationship relationship(int groupId, String typeId, String destinationId, String destinationTerm) {
		return new Relationship(typeId, destinationId).setGroupId(groupId)
				.setTarget(new ConceptMini(destinationId, EN).addActiveDescription(new Description(destinationTerm).setTypeId(Concepts.FSN)));
	}
}