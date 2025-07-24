package org.snomed.snowstorm.ecl;

import org.jetbrains.annotations.NotNull;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.*;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.domain.Branch.MAIN;
import static org.snomed.snowstorm.TestConcepts.DISORDER;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

public class ECLQueryServiceFilterTestConfig extends ECLQueryTestConfig {

	private static final String DEF_STATUS = "900000000000444006";
	private static final String MODULE = "900000000000443000";
	private static final String MODULE_A = "25000001";
	public static final String IPS_REFSET = "816080008";
	public static final String ICD_MAP_REFSET = "447562003";

	@PostConstruct
	public void beforeAll() throws ServiceException, InterruptedException {
		deleteAll();
		branchService.create(MAIN);

		List<Concept> allConcepts = new ArrayList<>();

		allConcepts.add(new Concept(DISORDER).addDescription(new Description("803980011", "Disease(disorder)")).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.addAll(newMetadataConcepts(DEF_STATUS, MODULE, PREFERRED, ACCEPTABLE, FSN, SYNONYM, TEXT_DEFINITION, "46011000052107"));
		allConcepts.add(new Concept(CORE_MODULE).addRelationship(new Relationship(ISA, MODULE)));
		allConcepts.add(new Concept(MODULE_A).addRelationship(new Relationship(ISA, MODULE)));
		allConcepts.add(new Concept(PRIMITIVE).addRelationship(new Relationship(ISA, DEF_STATUS)));
		allConcepts.add(new Concept(DEFINED).addRelationship(new Relationship(ISA, DEF_STATUS)));
		allConcepts.add(new Concept(REFSET).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(REFSET_HISTORICAL_ASSOCIATION).addRelationship(new Relationship(ISA, REFSET)));
		allConcepts.add(new Concept(REFSET_SAME_AS_ASSOCIATION).addRelationship(new Relationship(ISA, REFSET_HISTORICAL_ASSOCIATION)));
		allConcepts.add(new Concept(REFSET_SIMILAR_TO_ASSOCIATION).addRelationship(new Relationship(ISA, REFSET_HISTORICAL_ASSOCIATION)));
		createConceptsAndVersionCodeSystem(allConcepts, 20200131);

		allConcepts.add(new Concept("100001").addDescription(new Description("Athlete's heart (disorder)"))
				.addRelationship(ISA, DISORDER));
		allConcepts.add(new Concept("698271000").addDescription(new Description("Cardiac channelopathy"))
				.addRelationship(ISA, DISORDER));
		allConcepts.add(new Concept("100002")
				.addDescription(new Description( "Heart disease (disorder)").setLanguageCode("en").setType("FSN")
						.addLanguageRefsetMember(US_EN_LANG_REFSET, PREFERRED)
						.addLanguageRefsetMember(GB_EN_LANG_REFSET, PREFERRED))
				.addDescription(new Description("Heart disease").setLanguageCode("en").setType("SYNONYM")
						.addLanguageRefsetMember(US_EN_LANG_REFSET, PREFERRED)
						.addLanguageRefsetMember(GB_EN_LANG_REFSET, ACCEPTABLE))
				.addDescription(new Description("hjärtsjukdom").setLanguageCode("sv").setType("SYNONYM")
						.addLanguageRefsetMember("46011000052107", PREFERRED))
				.addDescription(new Description("Heart_disease_inactive").setActive(false)
						.addLanguageRefsetMember(US_EN_LANG_REFSET, PREFERRED)
						.addLanguageRefsetMember(GB_EN_LANG_REFSET, ACCEPTABLE))
				.addRelationship(ISA, DISORDER));
		createConceptsAndVersionCodeSystem(allConcepts, 20210131);
		allConcepts.add(new Concept("100003").setModuleId(MODULE_A).setDefinitionStatusId(DEFINED).addDescription(new Description( "Cardiac arrest (disorder)"))
				.addRelationship(ISA, DISORDER));
		// IPS refset
		allConcepts.add(new Concept(IPS_REFSET).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));

		// ICD Map and member concepts
		allConcepts.add(new Concept(ICD_MAP_REFSET).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept("427603009").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept("708094006").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept("698940002").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));

