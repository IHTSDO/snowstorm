package org.snomed.snowstorm.core.data.services.pojo;

import org.snomed.snowstorm.core.data.services.DescriptionService;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Objects;
import java.util.List;
import java.util.Set;

import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_CODES;

public class DescriptionCriteria {

	private static int searchTermMinLength = 3;
	private static int searchTermMaxLength = 250;

	private String term;
	private Collection<String> searchLanguageCodes = DEFAULT_LANGUAGE_CODES;
	private Boolean active;
	private Collection<String> modules;
	private String semanticTag;
	private Set<String> semanticTags;
	private Boolean conceptActive;
	private String conceptRefset;
	private boolean groupByConcept;
	private DescriptionService.SearchMode searchMode = DescriptionService.SearchMode.STANDARD;
	private Collection<Long> type;
	private Set<Long> preferredIn;
	private Set<Long> acceptableIn;
	private Set<Long> preferredOrAcceptableIn;
	private List<DisjunctionAcceptabilityCriteria> disjunctionAcceptabilityCriteria;
	private Collection<Long> conceptIds;

	public static void configure(int searchTermMinimumLength, int searchTermMaximumLength) {
		searchTermMinLength = searchTermMinimumLength;
		searchTermMaxLength = searchTermMaximumLength;
	}

	public boolean hasDescriptionCriteria() {
		return term != null
				|| modules != null
				|| semanticTag != null
				|| !CollectionUtils.isEmpty(preferredIn)
				|| !CollectionUtils.isEmpty(acceptableIn)
				|| !CollectionUtils.isEmpty(preferredOrAcceptableIn)
				|| !CollectionUtils.isEmpty(disjunctionAcceptabilityCriteria);
	}

	public DescriptionCriteria term(String term) {
		if (term != null) {
			if (term.isEmpty()) {
				term = null;
			} else if (term.length() < searchTermMinLength && !containsHanScript(term)) {
				throw new IllegalArgumentException(String.format("Search term must have at least %s characters.", searchTermMinLength));
			} else if (term.length() > searchTermMaxLength) {
				throw new IllegalArgumentException(String.format("Search term can not have more than %s characters.", searchTermMaxLength));
			}
		}
		this.term = term;
		return this;
	}

	private static boolean containsHanScript(String s) {
		return s.codePoints().anyMatch(
				codepoint ->
						Character.UnicodeScript.of(codepoint) == Character.UnicodeScript.HAN || Character.UnicodeScript.of(codepoint) == Character.UnicodeScript.HANGUL);
	}

	public String getTerm() {
		return term;
	}

	// Unordered collection of language codes to match descriptions against.
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

	public DescriptionCriteria modules(Collection<String> modules) {
		this.modules = modules;
		return this;
	}

	public Collection<String> getModules() {
		return modules;
	}

	public DescriptionCriteria semanticTag(String semanticTag) {
		this.semanticTag = semanticTag;
		return this;
	}

	public String getSemanticTag() {
		return semanticTag;
	}

	public DescriptionCriteria semanticTags(Set<String> semanticTags) {
		this.semanticTags = semanticTags;
		return this;
	}

	public Set<String> getSemanticTags() {
		return semanticTags;
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

	public DescriptionCriteria acceptableIn(Set<Long> acceptableIn) {
		this.acceptableIn = acceptableIn;
		return this;
	}

	public Set<Long> getAcceptableIn() {
		return acceptableIn;
	}

	public DescriptionCriteria preferredOrAcceptableIn(Set<Long> preferredOrAcceptableIn) {
		this.preferredOrAcceptableIn = preferredOrAcceptableIn;
		return this;
	}

	public Set<Long> getPreferredOrAcceptableIn() {
		return preferredOrAcceptableIn;
	}

	public void preferredOrAcceptableValues(Set<Long> preferredOrAcceptableIn, Set<Long> preferredIn, Set<Long> acceptableIn) {
		this.preferredOrAcceptableIn = preferredOrAcceptableIn;
		this.preferredIn = preferredIn;
		this.acceptableIn = acceptableIn;
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

	public DescriptionCriteria disjunctionAcceptabilityCriteria(List<DisjunctionAcceptabilityCriteria> disjunctionAcceptabilityCriteria) {
		this.disjunctionAcceptabilityCriteria = disjunctionAcceptabilityCriteria;
		return this;
	}

	public List<DisjunctionAcceptabilityCriteria> getDisjunctionAcceptabilityCriteria() {
		return disjunctionAcceptabilityCriteria;
	}

	public void conceptIds(Collection<Long> conceptIds) {
		this.conceptIds = conceptIds;
	}

	public Collection<Long> getConceptIds() {
		return conceptIds;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		DescriptionCriteria that = (DescriptionCriteria) o;
		return groupByConcept == that.groupByConcept &&
				Objects.equals(term, that.term) &&
				Objects.equals(searchLanguageCodes, that.searchLanguageCodes) &&
				Objects.equals(active, that.active) &&
				Objects.equals(modules, that.modules) &&
				Objects.equals(semanticTag, that.semanticTag) &&
				Objects.equals(semanticTags, that.semanticTags) &&
				Objects.equals(conceptActive, that.conceptActive) &&
				Objects.equals(conceptRefset, that.conceptRefset) &&
				searchMode == that.searchMode &&
				Objects.equals(type, that.type) &&
				Objects.equals(preferredIn, that.preferredIn) &&
				Objects.equals(acceptableIn, that.acceptableIn) &&
				Objects.equals(preferredOrAcceptableIn, that.preferredOrAcceptableIn) &&
				Objects.equals(disjunctionAcceptabilityCriteria, that.disjunctionAcceptabilityCriteria);
	}

	@Override
	public int hashCode() {
		return Objects.hash(term, searchLanguageCodes, active, modules, semanticTag, semanticTags, conceptActive, conceptRefset, groupByConcept, searchMode, type, preferredIn, acceptableIn, preferredOrAcceptableIn);
	}

	public static class DisjunctionAcceptabilityCriteria {

		private final Set<Long> preferred;

		private final Set<Long> acceptable;

		private final Set<Long> preferredOrAcceptable;

		public DisjunctionAcceptabilityCriteria(Set<Long> preferred, Set<Long> acceptable, Set<Long> preferredOrAcceptable) {
			this.preferred = preferred;
			this.acceptable = acceptable;
			this.preferredOrAcceptable = preferredOrAcceptable;
		}

		public Set<Long> getPreferred() {
			return preferred;
		}

		public Set<Long> getAcceptable() {
			return acceptable;
		}

		public Set<Long> getPreferredOrAcceptable() {
			return preferredOrAcceptable;
		}

		public boolean hasMultiple() {
			int counter = 0;
			if (preferred != null && !preferred.isEmpty()) {
				counter++;
			}
			if (acceptable != null && !acceptable.isEmpty()) {
				counter++;
			}
			if (preferredOrAcceptable != null && !preferredOrAcceptable.isEmpty()) {
				counter++;
			}
			return counter > 1;
		}
	}

}
