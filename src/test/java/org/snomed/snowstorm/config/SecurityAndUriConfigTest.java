package org.snomed.snowstorm.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.TestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
public class SecurityAndUriConfigTest {
    @Autowired
    private SecurityAndUriConfig securityAndUriConfig;

    @Test
    void testSortOrderProperties() {
        assertNotNull(securityAndUriConfig);
        assertTrue(securityAndUriConfig.api().isEnabled());
    }
}
