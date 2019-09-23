package org.snomed.snowstorm.config;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Map;

import static java.lang.Long.parseLong;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class SortOrderPropertiesTest {

	@Autowired
	private SortOrderProperties sortOrderProperties;

	@Test
	public void testSortOrderProperties() {
		assertNotNull(sortOrderProperties);
		assertNotNull(sortOrderProperties.getAttribute());

		Map<String, Map<Long, Short>> domainAttributeOrderMap = sortOrderProperties.getDomainAttributeOrderMap();
		assertTrue(domainAttributeOrderMap.keySet().size() > 5);

		Map<Long, Short> clinicalFindingAttributeOrders = domainAttributeOrderMap.get("finding");
		assertTrue(clinicalFindingAttributeOrders.keySet().size() > 5);
	}
}
