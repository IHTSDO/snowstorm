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
import org.springframework.data.domain.PageRequest;
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

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class ExportServiceTest extends AbstractTest {

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

		Concept langRefsetConcept = new Concept(Concepts.LANG_REFSET);
		concepts.add(langRefsetConcept);

		Concept gbLangRefsetConcept = new Concept(Concepts.GB_EN_LANG_REFSET).addRelationship(new Relationship(Concepts.ISA, Concepts.LANG_REFSET));
		concepts.add(gbLangRefsetConcept);

		// Version first two concepts
		String path = "MAIN";
		conceptService.create(concepts, path);
		releaseService.createVersion(20100131, path);

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
	}

	@Test
	public void exportRF2Archive() throws Exception {
		File exportFile = getTempFile("export", ".zip");
		exportFile.deleteOnExit();

		String descriptionLanguageRefsetMemberId = referenceSetMemberService.findMembers("MAIN", descriptionId, PageRequest.of(0, 10)).getContent().get(0).getMemberId();
		String textDefLanguageRefsetMemberId = referenceSetMemberService.findMembers("MAIN", textDefId, PageRequest.of(0, 10)).getContent().get(0).getMemberId();

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
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_sRefset_OWLAxiomDelta_INT_20180131.txt", axioms.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(ReferenceSetMemberExportWriter.HEADER + "\towlExpression", lines.get(0));
			assertEquals(owlMember.getId() + "\t\t1\t900000000000207008\t733073007\t123005000\tTransitiveObjectProperty(:123005000)", lines.get(1));
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
