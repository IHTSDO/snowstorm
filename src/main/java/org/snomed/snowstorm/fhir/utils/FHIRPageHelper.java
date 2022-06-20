package org.snomed.snowstorm.fhir.utils;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.util.stream.Collectors;

public class FHIRPageHelper {
	@NotNull
	public static <T> PageImpl<T> toPage(SearchHits<T> searchHits, Pageable pageRequest) {
		return new PageImpl<>(searchHits.get().map(SearchHit::getContent).collect(Collectors.toList()), pageRequest, searchHits.getTotalHits());
	}
}
