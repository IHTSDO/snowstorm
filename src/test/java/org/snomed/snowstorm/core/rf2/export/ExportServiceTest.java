package org.snomed.snowstorm.core.rf2.export;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.jobs.ExportConfiguration;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.rf2.RF2Constants;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.util.StreamUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.config.Config.PAGE_OF_ONE;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.MODULE;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class ExportServiceTest extends AbstractTest {

	private static final String DESCRIPTION_TYPE_REFERENCE_SET = "900000000000538005";

	@Autowired
	private ExportService exportService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	@Autowired
	private ReleaseService releaseService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private IdentifierComponentService identifierComponentService;

	@Autowired
	private BranchService branchService;

	private String descriptionId;
	private String textDefId;
	private ReferenceSetMember owlMember;

	@BeforeEach
	void setup() throws ServiceException {
		referenceSetMemberService.init();

		List<Concept> concepts = new ArrayList<>();

		Concept langRefsetConcept = new Concept(Concepts.LANG_REFSET).addDescription(new Description("640588126141710019", "Language refset"));
		concepts.add(langRefsetConcept);

		Concept gbLangRefsetConcept = new Concept(Concepts.GB_EN_LANG_REFSET).addRelationship(new Relationship(Concepts.ISA, Concepts.LANG_REFSET).setInferred(false));
		concepts.add(gbLangRefsetConcept);

		Concept owlExpressionRefsetConcept = new Concept(Concepts.OWL_EXPRESSION_TYPE_REFERENCE_SET);
		concepts.add(owlExpressionRefsetConcept);

		Concept simpleRefsetConcept = new Concept(Concepts.REFSET_SIMPLE);
		concepts.add(simpleRefsetConcept);

		Concept descriptionFormatRefsetConcept = new Concept(DESCRIPTION_TYPE_REFERENCE_SET);
		concepts.add(descriptionFormatRefsetConcept);

		Concept mrcmDomainRefsetConcept = new Concept(Concepts.REFSET_MRCM_DOMAIN);
		concepts.add(mrcmDomainRefsetConcept);

		// Version first few concepts
		String path = "MAIN";
		conceptService.batchCreate(concepts, path);
		releaseService.createVersion(20100131, path);

		// A concept against another dummy version
		conceptService.create(new Concept(Concepts.OWL_AXIOM_REFERENCE_SET)
				.addDescription(new Description("3494181019", "OWL axiom reference set"))
				.addAxiom(new Relationship(Concepts.ISA, Concepts.OWL_EXPRESSION_TYPE_REFERENCE_SET).setInferred(false)), path);
		releaseService.createVersion(20190131, path);

		String conceptId = "123001";
		descriptionId = "124011";
		textDefId = "124012";
		// Make some junk data just to test export
		Concept concept = new Concept(conceptId, null, true, Concepts.CORE_MODULE, Concepts.PRIMITIVE);
		concept.addDescription(
				new Description(descriptionId, null, true, Concepts.CORE_MODULE, conceptId, "en", Concepts.FSN, "Bleeding (finding)", Concepts.CASE_INSENSITIVE)
						.addLanguageRefsetMember(
								new ReferenceSetMember(null, null, true, Concepts.CORE_MODULE, Concepts.GB_EN_LANG_REFSET, null)
										.setAdditionalField("acceptabilityId", Concepts.PREFERRED)));
		concept.addDescription(
				new Description(textDefId, null, true, Concepts.CORE_MODULE, conceptId, "en", Concepts.TEXT_DEFINITION, "Bleeding Text Def", Concepts.CASE_INSENSITIVE)
						.addLanguageRefsetMember(
								new ReferenceSetMember(null, null, true, Concepts.CORE_MODULE, Concepts.GB_EN_LANG_REFSET, null)
										.setAdditionalField("acceptabilityId", Concepts.PREFERRED)));

		concept.addRelationship(new Relationship("125021", null, true, Concepts.CORE_MODULE, conceptId, "100001", 0, Concepts.ISA, Concepts.STATED_RELATIONSHIP, Concepts.EXISTENTIAL));
		concept.addRelationship(new Relationship("125022", null, true, Concepts.CORE_MODULE, conceptId, "100002", 0, Concepts.ISA, Concepts.INFERRED_RELATIONSHIP, Concepts.EXISTENTIAL));
		concept.addRelationship(new Relationship("125023", null, true, Concepts.CORE_MODULE, conceptId, "100003", 0, Concepts.ISA, Concepts.ADDITIONAL_RELATIONSHIP, Concepts.EXISTENTIAL));

		// add concrete attribute MRCM range constraint for data type
		createRangeConstraint("1142135004", "dec(>#0..)");
		// Concrete inferred relationship
		concept.addRelationship(new Relationship("125024", null, true, Concepts.CORE_MODULE, conceptId, "#100", 0, "1142135004", Concepts.INFERRED_RELATIONSHIP,
				Concepts.EXISTENTIAL));
		// Concrete Additional relationship
		concept.addRelationship(new Relationship("125025", null, true, Concepts.CORE_MODULE, conceptId, "\"PPA23080/021/002\"", 0, "680041000220101", ADDITIONAL_RELATIONSHIP,
				Concepts.EXISTENTIAL));
		conceptService.create(concept, path);

		owlMember = new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.OWL_AXIOM_REFERENCE_SET, "123005000");
		owlMember.setAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION, "TransitiveObjectProperty(:123005000)");
		referenceSetMemberService.createMember(path, owlMember);

		ReferenceSetMember simpleTypeRefsetMember = new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_SIMPLE, Concepts.CLINICAL_FINDING);
		referenceSetMemberService.createMember(path, simpleTypeRefsetMember);

		ReferenceSetMember descriptionTypeRefsetMember = new ReferenceSetMember(Concepts.CORE_MODULE, DESCRIPTION_TYPE_REFERENCE_SET, Concepts.FSN);
		descriptionTypeRefsetMember.setAdditionalField("descriptionFormat", "900000000000540000");
		descriptionTypeRefsetMember.setAdditionalField("descriptionLength", "255");
		referenceSetMemberService.createMember(path, descriptionTypeRefsetMember);

		Identifier identifier = new Identifier("634-6", null, true,  "715515008", "705114005", "22071010000106");
		identifierComponentService.createIdentifier(path, identifier);
	}

	@Test
	void exportDeltaRF2Archive() throws Exception {
		File exportFile = getTempFile("export", ".zip");
		exportFile.deleteOnExit();

		String descriptionTypeRefsetMemberId = referenceSetMemberService.findMembers("MAIN", Concepts.FSN, PAGE_OF_ONE).getContent().get(0).getMemberId();
		String descriptionLanguageRefsetMemberId = referenceSetMemberService.findMembers("MAIN", descriptionId, PAGE_OF_ONE).getContent().get(0).getMemberId();
		String textDefLanguageRefsetMemberId = referenceSetMemberService.findMembers("MAIN", textDefId, PAGE_OF_ONE).getContent().get(0).getMemberId();

		// Run export
		try (FileOutputStream outputStream = new FileOutputStream(exportFile)) {
			ExportConfiguration exportConfiguration = new ExportConfiguration("MAIN", RF2Type.DELTA);
			exportConfiguration.setFilenameEffectiveDate("20210731");
			exportService.createJob(exportConfiguration);
			exportService.exportRF2Archive(exportConfiguration, outputStream);
		}

		// Test export
		try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(exportFile))) {
			// Concepts
			ZipEntry concepts = zipInputStream.getNextEntry();
			assertNotNull(concepts);
			assertEquals("SnomedCT_Export/Delta/Terminology/sct2_Concept_Delta_INT_20210731.txt", concepts.getName());
			List<String> lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.CONCEPT_HEADER, lines.get(0));
			assertEquals("123001\t\t1\t900000000000207008\t900000000000074008", lines.get(1));

			// Descriptions
			ZipEntry descriptions = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/Delta/Terminology/sct2_Description_Delta_INT_20210731.txt", descriptions.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.DESCRIPTION_HEADER, lines.get(0));
			assertEquals("124011\t\t1\t900000000000207008\t123001\ten\t" + Concepts.FSN + "\tBleeding (finding)\t900000000000448009", lines.get(1));

			// Text Definitions
			ZipEntry textDefinitions = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/Delta/Terminology/sct2_TextDefinition_Delta_INT_20210731.txt", textDefinitions.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.DESCRIPTION_HEADER, lines.get(0));
			assertEquals("124012\t\t1\t900000000000207008\t123001\ten\t" + Concepts.TEXT_DEFINITION + "\tBleeding Text Def\t900000000000448009", lines.get(1));

			// Stated Relationships
			ZipEntry statedRelationships = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/Delta/Terminology/sct2_StatedRelationship_Delta_INT_20210731.txt", statedRelationships.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.RELATIONSHIP_HEADER, lines.get(0));
			assertEquals("125021\t\t1\t900000000000207008\t123001\t100001\t0\t116680003\t900000000000010007\t900000000000451002", lines.get(1));

			// Inferred (non-concrete) Relationships
			ZipEntry relationships = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/Delta/Terminology/sct2_Relationship_Delta_INT_20210731.txt", relationships.getName());
			lines = getLines(zipInputStream);
			assertEquals(3, lines.size());
			assertEquals(RF2Constants.RELATIONSHIP_HEADER, lines.get(0));
			assertTrue(lines.contains("125022\t\t1\t900000000000207008\t123001\t100002\t0\t116680003\t900000000000011006\t900000000000451002"));
			assertTrue(lines.contains("125023\t\t1\t900000000000207008\t123001\t100003\t0\t116680003\t900000000000227009\t900000000000451002"));

			// Concrete Relationships
			ZipEntry concreteRelationships = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/Delta/Terminology/sct2_RelationshipConcreteValues_Delta_INT_20210731.txt", concreteRelationships.getName());
			lines = getLines(zipInputStream);
			assertEquals(3, lines.size());
			assertEquals(RF2Constants.CONCRETE_RELATIONSHIP_HEADER, lines.get(0));
			// Inferred type concrete relationship
			assertTrue(lines.contains("125024\t\t1\t900000000000207008\t123001\t#100\t0\t1142135004\t900000000000011006\t900000000000451002"));
			// Additional type concrete relationship
			assertTrue(lines.contains("125025\t\t1\t900000000000207008\t123001\t\"PPA23080/021/002\"\t0\t680041000220101\t900000000000227009\t900000000000451002"));

			// Identifier
			ZipEntry identifierTypes = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/Delta/Terminology/sct2_Identifier_Delta_INT_20210731.txt", identifierTypes.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.IDENTIFIER_HEADER, lines.get(0));
			assertEquals( "634-6\t\t1\t715515008\t705114005\t22071010000106", lines.get(1));

			// Description Type Refset
			ZipEntry descriptionTypes = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/Delta/Refset/Metadata/der2_ciRefset_DescriptionTypeDelta_INT_20210731.txt", descriptionTypes.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.SIMPLE_REFSET_HEADER + "\tdescriptionFormat\tdescriptionLength", lines.get(0));
			assertEquals(descriptionTypeRefsetMemberId + "\t\t1\t900000000000207008\t900000000000538005\t900000000000003001\t900000000000540000\t255", lines.get(1));

			// Language Refset
			ZipEntry langRefset = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/Delta/Refset/Language/der2_cRefset_Language900000000000508004Delta_INT_20210731.txt", langRefset.getName());
			lines = getLines(zipInputStream);
			assertEquals(3, lines.size());
			assertEquals(RF2Constants.SIMPLE_REFSET_HEADER + "\tacceptabilityId", lines.get(0));
			assertTrue(lines.contains(descriptionLanguageRefsetMemberId + "\t\t1\t900000000000207008\t900000000000508004\t124011\t900000000000548007"));
			assertTrue(lines.contains(textDefLanguageRefsetMemberId + "\t\t1\t900000000000207008\t900000000000508004\t124012\t900000000000548007"));

			// OWL Axiom Refset
			ZipEntry axioms = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/Delta/Terminology/sct2_sRefset_OWLExpression733073007Delta_INT_20210731.txt", axioms.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.SIMPLE_REFSET_HEADER + "\towlExpression", lines.get(0));
			assertEquals(owlMember.getId() + "\t\t1\t900000000000207008\t733073007\t123005000\tTransitiveObjectProperty(:123005000)", lines.get(1));
		}
	}

	@Test
	void exportExtensionDeltaRF2Archive() throws Exception {
		File exportFile = getTempFile("export", ".zip");
		exportFile.deleteOnExit();

		// Create US code system
		// Reuse content from MAIN.. not a very realistic test as the unversioned content should be on the US branch but this is sufficient for testing this class.
		codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-US", "MAIN/SNOMEDCT-US"));

		String descriptionTypeRefsetMemberId = referenceSetMemberService.findMembers("MAIN/SNOMEDCT-US", Concepts.FSN, PAGE_OF_ONE).getContent().get(0).getMemberId();
		String descriptionLanguageRefsetMemberId = referenceSetMemberService.findMembers("MAIN/SNOMEDCT-US", descriptionId, PAGE_OF_ONE).getContent().get(0).getMemberId();
		String textDefLanguageRefsetMemberId = referenceSetMemberService.findMembers("MAIN/SNOMEDCT-US", textDefId, PAGE_OF_ONE).getContent().get(0).getMemberId();

		// Run export
		try (FileOutputStream outputStream = new FileOutputStream(exportFile)) {
			ExportConfiguration exportConfiguration = new ExportConfiguration("MAIN/SNOMEDCT-US", RF2Type.DELTA);
			exportConfiguration.setFilenameEffectiveDate("20210801");
			exportService.createJob(exportConfiguration);
			exportService.exportRF2Archive(exportConfiguration, outputStream);
		}

		// Test export
		try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(exportFile))) {
			// Concepts
			ZipEntry concepts = zipInputStream.getNextEntry();
			assertNotNull(concepts);
			assertEquals("SnomedCT_Export/Delta/Terminology/sct2_Concept_Delta_US_20210801.txt", concepts.getName());
			List<String> lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.CONCEPT_HEADER, lines.get(0));
			assertEquals("123001\t\t1\t900000000000207008\t900000000000074008", lines.get(1));

			// Descriptions
			ZipEntry descriptions = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/Delta/Terminology/sct2_Description_Delta_US_20210801.txt", descriptions.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.DESCRIPTION_HEADER, lines.get(0));
			assertEquals("124011\t\t1\t900000000000207008\t123001\ten\t" + Concepts.FSN + "\tBleeding (finding)\t900000000000448009", lines.get(1));

			// Text Definitions
			ZipEntry textDefinitions = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/Delta/Terminology/sct2_TextDefinition_Delta_US_20210801.txt", textDefinitions.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.DESCRIPTION_HEADER, lines.get(0));
			assertEquals("124012\t\t1\t900000000000207008\t123001\ten\t" + Concepts.TEXT_DEFINITION + "\tBleeding Text Def\t900000000000448009", lines.get(1));

			// Stated Relationships
			ZipEntry statedRelationships = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/Delta/Terminology/sct2_StatedRelationship_Delta_US_20210801.txt", statedRelationships.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.RELATIONSHIP_HEADER, lines.get(0));
			assertEquals("125021\t\t1\t900000000000207008\t123001\t100001\t0\t116680003\t900000000000010007\t900000000000451002", lines.get(1));

			// Inferred (non-concrete) Relationships
			ZipEntry relationships = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/Delta/Terminology/sct2_Relationship_Delta_US_20210801.txt", relationships.getName());
			lines = getLines(zipInputStream);
			assertEquals(3, lines.size());
			assertEquals(RF2Constants.RELATIONSHIP_HEADER, lines.get(0));
			assertTrue(lines.contains("125022\t\t1\t900000000000207008\t123001\t100002\t0\t116680003\t900000000000011006\t900000000000451002"));
			assertTrue(lines.contains("125023\t\t1\t900000000000207008\t123001\t100003\t0\t116680003\t900000000000227009\t900000000000451002"));

			// Concrete Relationships
			ZipEntry concreteRelationships = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/Delta/Terminology/sct2_RelationshipConcreteValues_Delta_US_20210801.txt", concreteRelationships.getName());
			lines = getLines(zipInputStream);
			assertEquals(3, lines.size());
			assertEquals(RF2Constants.CONCRETE_RELATIONSHIP_HEADER, lines.get(0));
			// Inferred type concrete relationship
			assertTrue(lines.contains("125024\t\t1\t900000000000207008\t123001\t#100\t0\t1142135004\t900000000000011006\t900000000000451002"));
			// Additional type concrete relationship
			assertTrue(lines.contains("125025\t\t1\t900000000000207008\t123001\t\"PPA23080/021/002\"\t0\t680041000220101\t900000000000227009\t900000000000451002"));

			// Identifier
			ZipEntry identifierTypes = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/Delta/Terminology/sct2_Identifier_Delta_US_20210801.txt", identifierTypes.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.IDENTIFIER_HEADER, lines.get(0));
			assertEquals( "634-6\t\t1\t715515008\t705114005\t22071010000106", lines.get(1));

			// Description Type Refset
			ZipEntry descriptionTypes = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/Delta/Refset/Metadata/der2_ciRefset_DescriptionTypeDelta_US_20210801.txt", descriptionTypes.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.SIMPLE_REFSET_HEADER + "\tdescriptionFormat\tdescriptionLength", lines.get(0));
			assertEquals(descriptionTypeRefsetMemberId + "\t\t1\t900000000000207008\t900000000000538005\t900000000000003001\t900000000000540000\t255", lines.get(1));

			// Language Refset
			ZipEntry langRefset = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/Delta/Refset/Language/der2_cRefset_Language900000000000508004Delta_US_20210801.txt", langRefset.getName());
			lines = getLines(zipInputStream);
			assertEquals(3, lines.size());
			assertEquals(RF2Constants.SIMPLE_REFSET_HEADER + "\tacceptabilityId", lines.get(0));
			assertTrue(lines.contains(descriptionLanguageRefsetMemberId + "\t\t1\t900000000000207008\t900000000000508004\t124011\t900000000000548007"));
			assertTrue(lines.contains(textDefLanguageRefsetMemberId + "\t\t1\t900000000000207008\t900000000000508004\t124012\t900000000000548007"));

			// OWL Axiom Refset
			ZipEntry axioms = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/Delta/Terminology/sct2_sRefset_OWLExpression733073007Delta_US_20210801.txt", axioms.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.SIMPLE_REFSET_HEADER + "\towlExpression", lines.get(0));
			assertEquals(owlMember.getId() + "\t\t1\t900000000000207008\t733073007\t123005000\tTransitiveObjectProperty(:123005000)", lines.get(1));
		}
	}

	@Test
	void exportDeltaRF2ArchiveLegacyNaming() throws Exception {
		File exportFile = getTempFile("export", ".zip");
		exportFile.deleteOnExit();

		String descriptionTypeRefsetMemberId = referenceSetMemberService.findMembers("MAIN", Concepts.FSN, PAGE_OF_ONE).getContent().get(0).getMemberId();
		String descriptionLanguageRefsetMemberId = referenceSetMemberService.findMembers("MAIN", descriptionId, PAGE_OF_ONE).getContent().get(0).getMemberId();
		String textDefLanguageRefsetMemberId = referenceSetMemberService.findMembers("MAIN", textDefId, PAGE_OF_ONE).getContent().get(0).getMemberId();

		// Run export
		try (FileOutputStream outputStream = new FileOutputStream(exportFile)) {
			ExportConfiguration exportConfiguration = new ExportConfiguration("MAIN", RF2Type.DELTA);
			exportConfiguration.setFilenameEffectiveDate("20210731");
			exportConfiguration.setLegacyZipNaming(true);
			exportService.createJob(exportConfiguration);
			exportService.exportRF2Archive(exportConfiguration, outputStream);
		}

		// Test export
		try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(exportFile))) {
			// Concepts
			ZipEntry concepts = zipInputStream.getNextEntry();
			assertNotNull(concepts);
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_Concept_Delta_INT_20210731.txt", concepts.getName());
			List<String> lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.CONCEPT_HEADER, lines.get(0));
			assertEquals("123001\t\t1\t900000000000207008\t900000000000074008", lines.get(1));

			// Descriptions
			ZipEntry descriptions = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_Description_Delta_INT_20210731.txt", descriptions.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.DESCRIPTION_HEADER, lines.get(0));
			assertEquals("124011\t\t1\t900000000000207008\t123001\ten\t" + Concepts.FSN + "\tBleeding (finding)\t900000000000448009", lines.get(1));

			// Text Definitions
			ZipEntry textDefinitions = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_TextDefinition_Delta_INT_20210731.txt", textDefinitions.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.DESCRIPTION_HEADER, lines.get(0));
			assertEquals("124012\t\t1\t900000000000207008\t123001\ten\t" + Concepts.TEXT_DEFINITION + "\tBleeding Text Def\t900000000000448009", lines.get(1));

			// Stated Relationships
			ZipEntry statedRelationships = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_StatedRelationship_Delta_INT_20210731.txt", statedRelationships.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.RELATIONSHIP_HEADER, lines.get(0));
			assertEquals("125021\t\t1\t900000000000207008\t123001\t100001\t0\t116680003\t900000000000010007\t900000000000451002", lines.get(1));

			// Inferred (non-concrete) Relationships
			ZipEntry relationships = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_Relationship_Delta_INT_20210731.txt", relationships.getName());
			lines = getLines(zipInputStream);
			assertEquals(3, lines.size());
			assertEquals(RF2Constants.RELATIONSHIP_HEADER, lines.get(0));
			assertTrue(lines.contains("125022\t\t1\t900000000000207008\t123001\t100002\t0\t116680003\t900000000000011006\t900000000000451002"));
			assertTrue(lines.contains("125023\t\t1\t900000000000207008\t123001\t100003\t0\t116680003\t900000000000227009\t900000000000451002"));

			// Concrete Relationships
			ZipEntry concreteRelationships = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_RelationshipConcreteValues_Delta_INT_20210731.txt", concreteRelationships.getName());
			lines = getLines(zipInputStream);
			assertEquals(3, lines.size());
			assertEquals(RF2Constants.CONCRETE_RELATIONSHIP_HEADER, lines.get(0));
			// Inferred type concrete relationship
			assertTrue(lines.contains("125024\t\t1\t900000000000207008\t123001\t#100\t0\t1142135004\t900000000000011006\t900000000000451002"));
			// Additional type concrete relationship
			assertTrue(lines.contains("125025\t\t1\t900000000000207008\t123001\t\"PPA23080/021/002\"\t0\t680041000220101\t900000000000227009\t900000000000451002"));

			// Identifier
			ZipEntry identifierTypes = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_Identifier_Delta_INT_20210731.txt", identifierTypes.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.IDENTIFIER_HEADER, lines.get(0));
			assertEquals( "634-6\t\t1\t715515008\t705114005\t22071010000106", lines.get(1));

			// Description Type Refset
			ZipEntry descriptionTypes = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Refset/Metadata/der2_ciRefset_DescriptionTypeDelta_INT_20210731.txt", descriptionTypes.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.SIMPLE_REFSET_HEADER + "\tdescriptionFormat\tdescriptionLength", lines.get(0));
			assertEquals(descriptionTypeRefsetMemberId + "\t\t1\t900000000000207008\t900000000000538005\t900000000000003001\t900000000000540000\t255", lines.get(1));

			// Language Refset
			ZipEntry langRefset = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Refset/Language/der2_cRefset_Language900000000000508004Delta_INT_20210731.txt", langRefset.getName());
			lines = getLines(zipInputStream);
			assertEquals(3, lines.size());
			assertEquals(RF2Constants.SIMPLE_REFSET_HEADER + "\tacceptabilityId", lines.get(0));
			assertTrue(lines.contains(descriptionLanguageRefsetMemberId + "\t\t1\t900000000000207008\t900000000000508004\t124011\t900000000000548007"));
			assertTrue(lines.contains(textDefLanguageRefsetMemberId + "\t\t1\t900000000000207008\t900000000000508004\t124012\t900000000000548007"));

			// OWL Axiom Refset
			ZipEntry axioms = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_sRefset_OWLExpression733073007Delta_INT_20210731.txt", axioms.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.SIMPLE_REFSET_HEADER + "\towlExpression", lines.get(0));
			assertEquals(owlMember.getId() + "\t\t1\t900000000000207008\t733073007\t123005000\tTransitiveObjectProperty(:123005000)", lines.get(1));
		}
	}

	@Test
	void exportRF2ArchiveForClassification() throws Exception {
		File exportFile = getTempFile("export", ".zip");
		exportFile.deleteOnExit();

		// Run export
		try (FileOutputStream outputStream = new FileOutputStream(exportFile)) {
			ExportConfiguration exportConfiguration = new ExportConfiguration("MAIN", RF2Type.DELTA);

			// FOR Classification
			exportConfiguration.setConceptsAndRelationshipsOnly(true);
			exportConfiguration.setLegacyZipNaming(true);

			exportConfiguration.setFilenameEffectiveDate("20210731");
			exportService.createJob(exportConfiguration);
			exportService.exportRF2Archive(exportConfiguration, outputStream);
		}

		// Test export
		try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(exportFile))) {
			// Concepts
			ZipEntry concepts = zipInputStream.getNextEntry();
			assertNotNull(concepts);
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_Concept_Delta_INT_20210731.txt", concepts.getName());
			List<String> lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.CONCEPT_HEADER, lines.get(0));
			assertEquals("123001\t\t1\t900000000000207008\t900000000000074008", lines.get(1));

			// Stated Relationships
			ZipEntry statedRelationships = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_StatedRelationship_Delta_INT_20210731.txt", statedRelationships.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.RELATIONSHIP_HEADER, lines.get(0));
			assertEquals("125021\t\t1\t900000000000207008\t123001\t100001\t0\t116680003\t900000000000010007\t900000000000451002", lines.get(1));

			// Inferred (non-concrete) Relationships
			ZipEntry relationships = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_Relationship_Delta_INT_20210731.txt", relationships.getName());
			lines = getLines(zipInputStream);
			assertEquals(3, lines.size());
			assertEquals(RF2Constants.RELATIONSHIP_HEADER, lines.get(0));
			assertTrue(lines.contains("125022\t\t1\t900000000000207008\t123001\t100002\t0\t116680003\t900000000000011006\t900000000000451002"));
			assertTrue(lines.contains("125023\t\t1\t900000000000207008\t123001\t100003\t0\t116680003\t900000000000227009\t900000000000451002"));

			// Concrete Relationships
			ZipEntry concreteRelationships = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_RelationshipConcreteValues_Delta_INT_20210731.txt", concreteRelationships.getName());
			lines = getLines(zipInputStream);
			assertEquals(3, lines.size());
			assertEquals(RF2Constants.CONCRETE_RELATIONSHIP_HEADER, lines.get(0));
			// Inferred type concrete relationship
			assertTrue(lines.contains("125024\t\t1\t900000000000207008\t123001\t#100\t0\t1142135004\t900000000000011006\t900000000000451002"));
			// Additional type concrete relationship
			assertTrue(lines.contains("125025\t\t1\t900000000000207008\t123001\t\"PPA23080/021/002\"\t0\t680041000220101\t900000000000227009\t900000000000451002"));


			// Identifier
			ZipEntry identifiers = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_Identifier_Delta_INT_20210731.txt", identifiers.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.IDENTIFIER_HEADER, lines.get(0));
			assertEquals( "634-6\t\t1\t715515008\t705114005\t22071010000106", lines.get(1));

			// OWL Axiom Refset
			ZipEntry axioms = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_sRefset_OWLExpression733073007Delta_INT_20210731.txt", axioms.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.SIMPLE_REFSET_HEADER + "\towlExpression", lines.get(0));
			assertEquals(owlMember.getId() + "\t\t1\t900000000000207008\t733073007\t123005000\tTransitiveObjectProperty(:123005000)", lines.get(1));
		}
	}

	@Test
	void exportRF2ArchiveForRefsetOnly() throws Exception {
		File exportFile = getTempFile("export", ".zip");
		exportFile.deleteOnExit();

		// Run export
		try (FileOutputStream outputStream = new FileOutputStream(exportFile)) {
			ExportConfiguration exportConfiguration = new ExportConfiguration("MAIN", RF2Type.SNAPSHOT);

			// FOR Refsets only
			Set<String> refsetIds = new HashSet<>();
			refsetIds.add("900000000000508004");
			exportConfiguration.setRefsetIds(refsetIds);
			exportConfiguration.setConceptsAndRelationshipsOnly(false);
			exportConfiguration.setLegacyZipNaming(false);
			exportConfiguration.setUnpromotedChangesOnly(false);

			exportConfiguration.setFilenameEffectiveDate("20210731");
			exportService.createJob(exportConfiguration);
			exportService.exportRF2Archive(exportConfiguration, outputStream);
		}
		
		String descriptionTypeRefsetMemberId = referenceSetMemberService.findMembers("MAIN", Concepts.FSN, PAGE_OF_ONE).getContent().get(0).getMemberId();
		String descriptionLanguageRefsetMemberId = referenceSetMemberService.findMembers("MAIN", descriptionId, PAGE_OF_ONE).getContent().get(0).getMemberId();
		String textDefLanguageRefsetMemberId = referenceSetMemberService.findMembers("MAIN", textDefId, PAGE_OF_ONE).getContent().get(0).getMemberId();


		// Test export
		try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(exportFile))) {

			ZipEntry file = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/Snapshot/Refset/Language/der2_cRefset_Language900000000000508004Snapshot_INT_20210731.txt", file.getName());
			List<String> lines = getLines(zipInputStream);
			assertEquals(3, lines.size());
			assertEquals(RF2Constants.SIMPLE_REFSET_HEADER + "\tacceptabilityId", lines.get(0));
			assertEquals(textDefLanguageRefsetMemberId + "\t\t1\t900000000000207008\t900000000000508004\t124012\t900000000000548007", lines.get(1));
			ZipEntry file2 = zipInputStream.getNextEntry();
			assertNull(file2);
		}
	}

	@Test
	void exportRF2ArchiveWithTransientEffectiveTime() throws Exception {
		File exportFile = getTempFile("export", ".zip");
		exportFile.deleteOnExit();

		// Run export
		try (FileOutputStream outputStream = new FileOutputStream(exportFile)) {
			ExportConfiguration exportConfiguration = new ExportConfiguration("MAIN", RF2Type.DELTA);

			exportConfiguration.setConceptsAndRelationshipsOnly(false);

			exportConfiguration.setFilenameEffectiveDate("20190416");
			exportConfiguration.setTransientEffectiveTime("20190731");
			exportService.createJob(exportConfiguration);
			exportService.exportRF2Archive(exportConfiguration, outputStream);
		}

		// Test export
		try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(exportFile))) {
			// Concepts
			ZipEntry concepts = zipInputStream.getNextEntry();
			assertNotNull(concepts);
			assertEquals("SnomedCT_Export/Delta/Terminology/sct2_Concept_Delta_INT_20190416.txt", concepts.getName());
			List<String> lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.CONCEPT_HEADER, lines.get(0));
			assertEquals("123001\t20190731\t1\t900000000000207008\t900000000000074008", lines.get(1));

			// Descriptions
			ZipEntry descriptions = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/Delta/Terminology/sct2_Description_Delta_INT_20190416.txt", descriptions.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RF2Constants.DESCRIPTION_HEADER, lines.get(0));
			assertEquals("124011\t20190731\t1\t900000000000207008\t123001\ten\t" + Concepts.FSN + "\tBleeding (finding)\t900000000000448009", lines.get(1));
		}

	}

	@Test
	void exportSnapshot() throws Exception {
		File exportFile = getTempFile("export", ".zip");
		exportFile.deleteOnExit();

		// Run export
		try (FileOutputStream outputStream = new FileOutputStream(exportFile)) {
			ExportConfiguration exportConfiguration = new ExportConfiguration("MAIN", RF2Type.SNAPSHOT);

			exportConfiguration.setConceptsAndRelationshipsOnly(false);
			exportConfiguration.setFilenameEffectiveDate("20190904");
			exportService.createJob(exportConfiguration);
			exportService.exportRF2Archive(exportConfiguration, outputStream);
		}

		// Test export
		try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(exportFile))) {
			// Concepts
			ZipEntry concepts = zipInputStream.getNextEntry();
			assertNotNull(concepts);
			assertEquals("SnomedCT_Export/Snapshot/Terminology/sct2_Concept_Snapshot_INT_20190904.txt", concepts.getName());
			List<String> lines = getLines(zipInputStream);
			assertEquals(9, lines.size());
			printLines(lines);

			assertEquals(RF2Constants.CONCEPT_HEADER, lines.get(0));
			assertTrue(lines.contains("123001\t\t1\t900000000000207008\t900000000000074008"));
			assertTrue(lines.contains("733073007\t20190131\t1\t900000000000207008\t900000000000074008"));
			assertTrue(lines.contains("900000000000508004\t20100131\t1\t900000000000207008\t900000000000074008"));
			assertTrue(lines.contains("900000000000506000\t20100131\t1\t900000000000207008\t900000000000074008"));
			assertTrue(lines.contains("723589008\t20100131\t1\t900000000000207008\t900000000000074008"));
			assertTrue(lines.contains("762676003\t20100131\t1\t900000000000207008\t900000000000074008"));
			assertTrue(lines.contains("446609009\t20100131\t1\t900000000000207008\t900000000000074008"));
			assertTrue(lines.contains("900000000000538005\t20100131\t1\t900000000000207008\t900000000000074008"));

			// Descriptions
			ZipEntry descriptions = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/Snapshot/Terminology/sct2_Description_Snapshot_INT_20190904.txt", descriptions.getName());
			lines = getLines(zipInputStream);
			printLines(lines);

			assertEquals(4, lines.size());
			assertEquals(RF2Constants.DESCRIPTION_HEADER, lines.get(0));
			assertTrue(lines.contains("3494181019\t20190131\t1\t900000000000207008\t733073007\ten\t900000000000013009\tOWL axiom reference set\t900000000000448009"));
			assertTrue(lines.contains("640588126141710019\t20100131\t1\t900000000000207008\t900000000000506000\ten\t900000000000013009\tLanguage refset\t900000000000448009"));
			assertTrue(lines.contains("124011\t\t1\t900000000000207008\t123001\ten\t900000000000003001\tBleeding (finding)\t900000000000448009"));
		}

	}

	@Test
	@SuppressWarnings("UnusedAssignment")
	void exportSnapshotWithStartEffectiveTime() throws Exception {
		File exportFile = getTempFile("export", ".zip");
		exportFile.deleteOnExit();

		// Run export
		try (FileOutputStream outputStream = new FileOutputStream(exportFile)) {
			ExportConfiguration exportConfiguration = new ExportConfiguration("MAIN", RF2Type.SNAPSHOT);

			exportConfiguration.setConceptsAndRelationshipsOnly(false);
			exportConfiguration.setFilenameEffectiveDate("20190904");
			exportConfiguration.setStartEffectiveTime("20190131");
			exportService.createJob(exportConfiguration);
			exportService.exportRF2Archive(exportConfiguration, outputStream);
		}

		// Test export
		try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(exportFile))) {
			// Concepts
			ZipEntry concepts = zipInputStream.getNextEntry();
			assertNotNull(concepts);
			assertEquals("SnomedCT_Export/Snapshot/Terminology/sct2_Concept_Snapshot_INT_20190904.txt", concepts.getName());
			List<String> lines = getLines(zipInputStream);
			printLines(lines);
			assertEquals(3, lines.size());
			int line = 0;

			assertEquals(RF2Constants.CONCEPT_HEADER, lines.get(line++));
			assertEquals("733073007\t20190131\t1\t900000000000207008\t900000000000074008", lines.get(line++));
			assertEquals("123001\t\t1\t900000000000207008\t900000000000074008", lines.get(line++));

			// Descriptions
			ZipEntry descriptions = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/Snapshot/Terminology/sct2_Description_Snapshot_INT_20190904.txt", descriptions.getName());
			lines = getLines(zipInputStream);
			printLines(lines);

			assertEquals(3, lines.size());
			line = 0;
			assertEquals(RF2Constants.DESCRIPTION_HEADER, lines.get(line++));
			assertEquals("3494181019\t20190131\t1\t900000000000207008\t733073007\ten\t900000000000013009\tOWL axiom reference set\t900000000000448009", lines.get(line++));
			assertEquals("124011\t\t1\t900000000000207008\t123001\ten\t900000000000003001\tBleeding (finding)\t900000000000448009", lines.get(line++));
		}
	}

	@Test
	void testExportRefsetMemberWithBlankFields() throws IOException {
		String path = "MAIN";
		owlMember = new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_DOMAIN, "433590000");
		owlMember.setAdditionalField("domainConstraint", "<< 433590000 |Administration of substance via specific route (procedure)|");
		owlMember.setAdditionalField("parentDomain", "<< 71388002 |Procedure (procedure)|");
		owlMember.setAdditionalField("proximalPrimitiveConstraint", "something here");
		owlMember.setAdditionalField("proximalPrimitiveRefinement", "something here");
		owlMember.setAdditionalField("domainTemplateForPrecoordination", "something here");
		owlMember.setAdditionalField("domainTemplateForPostcoordination", "something here");

		// Not setting the 'guideURL' field..

		referenceSetMemberService.createMember(path, owlMember);

		File exportFile = getTempFile("export", ".zip");
		exportFile.deleteOnExit();

		// Run export
		try (FileOutputStream outputStream = new FileOutputStream(exportFile)) {
			ExportConfiguration exportConfiguration = new ExportConfiguration("MAIN", RF2Type.SNAPSHOT);

			exportConfiguration.setConceptsAndRelationshipsOnly(false);
			exportConfiguration.setFilenameEffectiveDate("20190904");
			exportConfiguration.setStartEffectiveTime("20190131");
			exportService.createJob(exportConfiguration);
			exportService.exportRF2Archive(exportConfiguration, outputStream);
		}

		// Test export
		try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(exportFile))) {
			ZipEntry zipEntry;
			while ((zipEntry = zipInputStream.getNextEntry()) != null) {
				if (zipEntry.getName().contains("der2_sssssssRefset_MRCMDomainSnapshot")) {
					List<String> lines = getLines(zipInputStream);
					assertEquals(2, lines.size());
					printLines(lines);
					assertEquals("id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tdomainConstraint\tparentDomain\tproximalPrimitiveConstraint\tproximalPrimitiveRefinement\tdomainTemplateForPrecoordination\tdomainTemplateForPostcoordination\tguideURL", lines.get(0));
					assertTrue(lines.get(1).endsWith("\t\t1\t900000000000207008\t723589008\t433590000\t<< 433590000 |Administration of substance via specific route (procedure)|\t<< 71388002 |Procedure (procedure)|\tsomething here\tsomething here\tsomething here\tsomething here\t"));
				}
			}
		}
	}

	@Test
	void exportRF2Archive_ShouldIncludeTransientEffectiveTime_WhenGivenMDRS() throws IOException {
		// Prepare for export
		File exportFile = getTempFile("export", ".zip");
		exportFile.deleteOnExit();

		// Create simple mdrs entry
		ReferenceSetMember mdrs = new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.MODULE_DEPENDENCY_REFERENCE_SET, Concepts.MODEL_MODULE);
		referenceSetMemberService.createMember("MAIN", mdrs);

		// Export content
		try (FileOutputStream outputStream = new FileOutputStream(exportFile)) {
			ExportConfiguration exportConfiguration = new ExportConfiguration("MAIN", RF2Type.SNAPSHOT);
			exportConfiguration.setTransientEffectiveTime("20250101");
			exportService.createJob(exportConfiguration);
			exportService.exportRF2Archive(exportConfiguration, outputStream);
		}

		// Assert
		try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(exportFile))) {
			ZipEntry zipEntry;
			while ((zipEntry = zipInputStream.getNextEntry()) != null) {
				if (zipEntry.getName().contains("ModuleDependency")) {
					List<String> lines = getLines(zipInputStream);
					assertEquals(2, lines.size());
					assertEquals("id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tsourceEffectiveTime\ttargetEffectiveTime", lines.get(0));
					assertEquals(mdrs.getMemberId() + "\t20250101\t1\t900000000000207008\t900000000000534007\t900000000000012004\t20250101\t20250101", lines.get(1));
				}
			}
		}
	}

	@Test
	void exportRF2Archive_ShouldExportExpectedMDRS_WhenGivenExtension() throws ServiceException, IOException {
		CodeSystem codeSystem;
		Concept concept;
		ReferenceSetMember mdrs;

		// Create International
		codeSystem = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));

		// International creates new module
		concept = conceptService.create(
				new Concept()
						.addDescription(new Description("Drugs module (module)").setTypeId(FSN))
						.addDescription(new Description("Drugs module").setTypeId(SYNONYM))
						.addAxiom(new Relationship(ISA, MODULE)),
				"MAIN"
		);
		String drugsModuleId = concept.getConceptId();

		// International adds content to new module
		concept = conceptService.create(
				new Concept()
						.setModuleId(drugsModuleId)
						.addDescription(new Description("Medicine (medicine)").setTypeId(FSN).setModuleId(drugsModuleId))
						.addDescription(new Description("Medicine").setTypeId(SYNONYM).setModuleId(drugsModuleId))
						.addAxiom(new Relationship(ISA, SNOMEDCT_ROOT).setModuleId(drugsModuleId)),
				"MAIN"
		);

		// Create International MDRS
		mdrs = new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.MODULE_DEPENDENCY_REFERENCE_SET, Concepts.MODEL_MODULE);
		referenceSetMemberService.createMember("MAIN", mdrs);

		mdrs = new ReferenceSetMember(drugsModuleId, Concepts.MODULE_DEPENDENCY_REFERENCE_SET, CORE_MODULE);
		referenceSetMemberService.createMember("MAIN", mdrs);

		// Version International
		codeSystemService.createVersion(codeSystem, 20250101, "20250101");

		// Create Extension
		codeSystem = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-XX", "MAIN/SNOMEDCT-XX"));
		concept = conceptService.create(
				new Concept()
						.addDescription(new Description("Extension module (module)").setTypeId(FSN))
						.addDescription(new Description("Extension module").setTypeId(SYNONYM))
						.addAxiom(new Relationship(ISA, MODULE)),
				"MAIN/SNOMEDCT-XX"
		);
		String extModuleId = concept.getConceptId();
		branchService.updateMetadata(
				"MAIN/SNOMEDCT-XX",
				Map.of(Config.DEFAULT_MODULE_ID_KEY, extModuleId,
						Config.EXPECTED_EXTENSION_MODULES, List.of(extModuleId),
						Config.DEPENDENCY_PACKAGE, "20250101.zip"
				)
		);

		// Create Extension MDRS
		mdrs = new ReferenceSetMember(extModuleId, Concepts.MODULE_DEPENDENCY_REFERENCE_SET, CORE_MODULE);
		mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME, "");
		mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME, "20250101");
		mdrs = referenceSetMemberService.createMember("MAIN/SNOMEDCT-XX", mdrs);
		String extMdrsId = mdrs.getMemberId();

		// Version Extension
		codeSystem = codeSystemService.find("SNOMEDCT-XX");
		codeSystemService.createVersion(codeSystem, 20250102, "20250102");

		// Assert Extension post versioning
		mdrs = referenceSetMemberService.findMember("MAIN/SNOMEDCT-XX", extMdrsId);
		assertTrue(mdrs.isReleased());
		assertEquals(20250102, mdrs.getEffectiveTimeI());
		assertEquals(20250102, mdrs.getReleasedEffectiveTime());
		assertEquals("20250102", mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME));
		assertEquals("20250101", mdrs.getAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME));

		List<String> lines = getFileFromSnapshotExport("MAIN/SNOMEDCT-XX", "ModuleDependency");
		assertEquals(2, lines.size());
		assertEquals("id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tsourceEffectiveTime\ttargetEffectiveTime", lines.get(0));
		assertEquals(extMdrsId + "\t20250102\t1\t" + extModuleId + "\t900000000000534007\t" + CORE_MODULE + "\t20250102\t20250101", lines.get(1));
	}

	@Test
	void exportRF2Archive_ShouldExportDifferentMDRS_WhenGivenInternationalAndExtension() throws ServiceException, IOException {
		CodeSystem codeSystem;
		Concept concept;
		ReferenceSetMember mdrs;

		// Create International
		codeSystem = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT", "MAIN"));

		// International creates new module
		concept = conceptService.create(new Concept().addDescription(new Description("Drugs module (module)").setTypeId(FSN)).addDescription(new Description("Drugs module").setTypeId(SYNONYM)).addAxiom(new Relationship(ISA, MODULE)), "MAIN");
		String drugsModuleId = concept.getConceptId();

		// International adds content to new module
		concept = conceptService.create(new Concept().setModuleId(drugsModuleId).addDescription(new Description("Medicine (medicine)").setTypeId(FSN).setModuleId(drugsModuleId)).addDescription(new Description("Medicine").setTypeId(SYNONYM).setModuleId(drugsModuleId)).addAxiom(new Relationship(ISA, SNOMEDCT_ROOT).setModuleId(drugsModuleId)), "MAIN");

		// Create International MDRS
		mdrs = new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.MODULE_DEPENDENCY_REFERENCE_SET, Concepts.MODEL_MODULE);
		referenceSetMemberService.createMember("MAIN", mdrs);

		mdrs = new ReferenceSetMember(drugsModuleId, Concepts.MODULE_DEPENDENCY_REFERENCE_SET, CORE_MODULE);
		referenceSetMemberService.createMember("MAIN", mdrs);

		// Version International
		codeSystemService.createVersion(codeSystem, 20250101, "20250101");

		// Create Extension
		codeSystem = codeSystemService.createCodeSystem(new CodeSystem("SNOMEDCT-XX", "MAIN/SNOMEDCT-XX"));
		concept = conceptService.create(new Concept().addDescription(new Description("Extension module (module)").setTypeId(FSN)).addDescription(new Description("Extension module").setTypeId(SYNONYM)).addAxiom(new Relationship(ISA, MODULE)), "MAIN/SNOMEDCT-XX");
		String extModuleId = concept.getConceptId();
		branchService.updateMetadata("MAIN/SNOMEDCT-XX", Map.of(Config.DEFAULT_MODULE_ID_KEY, extModuleId, Config.EXPECTED_EXTENSION_MODULES, List.of(extModuleId), Config.DEPENDENCY_PACKAGE, "20250101.zip"));

		// Create Extension MDRS
		mdrs = new ReferenceSetMember(extModuleId, Concepts.MODULE_DEPENDENCY_REFERENCE_SET, CORE_MODULE);
		mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME, "");
		mdrs.setAdditionalField(ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME, "20250101");
		mdrs = referenceSetMemberService.createMember("MAIN/SNOMEDCT-XX", mdrs);

		// Version Extension
		codeSystem = codeSystemService.find("SNOMEDCT-XX");
		codeSystemService.createVersion(codeSystem, 20250102, "20250102");

		// Assert
		List<String> intMain = getFileFromSnapshotExport("MAIN", "ModuleDependency");
		List<String> intVersion = getFileFromSnapshotExport("MAIN/2025-01-01", "ModuleDependency");
		List<String> extMain = getFileFromSnapshotExport("MAIN/SNOMEDCT-XX", "ModuleDependency");
		List<String> extVersion = getFileFromSnapshotExport("MAIN/SNOMEDCT-XX/2025-01-02", "ModuleDependency");

		assertEquals(intMain, intVersion);
		assertEquals(extMain, extVersion);
		assertNotEquals(intMain, extMain);
		assertNotEquals(intVersion, extVersion);
	}

	private List<String> getFileFromSnapshotExport(String branchPath, String fileName) throws IOException {
		// Prepare for export
		File exportFile = getTempFile("export", ".zip");
		exportFile.deleteOnExit();

		// Export content
		try (FileOutputStream outputStream = new FileOutputStream(exportFile)) {
			ExportConfiguration exportConfiguration = new ExportConfiguration(branchPath, RF2Type.SNAPSHOT);
			exportService.createJob(exportConfiguration);
			exportService.exportRF2Archive(exportConfiguration, outputStream);
		}

		// Walk zip
		try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(exportFile))) {
			ZipEntry zipEntry;
			while ((zipEntry = zipInputStream.getNextEntry()) != null) {
				if (zipEntry.getName().contains(fileName)) {
					return getLines(zipInputStream);
				}
			}
		}

		return Collections.emptyList();
	}

	void printLines(List<String> lines) {
		for (String l : lines) {
			System.out.println(l);
		}
	}

	private List<String> getLines(ZipInputStream zipInputStream) throws IOException {
		File conceptFile = getTempFile("temp", ".txt");
		StreamUtils.copy(zipInputStream, new FileOutputStream(conceptFile), false, true);
		return java.nio.file.Files.readAllLines(conceptFile.toPath());
	}

	private File getTempFile(String name, String suffix) throws IOException {
		File tempFile = File.createTempFile(name, suffix);
		tempFile.deleteOnExit();
		return tempFile;
	}

}
