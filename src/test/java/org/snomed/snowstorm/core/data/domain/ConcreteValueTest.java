package org.snomed.snowstorm.core.data.domain;

import org.junit.jupiter.api.Test;
import org.snomed.otf.owltoolkit.domain.Relationship;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

class ConcreteValueTest {
    @Test
    public void dataTypeFromName_ShouldReturnExpectedDataType_WhenGivenExternalConcreteDecimal() {
        //given
        final String name = "decimal";

        //when
        final ConcreteValue.DataType result = ConcreteValue.DataType.fromName(name);
        final ConcreteValue.DataType expectedResult = ConcreteValue.DataType.DECIMAL;

        //then
        assertNotNull(result);
        assertEquals(expectedResult, result);
    }

    @Test
    public void dataTypeFromName_ShouldReturnExpectedDataType_WhenGivenExternalConcreteInteger() {
        //given
        final String name = "integer";

        //when
        final ConcreteValue.DataType result = ConcreteValue.DataType.fromName(name);
        final ConcreteValue.DataType expectedResult = ConcreteValue.DataType.INTEGER;

        //then
        assertNotNull(result);
        assertEquals(expectedResult, result);
    }

    @Test
    public void dataTypeFromName_ShouldReturnExpectedDataType_WhenGivenExternalConcreteString() {
        //given
        final String name = "string";

        //when
        final ConcreteValue.DataType result = ConcreteValue.DataType.fromName(name);
        final ConcreteValue.DataType expectedResult = ConcreteValue.DataType.STRING;

        //then
        assertNotNull(result);
        assertEquals(expectedResult, result);
    }

    @Test
    public void dataTypeFromName_ShouldThrowException_WhenGivenUnknownName() {
        //given
        final String name = "unknown";

        //then
        assertThrows(IllegalArgumentException.class, () -> ConcreteValue.DataType.fromName(name));
    }

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
    public void dataTypeFromShorthand_ShouldThrowException_WhenGivenUnknownShortHand() {
        //given
        final String shorthand = "unk";

        //then
        assertThrows(IllegalArgumentException.class, () -> ConcreteValue.DataType.fromName(shorthand));
    }

    @Test
    public void concreteValueFrom_ShouldThrowException_WhenValueDoesNotHaveConcretePrefixForString() {
        //given
        final String value = "\"A Promised Land\" is a memoir by Barack Obama.";
        final String dataType = "string";

        //then
        assertThrows(IllegalArgumentException.class, () -> ConcreteValue.from(value, dataType));
    }

    @Test
    public void concreteValueFrom_ShouldThrowException_WhenValueDoesNotHaveConcretePrefixForInteger() {
        //given
        final String value = "5";
        final String dataType = "integer";

        //then
        assertThrows(IllegalArgumentException.class, () -> ConcreteValue.from(value, dataType));
    }

    @Test
    public void concreteValueFrom_ShouldThrowException_WhenValueDoesNotHaveConcretePrefixForDecimal() {
        //given
        final String value = "3.14";
        final String dataType = "decimal";

        //then
        assertThrows(IllegalArgumentException.class, () -> ConcreteValue.from(value, dataType));
    }

    @Test
    public void concreteValueFrom_ShouldReturnExpectedConcreteValue_WhenGivenConcreteString() {
        //given
        final String inValue = "\"Two pills in the morning.\"";
        final String inDataType = "string";

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
        final String inDataType = "string";

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
        final String inDataType = "integer";

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
        final String inDataType = "decimal";

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
        final String inDataType = "decimal"; //But source states it is.

        //when
        final ConcreteValue concreteValue = ConcreteValue.from(inValue, inDataType);
        final String outValue = concreteValue.getValue();
        final ConcreteValue.DataType outDataType = concreteValue.getDataType();

        //then
        assertEquals("3", outValue);
        assertEquals(ConcreteValue.DataType.DECIMAL, outDataType);
    }


