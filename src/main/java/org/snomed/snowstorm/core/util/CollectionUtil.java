package org.snomed.snowstorm.core.util;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CollectionUtil {

	public static <T> Page<T> listIntersection(List<T> orderedListA, List<T> listB, Pageable pageable) {
		List<T> fullResultList = orderedListA.stream().filter(listB::contains).collect(Collectors.toList());
		List<T> pageOfResults = subList(fullResultList, pageable.getPageNumber(), pageable.getPageSize());
		return new PageImpl<T>(pageOfResults, pageable, fullResultList.size());
	}

	public static <T> List<T> subList(List<T> wholeList, int pageNumber, int pageSize) {
		int offset = pageNumber * pageSize;
		int limit = (pageNumber + 1) * pageSize;

		if (offset >= wholeList.size()) {
			return Collections.emptyList();
		}
		if (limit > wholeList.size()) {
			limit = wholeList.size();
		}

		return wholeList.subList(offset, limit);
	}
}
