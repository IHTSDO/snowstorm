package org.snomed.snowstorm.mrcm.model;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConcreteValueRangeConstraint {
	private static final Pattern TYPE_CONSTRAINT_PATTERN = Pattern.compile("([a-z]{3})(\\(.*\\))");
	private static final Pattern NUMERIC_RANGE_PATTERN = Pattern.compile("(.*)(\\.\\.)(.*)");
	public static final String OR_OPERATOR = "\\s";
	public static final String AND_OPERATOR = ",";
	public static final String HASH_SYMBOL = "#";
	private static final Pattern NUMERIC_MIN_VALUE_PATTERN = Pattern.compile("(>?)(#.*)");
	private static final Pattern NUMERIC_MAX_VALUE_PATTERN = Pattern.compile("(<?)(#.*)");
	public static final String NUMBER_CONSTRAINT_MISSING_SYMBOL = "Number constraint %s is missing %s";

	private enum Type {
		INTEGER("int"),
		STRING("str"),
		DECIMAL("dec");

		private final String value;

		Type(String value) {
			this.value = value;
		}
		public String getValue() {
			return this.value;
		}

		public static Type fromString(String type) {
			return Arrays.stream(Type.values())
					.filter(v -> v.getValue().equals(type))
					.findFirst()
					.orElseThrow(() -> new IllegalArgumentException("unknown type: " + type));
		}
	}


	private final Type type;
	private final String constraint;
	private boolean isRangeConstraint;
	private String minValue;
	private String maxValue;

	public ConcreteValueRangeConstraint(String rangeConstraint) {
		Matcher matcher = TYPE_CONSTRAINT_PATTERN.matcher(rangeConstraint);
		if (!matcher.matches()) {
			throw new IllegalArgumentException(String.format("Range constraint %s doesn't match pattern %s",
					rangeConstraint, TYPE_CONSTRAINT_PATTERN.pattern()));
		}
		type = Type.fromString(matcher.group(1));
		this.constraint = matcher.group(2).replace("(", "").replace(")", "");
		Matcher numericRangeMatcher = NUMERIC_RANGE_PATTERN.matcher(constraint);
		if (isNumber() && numericRangeMatcher.matches()) {
			minValue = numericRangeMatcher.group(1);
			maxValue = numericRangeMatcher.group(3);
			isRangeConstraint = true;
		}
		validateConstraint();
	}

	public boolean isNumber() {
		return Type.DECIMAL == this.type || Type.INTEGER == this.type;
	}

	public String getMinimumValue() {
		if (!isNumber()) {
			return null;
		}
		return this.minValue;
	}

	public String getMaximumValue() {
		if (!isNumber()) {
			return null;
		}
		return this.maxValue;
	}

	public boolean isString() {
		return Type.STRING == this.type;
	}

	public String getConstraint() {
		return this.constraint;
	}

	public boolean haveBothMinimumAndMaximum() {
		return this.minValue != null && !this.minValue.isEmpty() && this.maxValue!= null
				&& !this.maxValue.isEmpty();
	}

	public boolean isRangeConstraint() {
		return this.isRangeConstraint;
	}


	private void validateConstraint() {
		if (this.constraint.trim().isEmpty()) {
			throw new IllegalArgumentException("Constraint contains no value.");
		}
		if (isNumber()) {
			if (isRangeConstraint()) {
				validateNumberRangeConstraint(minValue, maxValue, this.type);
			} else {
				String[] values = this.constraint.split(OR_OPERATOR);
				if (values.length > 1) {
					Arrays.stream(values).forEach(value -> validateNumberConstraint(this.type, value));
					return;
				}
				values = constraint.split(AND_OPERATOR);
				if (values.length > 1) {
					Arrays.stream(values).forEach(value -> validateNumberConstraint(this.type, value));
					return;
				}
				validateNumberConstraint(this.type, this.constraint);
			}
		}
	}

	private void validateNumberRangeConstraint(String minValue, String maxValue, Type type) {
		if (minValue.isEmpty() && maxValue.isEmpty()) {
			throw new IllegalArgumentException(String.format("Both minimum and maximum range values are missing %s", constraint));
		}
		if (!minValue.isEmpty()) {
			validateMinimumConstraint(type, minValue);
		}
		if (!maxValue.isEmpty()) {
			validateMaximumConstraint(type, maxValue);
		}
		if (!minValue.trim().isEmpty() && !maxValue.trim().isEmpty()) {
			String minStr = minValue.substring(minValue.indexOf(HASH_SYMBOL) + 1);
			String maxStr = maxValue.substring(maxValue.indexOf(HASH_SYMBOL) + 1);
			String errorMsg = String.format("Minimum value of %s can not be great than the maximum value of %s", minStr, maxStr);
			if (Type.INTEGER == type && (Integer.parseInt(minStr) > Integer.parseInt(maxStr))) {
					throw new IllegalArgumentException(errorMsg);
			} else if (Type.DECIMAL == type && (Float.parseFloat(minStr) > Float.parseFloat(maxStr))) {
					throw new IllegalArgumentException(errorMsg);
			}
		}
	}

	private void validateMinimumConstraint(Type type, String constraint) {
		Matcher matcher = NUMERIC_MIN_VALUE_PATTERN.matcher(constraint);
		if (!matcher.matches()) {
			if (constraint.contains(HASH_SYMBOL)) {
				throw new IllegalArgumentException(String.format("Only > is allowed before the minimum value but got %s", constraint));
			} else {
				throw new IllegalArgumentException(String.format(NUMBER_CONSTRAINT_MISSING_SYMBOL, constraint, HASH_SYMBOL));
			}
		} else {
			String numberStr = matcher.group(2);
			validateNumberConstraint(type, numberStr);
		}
	}


	private void validateMaximumConstraint(Type type, String constraint) {
		Matcher matcher = NUMERIC_MAX_VALUE_PATTERN.matcher(constraint);
		if (!matcher.matches()) {
			if (constraint.contains(HASH_SYMBOL)) {
				throw new IllegalArgumentException(String.format("Only < is allowed before the maximum value but got %s", constraint));
			} else {
				throw new IllegalArgumentException(String.format("Number constraint %s does not have %s", constraint, HASH_SYMBOL));
			}
		} else {
			String numberStr = matcher.group(2);
			validateNumberConstraint(type, numberStr);
		}
	}

	private void validateNumberConstraint(Type type, String constraint) {
		if (!constraint.startsWith(HASH_SYMBOL)) {
			throw new IllegalArgumentException(String.format("Number constraint %s does not start with %s", constraint, HASH_SYMBOL));
		}
		if (constraint.length() == 1) {
			throw new IllegalArgumentException(String.format("Number constraint contains no value after %s", HASH_SYMBOL));
		}
		String numberInStr = constraint.substring(1);
		try {
			if (Type.INTEGER == type) {
				Integer.parseInt(numberInStr);
			} else if (Type.DECIMAL == type) {
				Float.parseFloat(numberInStr);
			}
		} catch(NumberFormatException e) {
			throw new IllegalArgumentException(String.format("%s is not a type of %s", numberInStr, type.value));
		}
	}
}

