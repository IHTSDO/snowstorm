package org.snomed.snowstorm.core.util;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class CollectionUtils {

	public static <T> Set<T> orEmpty(Set<T> collection) {
		return collection != null ? collection : Collections.emptySet();
	}

	public static <T> List<T> orEmpty(List<T> collection) {
		return collection != null ? collection : Collections.emptyList();
	}

}
