package org.snomed.snowstorm.syndication.services.importers.fixedversion.ucum;

import org.fhir.ucum.UcumEssenceService;
import org.fhir.ucum.UcumService;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;

class UcumCodeValidationServiceTest {

    private UcumCodeValidationService service;

    @BeforeEach
    void setup() {
        service = new UcumCodeValidationService();
    }

    @Test
    void validateCode_error() {
        Parameters result = service.validateCode("code", "codeSystem");
        assertEquals("BooleanType[false]", result.getParameter("result").getValue().toString());
        assertTrue(result.hasParameter("message"));
    }

    @Test
    void validateCode_ok() {
        UcumEssenceService ucumEssenceService = Mockito.mock(UcumEssenceService.class);
        doReturn(new UcumService.UcumVersionDetails(null, null)).when(ucumEssenceService).ucumIdentification();
        ReflectionTestUtils.setField(service, "ucumService", ucumEssenceService);
        Parameters result = service.validateCode("code", "codeSystem");
        assertEquals("BooleanType[true]", result.getParameter("result").getValue().toString());
        assertFalse(result.hasParameter("message"));
    }
}