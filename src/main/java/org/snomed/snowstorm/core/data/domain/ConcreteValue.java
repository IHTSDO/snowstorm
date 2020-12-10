package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.otf.owltoolkit.domain.Relationship;
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
        DECIMAL("DECIMAL", "dec"),
        INTEGER("INTEGER", "int"),
        STRING("STRING", "str");

        private final String name;
        private final String shorthand;

        DataType(final String name,
                 final String shorthand) {
            this.name = name;
            this.shorthand = shorthand;
        }

        public String getName() {
            return this.name;
        }

        public String getShorthand() {
            return this.shorthand;
        }

        /**
         * Create instance of DataType.
         *
         * @param name Data type for concrete value.
         * @return Instance of DataType.
         * @throws IllegalArgumentException If name is not recognised.
         */
        public static DataType fromName(final String name) {
            if (name == null) {
                throw new IllegalArgumentException("'dataType' cannot be null.");
            }

            switch (name.toUpperCase()) {
                case "DECIMAL":
                    return DataType.DECIMAL;
                case "INTEGER":
                    return DataType.INTEGER;
                case "STRING":
                    return DataType.STRING;
                default:
                    throw new IllegalArgumentException("Unknown name: " + name);
            }
        }

        /**
         * Create instance of DataType.
         *
         * @param dataType Shorthand data type for concrete value.
         * @return Instance of DataType.
         * @throws IllegalArgumentException If name is not recognised.
         */
        public static DataType fromShorthand(final String dataType) {
            if (dataType == null) {
                throw new IllegalArgumentException("'dataType' cannot be null.");
            }

            switch (dataType.toLowerCase()) {
                case "dec":
                    return DataType.DECIMAL;
                case "int":
                    return DataType.INTEGER;
                case "str":
                    return DataType.STRING;
                default:
                    throw new IllegalArgumentException("Unknown data type: " + dataType);
            }
        }
    }

    /**
     * Create instance of ConcreteValue.
     *
     * @param value    Concrete value with concrete prefix.
     * @param dataType Data type for concrete value.
     * @return Instance of ConcreteValue.
     * @throws IllegalArgumentException If value does not have concrete prefix.
     */
    public static ConcreteValue from(String value,
                                     final String dataType) {
        if (value == null || dataType == null) {
            throw new IllegalArgumentException();
        }

        if (value.startsWith("#")) {
            value = value.substring(1);
        } else if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1);
            value = value.substring(0, value.length() - 1);
        } else {
            throw new IllegalArgumentException("No concrete prefix present.");
        }

        final DataType dT = DataType.fromName(dataType);
        return new ConcreteValue(value, dT);
    }

    /**
     * Create instance of ConcreteValue.
     *
     * @param value    Concrete value with concrete prefix.
     * @param dataType Shorthand data type for concrete value.
     * @return Instance of ConcreteValue.
     * @throws IllegalArgumentException If value does not have concrete prefix.
     */
    public static ConcreteValue fromShorthand(String value,
                                              final String dataType) {
        if (value == null || dataType == null) {
            throw new IllegalArgumentException();
        }

        if (value.startsWith("#")) {
            value = value.substring(1);
        } else if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1);
            value = value.substring(0, value.length() - 1);
        } else {
            throw new IllegalArgumentException("No concrete prefix present.");
        }

        final DataType dT = DataType.fromShorthand(dataType);
        return new ConcreteValue(value, dT);
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
