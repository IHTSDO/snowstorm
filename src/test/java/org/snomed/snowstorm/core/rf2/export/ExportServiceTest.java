package org.snomed.snowstorm.core.rf2.export;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.jobs.ExportConfiguration;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.ReleaseService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.util.StreamUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.snomed.snowstorm.config.Config.PAGE_OF_ONE;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class ExportServiceTest extends AbstractTest {

	private static final String DESCRIPTION_TYPE_REFERENCE_SET = "900000000000538005";

	@Autowired
	private ExportService exportService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	@Autowired
	private ReleaseService releaseService;

	private String descriptionId;
	private String textDefId;
	private ReferenceSetMember owlMember;

	@Before
	public void setup() throws ServiceException {
		referenceSetMemberService.init();

		List<Concept> concepts = new ArrayList<>();

		Concept langRefsetConcept = new Concept(Concepts.LANG_REFSET).addDescription(new Description("640588126141710019", "Language refset"));
		concepts.add(langRefsetConcept);

		Concept gbLangRefsetConcept = new Concept(Concepts.GB_EN_LANG_REFSET).addRelationship(new Relationship(Concepts.ISA, Concepts.LANG_REFSET));
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
		conceptService.create(new Concept(Concepts.OWL_AXIOM_REFERENCE_SET).addDescription(new Description("3494181019", "OWL axiom reference set")).addRelationship(new Relationship(Concepts.ISA, Concepts.OWL_EXPRESSION_TYPE_REFERENCE_SET)), path);
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
	}

	@Test
	public void exportRF2Archive() throws Exception {
		File exportFile = getTempFile("export", ".zip");
		exportFile.deleteOnExit();

		String descriptionTypeRefsetMemberId = referenceSetMemberService.findMembers("MAIN", Concepts.FSN, PAGE_OF_ONE).getContent().get(0).getMemberId();
		String descriptionLanguageRefsetMemberId = referenceSetMemberService.findMembers("MAIN", descriptionId, PAGE_OF_ONE).getContent().get(0).getMemberId();
		String textDefLanguageRefsetMemberId = referenceSetMemberService.findMembers("MAIN", textDefId, PAGE_OF_ONE).getContent().get(0).getMemberId();

		// Run export
		try (FileOutputStream outputStream = new FileOutputStream(exportFile)) {
			ExportConfiguration exportConfiguration = new ExportConfiguration("MAIN", RF2Type.DELTA);
			exportConfiguration.setFilenameEffectiveDate("20180131");
			exportService.createJob(exportConfiguration);
			exportService.exportRF2Archive(exportConfiguration, outputStream);
		}

		// Test export
		try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(exportFile))) {
			// Concepts
			ZipEntry concepts = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_Concept_Delta_INT_20180131.txt", concepts.getName());
			List<String> lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(ConceptExportWriter.HEADER, lines.get(0));
			assertEquals("123001\t\t1\t900000000000207008\t900000000000074008", lines.get(1));

			// Descriptions
			ZipEntry descriptions = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_Description_Delta_INT_20180131.txt", descriptions.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(DescriptionExportWriter.HEADER, lines.get(0));
			assertEquals("124011\t\t1\t900000000000207008\t123001\ten\t" + Concepts.FSN + "\tBleeding (finding)\t900000000000448009", lines.get(1));

			// Text Definitions
			ZipEntry textDefinitions = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_TextDefinition_Delta_INT_20180131.txt", textDefinitions.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(DescriptionExportWriter.HEADER, lines.get(0));
			assertEquals("124012\t\t1\t900000000000207008\t123001\ten\t" + Concepts.TEXT_DEFINITION + "\tBleeding Text Def\t900000000000448009", lines.get(1));

			// Stated Relationships
			ZipEntry statedRelationships = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_StatedRelationship_Delta_INT_20180131.txt", statedRelationships.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RelationshipExportWriter.HEADER, lines.get(0));
			assertEquals("125021\t\t1\t900000000000207008\t123001\t100001\t0\t116680003\t900000000000010007\t900000000000451002", lines.get(1));

			// Inferred Relationships
			ZipEntry relationships = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_Relationship_Delta_INT_20180131.txt", relationships.getName());
			lines = getLines(zipInputStream);
			assertEquals(3, lines.size());
			assertEquals(RelationshipExportWriter.HEADER, lines.get(0));
			assertTrue(lines.contains("125022\t\t1\t900000000000207008\t123001\t100002\t0\t116680003\t900000000000011006\t900000000000451002"));
			assertTrue(lines.contains("125023\t\t1\t900000000000207008\t123001\t100003\t0\t116680003\t900000000000227009\t900000000000451002"));

			// Description Type Refset
			ZipEntry descriptionTypes = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Refset/Metadata/der2_ciRefset_DescriptionTypeDelta_INT_20180131.txt", descriptionTypes.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(ReferenceSetMemberExportWriter.HEADER + "\tdescriptionFormat\tdescriptionLength", lines.get(0));
			assertEquals(descriptionTypeRefsetMemberId + "\t\t1\t900000000000207008\t900000000000538005\t900000000000003001\t900000000000540000\t255", lines.get(1));

			// Language Refset
			ZipEntry langRefset = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Refset/Language/der2_cRefset_Language900000000000508004Delta_INT_20180131.txt", langRefset.getName());
			lines = getLines(zipInputStream);
			assertEquals(3, lines.size());
			assertEquals(ReferenceSetMemberExportWriter.HEADER + "\tacceptabilityId", lines.get(0));
			assertTrue(lines.contains(descriptionLanguageRefsetMemberId + "\t\t1\t900000000000207008\t900000000000508004\t124011\t900000000000548007"));
			assertTrue(lines.contains(textDefLanguageRefsetMemberId + "\t\t1\t900000000000207008\t900000000000508004\t124012\t900000000000548007"));

			// OWL Axiom Refset
			ZipEntry axioms = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_sRefset_OWLExpression733073007Delta_INT_20180131.txt", axioms.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(ReferenceSetMemberExportWriter.HEADER + "\towlExpression", lines.get(0));
			assertEquals(owlMember.getId() + "\t\t1\t900000000000207008\t733073007\t123005000\tTransitiveObjectProperty(:123005000)", lines.get(1));
		}
	}

	@Test
	public void exportRF2ArchiveForClassification() throws Exception {
		File exportFile = getTempFile("export", ".zip");
		exportFile.deleteOnExit();

		// Run export
		try (FileOutputStream outputStream = new FileOutputStream(exportFile)) {
			ExportConfiguration exportConfiguration = new ExportConfiguration("MAIN", RF2Type.DELTA);

			// FOR Classification
			exportConfiguration.setConceptsAndRelationshipsOnly(true);

			exportConfiguration.setFilenameEffectiveDate("20180131");
			exportService.createJob(exportConfiguration);
			exportService.exportRF2Archive(exportConfiguration, outputStream);
		}

		// Test export
		try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(exportFile))) {
			// Concepts
			ZipEntry concepts = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_Concept_Delta_INT_20180131.txt", concepts.getName());
			List<String> lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(ConceptExportWriter.HEADER, lines.get(0));
			assertEquals("123001\t\t1\t900000000000207008\t900000000000074008", lines.get(1));

			// Stated Relationships
			ZipEntry statedRelationships = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_StatedRelationship_Delta_INT_20180131.txt", statedRelationships.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RelationshipExportWriter.HEADER, lines.get(0));
			assertEquals("125021\t\t1\t900000000000207008\t123001\t100001\t0\t116680003\t900000000000010007\t900000000000451002", lines.get(1));

			// Inferred Relationships
			ZipEntry relationships = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_Relationship_Delta_INT_20180131.txt", relationships.getName());
			lines = getLines(zipInputStream);
			assertEquals(3, lines.size());
			assertEquals(RelationshipExportWriter.HEADER, lines.get(0));
			assertTrue(lines.contains("125022\t\t1\t900000000000207008\t123001\t100002\t0\t116680003\t900000000000011006\t900000000000451002"));
			assertTrue(lines.contains("125023\t\t1\t900000000000207008\t123001\t100003\t0\t116680003\t900000000000227009\t900000000000451002"));

			// OWL Axiom Refset
			ZipEntry axioms = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_sRefset_OWLExpression733073007Delta_INT_20180131.txt", axioms.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(ReferenceSetMemberExportWriter.HEADER + "\towlExpression", lines.get(0));
			assertEquals(owlMember.getId() + "\t\t1\t900000000000207008\t733073007\t123005000\tTransitiveObjectProperty(:123005000)", lines.get(1));
		}
	}

	@Test
	public void exportRF2ArchiveWithTransientEffectiveTime() throws Exception {
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
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_Concept_Delta_INT_20190416.txt", concepts.getName());
			List<String> lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(ConceptExportWriter.HEADER, lines.get(0));
			assertEquals("123001\t20190731\t1\t900000000000207008\t900000000000074008", lines.get(1));

			// Descriptions
			ZipEntry descriptions = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_Description_Delta_INT_20190416.txt", descriptions.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(DescriptionExportWriter.HEADER, lines.get(0));
			assertEquals("124011\t20190731\t1\t900000000000207008\t123001\ten\t" + Concepts.FSN + "\tBleeding (finding)\t900000000000448009", lines.get(1));
		}

	}

	@Test
	public void exportSnapshot() throws Exception {
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
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_Concept_Snapshot_INT_20190904.txt", concepts.getName());
			List<String> lines = getLines(zipInputStream);
			assertEquals(9, lines.size());
			int line = 0;
			printLines(lines);

			assertEquals(ConceptExportWriter.HEADER, lines.get(line++));
			assertEquals("900000000000506000\t20100131\t1\t900000000000207008\t900000000000074008", lines.get(line++));
			assertEquals("900000000000508004\t20100131\t1\t900000000000207008\t900000000000074008", lines.get(line++));
			assertEquals("762676003\t20100131\t1\t900000000000207008\t900000000000074008", lines.get(line++));
			assertEquals("446609009\t20100131\t1\t900000000000207008\t900000000000074008", lines.get(line++));
			assertEquals("900000000000538005\t20100131\t1\t900000000000207008\t900000000000074008", lines.get(line++));
			assertEquals("723589008\t20100131\t1\t900000000000207008\t900000000000074008", lines.get(line++));
			assertEquals("733073007\t20190131\t1\t900000000000207008\t900000000000074008", lines.get(line++));
			assertEquals("123001\t\t1\t900000000000207008\t900000000000074008", lines.get(line++));

			// Descriptions
			ZipEntry descriptions = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_Description_Snapshot_INT_20190904.txt", descriptions.getName());
			lines = getLines(zipInputStream);
			printLines(lines);

			assertEquals(4, lines.size());
			line = 0;
			assertEquals(DescriptionExportWriter.HEADER, lines.get(line++));
			assertEquals("640588126141710019\t20100131\t1\t900000000000207008\t900000000000506000\ten\t900000000000013009\tLanguage refset\t900000000000448009", lines.get(line++));
			assertEquals("3494181019\t20190131\t1\t900000000000207008\t733073007\ten\t900000000000013009\tOWL axiom reference set\t900000000000448009", lines.get(line++));
			assertEquals("124011\t\t1\t900000000000207008\t123001\ten\t900000000000003001\tBleeding (finding)\t900000000000448009", lines.get(line++));
		}

	}

	@Test
	public void exportSnapshotWithStartEffectiveTime() throws Exception {
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
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_Concept_Snapshot_INT_20190904.txt", concepts.getName());
			List<String> lines = getLines(zipInputStream);
			printLines(lines);
			assertEquals(3, lines.size());
			int line = 0;

			assertEquals(ConceptExportWriter.HEADER, lines.get(line++));
			assertEquals("733073007\t20190131\t1\t900000000000207008\t900000000000074008", lines.get(line++));
			assertEquals("123001\t\t1\t900000000000207008\t900000000000074008", lines.get(line++));

			// Descriptions
			ZipEntry descriptions = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_Description_Snapshot_INT_20190904.txt", descriptions.getName());
			lines = getLines(zipInputStream);
			printLines(lines);

			assertEquals(3, lines.size());
			line = 0;
			assertEquals(DescriptionExportWriter.HEADER, lines.get(line++));
			assertEquals("3494181019\t20190131\t1\t900000000000207008\t733073007\ten\t900000000000013009\tOWL axiom reference set\t900000000000448009", lines.get(line++));
			assertEquals("124011\t\t1\t900000000000207008\t123001\ten\t900000000000003001\tBleeding (finding)\t900000000000448009", lines.get(line++));
		}

	}

	@Test
	public void testExportRefsetMemberWithBlankFields() throws IOException {
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

	public void printLines(List<String> lines) {
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
