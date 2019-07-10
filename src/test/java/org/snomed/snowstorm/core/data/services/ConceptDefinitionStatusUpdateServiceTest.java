package org.snomed.snowstorm.core.data.services;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Commit;
import org.assertj.core.util.Lists;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static org.junit.Assert.*;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_CODES;
import static org.snomed.snowstorm.core.data.domain.Concepts.CORE_MODULE;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class ConceptDefinitionStatusUpdateServiceTest extends AbstractTest {

	private  String MAIN = "MAIN";

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	@Autowired
	private ConceptDefinitionStatusUpdateService definitionStatusUpdateService;

	@Autowired
	private ImportService importService;

	@Autowired
	private BranchMetadataHelper branchMetadataHelper;

	@Test
	public void testNewConceptAuthoring() throws ServiceException {
		//create a new concept with class axiom
		final Concept concept = new Concept("50960005", 20020131, true, Concepts.CORE_MODULE, "900000000000074008");
		concept.addAxiom(new Axiom(null, Concepts.FULLY_DEFINED, Sets.newHashSet(new Relationship(Concepts.ISA, "10000100"), new Relationship("10000200", "10000300"))).setModuleId(CORE_MODULE));
		conceptService.create(concept, MAIN);
		assertEquals(1, conceptService.find(concept.getConceptId(), MAIN).getClassAxioms().size());
		final Concept savedConcept = conceptService.find("50960005", MAIN);
		Assert.assertNotNull(savedConcept);
		assertEquals(1, savedConcept.getClassAxioms().size());
		Axiom axiom = savedConcept.getClassAxioms().iterator().next();
		assertEquals(Concepts.FULLY_DEFINED, axiom.getDefinitionStatusId());
		assertEquals("Concept and class axiom should have the same definition status", axiom.getDefinitionStatusId(), savedConcept.getDefinitionStatusId());

		Page<ReferenceSetMember> members = referenceSetMemberService.findMembers(MAIN,
				new MemberSearchRequest().active(true).referenceSet(Concepts.OWL_AXIOM_REFERENCE_SET).referencedComponentId(savedConcept.getConceptId()), PageRequest.of(0, 10));
		assertEquals(1, members.getTotalElements());
		String axiomId = axiom.getAxiomId();
		ReferenceSetMember referenceSetMember = members.getContent().stream().filter(member -> member.getMemberId().equals(axiomId)).collect(Collectors.toList()).get(0);
		assertEquals("EquivalentClasses(:50960005 ObjectIntersectionOf(:10000100 ObjectSomeValuesFrom(:609096000 ObjectSomeValuesFrom(:10000200 :10000300))))",
				referenceSetMember.getAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION));
	}

	@Test
	public void testAxiomRefsetMemberUpdate() throws ServiceException {
		//create new concept with class axiom
		final Concept concept = new Concept("50960005", 20020131, true, Concepts.CORE_MODULE, Concepts.FULLY_DEFINED);
		concept.addAxiom(new Axiom(null, Concepts.FULLY_DEFINED, Sets.newHashSet(new Relationship(Concepts.ISA, "10000100"), new Relationship("10000200", "10000300"))).setModuleId(CORE_MODULE));
		conceptService.create(concept, MAIN);

		Concept savedConcept = conceptService.find("50960005", MAIN);
		assertEquals(1, savedConcept.getClassAxioms().size());
		assertEquals(Concepts.FULLY_DEFINED, savedConcept.getDefinitionStatusId());

		String taskBranch = "MAIN/task1";
		branchService.create(taskBranch);

		Concept existing = conceptService.find("50960005", taskBranch);
		assertEquals(1, existing.getClassAxioms().size());
		assertEquals(Concepts.FULLY_DEFINED, existing.getDefinitionStatusId());

		Axiom axiom = existing.getClassAxioms().iterator().next();
		Page<ReferenceSetMember> members = referenceSetMemberService.findMembers(taskBranch,
				new MemberSearchRequest().active(true).referenceSet(Concepts.OWL_AXIOM_REFERENCE_SET).referencedComponentId(existing.getConceptId()), PageRequest.of(0, 10));
		assertEquals(1, members.getTotalElements());
		String axiomId = axiom.getAxiomId();
		ReferenceSetMember referenceSetMember = members.getContent().stream().filter(member -> member.getMemberId().equals(axiomId)).collect(Collectors.toList()).get(0);
		assertEquals("EquivalentClasses(:50960005 ObjectIntersectionOf(:10000100 ObjectSomeValuesFrom(:609096000 ObjectSomeValuesFrom(:10000200 :10000300))))",
				referenceSetMember.getAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION));
		//update to a SubClass axiom
		ReferenceSetMember updatedRefsetMember = referenceSetMember;
		updatedRefsetMember.setAdditionalField("owlExpression","SubClassOf(:50960005 ObjectIntersectionOf(:10000100 ObjectSomeValuesFrom(:609096000 ObjectSomeValuesFrom(:10000200 :10000300))))");
		updatedRefsetMember.setMemberId(referenceSetMember.getMemberId());
		referenceSetMemberService.updateMember(taskBranch, updatedRefsetMember);

		Concept updatedConcept = conceptService.find(concept.getConceptId(), taskBranch);
		assertEquals("900000000000074008", updatedConcept.getDefinitionStatusId());
		assertEquals(1, updatedConcept.getClassAxioms().size());
		Axiom updatedAxiom = updatedConcept.getClassAxioms().iterator().next();
		assertEquals("900000000000074008", updatedAxiom.getDefinitionStatusId());
	}

	@Test
	public void testClassAxiomRefsetDeletion() throws ServiceException {
		//create a new concept with class axiom
		final Concept concept = new Concept("50960005", 20020131, true, Concepts.CORE_MODULE, Concepts.FULLY_DEFINED);
		concept.addAxiom(new Axiom(null, Concepts.FULLY_DEFINED, Sets.newHashSet(new Relationship(Concepts.ISA, "10000100"), new Relationship("10000200", "10000300"))).setModuleId(CORE_MODULE));
		conceptService.create(concept, MAIN);

		Concept savedConcept = conceptService.find("50960005", MAIN);
		assertEquals(1, savedConcept.getClassAxioms().size());
		assertEquals(Concepts.FULLY_DEFINED, savedConcept.getDefinitionStatusId());

		Axiom axiom = savedConcept.getClassAxioms().iterator().next();
		referenceSetMemberService.deleteMember(MAIN, axiom.getAxiomId());

		Concept updatedConcept = conceptService.find(concept.getConceptId(), MAIN);
		assertEquals(Concepts.PRIMITIVE, updatedConcept.getDefinitionStatusId());
	}

	@Test
	public void testRevertingClassAxiomRefsetUpdate() throws ServiceException {
		//create new concept with class axiom
		final Concept concept = new Concept("50960005", 20020131, true, Concepts.CORE_MODULE, Concepts.FULLY_DEFINED);
		concept.addAxiom(new Axiom(null, Concepts.FULLY_DEFINED, Sets.newHashSet(new Relationship(Concepts.ISA, "10000100"), new Relationship("10000200", "10000300"))).setModuleId(CORE_MODULE));
		conceptService.create(concept, MAIN);

		Concept conceptFromMain = conceptService.find("50960005", MAIN);
		assertEquals(1, conceptFromMain.getClassAxioms().size());
		assertEquals(Concepts.FULLY_DEFINED, conceptFromMain.getDefinitionStatusId());

		String taskBranch = MAIN + "/task1";
		branchService.create(taskBranch);

		Concept existing = conceptService.find("50960005", taskBranch);
		assertEquals(1, existing.getClassAxioms().size());
		assertEquals(Concepts.FULLY_DEFINED, existing.getDefinitionStatusId());

		Axiom axiom = existing.getClassAxioms().iterator().next();
		Page<ReferenceSetMember> members = referenceSetMemberService.findMembers(taskBranch,
				new MemberSearchRequest().active(true).referenceSet(Concepts.OWL_AXIOM_REFERENCE_SET).referencedComponentId(existing.getConceptId()), PageRequest.of(0, 10));
		assertEquals(1, members.getTotalElements());
		String axiomId = axiom.getAxiomId();
		ReferenceSetMember referenceSetMember = members.getContent().stream().filter(member -> member.getMemberId().equals(axiomId)).collect(Collectors.toList()).get(0);
		assertEquals("EquivalentClasses(:50960005 ObjectIntersectionOf(:10000100 ObjectSomeValuesFrom(:609096000 ObjectSomeValuesFrom(:10000200 :10000300))))",
				referenceSetMember.getAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION));
		//update to a SubClass axiom
		ReferenceSetMember updatedRefsetMember = referenceSetMember;
		updatedRefsetMember.setAdditionalField("owlExpression","SubClassOf(:50960005 ObjectIntersectionOf(:10000100 ObjectSomeValuesFrom(:609096000 ObjectSomeValuesFrom(:10000200 :10000300))))");
		updatedRefsetMember.setMemberId(referenceSetMember.getMemberId());
		referenceSetMemberService.updateMember(taskBranch, updatedRefsetMember);

		Concept updatedConcept = conceptService.find(concept.getConceptId(), taskBranch);
		assertEquals("900000000000074008", updatedConcept.getDefinitionStatusId());
		assertEquals(1, updatedConcept.getClassAxioms().size());
		Axiom updatedAxiom = updatedConcept.getClassAxioms().iterator().next();
		assertEquals("900000000000074008", updatedAxiom.getDefinitionStatusId());

		//revert changes (i.e save the concept state from the parent branch
		conceptService.update(conceptFromMain, taskBranch);
		Concept afterReverting = conceptService.find("50960005", taskBranch);
		assertEquals(1, afterReverting.getClassAxioms().size());
		assertEquals(Concepts.FULLY_DEFINED, afterReverting.getDefinitionStatusId());
	}

	@Test
	public void importStatedSnapshotThenCompleteOwl() throws IOException, ReleaseImportException, ServiceException {
		File rf2Archive = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/main/resources/dummy-snomed-content/RF2Release");
		File completeOwlRf2Archive = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/main/resources/dummy-snomed-content/conversion-to-complete-owl");

		// Import stated relationship snapshot
		try (FileInputStream releaseFileStream = new FileInputStream(rf2Archive)) {
			importService.importArchive(importService.createJob(RF2Type.SNAPSHOT, MAIN, false), releaseFileStream);
		}
		assertEquals("[131148009:FULLY_DEFINED, 413350009:FULLY_DEFINED]", getDefinedConcepts().toString());

		// Mess up the concept definition statuses
		Collection<Concept> concepts = conceptService.find(MAIN, Lists.newArrayList("131148009", "413350009"), DEFAULT_LANGUAGE_CODES);
		concepts.forEach(concept -> concept.setDefinitionStatusId(Concepts.PRIMITIVE));
		conceptService.createUpdate(new ArrayList<>(concepts), MAIN);
		concepts = conceptService.find(MAIN, Lists.newArrayList("64572001", "125676002"), DEFAULT_LANGUAGE_CODES);
		concepts.forEach(concept -> concept.setDefinitionStatusId(Concepts.FULLY_DEFINED));
		conceptService.createUpdate(new ArrayList<>(concepts), MAIN);
		assertEquals("Expecting only two primitive concepts to be fully defined.",
				"[64572001:FULLY_DEFINED, 125676002:FULLY_DEFINED]", getDefinedConcepts().toString());

		// Import complete OWL delta - let the definition status update hook fix the statuses
		try (FileInputStream releaseFileStream = new FileInputStream(completeOwlRf2Archive)) {
			importService.importArchive(importService.createJob(RF2Type.DELTA, MAIN, false), releaseFileStream);
		}
		assertEquals("Expecting statuses to be fixed to original set of FD concepts.",
				"[131148009:FULLY_DEFINED, 413350009:FULLY_DEFINED]", getDefinedConcepts().toString());
	}

	@Test
	public void updatePublishedClassAxiom() throws ServiceException {
		//create a published concept with class axiom
		final Concept concept = new Concept("50960005", 20020131, true, Concepts.CORE_MODULE, "900000000000074008");
		concept.addAxiom(new Axiom(null, Concepts.FULLY_DEFINED, Sets.newHashSet(new Relationship(Concepts.ISA, "10000100"), new Relationship("10000200", "10000300"))).setModuleId(CORE_MODULE));
		conceptService.create(concept, MAIN);

		Concept savedConcept = conceptService.find(concept.getConceptId(), MAIN);
		assertEquals(Concepts.FULLY_DEFINED, savedConcept.getDefinitionStatusId());
		savedConcept.release(20020131);
		try (Commit commit = branchService.openCommit(MAIN, branchMetadataHelper.getBranchLockMetadata("Release concept " + savedConcept.getConceptId()))) {
			savedConcept.markChanged();
			conceptService.doSaveBatchComponents(Arrays.asList(savedConcept), Concept.class, commit);
			commit.markSuccessful();
		}

		Concept publishedConcept = conceptService.find(concept.getConceptId(), MAIN);
		assertEquals("50960005", publishedConcept.getConceptId());
		assertTrue(publishedConcept.isReleased());
		assertEquals("20020131", publishedConcept.getEffectiveTime());
		assertNotNull(publishedConcept.getReleaseHash());

		assertEquals(1, publishedConcept.getClassAxioms().size());
		Axiom axiom = publishedConcept.getClassAxioms().iterator().next();
		assertEquals("900000000000073002", axiom.getDefinitionStatusId());

		ReferenceSetMember axiomRefsetMember = referenceSetMemberService.findMember(MAIN, axiom.getAxiomId());
		assertNotNull(axiomRefsetMember);
		String expression = axiomRefsetMember.getAdditionalField("owlExpression");
		assertTrue(expression.startsWith("EquivalentClasses("));
		String updatedExpression = expression.replace("EquivalentClasses(", "SubClassOf(");
		axiomRefsetMember.setAdditionalField("owlExpression", updatedExpression);
		referenceSetMemberService.updateMember(MAIN, axiomRefsetMember);

		Concept updatedConcept = conceptService.find(publishedConcept.getConceptId(), MAIN);
		assertEquals("900000000000074008", updatedConcept.getDefinitionStatusId());
		assertTrue(updatedConcept.isReleased());
		assertNull(updatedConcept.getEffectiveTime());
	}

	public List<String> getDefinedConcepts() {
		return conceptService.findAll(MAIN, LARGE_PAGE).stream()
					.filter(concept -> concept.getDefinitionStatusId().equals(Concepts.FULLY_DEFINED))
					.sorted(Comparator.comparing(Concept::getConceptIdAsLong))
					.map(concept -> concept.getId() + ":" + concept.getDefinitionStatus()).collect(Collectors.toList());
	}

}
