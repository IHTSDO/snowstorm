package org.ihtsdo.elasticsnomed.core.util;

import java.util.List;
import java.util.stream.Collectors;

public class CollectionUtil {
	public static List<Long> listIntersection(List<Long> orderedListA, List<Long> listB) {
		return orderedListA.stream().filter(listB::contains).collect(Collectors.toList());
	}

	public static List<Long> subList(List<Long> wholeList, int pageSize) {
		return wholeList.subList(0, wholeList.size() > pageSize ? pageSize : wholeList.size());
	}
}
