package org.snomed.snowstorm.fhir.utils;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

public class FHIRPageHelper {

	private FHIRPageHelper() {}

	@NotNull
	public static <T> PageImpl<T> toPage(SearchHits<T> searchHits, Pageable pageRequest) {
		return new PageImpl<>(searchHits.get().map(SearchHit::getContent).toList(), pageRequest, searchHits.getTotalHits());
	}
}
