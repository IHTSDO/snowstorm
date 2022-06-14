package org.snomed.snowstorm.fhir.domain;

import org.hl7.fhir.r4.model.ValueSet;

import java.util.ArrayList;
import java.util.List;

import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

public class FHIRValueSetCompose {

	private List<FHIRValueSetCriteria> include;

	private List<FHIRValueSetCriteria> exclude;

	public FHIRValueSetCompose() {
	}

	public FHIRValueSetCompose(ValueSet.ValueSetComposeComponent hapiCompose) {
		this();
		for (ValueSet.ConceptSetComponent hapiInclude : hapiCompose.getInclude()) {
			addInclude(new FHIRValueSetCriteria(hapiInclude));
		}
		for (ValueSet.ConceptSetComponent hapiExclude : hapiCompose.getExclude()) {
			addInclude(new FHIRValueSetCriteria(hapiExclude));
		}
	}

	public ValueSet.ValueSetComposeComponent getHapi() {
		ValueSet.ValueSetComposeComponent hapiCompose = new ValueSet.ValueSetComposeComponent();
		for (FHIRValueSetCriteria include : orEmpty(getInclude())) {
			hapiCompose.addInclude(include.getHapi());
		}
		for (FHIRValueSetCriteria exclude : orEmpty(getExclude())) {
			hapiCompose.addExclude(exclude.getHapi());
		}
		return hapiCompose;
	}

	public void addInclude(FHIRValueSetCriteria criteria) {
		if (include == null) {
			include = new ArrayList<>();
		}
		include.add(criteria);
	}

	public void addExclude(FHIRValueSetCriteria criteria) {
		if (exclude == null) {
			exclude = new ArrayList<>();
		}
		exclude.add(criteria);
	}

	public List<FHIRValueSetCriteria> getInclude() {
		return include;
	}

	public void setInclude(List<FHIRValueSetCriteria> include) {
		this.include = include;
	}

	public List<FHIRValueSetCriteria> getExclude() {
		return exclude;
	}

	public void setExclude(List<FHIRValueSetCriteria> exclude) {
		this.exclude = exclude;
	}
}