    @Test
    public void concreteValueFromShorthand_ShouldThrowException_WhenValueDoesNotHaveConcretePrefixForString() {
        //given
        final String value = "\"A Promised Land\" is a memoir by Barack Obama.";
        final String dataType = "str";

        //then
        assertThrows(IllegalArgumentException.class, () -> ConcreteValue.fromShorthand(value, dataType));
    }

    @Test
    public void concreteValueFromShorthand_ShouldThrowException_WhenValueDoesNotHaveConcretePrefixForInteger() {
        //given
        final String value = "5";
        final String dataType = "int";

        //then
        assertThrows(IllegalArgumentException.class, () -> ConcreteValue.fromShorthand(value, dataType));
    }

    @Test
    public void concreteValueFromShorthand_ShouldThrowException_WhenValueDoesNotHaveConcretePrefixForDecimal() {
        //given
        final String value = "3.14";
        final String dataType = "dec";

        //then
        assertThrows(IllegalArgumentException.class, () -> ConcreteValue.fromShorthand(value, dataType));
    }

    @Test
    public void concreteValueFromShorthand_ShouldReturnExpectedConcreteValue_WhenGivenConcreteString() {
        //given
        final String inValue = "\"Two pills in the morning.\"";
        final String inDataType = "str";

        //when
        final ConcreteValue concreteValue = ConcreteValue.fromShorthand(inValue, inDataType);
        final String outValue = concreteValue.getValue();
        final ConcreteValue.DataType outDataType = concreteValue.getDataType();

        //then
        assertEquals("Two pills in the morning.", outValue);
        assertEquals(ConcreteValue.DataType.STRING, outDataType);
    }

    @Test
    public void concreteValueFromShorthand_ShouldReturnExpectedConcreteValue_WhenGivenConcreteStringWithNestedQuote() {
        //given
        final String inValue = "\"\"A Promised Land\" is a memoir by Barack Obama.\"";
        final String inDataType = "str";

        //when
        final ConcreteValue concreteValue = ConcreteValue.fromShorthand(inValue, inDataType);
        final String outValue = concreteValue.getValue();
        final ConcreteValue.DataType outDataType = concreteValue.getDataType();

        //then
        assertEquals("\"A Promised Land\" is a memoir by Barack Obama.", outValue);
        assertEquals(ConcreteValue.DataType.STRING, outDataType);
    }

    @Test
    public void concreteValueFromShorthand_ShouldReturnExpectedConcreteValue_WhenGivenConcreteInteger() {
        //given
        final String inValue = "#5";
        final String inDataType = "int";

        //when
        final ConcreteValue concreteValue = ConcreteValue.fromShorthand(inValue, inDataType);
        final String outValue = concreteValue.getValue();
        final ConcreteValue.DataType outDataType = concreteValue.getDataType();

        //then
        assertEquals("5", outValue);
        assertEquals(ConcreteValue.DataType.INTEGER, outDataType);
    }

    @Test
    public void concreteValueFromShorthand_ShouldReturnExpectedConcreteValue_WhenGivenConcreteDecimal() {
        //given
        final String inValue = "#3.14";
        final String inDataType = "dec";

        //when
        final ConcreteValue concreteValue = ConcreteValue.fromShorthand(inValue, inDataType);
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
    public void concreteValueFromShorthand_ShouldReturnConcreteValueWithSameDataTypeAsInput() {
        //given
        final String inValue = "#3"; //Doesn't look like a Decimal.
        final String inDataType = "dec"; //But source states it is.

        //when
        final ConcreteValue concreteValue = ConcreteValue.fromShorthand(inValue, inDataType);
        final String outValue = concreteValue.getValue();
        final ConcreteValue.DataType outDataType = concreteValue.getDataType();

        //then
        assertEquals("3", outValue);
        assertEquals(ConcreteValue.DataType.DECIMAL, outDataType);
    }
}
