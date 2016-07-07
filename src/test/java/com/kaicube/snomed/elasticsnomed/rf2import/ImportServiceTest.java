package com.kaicube.snomed.elasticsnomed.rf2import;

import com.kaicube.snomed.elasticsnomed.App;
import com.kaicube.snomed.elasticsnomed.TestConfig;
import com.kaicube.snomed.elasticsnomed.domain.Concept;
import com.kaicube.snomed.elasticsnomed.domain.Description;
import com.kaicube.snomed.elasticsnomed.services.BranchService;
import com.kaicube.snomed.elasticsnomed.services.ConceptService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class ImportServiceTest {

	@Autowired
	private ImportService importService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	@Test
	public void testImportSnapshot() throws Exception {
		branchService.create("MAIN");
		final String branchPath = "MAIN/import";
		branchService.create(branchPath);
		importService.importSnapshot(getClass().getResource("/MiniCT_INT_GB_20140131").getPath(), branchPath);

		final Concept conceptBleeding = conceptService.find("131148009", branchPath);
		final Set<Description> descriptions = conceptBleeding.getDescriptions();
		Assert.assertEquals(2, descriptions.size());
		Description description = null;
		for (Description d : descriptions) {
			if (d.getDescriptionId().equals("210860014")) {
				description = d;
			}
		}
		Assert.assertNotNull(description);
		Assert.assertEquals("Bleeding", description.getTerm());
		final Map<String, String> acceptabilityMap = description.getAcceptabilityMap();
		Assert.assertEquals(1, acceptabilityMap.size());
		Assert.assertEquals("900000000000548007", acceptabilityMap.get("900000000000508004"));

		Assert.assertEquals(4, conceptBleeding.getRelationships().size());

		final Page<Concept> conceptPage = conceptService.findAll(branchPath, new PageRequest(0, 200));
		Assert.assertEquals(102, conceptPage.getNumberOfElements());
		final List<Concept> concepts = conceptPage.getContent();
		Concept conceptMechanicalAbnormality = null;
		for (Concept concept : concepts) {
			if (concept.getConceptId().equals("131148009")) {
				conceptMechanicalAbnormality = concept;
			}
		}

		Assert.assertNotNull(conceptMechanicalAbnormality);

		Assert.assertEquals(2, conceptMechanicalAbnormality.getDescriptions().size());
		Assert.assertEquals(4, conceptMechanicalAbnormality.getRelationships().size());
	}

	@Before
	@After
	public void tearDown() {
		conceptService.deleteAll();
		branchService.deleteAll();
	}

}
