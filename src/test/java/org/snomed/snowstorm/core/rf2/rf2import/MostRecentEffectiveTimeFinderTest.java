package org.snomed.snowstorm.core.rf2.rf2import;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class MostRecentEffectiveTimeFinderTest extends AbstractTest {

    @Autowired
    private ConceptService conceptService;

    @Autowired
    private CodeSystemService codeSystemService;

    @Autowired
    private MostRecentEffectiveTimeFinder mostRecentEffectiveTimeFinder;

    private CodeSystem codeSystem;

    @BeforeEach
    void setUp() {
        codeSystem = new CodeSystem("SNOMEDCT", "MAIN");
        codeSystemService.createCodeSystem(codeSystem);
    }

    @Test
    void findLatestEffectiveTimeByModuleId() throws ServiceException {
        // default core module
        conceptService.create(new Concept("100000"), "MAIN");
        conceptService.create(new Concept("100001", "10000111"), "MAIN");
        conceptService.create(new Concept("100002", "10000222"), "MAIN");
        codeSystemService.createVersion(codeSystem, 20240801, "20240801 release");

        Map<String, Integer> results = mostRecentEffectiveTimeFinder.getEffectiveTimeByModuleId("MAIN", false);
        assertNotNull(results);
        assertEquals(3, results.size());
        assertEquals(20240801, results.get("10000111").intValue());
        assertEquals(20240801, results.get("10000222").intValue());
        assertEquals(20240801, results.get(Concepts.CORE_MODULE).intValue());


        results = mostRecentEffectiveTimeFinder.getEffectiveTimeByModuleId("MAIN", true);
        assertNotNull(results);
        assertEquals(4, results.size(), "Model module is included");
        assertEquals(20240801, results.get(Concepts.MODEL_MODULE).intValue());


        conceptService.create(new Concept("100003", "10000111"), "MAIN");
        conceptService.create(new Concept("100004", "10000333"), "MAIN");
        codeSystemService.createVersion(codeSystem, 20240901, "20240901 release");

        results = mostRecentEffectiveTimeFinder.getEffectiveTimeByModuleId("MAIN", false);
        assertNotNull(results);
        assertEquals(4, results.size());
        assertEquals(20240901, results.get("10000111").intValue());
        // Even there is no change for module id 10000222 however MDRS is automatically updated to the latest effective time during versioning
        assertEquals(20240901, results.get("10000222").intValue());
        assertEquals(20240901, results.get("10000333").intValue());
    }
}