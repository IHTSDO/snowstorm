package org.snomed.snowstorm.mrcm.model;

import org.snomed.snowstorm.core.data.services.ServiceException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Cardinality {

	private static final Pattern WITH_MAX = Pattern.compile("([0-9])\\.\\.([0-9])");
	private static final Pattern NO_MAX = Pattern.compile("([0-9])\\.\\.\\*");

	private String value;
	private int min;
	private Integer max;

	public Cardinality(int min, Integer max) {
		this.min = min;
		this.max = max;
		value = min + ".." + (max == null ? "*" : max.intValue());
	}

	public Cardinality(String value) throws ServiceException {
		this.value = value;
		Matcher matcher = WITH_MAX.matcher(value);
		if (matcher.matches()) {
			min = Integer.parseInt(matcher.group(1));
			max = Integer.parseInt(matcher.group(2));
		} else if ((matcher = NO_MAX.matcher(value)).matches()) {
			min = Integer.parseInt(matcher.group(1));
		} else {
			throw new ServiceException("Bad cardinality format '" + value + "'.");
		}
	}

	public String getValue() {
		return value;
	}

	public int getMin() {
		return min;
	}

	public Integer getMax() {
		return max;
	}
}
