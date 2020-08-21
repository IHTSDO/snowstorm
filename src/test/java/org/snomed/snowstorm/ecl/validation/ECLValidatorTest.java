package org.snomed.snowstorm.ecl.validation;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;

import static java.lang.String.join;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ECLValidatorTest extends AbstractTest {

    private static final String BRANCH = "MAIN";

    private static final String EXCEPTION_MESSAGE = "Concepts in the ECL request do not exist or are inactive on branch " + BRANCH + ": ";

    private static final String CONCEPT_IDS_DELIMITER = ", ";

    @Autowired
    private ConceptService conceptService;

    @Autowired
    private ECLValidator eclValidator;

    @Test
    public void validateWithActiveConcepts() throws ServiceException {
        createActiveConcept("415360003");

        String eclExpression = "415360003 |GenSevere acute respiratory syndrome-related coronavirus (organism)|";
        assertThatCode(() -> eclValidator.validate(eclExpression, BRANCH)).doesNotThrowAnyException();
    }

    @Test
    public void validateWithActiveAndExistingConcepts() throws ServiceException {
        createActiveConcept("246075003");
        oneExistingConcept("387517004");

        String eclExpression = "* : 246075003 |Causative agent| = 387517004 |Paracetamol|";
        assertThatCode(() -> eclValidator.validate(eclExpression, BRANCH)).doesNotThrowAnyException();
    }

    @Test
    public void failValidationWithInactiveConcepts() throws ServiceException {
        createInactiveConcept("243608008");

        String eclExpression = "243608008 |Genus Coronavirus (organism)|";
        assertException(eclExpression, "243608008");
    }

    @Test
    public void failValidationWithActiveAndNonexistentConcepts() throws ServiceException {
        createActiveConcept("246075003");
        nonexistentConcept("387517004");

        String eclExpression = "* : 246075003 |Causative agent| = 387517004 |Paracetamol|";
        assertException(eclExpression, "387517004");
    }

    @Test
    public void failValidationWithInactiveAndExistingConcepts() throws ServiceException {
        createInactiveConcept("246075003");
        oneExistingConcept("387517004");

        String eclExpression = "* : 246075003 |Causative agent| = 387517004 |Paracetamol|";
        assertException(eclExpression, "246075003");
    }

    @Test
    public void failValidationWithInactiveAndNonexistentConcepts() throws ServiceException {
        createInactiveConcept("246075003");
        nonexistentConcept("387517004");

        String eclExpression = "* : 246075003 |Causative agent| = 387517004 |Paracetamol|";
        assertException(eclExpression, "246075003", "387517004");
    }

    @Test
    public void failValidationWithTwoInactiveAndOneNonexistentConcepts() throws ServiceException {
        createInactiveConcept("105590001");
        createInactiveConcept("127489000");
        nonexistentConcept("249999999101");

        String eclExpression = "< 105590001 |Substance|: R 127489000 |Has active ingredient| = 249999999101 |TRIPHASIL tablet|";
        assertException(eclExpression, "127489000", "105590001", "249999999101");
    }

    private void assertException(String eclExpression, String... conceptIds) {
        String exceptionMessage = EXCEPTION_MESSAGE + join(CONCEPT_IDS_DELIMITER, conceptIds) + ".";
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> eclValidator.validate(eclExpression, BRANCH));
        assertThat(exception.getMessage()).isEqualTo(exceptionMessage);
    }

    private void createActiveConcept(String conceptId) throws ServiceException {
        createConcept(conceptId, true);
    }

    private void createInactiveConcept(String conceptId) throws ServiceException {
        createConcept(conceptId, false);
    }

    private void oneExistingConcept(String conceptId) throws ServiceException {
        createConcept(conceptId, true);
    }

    private void createConcept(String conceptId, boolean isActive) throws ServiceException {
        Concept concept = new Concept(conceptId, 20020131, isActive, Concepts.CORE_MODULE, "900000000000074008");
        conceptService.create(concept, BRANCH);
    }

    private void nonexistentConcept(String conceptId) {
        if (conceptService.find(conceptId, BRANCH) != null) {
            conceptService.deleteConceptAndComponents(conceptId, BRANCH, true);
        }
    }
}
