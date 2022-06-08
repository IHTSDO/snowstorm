package org.snomed.snowstorm.core.util;

import java.util.function.Function;

public class ObjectUtils {

	public static <T, R> R getNullSafe(T object, Function<T, R> getter) {
		return object != null ? getter.apply(object) : null;
	}

}
