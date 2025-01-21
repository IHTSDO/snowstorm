package org.snomed.snowstorm.fhir.domain;

import org.hl7.fhir.r4.model.ValueSet;

import java.util.ArrayList;
import java.util.List;

import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

public class FHIRValueSetCompose {

	private List<FHIRValueSetCriteria> include;

	private List<FHIRValueSetCriteria> exclude;

	private List<FHIRExtension> extensions;

	private Boolean inactive;

	public FHIRValueSetCompose() {
	}

	public FHIRValueSetCompose(ValueSet.ValueSetComposeComponent hapiCompose) {
		this();
		if (!hapiCompose.hasInactive()){
			this.setInactive(null);
		} else {
			this.setInactive(hapiCompose.getInactive());
		}
		for (ValueSet.ConceptSetComponent hapiInclude : hapiCompose.getInclude()) {
			addInclude(new FHIRValueSetCriteria(hapiInclude));
		}
		for (ValueSet.ConceptSetComponent hapiExclude : hapiCompose.getExclude()) {
			addInclude(new FHIRValueSetCriteria(hapiExclude));
		}

		hapiCompose.getExtension().forEach( ext -> {
			if (extensions == null){
				extensions = new ArrayList<>();
			}

			extensions.add(new FHIRExtension(ext));

		});
	}

	public ValueSet.ValueSetComposeComponent getHapi() {
		ValueSet.ValueSetComposeComponent hapiCompose = new ValueSet.ValueSetComposeComponent();
		if (this.isInactive()!=null) {
			hapiCompose.setInactive(this.isInactive());
		}
		for (FHIRValueSetCriteria include : orEmpty(getInclude())) {
			hapiCompose.addInclude(include.getHapi());
		}
		for (FHIRValueSetCriteria exclude : orEmpty(getExclude())) {
			hapiCompose.addExclude(exclude.getHapi());
		}

		orEmpty(extensions).forEach( ext ->{
			hapiCompose.addExtension(ext.getHapi());
		});
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

	public void setInactive(Boolean inactive) {
		this.inactive = inactive;
	}

	public Boolean isInactive() {
		return inactive;
	}

	public List<FHIRExtension> getExtensions() {
		return extensions;
	}

	public void setExtensions(List<FHIRExtension> extensions) {
		this.extensions = extensions;
	}

}
