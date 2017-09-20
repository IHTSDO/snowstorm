package org.ihtsdo.elasticsnomed.core.util;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CollectionUtil {
	public static List<Long> listIntersection(List<Long> orderedListA, List<Long> listB) {
		return orderedListA.stream().filter(listB::contains).collect(Collectors.toList());
	}

	public static List<Long> subList(List<Long> wholeList, int page, int pageSize) {
		int offset = page * pageSize;
		int limit = (page + 1) * pageSize;

		if (offset >= wholeList.size()) {
			return Collections.emptyList();
		}
		if (limit > wholeList.size()) {
			limit = wholeList.size();
		}

		return wholeList.subList(offset, limit);
	}
}
