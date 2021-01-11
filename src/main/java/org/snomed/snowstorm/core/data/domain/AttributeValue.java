package org.snomed.snowstorm.core.data.domain;

public interface AttributeValue {

	String toString(boolean includeTerms);
	
	boolean isConcrete();
}
