package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import jdk.nashorn.internal.objects.annotations.Setter;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.parameters.P;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static org.junit.Assert.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class ConceptServiceAttributeTest extends AbstractTest {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ImportService importService;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;
	private static final String PATH = "MAIN";

	@Before
	public void setup() throws IOException {
		branchService.create(PATH);
		File rf2Archive = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/main/resources/dummy-snomed-content/RF2Release");
		File completeOwlRf2Archive = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/main/resources/dummy-snomed-content/conversion-to-complete-owl");
		importRF2(RF2Type.SNAPSHOT, rf2Archive, PATH);
		importRF2(RF2Type.DELTA, completeOwlRf2Archive, PATH);
	}

	@Test
	public void testSaveAttributeConceptWithSubObjectPropertyOfAxiom() throws ServiceException {
		final Concept newAttributeConcept = new Concept("246501002", MODEL_MODULE);
		newAttributeConcept.addAxiom(new Relationship(Concepts.ISA, CONCEPT_MODEL_OBJECT_ATTRIBUTE));
		String conceptId = conceptService.create(newAttributeConcept, PATH).getConceptId();
		assertEquals(1, conceptService.find(conceptId, PATH).getClassAxioms().size());

		Page<ReferenceSetMember> members = referenceSetMemberService.findMembers(PATH,
				new MemberSearchRequest().active(true).referenceSet(OWL_AXIOM_REFERENCE_SET).referencedComponentId(conceptId), PageRequest.of(0, 10));
		assertEquals(1, members.getTotalElements());
		ReferenceSetMember referenceSetMember = members.getContent().get(0);
		String owlExpression = referenceSetMember.getAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION);
		System.out.println(owlExpression);
		assertEquals("SubObjectPropertyOf", owlExpression.substring(0, owlExpression.indexOf("(")));
	}

	public void importRF2(RF2Type type, File rf2Archive, String path) throws IOException {
		String importJobId = importService.createJob(type, path, false);
		try (FileInputStream releaseFileStream = new FileInputStream(rf2Archive)) {
			importService.importArchive(importJobId, releaseFileStream);
		} catch (ReleaseImportException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

}