		// Two inactive concepts
		allConcepts.add(new Concept("200001").setActive(false).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept("200002").setActive(false).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		createConceptsAndVersionCodeSystem(allConcepts, 20220131);

		// Some active and some inactive concepts are active members of the IPS refset.
		memberService.createMembers(MAIN, createSimpleRefsetMembers(IPS_REFSET, "100001", "100002", "200001", "200002"));

		// Historical associations
		memberService.createMembers(MAIN, Set.of(
				new ReferenceSetMember(CORE_MODULE, REFSET_SAME_AS_ASSOCIATION, "200001")
						.setAdditionalField(ReferenceSetMember.AssociationFields.TARGET_COMP_ID, "100001"),
				new ReferenceSetMember(CORE_MODULE, REFSET_SIMILAR_TO_ASSOCIATION, "200002")
						.setAdditionalField(ReferenceSetMember.AssociationFields.TARGET_COMP_ID, "100001"),
				new ReferenceSetMember(CORE_MODULE, REFSET_SIMILAR_TO_ASSOCIATION, "200002")// Concept 200002 is a member twice
						.setAdditionalField(ReferenceSetMember.AssociationFields.TARGET_COMP_ID, "100002")
		));

		// ICD-10 complex map
		ReferenceSetMember inactiveMember = new ReferenceSetMember(CORE_MODULE, ICD_MAP_REFSET, "698940002")
				.setAdditionalField(ReferenceSetMember.AssociationFields.MAP_TARGET, "M14.59")
				.setAdditionalField("mapGroup", "1")
				.setAdditionalField("mapPriority", "2");
		inactiveMember.setReleased(true);
		inactiveMember.setReleaseHash("");
		inactiveMember.setActive(false);
		memberService.createMembers(MAIN, Set.of(
				new ReferenceSetMember(CORE_MODULE, ICD_MAP_REFSET, "427603009")
						.setAdditionalField(ReferenceSetMember.AssociationFields.MAP_TARGET, "J45.9")
						.setAdditionalField("mapGroup", "2")
						.setAdditionalField("mapPriority", "1"),
				new ReferenceSetMember(CORE_MODULE, ICD_MAP_REFSET, "708094006")
						.setAdditionalField(ReferenceSetMember.AssociationFields.MAP_TARGET, "J45.8")
						.setAdditionalField("mapGroup", "1")
						.setAdditionalField("mapPriority", "1"),
				// Member with missing concept
				new ReferenceSetMember(CORE_MODULE, ICD_MAP_REFSET, "101010101001")
						.setAdditionalField(ReferenceSetMember.AssociationFields.MAP_TARGET, "X")
						.setAdditionalField("mapGroup", "1")
						.setAdditionalField("mapPriority", "1"),
				inactiveMember
		));
	}

	@NotNull
	private Set<ReferenceSetMember> createSimpleRefsetMembers(String refsetId, String... referencedComponentIds) {
		return Arrays.stream(referencedComponentIds).map(id -> new ReferenceSetMember(CORE_MODULE, refsetId, id)).collect(Collectors.toSet());
	}

	private void createConceptsAndVersionCodeSystem(List<Concept> allConcepts, int effectiveDate) throws ServiceException {
		conceptService.batchCreate(allConcepts, MAIN);
		allConcepts.clear();
		CodeSystem codeSystem = codeSystemService.findByBranchPath("MAIN").orElse(null);
		if (codeSystem == null) {
			codeSystem = new CodeSystem("SNOMEDCT", "MAIN");
			codeSystemService.createCodeSystem(codeSystem);
		}
		codeSystemService.createVersion(codeSystemService.find("SNOMEDCT"), effectiveDate, "");
	}

	private List<Concept> newMetadataConcepts(String... conceptIds) {
		return Arrays.stream(conceptIds).map(id -> new Concept(id).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT))).collect(Collectors.toList());
	}
}
