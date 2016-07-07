package com.kaicube.snomed.elasticsnomed.domain;

import com.fasterxml.jackson.annotation.JsonView;
import com.kaicube.snomed.elasticsnomed.rest.View;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldIndex;

@Document(type = "member", indexName = "snomed")
public class LanguageReferenceSetMember extends ReferenceSetMember {

	@JsonView(value = View.Component.class)
	@Field(index = FieldIndex.not_analyzed)
	private String acceptabilityId;

	public LanguageReferenceSetMember() {
	}

	public LanguageReferenceSetMember(String memberId, String effectiveTime, boolean active, String moduleId, String refsetId, String referencedComponentId, String acceptabilityId) {
		super(memberId, effectiveTime, active, moduleId, refsetId, referencedComponentId);
		this.acceptabilityId = acceptabilityId;
	}

	public String getAcceptabilityId() {
		return acceptabilityId;
	}

	public void setAcceptabilityId(String acceptabilityId) {
		this.acceptabilityId = acceptabilityId;
	}
}
