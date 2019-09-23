package org.snomed.snowstorm.validation;

import com.google.common.collect.Lists;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.DescriptionService;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.validation.domain.DroolsConcept;
import org.snomed.snowstorm.validation.domain.DroolsDescription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Set;

import static org.snomed.snowstorm.core.data.domain.Concepts.ISA;
import static org.snomed.snowstorm.core.data.domain.Concepts.SNOMEDCT_ROOT;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class DescriptionDroolsValidationServiceTest extends AbstractTest {

    @Autowired
    private ConceptService conceptService;

    @Autowired
    private DescriptionService descriptionService;

    @Autowired
    private VersionControlHelper versionControlHelper;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private QueryService queryService;

    private DescriptionDroolsValidationService validationService;

    public static final String PATH = "MAIN";

    private Concept root;

    private Concept bodyStructureAncestor;

    private Concept bodyStructureDescendant1;

    private Concept bodyStructureDescendant2;

    @Before
    public void setup() throws ServiceException {
        root = new Concept(SNOMEDCT_ROOT);
        bodyStructureAncestor = new Concept("123037004").addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)).addFSN("Body structure (body structure)");
        bodyStructureDescendant1 = new Concept("442083009").addRelationship(new Relationship(ISA, bodyStructureAncestor.getId())).addFSN("Anatomical or acquired body structure (body structure)");
        bodyStructureDescendant2 = new Concept("302509004").addRelationship(new Relationship(ISA, bodyStructureDescendant1.getId())).addFSN("Entire heart (body structure)").addDescription(new Description("444221019", 20170731, true, "900000000000207008", "302509004", "en", "900000000000013009", "Entire heart", "900000000000448009"));

        conceptService.batchCreate(Lists.newArrayList(root, bodyStructureAncestor, bodyStructureDescendant1, bodyStructureDescendant2), PATH);

        validationService = new DescriptionDroolsValidationService(PATH, versionControlHelper.getBranchCriteria(PATH), versionControlHelper, elasticsearchOperations, descriptionService, queryService, null);
    }

    @Test
    public void findMatchingDescriptionInHierarchy() {

        Concept concept = new Concept("1760555000");
        concept.addRelationship(new Relationship(ISA, bodyStructureDescendant1.getId()));

        Description description = new Description("5582049016", null, true, "900000000000207008", "1760555000", "en", "900000000000013009", "Entire heart", "900000000000448009");
        org.ihtsdo.drools.domain.Concept droolConcept = new DroolsConcept(concept);
        org.ihtsdo.drools.domain.Description droolDescription = new DroolsDescription(description);

        Set<org.ihtsdo.drools.domain.Description> descriptions = (validationService.findMatchingDescriptionInHierarchy(droolConcept, droolDescription));

        Assert.assertEquals(1, descriptions.size());
    }
}
