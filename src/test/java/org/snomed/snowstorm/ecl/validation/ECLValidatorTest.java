package org.snomed.snowstorm.ecl.validation;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ECLValidatorTest extends AbstractTest {

    private static final String MAIN_BRANCH = "MAIN";

    @Autowired
    private ConceptService conceptService;

    @Autowired
    private ECLValidator eclValidator;

    @Test
    public void validateEclExpressionWithActiveConcepts() throws ServiceException {
        createActiveConcept("415360003");

        String eclExpression = "415360003 |GenSevere acute respiratory syndrome-related coronavirus (organism)|";
        assertThatCode(() -> eclValidator.validateEcl(eclExpression, MAIN_BRANCH)).doesNotThrowAnyException();
    }

    @Test
    public void failValidationEclExpressionWithInactiveConcepts() throws ServiceException {
        createInactiveConcept("243608008");

        String eclExpression = "243608008 |Genus Coronavirus (organism)|";
        assertThrows(IllegalArgumentException.class, () -> eclValidator.validateEcl(eclExpression, MAIN_BRANCH));
    }

    private void createActiveConcept(String conceptId) throws ServiceException {
        createConcept(conceptId, true);
    }

    private void createInactiveConcept(String conceptId) throws ServiceException {
        createConcept(conceptId, false);
    }

    private void createConcept(String conceptId, boolean isActive) throws ServiceException {
        Concept inactiveConcept = new Concept(conceptId, 20020131, isActive, Concepts.CORE_MODULE, "900000000000074008");
        conceptService.create(inactiveConcept, MAIN_BRANCH);
    }
}