package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

/**
 * MAINT-3068: activating an inactive synonym (acceptable when another preferred exists),
 * then inactivating again must not change acceptabilityId on other inactive language refset members.
 */
class ReactivateSynonymLangRefsetTest extends AbstractTest {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private BranchService branchService;

	private static final String INT_MAIN = "MAIN";
	private static final String EXT_MAIN = "MAIN/SNOMEDCT-XX";

	@BeforeEach
	void setup() {
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", INT_MAIN));
	}

	@Test
	void reactivateThenInactivateSynonym_shouldNotChangeInactiveLangRefsetMemberAcceptability() throws ServiceException {
		Map<String, String> preferred = Map.of(
				US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED),
				GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(PREFERRED));
		Map<String, String> acceptable = Map.of(
				US_EN_LANG_REFSET, descriptionAcceptabilityNames.get(ACCEPTABLE),
				GB_EN_LANG_REFSET, descriptionAcceptabilityNames.get(ACCEPTABLE));

		// International concept
		Concept concept = conceptService.create(
				new Concept()
						.addDescription(new Description("Bleeding (finding)").setTypeId(FSN).setAcceptabilityMap(preferred))
						.addDescription(new Description("Bleeding").setTypeId(SYNONYM).setAcceptabilityMap(preferred))
						.addDescription(new Description("Hemorrhage").setTypeId(SYNONYM).setAcceptabilityMap(preferred))
						.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)),
				INT_MAIN);
		String conceptId = concept.getConceptId();
		codeSystemService.createVersion(codeSystemService.find("SNOMEDCT"), 20240101, "20240101");

		// Extension
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-XX", EXT_MAIN));
		String extModuleA = conceptService.create(
				new Concept()
						.addDescription(new Description("Extension module (module)").setTypeId(FSN).setAcceptabilityMap(preferred))
						.addDescription(new Description("Extension module").setTypeId(SYNONYM).setAcceptabilityMap(preferred))
						.addAxiom(new Relationship(ISA, MODULE)),
				EXT_MAIN).getConceptId();
		String extModuleB = conceptService.create(
				new Concept()
						.addDescription(new Description("Extension medicine module (module)").setTypeId(FSN).setAcceptabilityMap(preferred))
						.addDescription(new Description("Extension medicine module").setTypeId(SYNONYM).setAcceptabilityMap(preferred))
						.addAxiom(new Relationship(ISA, MODULE)),
				EXT_MAIN).getConceptId();
		branchService.updateMetadata(EXT_MAIN, Map.of(
				Config.DEFAULT_MODULE_ID_KEY, extModuleA,
				Config.EXPECTED_EXTENSION_MODULES, List.of(extModuleA, extModuleB)));

		// Extension preferred synonym for Hemorrhage; inactivate on extension
		concept = conceptService.find(conceptId, EXT_MAIN);
		Description hemorrhage = getDescriptionByTerm(concept, "Hemorrhage");
		hemorrhage.setModuleId(extModuleA);
		hemorrhage.clearLanguageRefsetMembers();
		hemorrhage.addLanguageRefsetMember(US_EN_LANG_REFSET, PREFERRED);
		hemorrhage.addLanguageRefsetMember(GB_EN_LANG_REFSET, PREFERRED);
		conceptService.update(concept, EXT_MAIN);
		codeSystemService.createVersion(codeSystemService.find("SNOMEDCT-XX"), 20240102, "20240102");

		concept = simulateRestTransfer(conceptService.find(conceptId, EXT_MAIN));
		hemorrhage = getDescriptionByTerm(concept, "Hemorrhage");
		hemorrhage.setActive(false);
		conceptService.update(concept, EXT_MAIN);
		codeSystemService.createVersion(codeSystemService.find("SNOMEDCT-XX"), 20240103, "20240103");

		// Capture inactive lang refset members before reactivation
		concept = conceptService.find(conceptId, EXT_MAIN);
		hemorrhage = getDescriptionByTerm(concept, "Hemorrhage");
		assertFalse(hemorrhage.isActive());
		Map<String, String> inactiveAcceptabilityBefore = captureInactiveLangRefsetAcceptability(hemorrhage, US_EN_LANG_REFSET);

		// Reactivate as acceptable (Bleeding remains preferred on extension)
		concept = simulateRestTransfer(conceptService.find(conceptId, EXT_MAIN));
		hemorrhage = getDescriptionByTerm(concept, "Hemorrhage");
		hemorrhage.setActive(true);
		hemorrhage.setAcceptabilityMap(acceptable);
		conceptService.update(concept, EXT_MAIN);

		concept = conceptService.find(conceptId, EXT_MAIN);
		hemorrhage = getDescriptionByTerm(concept, "Hemorrhage");
		assertTrue(hemorrhage.isActive());
		assertEquals("ACCEPTABLE", hemorrhage.getAcceptabilityMap().get(US_EN_LANG_REFSET));

		// Inactivate again
		concept = simulateRestTransfer(conceptService.find(conceptId, EXT_MAIN));
		hemorrhage = getDescriptionByTerm(concept, "Hemorrhage");
		hemorrhage.setActive(false);
		conceptService.update(concept, EXT_MAIN);

		// Inactive lang refset members must retain acceptabilityId from before reactivation
		concept = conceptService.find(conceptId, EXT_MAIN);
		hemorrhage = getDescriptionByTerm(concept, "Hemorrhage");
		assertFalse(hemorrhage.isActive());
		Map<String, String> inactiveAcceptabilityAfter = captureInactiveLangRefsetAcceptability(hemorrhage, US_EN_LANG_REFSET);

		for (Map.Entry<String, String> entry : inactiveAcceptabilityBefore.entrySet()) {
			assertEquals(entry.getValue(), inactiveAcceptabilityAfter.get(entry.getKey()),
					"Inactive language refset member acceptabilityId must not change for member " + entry.getKey());
		}
	}

	private Map<String, String> captureInactiveLangRefsetAcceptability(Description description, String langRefset) {
		Set<ReferenceSetMember> members = description.getLangRefsetMembersMap().get(langRefset);
		if (members == null) {
			return Map.of();
		}
		return members.stream()
				.filter(m -> !m.isActive())
				.collect(Collectors.toMap(ReferenceSetMember::getMemberId,
						m -> m.getAdditionalField(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID)));
	}

	private Description getDescriptionByTerm(Concept concept, String term) {
		return concept.getDescriptions().stream()
				.filter(d -> term.equals(d.getTerm()))
				.findFirst()
				.orElseThrow();
	}
}
