package org.snomed.snowstorm.core.util;

import java.util.function.Predicate;

public class PredicateUtil {

	public static <R> Predicate<R> not(Predicate<R> predicate) {
		return predicate.negate();
	}
}
