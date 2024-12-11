package org.snomed.snowstorm.core.data.services.identifier;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class LocalSequentialIdentifierSourceTest extends AbstractTest {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ReferenceSetMemberService memberService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	private LocalSequentialIdentifierSource identifierSource;

	@BeforeEach
	public void setup() throws ServiceException {
		identifierSource = new LocalSequentialIdentifierSource(elasticsearchOperations);
		conceptService.create(new Concept("840539006"), "MAIN");
		branchService.create("MAIN/B");
		conceptService.create(new Concept("1119302008"), "MAIN/B");
		branchService.create("MAIN/SNOMEDCT-SE");
		conceptService.create(new Concept("1119302008").addDescription(new Description("4826281000052116", "akut covid-19")), "MAIN/SNOMEDCT-SE");
	}

	@Test
	void reserveIds() {
		// Reserve concept ids
		assertEquals("[1119303003, 1119304009, 1119305005]",
				Arrays.toString(identifierSource.reserveIds(0, "00", 3).toArray()));

		// Subsequent concept ids (without saving previous)
		assertEquals("[1119306006, 1119307002, 1119308007]",
				Arrays.toString(identifierSource.reserveIds(0, "00", 3).toArray()));

		assertEquals("[4826291000052119, 4826301000052115, 4826311000052118]",
				Arrays.toString(identifierSource.reserveIds(1000052, "11", 3).toArray()));

		assertEquals("[11000055126, 21000055122, 31000055124]",
				Arrays.toString(identifierSource.reserveIds(1000055, "12", 3).toArray()));

		// Expression ids
		assertEquals("[11000055161, 21000055167, 31000055169]",
				Arrays.toString(identifierSource.reserveIds(1000055, "16", 3).toArray()));

		// Subsequent expression ids (existing greatest id found using referencedComponentId)
		assertEquals("[41000055160, 51000055162]",
				Arrays.toString(identifierSource.reserveIds(1000055, "16", 2).toArray()));

		// Push sequence 6 into the store, without using the id gen service
		memberService.createMember("MAIN/B", new ReferenceSetMember("1119302008", "1119302008", "61000055164"));

		// Assert next assigned starts at sequence 7
		assertEquals("[71000055166, 81000055168, 91000055165]",
				Arrays.toString(identifierSource.reserveIds(1000055, "16", 3).toArray()));
	}
}
