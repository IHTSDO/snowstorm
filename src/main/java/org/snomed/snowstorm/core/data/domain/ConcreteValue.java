package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.rest.View;
import org.springframework.data.annotation.Transient;

/**
 * Represent a concrete value.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonView(value = View.Component.class)
public class ConcreteValue {
    @Transient
    private final ConcreteValue.DataType dataType;

    @Transient
    private final String value;

    public enum DataType {
        DECIMAL("dec"),
        INTEGER("int"),
        STRING("str");

        private final String shorthand;

        DataType(String shorthand) {
            this.shorthand = shorthand;
        }

        public String getShorthand() {
            return this.shorthand;
        }

        /**
         * Create instance of DataType.
         *
         * @param dataType Shorthand data type for concrete value.
         * @return Instance of DataType.
         */
        public static DataType fromShorthand(String dataType) {
            if (dataType == null) {
                throw new IllegalArgumentException("'dataType' cannot be null.");
            }

            for (DataType type : DataType.values()) {
                if (type.getShorthand().equals(dataType)) {
                    return type;
                }
            }

            return null;
        }
    }

    /**
     * Create instance of ConcreteValue.
     *
     * @param value    Concrete value with concrete prefix.
     * @param dataType Data type for concrete value.
     * @return Instance of ConcreteValue.
     * @throws IllegalArgumentException If dataType is not recognised.
     */
    public static ConcreteValue from(String value, String dataType) {
        if (value == null || dataType == null) {
            throw new IllegalArgumentException();
        }

        return new ConcreteValue(
                ConcreteValue.removeConcretePrefix(value),
                DataType.fromShorthand(dataType)
        );
    }

    /**
     * Return new String with concrete prefix removed.
     *
     * @param value Concrete value with concrete prefix.
     * @return String with concrete prefix removed.
     */
    public static String removeConcretePrefix(String value) {
        String valueWithoutPrefix = value;
        valueWithoutPrefix = valueWithoutPrefix.substring(1);
        if (valueWithoutPrefix.endsWith("\"")) {
            valueWithoutPrefix = valueWithoutPrefix.substring(0, valueWithoutPrefix.length() - 1);
        }

        return valueWithoutPrefix;
    }

    public ConcreteValue(String value, ConcreteValue.DataType dataType) {
        this.value = value;
        this.dataType = dataType;
    }

    public ConcreteValue.DataType getDataType() {
        return this.dataType;
    }

    public String getValue() {
        return this.value;
    }
}
