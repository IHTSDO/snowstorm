package org.snomed.snowstorm.core.data.domain;

import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

class ConceptValueTest {
    @Test
    public void conceptValue_ShouldThrowException_WhenGivenNull() {
        //then
        assertThrows(IllegalArgumentException.class, () -> new ConceptValue(null));
    }

    @Test
    public void conceptValue_ShouldThrowException_WhenGivenNonConcreteValue() {
        //given
        final String nonConcreteValue = "123456789";

        //then
        assertThrows(IllegalArgumentException.class, () -> new ConceptValue(nonConcreteValue));
    }

    @Test
    public void conceptValue_ShouldThrowException_WhenGivenBareMinimumNumber() {
        //given
        final String nonConcreteValue = "#";

        //then
        assertThrows(IllegalArgumentException.class, () -> new ConceptValue(nonConcreteValue));
    }

    @Test
    public void conceptValue_ShouldThrowException_WhenGivenBareMinimumString() {
        //given
        final String nonConcreteValue = "\"";

        //then
        assertThrows(IllegalArgumentException.class, () -> new ConceptValue(nonConcreteValue));
    }

    @Test
    public void conceptValue_ShouldStoreCorrectDataType_WhenGivenString() {
        //given
        final String input = "\"Two pills daily.\"";

        //when
        final ConceptValue conceptValue = new ConceptValue(input);
        final ConceptValue.DataType result = conceptValue.getDataType();

        //then
        assertEquals(ConceptValue.DataType.STRING, result);
    }

    @Test
    public void conceptValue_ShouldStoreCorrectDataType_WhenGivenDecimal() {
        //given
        final String input = "#5.5";

        //when
        final ConceptValue conceptValue = new ConceptValue(input);
        final ConceptValue.DataType result = conceptValue.getDataType();

        //then
        assertEquals(ConceptValue.DataType.DECIMAL, result);
    }

    @Test
    public void conceptValue_ShouldStoreCorrectDataType_WhenGivenInteger() {
        //given
        final String input = "#100";

        //when
        final ConceptValue conceptValue = new ConceptValue(input);
        final ConceptValue.DataType result = conceptValue.getDataType();

        //then
        assertEquals(ConceptValue.DataType.INTEGER, result);
    }

    @Test
    public void conceptValue_ShouldStoreCorrectValue_WhenGivenConcreteString() {
        //given
        final String input = "\"\"A Promised Land\" is a memoir by Barack Obama.\"";

        //when
        final ConceptValue conceptValue = new ConceptValue(input);
        final String actualResult = conceptValue.getValue();
        final String expectedResult = "\"\"A Promised Land\" is a memoir by Barack Obama.\"";

        //then
        assertEquals(expectedResult, actualResult);
    }

    @Test
    public void conceptValue_ShouldStoreCorrectValue_WhenGivenConcreteStringWithHash() {
        //given
        final String input = "\"Take pill #4 twice a day.\"";

        //when
        final ConceptValue conceptValue = new ConceptValue(input);
        final String actualResult = conceptValue.getValue();
        final String expectedResult = "\"Take pill #4 twice a day.\"";

        //then
        assertEquals(expectedResult, actualResult);
    }

    @Test
    public void conceptValue_ShouldStoreCorrectValue_WhenGivenConcreteInteger() {
        //given
        final String input = "#26112020";

        //when
        final ConceptValue conceptValue = new ConceptValue(input);
        final String actualResult = conceptValue.getValue();
        final String expectedResult = "26112020";

        //then
        assertEquals(expectedResult, actualResult);
    }

    @Test
    public void conceptValue_ShouldStoreCorrectValue_WhenGivenConcreteDecimal() {
        //given
        final String input = "#3.14";

        //when
        final ConceptValue conceptValue = new ConceptValue(input);
        final String actualResult = conceptValue.getValue();
        final String expectedResult = "3.14";

        //then
        assertEquals(expectedResult, actualResult);
    }
}