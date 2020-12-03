package org.snomed.snowstorm.core.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SetUtils {
	/**
	 * Returns a new set containing the items from the first set which remain after the items from the second set are removed.
	 * Either set can be null. If the first set is null an empty set will be returned.
	 * @param items        	The set to work on.
	 * @param itemsToRemove	The set of items to remove.
	 * @return A new set containing the items in the first set after items from the second set are removed.
	 */
	public static <T> Set<T> remainder(Set<T> items, Set<T> itemsToRemove) {
		if (items == null) {
			return Collections.emptySet();
		}
		Set<T> itemsCopy = new HashSet<>(items);
		if (itemsToRemove != null) {
			itemsCopy.removeAll(itemsToRemove);
		}
		return itemsCopy;
	}
}
