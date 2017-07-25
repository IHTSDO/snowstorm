package org.ihtsdo.elasticsnomed.core.rf2.export;

import org.ihtsdo.elasticsnomed.TestConfig;
import org.ihtsdo.elasticsnomed.core.data.domain.*;
import org.ihtsdo.elasticsnomed.core.data.services.ConceptService;
import org.ihtsdo.elasticsnomed.core.data.services.ReferenceSetMemberService;
import org.ihtsdo.elasticsnomed.core.util.StreamUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class ExportServiceTest {

	@Autowired
	private ExportService exportService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;
	private String descriptionId;

	@Before
	public void setup() throws IOException {
		referenceSetMemberService.init();

		List<Concept> concepts = new ArrayList<>();

		Concept langRefsetConcept = new Concept(Concepts.LANG_REFSET);
		concepts.add(langRefsetConcept);

		Concept gbLangRefsetConcept = new Concept(Concepts.GB_EN_LANG_REFSET).addRelationship(new Relationship(Concepts.ISA, Concepts.LANG_REFSET));
		concepts.add(gbLangRefsetConcept);

		String conceptId = "123001";
		descriptionId = "124011";
		Concept concept = new Concept(conceptId, "", true, Concepts.CORE_MODULE, Concepts.PRIMITIVE);
		concept.addDescription(
				new Description(descriptionId, "", true, Concepts.CORE_MODULE, conceptId, "en", Concepts.FSN, "Bleeding (finding)", Concepts.CASE_INSENSITIVE)
						.addLanguageRefsetMember(
								new ReferenceSetMember(null, "", true, Concepts.CORE_MODULE, Concepts.GB_EN_LANG_REFSET, descriptionId)
										.setAdditionalField("acceptabilityId", Concepts.PREFERRED)));
		concept.addRelationship(new Relationship("125021", "", true, Concepts.CORE_MODULE, conceptId, "100001", 0, Concepts.ISA, Concepts.STATED_RELATIONSHIP, Concepts.EXISTENTIAL));
		concepts.add(concept);
		conceptService.create(concepts, "MAIN");

		conceptService.releaseConceptsForTest("20100131", "MAIN", langRefsetConcept, gbLangRefsetConcept);
	}

	@Test
	public void exportRF2Archive() throws Exception {
		File exportFile = getTempFile("export", ".zip");
		exportFile.deleteOnExit();

		String languageRefsetMemberId = referenceSetMemberService.findMembers("MAIN", descriptionId, new PageRequest(0, 10)).getContent().get(0).getMemberId();

		// Run export
		try (FileOutputStream outputStream = new FileOutputStream(exportFile)) {
			exportService.exportRF2Archive("MAIN", "20180131", ExportType.DELTA, outputStream);
		}

		// Test export
		try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(exportFile))) {
			// Concepts
			ZipEntry concepts = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_Concept_Delta_20180131.txt", concepts.getName());
			List<String> lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(ConceptExportWriter.HEADER, lines.get(0));
			assertEquals("123001\t\t1\t900000000000207008\t900000000000074008", lines.get(1));

			// Descriptions
			ZipEntry descriptions = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_Description_Delta_20180131.txt", descriptions.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(DescriptionExportWriter.HEADER, lines.get(0));
			assertEquals("124011\t\t1\t900000000000207008\t123001\ten\t900000000000003001\tBleeding (finding)\t900000000000448009", lines.get(1));

			// Stated Relationships
			ZipEntry relationships = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Terminology/sct2_StatedRelationship_Delta_20180131.txt", relationships.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals(RelationshipExportWriter.HEADER, lines.get(0));
			assertEquals("125021\t\t1\t900000000000207008\t123001\t100001\t0\t116680003\t900000000000010007\t900000000000451002", lines.get(1));

			// Language Refset
			ZipEntry langRefset = zipInputStream.getNextEntry();
			assertEquals("SnomedCT_Export/RF2Release/Refset/Language/der2_cRefset_900000000000508004Delta_20180131.txt", langRefset.getName());
			lines = getLines(zipInputStream);
			assertEquals(2, lines.size());
			assertEquals("id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tacceptabilityId", lines.get(0));
			assertEquals(languageRefsetMemberId + "\t\t1\t900000000000207008\t900000000000508004\t124011\t900000000000548007", lines.get(1));
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
