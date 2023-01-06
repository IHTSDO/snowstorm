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
class SortOrderPropertiesTest {

	@Autowired
	private SortOrderProperties sortOrderProperties;

	@Test
	void testSortOrderProperties() {
		assertNotNull(sortOrderProperties);
		assertNotNull(sortOrderProperties.getAttribute());

		Map<String, Map<Long, Short>> domainAttributeOrderMap = sortOrderProperties.getDomainAttributeOrderMap();
		assertTrue(domainAttributeOrderMap.keySet().size() > 5);

		Map<Long, Short> clinicalFindingAttributeOrders = domainAttributeOrderMap.get("finding");
		assertTrue(clinicalFindingAttributeOrders.keySet().size() > 5);
	}
}
