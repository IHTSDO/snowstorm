package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConceptUpdateHelperTest extends AbstractTest {
	private static final String BRANCH_A = "MAIN/AAA";
	private static final String SHORTNAME_A = "SNOMEDCT-AAA";
	private static final String MODULE_A = "12345678910";

	private static final String BRANCH_AB = "MAIN/AAA/BBB";
	private static final String SHORTNAME_AB = "SNOMEDCT-BBB";
	private static final String MODULE_AB = "1234567891011";

	private static final String FINDING_SITE = "363698007";
	private static final String STOMACH = "69695003";
	private static final String WITH_SIZE = "103373006";
	private static final String LARGE = "255509001";

	private static final String CONCEPT_ID = "12345";
	private static final Concept CONCEPT = new Concept(CONCEPT_ID, MODULE_A)
			.addFSN("Pizza (food)")
			.addDescription(new Description("123456", "Cheese.").setModuleId(MODULE_A))
			.addDescription(new Description("1234567", "Tomato.").setModuleId(MODULE_A))
			.addDescription(new Description("12345678", "Basil.").setModuleId(MODULE_AB))
			.addRelationship(new Relationship("1", Concepts.ISA, Concepts.SNOMEDCT_ROOT))
			.addRelationship(new Relationship("2", FINDING_SITE, STOMACH));

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Test
	void saveNewOrUpdatedConcepts_ShouldRestoreDetailsFromParentCodeSystem() throws ServiceException {
		// Extension A creates a Concept et al
		givenCodeSystemExists(SHORTNAME_A, BRANCH_A);
		givenBranchExists(BRANCH_A, MODULE_A);
		givenConceptExists();
		givenCodeSystemVersionExists();
		assertEffectiveTimeAndModuleId(getRelationship(FINDING_SITE, getConcept(BRANCH_A)), 20210131, MODULE_A);

		// Extension B modifies Extension A's Concept by de-activating a Relationship
		givenCodeSystemExists(SHORTNAME_AB, BRANCH_AB);
		givenBranchExists(BRANCH_AB, MODULE_AB);
		whenConceptModified(getConcept(BRANCH_A), false);
		assertEffectiveTimeAndModuleId(getRelationship(FINDING_SITE, getConcept(BRANCH_AB)), null, MODULE_AB);

		// Extension B modifies Extension A's Concept (again) by reverting their change (inactivating Relationship)
		whenConceptModified(getConcept(BRANCH_AB), true);
		assertEffectiveTimeAndModuleId(getRelationship(FINDING_SITE, getConcept(BRANCH_AB)), 20210131, MODULE_A); //Effective Time and Module Id restored from parent.
	}

	@Test
	void saveNewOrUpdatedConcepts_ShouldNotMoveDescriptionToDifferentCodeSystem() throws ServiceException {
		// Extension A has a Component in one module, but a Description is another (which is wrong).
		givenCodeSystemExists(SHORTNAME_A, BRANCH_A);
		givenBranchExists(BRANCH_A, MODULE_A);
		givenConceptExists();
		givenCodeSystemVersionExists();
		assertEffectiveTimeAndModuleId(getDescription("Basil.", getConcept(BRANCH_A)), 20210131, MODULE_A); // Changed to be same as Concept

		// Extension B modifies Extension A's Concept by de-activating a Relationship. Description should be not be moved to Extension B.
		givenCodeSystemExists(SHORTNAME_AB, BRANCH_AB);
		givenBranchExists(BRANCH_AB, MODULE_AB);
		whenConceptModified(getConcept(BRANCH_A), false);
		assertEffectiveTimeAndModuleId(getDescription("Basil.", getConcept(BRANCH_AB)), 20210131, MODULE_A);
	}

	@Test
	void saveNewOrUpdatedConcepts_ShouldAddNewRelationshipToCorrectModule() throws ServiceException {
		// Extension A has a Component in one module, but a Description is another (which is wrong).
		givenCodeSystemExists(SHORTNAME_A, BRANCH_A);
		givenBranchExists(BRANCH_A, MODULE_A);
		givenConceptExists();
		givenCodeSystemVersionExists();
		assertEffectiveTimeAndModuleId(getRelationship(FINDING_SITE, getConcept(BRANCH_A)), 20210131, MODULE_A);

		// Extension B modifies Extension A's Concept by adding a Relationship.
		givenCodeSystemExists(SHORTNAME_AB, BRANCH_AB);
		givenBranchExists(BRANCH_AB, MODULE_AB);
		whenRelationshipAdded(getConcept(BRANCH_A));
		assertEffectiveTimeAndModuleId(getRelationship(WITH_SIZE, getConcept(BRANCH_AB)), null, MODULE_AB); //New Relationship in correct module.
	}

	private void givenCodeSystemExists(String shortName, String branchPath) {
		codeSystemService.createCodeSystem(new CodeSystem(shortName, branchPath));
	}

	private void givenBranchExists(String branchPath, String moduleId) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("previousPackage", "test-previous-package.zip");
		metadata.put("defaultModuleId", moduleId);

		branchService.updateMetadata(branchPath, metadata);
	}

	private void givenConceptExists() throws ServiceException {
		conceptService.create(CONCEPT, BRANCH_A);
	}

	private void givenCodeSystemVersionExists() {
		codeSystemService.createVersion(codeSystemService.find(SHORTNAME_A), 20210131, "20210131");
	}

	private Concept getConcept(String branchPath) {
		return conceptService.find(CONCEPT_ID, branchPath);
	}

	private Relationship getRelationship(String typeId, Concept concept) {
		Set<Relationship> relationships = concept.getRelationships();
		for (Relationship relationship : relationships) {
			if (relationship.getTypeId().equals(typeId)) {
				return relationship;
			}
		}

		return null;
	}

	private Description getDescription(String name, Concept concept) {
		Set<Description> descriptions = concept.getDescriptions();
		for (Description description : descriptions) {
			if (description.getTerm().equals(name)) {
				return description;
			}
		}

		return null;
	}

	private void assertEffectiveTimeAndModuleId(Relationship relationship, Integer expectedEffectiveTime, String expectedModuleId) {
		assertEquals(expectedEffectiveTime, relationship.getEffectiveTimeI());
		assertEquals(expectedModuleId, relationship.getModuleId());
	}

	private void assertEffectiveTimeAndModuleId(Description description, Integer expectedEffectiveTime, String expectedModuleId) {
		assertEquals(expectedEffectiveTime, description.getEffectiveTimeI());
		assertEquals(expectedModuleId, description.getModuleId());
	}

	private void whenConceptModified(Concept concept, boolean newState) throws ServiceException {
		Set<Relationship> relationships = concept.getRelationships();
		for (Relationship relationship : relationships) {
			if (relationship.getTypeId().equals(FINDING_SITE)) {
				relationship.setActive(newState);
				relationship.setEffectiveTimeI(null);
			}
		}
		conceptService.update(concept, BRANCH_AB);
	}

	private void whenRelationshipAdded(Concept concept) throws ServiceException {
		concept.addRelationship(new Relationship(WITH_SIZE, LARGE));
		conceptService.update(concept, BRANCH_AB);
	}
}
