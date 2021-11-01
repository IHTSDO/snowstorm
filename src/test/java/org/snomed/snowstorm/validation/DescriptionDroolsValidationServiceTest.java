package org.snomed.snowstorm.validation;

import com.google.common.collect.Lists;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.snomed.snowstorm.core.data.domain.Concepts.ISA;
import static org.snomed.snowstorm.core.data.domain.Concepts.SNOMEDCT_ROOT;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class DescriptionDroolsValidationServiceTest extends AbstractTest {

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

    private Concept bodyStructureDescendant1;

    @BeforeEach
    void setup() throws ServiceException {
        Concept root = new Concept(SNOMEDCT_ROOT);
        Concept bodyStructureAncestor = new Concept("123037004")
                .addAxiom(new Relationship(ISA, SNOMEDCT_ROOT))
                .addRelationship(new Relationship(ISA, SNOMEDCT_ROOT))
                .addFSN("Body structure (body structure)");
        bodyStructureDescendant1 = new Concept("442083009")
				.addAxiom(new Relationship(ISA, bodyStructureAncestor.getId()))
				.addFSN("Anatomical or acquired body structure (body structure)");
        Concept bodyStructureDescendant2 = new Concept("302509004")
                .addAxiom(new Relationship(ISA, bodyStructureDescendant1.getId()))
                .addFSN("Entire heart (body structure)")
                .addDescription(new Description("444221019", 20170731, true, "900000000000207008", "302509004", "en", "900000000000013009", "Entire heart", "900000000000448009"));

        conceptService.batchCreate(Lists.newArrayList(root, bodyStructureAncestor, bodyStructureDescendant1, bodyStructureDescendant2), PATH);

        BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(PATH);
        DisposableQueryService disposableQueryService = new DisposableQueryService(queryService, PATH, branchCriteria);
        validationService = new DescriptionDroolsValidationService(PATH, branchCriteria, elasticsearchOperations, descriptionService, disposableQueryService, null,
                Collections.singleton("123037004"));
    }

    @Test
    void findMatchingDescriptionInHierarchy() {
        Concept concept = new Concept("1760555000");
        concept.addAxiom(new Relationship(ISA, bodyStructureDescendant1.getId()));

        Description description = new Description("5582049016", null, true, "900000000000207008", "1760555000",
                "en", "900000000000013009", "Entire heart", "900000000000448009");
        org.ihtsdo.drools.domain.Concept droolConcept = new DroolsConcept(concept);
        org.ihtsdo.drools.domain.Description droolDescription = new DroolsDescription(description);

        Set<org.ihtsdo.drools.domain.Description> descriptions = validationService.findMatchingDescriptionInHierarchy(droolConcept, droolDescription);

        assertEquals(1, descriptions.size());
    }
}
