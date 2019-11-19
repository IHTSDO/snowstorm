package org.snomed.snowstorm.core.data.services.pojo;

import org.snomed.snowstorm.core.data.services.DescriptionService;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.snomed.snowstorm.core.data.services.DescriptionService.EN_LANGUAGE_CODES;

public class DescriptionCriteria {

	private String term;
	private Collection<String> searchLanguageCodes = EN_LANGUAGE_CODES;
	private Boolean active;
	private String module;
	private String semanticTag;
	private Boolean conceptActive;
	private String conceptRefset;
	private boolean groupByConcept;
	private DescriptionService.SearchMode searchMode = DescriptionService.SearchMode.STANDARD;
	private Collection<Long> type;
	private Set<Long> preferredIn;

	public DescriptionCriteria term(String term) {
		this.term = term;
		return this;
	}

	public String getTerm() {
		return term;
	}

	public DescriptionCriteria searchLanguageCodes(Collection<String> searchLanguageCodes) {
		this.searchLanguageCodes = searchLanguageCodes;
		return this;
	}

	public Collection<String> getSearchLanguageCodes() {
		return searchLanguageCodes;
	}

	public DescriptionCriteria active(Boolean active) {
		this.active = active;
		return this;
	}

	public Boolean getActive() {
		return active;
	}

	public DescriptionCriteria module(String module) {
		this.module = module;
		return this;
	}

	public String getModule() {
		return module;
	}

	public DescriptionCriteria semanticTag(String semanticTag) {
		this.semanticTag = semanticTag;
		return this;
	}

	public String getSemanticTag() {
		return semanticTag;
	}

	public DescriptionCriteria type(Collection<Long> type) {
		this.type = type;
		return this;
	}

	public Collection<Long> getType() {
		return type;
	}

	public DescriptionCriteria preferredIn(Set<Long> preferredIn) {
		this.preferredIn = preferredIn;
		return this;
	}

	public Set<Long> getPreferredIn() {
		return preferredIn;
	}

	public boolean hasLanguageRefsetClauses() {
		return !CollectionUtils.isEmpty(preferredIn);
	}

	public DescriptionCriteria conceptActive(Boolean conceptActive) {
		this.conceptActive = conceptActive;
		return this;
	}

	public Boolean getConceptActive() {
		return conceptActive;
	}

	public DescriptionCriteria conceptRefset(String conceptRefset) {
		this.conceptRefset = conceptRefset;
		return this;
	}

	public String getConceptRefset() {
		return conceptRefset;
	}

	public DescriptionCriteria groupByConcept(boolean groupByConcept) {
		this.groupByConcept = groupByConcept;
		return this;
	}

	public boolean isGroupByConcept() {
		return groupByConcept;
	}

	public DescriptionCriteria searchMode(DescriptionService.SearchMode searchMode) {
		this.searchMode = searchMode;
		return this;
	}

	public DescriptionService.SearchMode getSearchMode() {
		return searchMode;
	}

	//	return this;
}
