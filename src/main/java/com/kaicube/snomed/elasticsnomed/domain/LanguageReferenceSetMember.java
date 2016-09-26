package com.kaicube.snomed.elasticsnomed.domain;

import com.fasterxml.jackson.annotation.JsonView;
import com.kaicube.snomed.elasticsnomed.rest.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldIndex;

import java.util.UUID;

@Document(type = "member", indexName = "snomed")
public class LanguageReferenceSetMember extends ReferenceSetMember<LanguageReferenceSetMember> {

	@JsonView(value = View.Component.class)
	@Field(index = FieldIndex.not_analyzed)
	private String acceptabilityId;

	@JsonView(value = View.Component.class)
	@Field(index = FieldIndex.not_analyzed)
	private String conceptId;

	private static final Logger logger = LoggerFactory.getLogger(LanguageReferenceSetMember.class);

	public LanguageReferenceSetMember() {
	}

	public LanguageReferenceSetMember(String memberId, String effectiveTime, boolean active, String moduleId, String refsetId, String referencedComponentId, String acceptabilityId) {
		super(memberId, effectiveTime, active, moduleId, refsetId, referencedComponentId);
		this.acceptabilityId = acceptabilityId;
	}

	public LanguageReferenceSetMember(String refsetId, String referencedComponentId, String acceptabilityId) {
		super(UUID.randomUUID().toString(), null, true, Concepts.CORE_MODULE, refsetId, referencedComponentId);
		this.acceptabilityId = acceptabilityId;
	}

	@Override
	public boolean isComponentChanged(LanguageReferenceSetMember that) {
		final boolean changed = super.isComponentChanged(that)
				|| !acceptabilityId.equals(that.acceptabilityId);
		if (changed) logger.debug("Lang Member Changed:\n{}\n{}", this, that);
		return changed;
	}

	public String getAcceptabilityId() {
		return acceptabilityId;
	}

	public void setAcceptabilityId(String acceptabilityId) {
		this.acceptabilityId = acceptabilityId;
	}

	public String getConceptId() {
		return conceptId;
	}

	public void setConceptId(String conceptId) {
		this.conceptId = conceptId;
	}
}
