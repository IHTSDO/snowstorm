package org.snomed.snowstorm.core.data.services.identifier;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
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
	private BranchService branchService;

	@Autowired
	private ElasticsearchRestTemplate elasticsearchTemplate;

	private LocalSequentialIdentifierSource identifierSource;

	@BeforeEach
	public void setup() throws ServiceException {
		identifierSource = new LocalSequentialIdentifierSource(elasticsearchTemplate);
		conceptService.create(new Concept("840539006"), "MAIN");
		branchService.create("MAIN/B");
		conceptService.create(new Concept("1119302008"), "MAIN/B");
		branchService.create("MAIN/SNOMEDCT-SE");
		conceptService.create(new Concept("1119302008").addDescription(new Description("4826281000052116", "akut covid-19")), "MAIN/SNOMEDCT-SE");
	}

	@Test
	void reserveIds() {
		assertEquals("[1119303003, 1119304009, 1119305005]",
				Arrays.toString(identifierSource.reserveIds(0, "00", 3).toArray()));

		assertEquals("[4826291000052119, 4826301000052115, 4826311000052118]",
				Arrays.toString(identifierSource.reserveIds(1000052, "11", 3).toArray()));

		assertEquals("[11000055126, 21000055122, 31000055124]",
				Arrays.toString(identifierSource.reserveIds(1000055, "12", 3).toArray()));
	}
}
