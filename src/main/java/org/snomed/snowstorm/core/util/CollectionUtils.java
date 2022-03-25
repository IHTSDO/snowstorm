package org.snomed.snowstorm.core.util;

import java.util.Collection;
import java.util.Collections;

public class CollectionUtils {

	public static <T> Collection<T> orEmpty(Collection<T> collection) {
		return collection != null ? collection : Collections.emptySet();
	}

}
