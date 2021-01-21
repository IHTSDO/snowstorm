package org.snomed.snowstorm.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockito.Mockito;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.validation.ConceptValidationThenOperationService.ConceptValidationOperation;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class ConceptValidationThenOperationServiceTest {

	@Test
	public void testWithParameters() {
		final Concept concept = Mockito.mock(Concept.class);
		final ConceptValidationThenOperationService conceptValidationThenOperationService =
				new ConceptValidationThenOperationService().withParameters(concept, Collections.emptyList(), "MAIN");
		assertEquals(concept, conceptValidationThenOperationService.getConcept());
		assertEquals("MAIN", conceptValidationThenOperationService.getBranchPath());
	}

	@Test
	public void testThenDo() {
		final ConceptValidationThenOperationService conceptValidationThenOperationService =
				new ConceptValidationThenOperationService().thenDo(ConceptValidationOperation.CREATE);
		assertEquals(ConceptValidationOperation.CREATE, conceptValidationThenOperationService.getOperation());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testCallingExecuteWithoutSettingConceptThrowsException() throws JsonProcessingException, ServiceException {
		new ConceptValidationThenOperationService().withParameters(null, Collections.emptyList(), "MAIN")
				.thenDo(ConceptValidationOperation.CREATE).execute();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testCallingExecuteWithoutSettingBranchPathThrowsException() throws JsonProcessingException, ServiceException {
		new ConceptValidationThenOperationService().withParameters(Mockito.mock(Concept.class), Collections.emptyList(), null)
				.thenDo(ConceptValidationOperation.CREATE).execute();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testCallingExecuteWithoutSettingOperationThrowsException() throws JsonProcessingException, ServiceException {
		new ConceptValidationThenOperationService().withParameters(Mockito.mock(Concept.class), Collections.emptyList(), "MAIN")
				.thenDo(null).execute();
	}
}
