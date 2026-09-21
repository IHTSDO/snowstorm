package org.snomed.snowstorm.core.util;

import com.google.common.collect.Iterables;

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

	/**
	 * Splits a collection into batches of at most maxBatchSize, without over allocating for a collection that already
	 * fits in one batch. Guava's partition allocates an array of the full batch size for every batch it yields, so
	 * batching a handful of ids at the Elasticsearch terms clause limit would allocate a 65000 element array to hold
	 * them.
	 */
	public static <T> Iterable<List<T>> partition(Collection<T> collection, int maxBatchSize) {
		if (collection.isEmpty()) {
			return Collections.emptyList();
		}
		return Iterables.partition(collection, Math.min(maxBatchSize, collection.size()));
	}

}
