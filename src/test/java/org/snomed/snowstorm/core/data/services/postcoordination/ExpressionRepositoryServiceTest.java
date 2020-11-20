package org.snomed.snowstorm.core.data.services.postcoordination;

import com.google.common.collect.Lists;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestTemplate;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.fail;
import static org.snomed.snowstorm.core.data.domain.Concepts.ISA;

class ExpressionRepositoryServiceTest extends AbstractTest {

	@Autowired
	private ExpressionRepositoryService expressionRepository;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ImportService importService;

	@BeforeEach
	public void setup() throws ServiceException, ReleaseImportException, IOException {
		conceptService.batchCreate(Lists.newArrayList(
				new Concept("83152002"),
				new Concept("421720008"),
				new Concept("405815000"),
				new Concept("49062001").addFSN("Device (physical object)"),
					new Concept("122456005").addFSN("Laser device").addRelationship(new Relationship(ISA, "49062001")),
				new Concept("7946007"),
				new Concept("405813007").addFSN("Procedure site - direct (attribute)"),
				new Concept("71388002"),
				new Concept("129264002").addFSN("Action (qualifier value)"),
					new Concept("129304002").addRelationship(new Relationship(ISA, "129264002")),
				new Concept("260686004"),
				new Concept("272741003").addFSN("Laterality (attribute)"),
				new Concept("182353008").addFSN("Side"),
					new Concept("24028007").addFSN("Right").addRelationship(new Relationship(ISA, "182353008")),
				new Concept("442083009").addFSN("Anatomical or acquired body structure (body structure)"),
					new Concept("15497006").addRelationship(new Relationship(ISA, "442083009")),
				new Concept("388441000").addFSN("Horse")
		), "MAIN");

		File dummyMrcmImportFile = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2_dummy_mrcm_snap");
		String importJob = importService.createJob(RF2Type.SNAPSHOT, "MAIN", false, false);
		try (FileInputStream inputStream = new FileInputStream(dummyMrcmImportFile)) {
			importService.importArchive(importJob, inputStream);
		}
	}

	@Test
	public void createExpression() throws ServiceException {
		String branch = "MAIN";

		// Single concept
		assertEquals("=== 83152002",
				expressionRepository.createExpression(branch, "83152002 |Oophorectomy|", "").getCloseToUserForm());

		// Single concept with explicit definition status
		assertEquals("=== 83152002",
				expressionRepository.createExpression(branch, "===83152002 |Oophorectomy|", "").getCloseToUserForm());

		// Single concept with explicit subtype definition status
		assertEquals("<<< 83152002",
				expressionRepository.createExpression(branch, "<<<  83152002 |Oophorectomy|", "").getCloseToUserForm());

		// Multiple focus concepts
		assertEquals("=== 421720008 + 7946007",
				expressionRepository.createExpression(branch, "421720008 |Spray dose form| + 7946007 |Drug suspension|", "").getCloseToUserForm());
		// Same concepts stated in reverse order to test concept sorting
		assertEquals("=== 421720008 + 7946007",
				expressionRepository.createExpression(branch, "7946007 |Drug suspension| + 421720008 |Spray dose form|", "").getCloseToUserForm());


		// With single refinement
		assertEquals("=== 83152002 : 405815000 = 122456005",
				expressionRepository.createExpression(branch, "    83152002 |Oophorectomy| :  405815000 |Procedure device|  =  122456005 |Laser device|", "").getCloseToUserForm());

		// With multiple refinements, attributes are sorted
		assertEquals("=== 71388002 : 260686004 = 129304002, 405813007 = 15497006, 405815000 = 122456005",
				expressionRepository.createExpression(branch, "   71388002 |Procedure| :" +
						"       405815000 |Procedure device|  =  122456005 |Laser device| ," +
						"       260686004 |Method|  =  129304002 |Excision - action| ," +
						"       405813007 |Procedure site - direct|  =  15497006 |Ovarian structure|", "").getCloseToUserForm());

		Page<PostCoordinatedExpression> page = expressionRepository.findAll(branch, PageRequest.of(0, 10));
		assertEquals(7, page.getTotalElements());

		Page<PostCoordinatedExpression> results = expressionRepository.findByCanonicalCloseToUserForm(branch, "=== 83152002 : 405815000 = 122456005", PageRequest.of(0, 1));
		assertEquals(1, results.getTotalElements());
		assertEquals("=== 83152002 : 405815000 = 122456005", results.getContent().get(0).getCloseToUserForm());
	}

