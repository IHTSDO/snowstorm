package org.snomed.snowstorm.core.rf2.export;

@FunctionalInterface
public interface ExportFilter<T> {
	public boolean isValid(T obj);
}
