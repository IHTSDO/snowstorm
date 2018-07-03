package org.snomed.snowstorm.rest.converter;

public interface AggregationNameConverter {

	boolean canConvert(String aggregationGroupName);

	String convert(String aggregationName);

}
