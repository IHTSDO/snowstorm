package org.snomed.snowstorm.mrcm.model;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConcreteValueRangeConstraint {
	private static final Pattern TYPE_CONSTRAINT_PATTERN = Pattern.compile("([a-z]{3})(\\(.*\\))");
	private static final Pattern NUMERIC_RANGE_PATTERN = Pattern.compile("(.*)(\\.\\.)(.*)");
	public static final String OR_OPERATOR = "\\s";
	public static final String AND_OPERATOR = ",";

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


}

