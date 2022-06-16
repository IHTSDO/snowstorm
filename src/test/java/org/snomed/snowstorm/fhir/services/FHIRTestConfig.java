package org.snomed.snowstorm.fhir.services;

import io.kaicode.elasticvc.api.BranchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.data.services.pojo.CodeSystemConfiguration;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.snomed.snowstorm.core.data.domain.Concepts.REFSET_MRCM_ATTRIBUTE_RANGE_INTERNATIONAL;
import static org.snomed.snowstorm.fhir.services.AbstractFHIRTest.*;

/**
 * 	Set up read-only data for FHIR tests
 */
public class FHIRTestConfig extends TestConfig {

	@Autowired
	protected BranchService branchService;

	@Autowired
	protected ConceptService conceptService;

	@Autowired
	protected CodeSystemService codeSystemService;

	@Autowired
	protected ReferenceSetMemberService memberService;

	@Autowired
	protected CodeSystemConfigurationService codeSystemConfigurationService;

	protected final String MAIN = "MAIN";

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	@PostConstruct
	public void beforeAll() throws ServiceException {
		List<Concept> concepts = new ArrayList<>();
		concepts.add(new Concept(Concepts.SNOMEDCT_ROOT));
		for (int x=1; x<=10; x++) {
			createDummyConcepts(x, concepts, false);
		}
		branchService.create(MAIN);
		concepts.add(new Concept(sampleInactiveSCTID).setActive(false));
		conceptService.batchCreate(concepts, MAIN);

		// Version content to fill effectiveTime fields
		CodeSystem codeSystem = new CodeSystem("SNOMEDCT", MAIN);

		codeSystemService.createCodeSystem(codeSystem);
		String versionBranch = codeSystemService.createVersion(codeSystem, 20190131, "");

		//Now create a new branch to hold a new edition
		String releaseBranch = MAIN + "/" + versionBranch;
		String branchWK = releaseBranch + "/SNOMEDCT-WK";
		CodeSystem codeSystemWK = new CodeSystem("SNOMEDCT-WK", branchWK);
		codeSystemService.createCodeSystem(codeSystemWK);

		//And tell the configuration about that new module
		CodeSystemConfiguration config = new CodeSystemConfiguration("SNOMEDCT-WK", "SNOMEDCT-WK", sampleModuleId, null, null);
		codeSystemConfigurationService.getConfigurations().add(config);

		concepts.clear();
		//The new module will inherit the 10 concepts from MAIN.  Add two new unqique to MAIN/SNOMEDCT-WK
		for (int x=11; x<=12; x++) {
			createDummyConcepts(x, concepts, false);
		}
		// add MRCM constraint for concrete attribute
		createRangeConstraint(branchWK, STRENGTH_NUMERATOR, "dec(>#0..)");
		createDummyConcepts(13, concepts, true);
		conceptService.batchCreate(concepts, branchWK);
		codeSystemService.createVersion(codeSystemWK, sampleVersion, "Unit Test Version");

		assertNotNull(codeSystemService.findByDefaultModule(sampleModuleId));

		ReferenceSetMember member = new ReferenceSetMember(null, Concepts.REFSET_SAME_AS_ASSOCIATION, sampleSCTID);
		member.setAdditionalField(ReferenceSetMember.AssociationFields.TARGET_COMP_ID, "88189002");
		memberService.createMember(MAIN, member);

		logger.info("FHIR test data setup complete");
	}

	@PreDestroy
	public void tearDown() throws InterruptedException {
		branchService.deleteAll();
		conceptService.deleteAll();
		codeSystemService.deleteAll();
	}

	private void createDummyConcepts(int sequence, List<Concept> concepts, boolean concrete) {
		// Create dummy concept with descriptions and relationship
		Concept concept = new Concept("25775" + sequence + "006")
						.addRelationship(new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT))
						.addDescription(new Description("Baked potato " + sequence + " (Substance)")
								.setTypeId(Concepts.FSN)
								.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED))
						.addDescription(new Description("Baked potato " + sequence)
								.setTypeId(Concepts.SYNONYM)
								.addLanguageRefsetMember(Concepts.US_EN_LANG_REFSET, Concepts.PREFERRED));
		
		if (concrete) {
			concept.addAxiom(new Relationship(Concepts.ISA, Concepts.SUBSTANCE),
					Relationship.newConcrete(STRENGTH_NUMERATOR, ConcreteValue.newDecimal("#500")));
		} else {
			concept.addAxiom(new Relationship(Concepts.ISA, Concepts.SUBSTANCE));
		}
		concepts.add(concept);
	}

	private void createRangeConstraint(String branchPath, String referencedComponentId, String rangeConstraint) {
		ReferenceSetMember rangeMember = new ReferenceSetMember("900000000000207008", REFSET_MRCM_ATTRIBUTE_RANGE_INTERNATIONAL, referencedComponentId);
		rangeMember.setAdditionalField("rangeConstraint", rangeConstraint);
		rangeMember.setAdditionalField("attributeRule", "");
		rangeMember.setAdditionalField("ruleStrengthId", "723597001");
		rangeMember.setAdditionalField("contentTypeId", "723596005");
		memberService.createMember(branchPath, rangeMember);
	}

}
