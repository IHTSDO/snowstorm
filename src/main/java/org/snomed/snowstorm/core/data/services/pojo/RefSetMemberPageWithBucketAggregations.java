package org.snomed.snowstorm.core.data.services.pojo;

import com.fasterxml.jackson.annotation.JsonView;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.rest.View;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public class RefSetMemberPageWithBucketAggregations<T> extends PageImpl<T> {

	private Map<String, Long> memberCountsByReferenceSet;
	private Map<String, ConceptMini> referenceSets;

	public RefSetMemberPageWithBucketAggregations(List<T> content, Pageable pageable, long total, Map<String, Long> memberCountsByReferenceSet) {
		super(content, pageable, total);
		this.memberCountsByReferenceSet = memberCountsByReferenceSet;
	}

	@JsonView(value = View.Component.class)
	public Map<String, Long> getMemberCountsByReferenceSet() {
		return memberCountsByReferenceSet;
	}

	@JsonView(value = View.Component.class)
	public Map<String, ConceptMini> getReferenceSets() {
		return referenceSets;
	}

	public void setReferenceSets(Map<String, ConceptMini> referenceSets) {
		this.referenceSets = referenceSets;
	}
}
