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

        public static DataType from(final Relationship.ConcreteValue.Type type) {
            if (type == null) {
                throw new IllegalArgumentException("'type' cannot be null.");
            }

            final String typeName = type.getName();
            return DataType.valueOf(typeName);
        }

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
     * Create instance of ConcreteValue from source.
     *
     * @param concreteValue External type to be converted to internal type.
     * @return Instance of internal ConcreteValue type.
     */
    public static ConcreteValue from(final org.snomed.otf.owltoolkit.domain.Relationship.ConcreteValue concreteValue) {
        if (concreteValue == null) {
            throw new IllegalArgumentException("'concreteValue' cannot be null.");
        }

        final Relationship.ConcreteValue.Type externalType = concreteValue.getType();
        final DataType dataType = DataType.from(externalType);
        final String value = concreteValue.asString();
        return new ConcreteValue(value, dataType);
    }

    /**
     * Create instance of ConcreteValue.
     *
     * @param value    Concrete value with concrete prefix.
     * @param dataType Shorthand data type for concrete value.
     * @return Instance of ConcreteValue.
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
