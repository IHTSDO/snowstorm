package org.snomed.snowstorm.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CollectionComparators {

	public static <T extends Comparable<T>> int compareLists(List<T> listA, List<T> listB) {
		int items = Math.max(listA.size(), listB.size());
		for (int i = 0; i < items; i++) {
			if (i == listA.size()) {
				// ListA is shorter and therefore less than ListB.
				return -1;
			}
			if (i == listB.size()) {
				// ListB is shorter and therefore less than ListA.
				return 1;
			}
			// Both lists have an object at index i.. compare them.
			T a = listA.get(i);
			T b = listB.get(i);
			int valueResult = a.compareTo(b);
			if (valueResult != 0) {
				return valueResult;
			}
		}
		// Both lists are the same length and contain values which are equal.
		return 0;
	}

	public static <T extends Comparable<T>> int compareSets(Set<T> setA, Set<T> setB) {
		List<T> listA = new ArrayList<>(setA);
		listA.sort(null);
		List<T> listB = new ArrayList<>(setB);
		listB.sort(null);
		return compareLists(listA, listB);
	}
}