	@Test
	public void handleExpressionWithBadSyntax() throws ServiceException {
		// Missing colon
		assertIllegalArgumentParsingError("373873005 |Pharmaceutical / biologic product|" +
				"\t\t411116001 |Has dose form|  = 421720008 |Spray dose form|");

		// Missing equals
		assertIllegalArgumentParsingError("373873005 |Pharmaceutical / biologic product| :" +
				"\t\t411116001 |Has dose form| 421720008 |Spray dose form|");

		// Double equals
		assertIllegalArgumentParsingError("373873005 |Pharmaceutical / biologic product| :" +
				"\t\t411116001 |Has dose form| == 421720008 |Spray dose form|");

		assertEquals("No invalid expressions should have been saved.",
				0, expressionRepository.findAll("MAIN", PageRequest.of(0, 5)).getTotalElements());
	}

	@Test
	public void attributeRangeMRCMValidation() throws ServiceException {
		String branch = "MAIN";

		// All in range as per data in setup
		expressionRepository.createExpression(branch, "   71388002 |Procedure| :" +
				"       405815000 |Procedure device| = 122456005 |Laser device| ," +
				"       260686004 |Method| = 129304002 |Excision - action| ," +
				"       405813007 |Procedure site - direct| = 15497006 |Ovarian structure|", "");

		try {
			expressionRepository.createExpression(branch, "   71388002 |Procedure| :" +
					"       405815000 |Procedure device| = 122456005 |Laser device| ," +
					"       260686004 |Method| = 129304002 |Excision - action| ," +
					"       405813007 |Procedure site - direct| = 388441000 |Horse|", "");
			fail("Should have thrown exception.");
		} catch (IllegalArgumentException e) {
			assertEquals("Value 388441000 | Horse | is not within the permitted range" +
							" of attribute 405813007 | Procedure site - direct (attribute) | - (<< 442083009 |Anatomical or acquired body structure (body structure)|).",
					e.getMessage());
		}
	}

	@Test
	public void attributeRangeMRCMValidationOfAttributeValueWithinExpression() throws ServiceException {
		String branch = "MAIN";

		// All in range as per data in setup
		expressionRepository.createExpression(branch, "71388002 |Procedure| :" +
				"       405815000 |Procedure device| = 122456005 |Laser device| ," +
				"       260686004 |Method| = 129304002 |Excision - action| ," +
				"       405813007 |Procedure site - direct| = ( 15497006 |Ovarian structure| : 272741003 |Laterality| = 24028007 |Right| )", "");

		try {
			expressionRepository.createExpression(branch, "71388002 |Procedure| :" +
					"       405815000 |Procedure device| = 122456005 |Laser device| ," +
					"       260686004 |Method| = 129304002 |Excision - action| ," +
					"       405813007 |Procedure site - direct| = ( 15497006 |Ovarian structure| : 272741003 |Laterality| = 388441000 |Horse| )", "");
			fail("Should have thrown exception.");
		} catch (IllegalArgumentException e) {
			assertEquals("Value 388441000 | Horse | is not within the permitted range" +
							" of attribute 272741003 | Laterality (attribute) | - (<< 182353008 |Side (qualifier value)|).",
					e.getMessage());
		}
	}

	@Test
	public void attributeRangeMRCMValidationOfAttributeWithExpressionValue() throws ServiceException {
		String branch = "MAIN";

		// All in range as per data in setup
		expressionRepository.createExpression(branch, "71388002 |Procedure| :" +
				"       405815000 |Procedure device| = 122456005 |Laser device| ," +
				"       260686004 |Method| = 129304002 |Excision - action| ," +
				"       405813007 |Procedure site - direct| = ( 15497006 |Ovarian structure| : 272741003 |Laterality| = 24028007 |Right| )", "");

		try {
			expressionRepository.createExpression(branch, "71388002 |Procedure| :" +
					"       405815000 |Procedure device| = 122456005 |Laser device| ," +
					"       260686004 |Method| = 129304002 |Excision - action| ," +
					"       405813007 |Procedure site - direct| = ( 388441000 |Horse| : 272741003 |Laterality| = 24028007 |Right| )", "");
			fail("Should have thrown exception.");
		} catch (IllegalArgumentException e) {
			assertEquals("Value 388441000 | Horse | is not within the permitted range" +
							" of attribute 405813007 | Procedure site - direct (attribute) | - (<< 442083009 |Anatomical or acquired body structure (body structure)|).",
					e.getMessage());
		}
	}

	private void assertIllegalArgumentParsingError(String closeToUserForm) throws ServiceException {
		try {
			expressionRepository.createExpression("MAIN", closeToUserForm, "");
			fail();
		} catch (IllegalArgumentException e) {
			// Good
			assertTrue(e.getMessage().startsWith("Failed to parse expression"));
		}
	}
}
