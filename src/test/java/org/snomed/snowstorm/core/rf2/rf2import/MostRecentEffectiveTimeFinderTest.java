package org.snomed.snowstorm.core.rf2.rf2import;

import io.kaicode.elasticvc.api.BranchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.CORE_MODULE;
import static org.snomed.snowstorm.core.data.domain.Concepts.MODEL_MODULE;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class MostRecentEffectiveTimeFinderTest extends AbstractTest {

    @Autowired
    private ConceptService conceptService;

    @Autowired
    private CodeSystemService codeSystemService;

    @Autowired
    private MostRecentEffectiveTimeFinder mostRecentEffectiveTimeFinder;

    @Autowired
    private ReferenceSetMemberService referenceSetMemberService;

    @Autowired
    private BranchService branchService;

    private CodeSystem codeSystem;

    @BeforeEach
    void setUp() {
        codeSystem = new CodeSystem("SNOMEDCT", "MAIN");
        codeSystemService.createCodeSystem(codeSystem);
    }

    @Test
    void findLatestEffectiveTimeByModuleId() throws ServiceException {
        String moduleA = "10000111";
        String moduleB = "10000222";
        String moduleC = "10000333";
        branchService.updateMetadata("MAIN", Map.of(Config.EXPECTED_EXTENSION_MODULES, List.of(CORE_MODULE, MODEL_MODULE, moduleA, moduleB, moduleC)));
        conceptService.create(new Concept("100000", CORE_MODULE), "MAIN");
        conceptService.create(new Concept("100001", moduleA), "MAIN");
        conceptService.create(new Concept("100002", moduleB), "MAIN");

        // Core depends on Model, C depends on B, B depends on A
        referenceSetMemberService.createMember("MAIN", new ReferenceSetMember().setModuleId(CORE_MODULE).setReferencedComponentId(MODEL_MODULE).setRefsetId(Concepts.MODULE_DEPENDENCY_REFERENCE_SET));
        referenceSetMemberService.createMember("MAIN", new ReferenceSetMember().setModuleId(moduleC).setReferencedComponentId(moduleB).setRefsetId(Concepts.MODULE_DEPENDENCY_REFERENCE_SET));
        referenceSetMemberService.createMember("MAIN", new ReferenceSetMember().setModuleId(moduleB).setReferencedComponentId(moduleA).setRefsetId(Concepts.MODULE_DEPENDENCY_REFERENCE_SET));
        codeSystemService.createVersion(codeSystem, 20240801, "20240801 release");

        Map<String, Integer> results = mostRecentEffectiveTimeFinder.getEffectiveTimeByModuleId("MAIN", false);
        assertEquals(4, results.size());
        assertEquals(20240801, results.get(CORE_MODULE).intValue());
        assertEquals(20240801, results.get(moduleA).intValue());
        assertEquals(20240801, results.get(moduleB).intValue());
        assertEquals(20240801, results.get(moduleC).intValue());

        results = mostRecentEffectiveTimeFinder.getEffectiveTimeByModuleId("MAIN", true);
        assertEquals(4, results.size());
        assertEquals(20240801, results.get(Concepts.MODEL_MODULE).intValue());

        conceptService.create(new Concept("100003", moduleA), "MAIN");
        conceptService.create(new Concept("100004", moduleC), "MAIN");
        codeSystemService.createVersion(codeSystem, 20240901, "20240901 release");

        results = mostRecentEffectiveTimeFinder.getEffectiveTimeByModuleId("MAIN", false);
        assertNotNull(results);
        assertEquals(4, results.size());
        assertEquals(20240901, results.get(CORE_MODULE).intValue());
        assertEquals(20240901, results.get(moduleA).intValue());
        // This will be 20240901 once dynamic modules are included for International
        assertEquals(20240801, results.get(moduleB).intValue());
        assertEquals(20240901, results.get(moduleC).intValue());
    }
}