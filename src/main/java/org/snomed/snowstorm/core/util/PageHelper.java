package org.snomed.snowstorm.core.util;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.SearchAfterPageRequest;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PageHelper {

	public static <T> SearchAfterPage<T> listIntersection(List<T> orderedListA, Collection<T> listB, Pageable pageable, Function<T, Object[]> searchAfterExtractor) {
		List<T> fullResultList = orderedListA.stream().filter(listB::contains).collect(Collectors.toList());
		return fullListToPage(fullResultList, pageable, searchAfterExtractor);
	}

	public static <T> SearchAfterPage<T> fullListToPage(List<T> fullResultList, Pageable pageable, Function<T, Object[]> searchAfterExtractor) {
		List<T> pageOfResults;
		if (pageable instanceof SearchAfterPageRequest) {
			Object[] searchAfter = ((SearchAfterPageRequest) pageable).getSearchAfter();
			pageOfResults = subList(fullResultList, searchAfter, pageable.getPageSize(), searchAfterExtractor);
		} else if (pageable != null) {
			pageOfResults = subList(fullResultList, pageable.getPageNumber(), pageable.getPageSize());
		} else {
			pageOfResults = fullResultList;
			pageable = PageRequest.of(0, Math.max(fullResultList.size(), 10));
		}

		T lastItem = getLastItem(pageOfResults);
		return new SearchAfterPageImpl<>(pageOfResults, pageable, fullResultList.size(), searchAfterExtractor.apply(lastItem));
	}

	public static <T, R> SearchAfterPage<R> mapToSearchAfterPage(Page<T> page, Function<T, R> mapFunction, Function<R, Object[]> searchAfterExtractor) {
		List<R> mappedList = page.getContent().stream().map(mapFunction).collect(Collectors.toList());
		return new SearchAfterPageImpl<>(mappedList, page.getPageable(), page.getTotalElements(), searchAfterExtractor.apply(getLastItem(mappedList)));
	}

	private static <T> List<T> subList(List<T> wholeList, Object[] searchAfter, int pageSize, Function<T, Object[]> searchAfterExtractor) {
		int offset = 0;
		for (T item : wholeList) {
			Object[] itemSearchAfterValue = searchAfterExtractor.apply(item);
			offset++;
			if (Arrays.equals(searchAfter, itemSearchAfterValue)) {
				break;
			}
		}
		return subListWithOffset(wholeList, offset, pageSize);
	}

	public static <T> List<T> subList(List<T> wholeList, int pageNumber, int pageSize) {
		if (wholeList == null) {
			return null;
		}
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

	private static <T> List<T> subListWithOffset(List<T> wholeList, int offset, int pageSize) {
		if (offset >= wholeList.size()) {
			return Collections.emptyList();
		}
		int toOffset = offset + pageSize;
		if (toOffset > wholeList.size()) {
			toOffset = wholeList.size();
		}

		return wholeList.subList(offset, toOffset);
	}

	public static <T> SearchAfterPage<T> toSearchAfterPage(Page<T> page, Function<T, Object[]> searchAfterExtractor) {
		return toSearchAfterPage(page, searchAfterExtractor, null);
	}

	public static <T> SearchAfterPage<T> toSearchAfterPage(Page<T> page, Function<T, Object[]> searchAfterExtractor, Integer forceTotalElements) {
		return new SearchAfterPageImpl<>(page.getContent(), page.getPageable(), forceTotalElements != null ? forceTotalElements : page.getTotalElements(), searchAfterExtractor.apply(getLastItem(page.getContent())));

	}

	public static <T, R> SearchAfterPage<T> toSearchAfterPage(List<T> results, SearchAfterPage<R> searchAfterPage) {
		return new SearchAfterPageImpl<>(results, searchAfterPage.getPageable(), searchAfterPage.getTotalElements(), searchAfterPage.getSearchAfter());
	}

	private static <T> T getLastItem(List<T> pageOfResults) {
		return pageOfResults.isEmpty() ? null : pageOfResults.get(pageOfResults.size() - 1);
	}

	public static <T> SearchAfterPage<T> toSearchAfterPage(SearchHits<T> searchHits, Pageable pageable) {
		Object[] searchAfter = null;
		if (!searchHits.isEmpty()) {
			searchAfter = searchHits.getSearchHit(searchHits.getSearchHits().size()-1).getSortValues().toArray();
		}
		return new SearchAfterPageImpl<>(searchHits.stream().map(SearchHit::getContent).collect(Collectors.toList()),
				(pageable != null) ? pageable : Pageable.unpaged(), searchHits.getTotalHits(), searchAfter);
	}

	public static <T, R> SearchAfterPage<R> toSearchAfterPage(SearchHits<T> searchHits, Function<T, R> mapFunction, Pageable pageable) {
		Object[] searchAfter = null;
		if (!searchHits.isEmpty()) {
			searchAfter = searchHits.getSearchHit(searchHits.getSearchHits().size()-1).getSortValues().toArray();
		}
		return new SearchAfterPageImpl<>(searchHits.stream().map(SearchHit::getContent).map(mapFunction).collect(Collectors.toList()),
				(pageable != null) ? pageable : Pageable.unpaged(), searchHits.getTotalHits(), searchAfter);
	}
}
