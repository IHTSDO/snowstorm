package org.snomed.snowstorm.validation;

import org.ihtsdo.drools.service.TestResourceProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DescriptionDroolsValidationServiceNamedSetTest {

	private static final String EXEMPT_MODULES = "redundant-isa-exempt-modules";
	private static final String AU_MODULE = "32506021000036107";

	private DescriptionDroolsValidationService validationService;

	@BeforeEach
	void setup() {
		TestResourceProvider testResourceProvider = mock(TestResourceProvider.class);
		when(testResourceProvider.getSemanticHierarchyMap()).thenReturn(Map.of(
				EXEMPT_MODULES, Set.of(AU_MODULE),
				"empty-set", Collections.emptySet()));
		validationService = new DescriptionDroolsValidationService("MAIN", null, null, null, null, testResourceProvider, Collections.emptySet());
	}

	@Test
	void listedValueIsInSet() {
		assertTrue(validationService.isInNamedSet(EXEMPT_MODULES, AU_MODULE));
	}

	@Test
	void unlistedValueIsNotInSet() {
		assertFalse(validationService.isInNamedSet(EXEMPT_MODULES, "900000000000207008"));
	}

	@Test
	void absentOrEmptyKeyMatchesNothing() {
		assertFalse(validationService.isInNamedSet("no-such-key", AU_MODULE));
		assertFalse(validationService.isInNamedSet("empty-set", AU_MODULE));
	}

	@Test
	void substringOfListedValueDoesNotMatch() {
		assertFalse(validationService.isInNamedSet(EXEMPT_MODULES, "3250602100003610"));
		assertFalse(validationService.isInNamedSet(EXEMPT_MODULES, AU_MODULE + ",1"));
	}

	@Test
	void nullsAreNotInSet() {
		assertFalse(validationService.isInNamedSet(null, AU_MODULE));
		assertFalse(validationService.isInNamedSet(EXEMPT_MODULES, null));
	}
}
