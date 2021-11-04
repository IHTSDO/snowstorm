package org.snomed.snowstorm.core.data.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.rest.View;
import org.springframework.data.annotation.Transient;

import java.util.Arrays;
import java.util.Objects;

/**
 * Represent a concrete value.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonView(value = View.Component.class)
public class ConcreteValue {

	@Transient
	private ConcreteValue.DataType dataType;

	@Transient
	private String value;

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

			return Arrays.stream(DataType.values()).filter(type -> type.getShorthand().equals(dataType)).findFirst().orElse(null);
		}

		/**
		 * Create instance of DataType.
		 * @return Instance of DataType.
		 */
		public static DataType fromPrefix(String value) {
			if (value == null || value.length() < 2) {
				throw new IllegalArgumentException("'value' cannot be null or less than 2 characters long. Received: '" + value + "'");
			}
			//We'll have to assume a decimal given a # symbol with no further information
			switch (value.charAt(0)) {
				case '\"': return DataType.STRING;
				case '#': return DataType.DECIMAL;
				default : throw new IllegalArgumentException("Unrecognised concrete value prefix in  '" + value + "'");
			}
		}
	}

	public ConcreteValue() {
	}

	public ConcreteValue(final String value, final ConcreteValue.DataType dataType) {
		this.value = value;
		this.dataType = dataType;
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
			throw new IllegalArgumentException("The concrete value with prefix or data type cannot be null. Value: " + value + " | Data type: " + dataType);
		}

		return new ConcreteValue(
				ConcreteValue.removeConcretePrefix(value),
				DataType.fromShorthand(dataType)
		);
	}

	/**
	 * Create instance of ConcreteValue.
	 * @param value    Concrete value with concrete prefix.
	 * @return Instance of ConcreteValue.
	 * @throws IllegalArgumentException If dataType is not recognised.
	 */
	public static ConcreteValue from(String value) {
		if (value == null) {
			throw new IllegalArgumentException("The concrete value with prefix cannot be null.");
		}

		return new ConcreteValue(
				ConcreteValue.removeConcretePrefix(value),
				DataType.fromPrefix(value)
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
		if (value != null && (value.startsWith("#") || value.startsWith("\""))) {
			valueWithoutPrefix = value.substring(1);
		}

		if (valueWithoutPrefix != null && valueWithoutPrefix.endsWith("\"")) {
			valueWithoutPrefix = valueWithoutPrefix.substring(0, valueWithoutPrefix.length() - 1);
		}

		return valueWithoutPrefix;
	}

	public String getValueWithPrefix() {
		if (dataType == DataType.DECIMAL || dataType == DataType.INTEGER) {
			return "#" + value;
		}
		if (dataType == DataType.STRING) {
			return "\"" + value + "\"";
		}
		return value;
	}

	public static ConcreteValue newInteger(String value) {
		return ConcreteValue.from(value, DataType.INTEGER.getShorthand());
	}

	public static ConcreteValue newDecimal(String value) {
		return ConcreteValue.from(value, DataType.DECIMAL.getShorthand());
	}

	public static ConcreteValue newString(String value) {
		if (value == null || !value.startsWith("\"")) {
			throw new IllegalArgumentException(String.format("String concrete value %s should start with \"", value));
		}
		return ConcreteValue.from(value, DataType.STRING.getShorthand());
	}

	public ConcreteValue.DataType getDataType() {
		return this.dataType;
	}

	public String getValue() {
		return this.value;
	}
	
	@Override
	public String toString() {
		switch (dataType) {
			case STRING : return "\"" + this.value + "\"";
			default: return "#" + this.value;
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ConcreteValue that = (ConcreteValue) o;
		return dataType == that.dataType &&
				Objects.equals(value, that.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(dataType, value);
	}
}
