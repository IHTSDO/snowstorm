package org.snomed.snowstorm.core.data.domain;

import org.junit.jupiter.api.Test;
import org.snomed.otf.owltoolkit.domain.Relationship;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

class ConcreteValueTest {
    @Test
    public void dataTypeFromShortHand_ShouldReturnExpectedDataType_WhenGivenExternalConcreteDec() {
        //given
        final String shorthand = "dec";

        //when
        final ConcreteValue.DataType result = ConcreteValue.DataType.fromShorthand(shorthand);
        final ConcreteValue.DataType expectedResult = ConcreteValue.DataType.DECIMAL;

        //then
        assertNotNull(result);
        assertEquals(expectedResult, result);
    }

    @Test
    public void dataTypeFromShortHand_ShouldReturnExpectedDataType_WhenGivenExternalConcreteInt() {
        //given
        final String shorthand = "int";

        //when
        final ConcreteValue.DataType result = ConcreteValue.DataType.fromShorthand(shorthand);
        final ConcreteValue.DataType expectedResult = ConcreteValue.DataType.INTEGER;

        //then
        assertNotNull(result);
        assertEquals(expectedResult, result);
    }

    @Test
    public void dataTypeFromShortHand_ShouldReturnExpectedDataType_WhenGivenExternalConcreteStr() {
        //given
        final String shorthand = "str";

        //when
        final ConcreteValue.DataType result = ConcreteValue.DataType.fromShorthand(shorthand);
        final ConcreteValue.DataType expectedResult = ConcreteValue.DataType.STRING;

        //then
        assertNotNull(result);
        assertEquals(expectedResult, result);
    }

    @Test
    public void concreteValueFrom_ShouldReturnExpectedConcreteValue_WhenGivenConcreteString() {
        //given
        final String inValue = "\"Two pills in the morning.\"";
        final String inDataType = "STRING";

        //when
        final ConcreteValue concreteValue = ConcreteValue.from(inValue, inDataType);
        final String outValue = concreteValue.getValue();
        final ConcreteValue.DataType outDataType = concreteValue.getDataType();

        //then
        assertEquals("Two pills in the morning.", outValue);
        assertEquals(ConcreteValue.DataType.STRING, outDataType);
    }

    @Test
    public void concreteValueFrom_ShouldReturnExpectedConcreteValue_WhenGivenConcreteStringWithNestedQuote() {
        //given
        final String inValue = "\"\"A Promised Land\" is a memoir by Barack Obama.\"";
        final String inDataType = "STRING";

        //when
        final ConcreteValue concreteValue = ConcreteValue.from(inValue, inDataType);
        final String outValue = concreteValue.getValue();
        final ConcreteValue.DataType outDataType = concreteValue.getDataType();

        //then
        assertEquals("\"A Promised Land\" is a memoir by Barack Obama.", outValue);
        assertEquals(ConcreteValue.DataType.STRING, outDataType);
    }

    @Test
    public void concreteValueFrom_ShouldReturnExpectedConcreteValue_WhenGivenConcreteInteger() {
        //given
        final String inValue = "#5";
        final String inDataType = "INTEGER";

        //when
        final ConcreteValue concreteValue = ConcreteValue.from(inValue, inDataType);
        final String outValue = concreteValue.getValue();
        final ConcreteValue.DataType outDataType = concreteValue.getDataType();

        //then
        assertEquals("5", outValue);
        assertEquals(ConcreteValue.DataType.INTEGER, outDataType);
    }

    @Test
    public void concreteValueFrom_ShouldReturnExpectedConcreteValue_WhenGivenConcreteDecimal() {
        //given
        final String inValue = "#3.14";
        final String inDataType = "DECIMAL";

        //when
        final ConcreteValue concreteValue = ConcreteValue.from(inValue, inDataType);
        final String outValue = concreteValue.getValue();
        final ConcreteValue.DataType outDataType = concreteValue.getDataType();

        //then
        assertEquals("3.14", outValue);
        assertEquals(ConcreteValue.DataType.DECIMAL, outDataType);
    }

    /*
     * Testing dataType is the same as given dataType, i.e.
     * same as use case for using MRCM.
     * */
    @Test
    public void concreteValueFrom_ShouldReturnConcreteValueWithSameDataTypeAsInput() {
        //given
        final String inValue = "#3"; //Doesn't look like a Decimal.
        final String inDataType = "DECIMAL"; //But source states it is.

        //when
        final ConcreteValue concreteValue = ConcreteValue.from(inValue, inDataType);
        final String outValue = concreteValue.getValue();
        final ConcreteValue.DataType outDataType = concreteValue.getDataType();

        //then
        assertEquals("3", outValue);
        assertEquals(ConcreteValue.DataType.DECIMAL, outDataType);
    }

    @Test
    public void removeConcretePrefix_ShouldRemoveConcretePrefix_WhenGivenString() {
        //given
        final String value = "\"Two pills per day.\"";

        //when
        final String result = ConcreteValue.removeConcretePrefix(value);

        //then
        assertEquals("Two pills per day.", result);
    }

    @Test
    public void removeConcretePrefix_ShouldRemoveConcretePrefix_WhenGivenInteger() {
        //given
        final String value = "#5";

        //when
        final String result = ConcreteValue.removeConcretePrefix(value);

        //then
        assertEquals("5", result);
    }

    @Test
    public void removeConcretePrefix_ShouldRemoveConcretePrefix_WhenGivenDecimal() {
        //given
        final String value = "#3.14";

        //when
        final String result = ConcreteValue.removeConcretePrefix(value);

        //then
        assertEquals("3.14", result);
    }
}
