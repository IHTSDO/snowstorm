package org.snomed.snowstorm.core.data.domain;

import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

class ConcreteValueTest {
    @Test
    public void concreteValue_ShouldThrowException_WhenGivenNull() {
        //then
        assertThrows(IllegalArgumentException.class, () -> new ConcreteValue(null));
    }

    @Test
    public void concreteValue_ShouldThrowException_WhenGivenNonConcreteValue() {
        //given
        final String nonConcreteValue = "123456789";

        //then
        assertThrows(IllegalArgumentException.class, () -> new ConcreteValue(nonConcreteValue));
    }

    @Test
    public void concreteValue_ShouldThrowException_WhenGivenBareMinimumNumber() {
        //given
        final String nonConcreteValue = "#";

        //then
        assertThrows(IllegalArgumentException.class, () -> new ConcreteValue(nonConcreteValue));
    }

    @Test
    public void concreteValue_ShouldThrowException_WhenGivenBareMinimumString() {
        //given
        final String nonConcreteValue = "\"";

        //then
        assertThrows(IllegalArgumentException.class, () -> new ConcreteValue(nonConcreteValue));
    }

    @Test
    public void concreteValue_ShouldStoreCorrectDataType_WhenGivenString() {
        //given
        final String input = "\"Two pills daily.\"";

        //when
        final ConcreteValue concreteValue = new ConcreteValue(input);
        final ConcreteValue.DataType result = concreteValue.getDataType();

        //then
        assertEquals(ConcreteValue.DataType.STRING, result);
    }

    @Test
    public void concreteValue_ShouldStoreCorrectDataType_WhenGivenDecimal() {
        //given
        final String input = "#5.5";

        //when
        final ConcreteValue concreteValue = new ConcreteValue(input);
        final ConcreteValue.DataType result = concreteValue.getDataType();

        //then
        assertEquals(ConcreteValue.DataType.DECIMAL, result);
    }

    @Test
    public void concreteValue_ShouldStoreCorrectDataType_WhenGivenInteger() {
        //given
        final String input = "#100";

        //when
        final ConcreteValue concreteValue = new ConcreteValue(input);
        final ConcreteValue.DataType result = concreteValue.getDataType();

        //then
        assertEquals(ConcreteValue.DataType.INTEGER, result);
    }

    @Test
    public void concreteValue_ShouldStoreCorrectValue_WhenGivenConcreteString() {
        //given
        final String input = "\"\"A Promised Land\" is a memoir by Barack Obama.\"";

        //when
        final ConcreteValue concreteValue = new ConcreteValue(input);
        final String actualResult = concreteValue.getValue();
        final String expectedResult = "\"\"A Promised Land\" is a memoir by Barack Obama.\"";

        //then
        assertEquals(expectedResult, actualResult);
    }

    @Test
    public void concreteValue_ShouldStoreCorrectValue_WhenGivenConcreteStringWithHash() {
        //given
        final String input = "\"Take pill #4 twice a day.\"";

        //when
        final ConcreteValue concreteValue = new ConcreteValue(input);
        final String actualResult = concreteValue.getValue();
        final String expectedResult = "\"Take pill #4 twice a day.\"";

        //then
        assertEquals(expectedResult, actualResult);
    }

    @Test
    public void concreteValue_ShouldStoreCorrectValue_WhenGivenConcreteInteger() {
        //given
        final String input = "#26112020";

        //when
        final ConcreteValue concreteValue = new ConcreteValue(input);
        final String actualResult = concreteValue.getValue();
        final String expectedResult = "26112020";

        //then
        assertEquals(expectedResult, actualResult);
    }

    @Test
    public void concreteValue_ShouldStoreCorrectValue_WhenGivenConcreteDecimal() {
        //given
        final String input = "#3.14";

        //when
        final ConcreteValue concreteValue = new ConcreteValue(input);
        final String actualResult = concreteValue.getValue();
        final String expectedResult = "3.14";

        //then
        assertEquals(expectedResult, actualResult);
    }
}