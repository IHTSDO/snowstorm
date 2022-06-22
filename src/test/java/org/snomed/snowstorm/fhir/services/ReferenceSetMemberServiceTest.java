package org.snomed.snowstorm.fhir.services;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.pojo.Coding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A test for the ReferenceSetMemberService that depends on FHIR data setup
 */
public class ReferenceSetMemberServiceTest extends AbstractFHIRTest {

	@Autowired
	private ReferenceSetMemberService memberService;

	@Test
	void testAddMapTargetCodingWithTermToMembers() {
		Page<ReferenceSetMember> members = memberService.findMembers("MAIN", new MemberSearchRequest()
				.referenceSet("447562003").includeNonSnomedMapTerms(true), PageRequest.of(0, 10));
		assertEquals(1, (int) members.getTotalElements());
		ReferenceSetMember member = members.getContent().get(0);
		Coding mapTargetCoding = member.getMapTargetCoding();
		assertNotNull(mapTargetCoding);
		assertEquals("Coding{system='http://hl7.org/fhir/sid/icd-10', code='A1.100', display='The display'}", mapTargetCoding.toString());
	}

}
