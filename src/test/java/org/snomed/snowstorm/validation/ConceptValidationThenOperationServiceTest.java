package org.snomed.snowstorm.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockito.Mockito;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.validation.ConceptValidationThenOperationService.ConceptValidationOperation;

import static org.junit.Assert.assertEquals;

public class ConceptValidationThenOperationServiceTest {

	@Test
	public void testWithParameters() {
		final Concept concept = Mockito.mock(Concept.class);
		final ConceptValidationThenOperationService conceptValidationThenOperationService =
				new ConceptValidationThenOperationService().withParameters(concept, "en", "MAIN");
		assertEquals(concept, conceptValidationThenOperationService.getConcept());
		assertEquals("en", conceptValidationThenOperationService.getAcceptLanguageHeader());
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
		new ConceptValidationThenOperationService().withParameters(null, "en", "MAIN")
				.thenDo(ConceptValidationOperation.CREATE).execute();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testCallingExecuteWithoutSettingAcceptLanguageHeaderThrowsException() throws JsonProcessingException, ServiceException {
		new ConceptValidationThenOperationService().withParameters(Mockito.mock(Concept.class), null, "MAIN")
				.thenDo(ConceptValidationOperation.CREATE).execute();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testCallingExecuteWithoutSettingBranchPathThrowsException() throws JsonProcessingException, ServiceException {
		new ConceptValidationThenOperationService().withParameters(Mockito.mock(Concept.class), "en", null)
				.thenDo(ConceptValidationOperation.CREATE).execute();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testCallingExecuteWithoutSettingOperationThrowsException() throws JsonProcessingException, ServiceException {
		new ConceptValidationThenOperationService().withParameters(Mockito.mock(Concept.class), "en", "MAIN")
				.thenDo(null).execute();
	}
}
