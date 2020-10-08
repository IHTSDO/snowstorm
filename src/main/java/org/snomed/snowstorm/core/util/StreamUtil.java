package org.snomed.snowstorm.core.util;

import org.snomed.snowstorm.core.data.domain.ConceptMini;

import java.util.function.BinaryOperator;

public class StreamUtil {
	public static final BinaryOperator<ConceptMini> MERGE_FUNCTION = (u, v) -> {
		throw new IllegalStateException(String.format("Duplicate key %s", u.getConceptId()));
	};
}
