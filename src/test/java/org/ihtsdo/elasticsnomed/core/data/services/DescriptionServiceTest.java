package org.ihtsdo.elasticsnomed.core.data.services;

import com.google.common.collect.Lists;
import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.elasticsnomed.TestConfig;
import org.ihtsdo.elasticsnomed.core.data.domain.Concept;
import org.ihtsdo.elasticsnomed.core.data.domain.Description;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;
import java.util.stream.Collectors;

import static org.ihtsdo.elasticsnomed.core.data.services.ServiceTestUtil.PAGE_REQUEST;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class DescriptionServiceTest {

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private DescriptionService descriptionService;
	private ServiceTestUtil testUtil;

	@Before
	public void setup() {
		branchService.create("MAIN");
		testUtil = new ServiceTestUtil(conceptService);
	}

	@Test
	public void testDescriptionSearch() throws ServiceException {
		testUtil.createConceptWithPathIdAndTerms("MAIN", "1", "Heart");
		testUtil.createConceptWithPathIdAndTerms("MAIN", "2", "Lung");
		testUtil.createConceptWithPathIdAndTerms("MAIN", "6", "Foot cramps");
		testUtil.createConceptWithPathIdAndTerms("MAIN", "7", "Foot cramp");
		testUtil.createConceptWithPathIdAndTerms("MAIN", "3", "Foot bone");
		testUtil.createConceptWithPathIdAndTerms("MAIN", "4", "Foot");
		testUtil.createConceptWithPathIdAndTerms("MAIN", "5", "Footwear");

		List<Description> content = descriptionService.findDescriptions("MAIN", "Foo cr", PAGE_REQUEST).getContent();
		List<String> actualTerms = content.stream().map(Description::getTerm).collect(Collectors.toList());
		assertEquals(Lists.newArrayList("Foot cramp", "Foot cramps"), actualTerms);

		content = descriptionService.findDescriptions("MAIN", "Foo", PAGE_REQUEST).getContent();
		actualTerms = content.stream().map(Description::getTerm).collect(Collectors.toList());
		assertEquals(Lists.newArrayList("Foot", "Footwear", "Foot bone", "Foot cramp", "Foot cramps"), actualTerms);

		content = descriptionService.findDescriptions("MAIN", "cramps", PAGE_REQUEST).getContent();
		actualTerms = content.stream().map(Description::getTerm).collect(Collectors.toList());
		assertEquals(Lists.newArrayList("Foot cramps"), actualTerms);
	}

	@After
	public void tearDown() {
		conceptService.deleteAll();
		branchService.deleteAll();
	}

}