package org.snomed.snowstorm.rest.pojo;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Set;

@JsonPropertyOrder({"total", "limit", "offset", "referencesByType"})
public class ConceptReferencesResult {

	private final long total;
	private final long limit;
	private final long offset;
	private Collection<TypeReferences> referencesByType;

	public ConceptReferencesResult(Set<TypeReferences> referencesByType, Pageable pageable, long totalElements) {
		this.referencesByType = referencesByType;
		this.offset = pageable.getOffset();
		this.limit = pageable.getPageSize();
		this.total = totalElements;
	}

	public long getTotal() {
		return total;
	}

	public long getLimit() {
		return limit;
	}

	public long getOffset() {
		return offset;
	}

	public Collection<TypeReferences> getReferencesByType() {
		return referencesByType;
	}
}
