package org.snomed.snowstorm.fhir.domain;

import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.ArrayList;
import java.util.List;

import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

public class FHIRValueSetCriteria {

	@Field(type = FieldType.Keyword)
	private String system;

	@Field(type = FieldType.Keyword)
	private String version;

	@Field(type = FieldType.Keyword)
	private List<String> codes;

	private List<FHIRValueSetFilter> filter;

	public FHIRValueSetCriteria() {
	}

	public FHIRValueSetCriteria(ValueSet.ConceptSetComponent hapiCriteria) {
		system = hapiCriteria.getSystem();
		version = hapiCriteria.getVersion();
		for (ValueSet.ConceptReferenceComponent code : hapiCriteria.getConcept()) {
			if (codes == null) {
				codes = new ArrayList<>();
			}
			codes.add(code.getCode());
		}
		for (ValueSet.ConceptSetFilterComponent hapiFilter : hapiCriteria.getFilter()) {
			if (filter == null) {
				filter = new ArrayList<>();
			}
			filter.add(new FHIRValueSetFilter(hapiFilter));
		}
	}

	public ValueSet.ConceptSetComponent getHapi() {
		ValueSet.ConceptSetComponent hapiConceptSet = new ValueSet.ConceptSetComponent();
		hapiConceptSet.setSystem(system);
		hapiConceptSet.setVersion(version);
		for (String code : orEmpty(codes)) {
			ValueSet.ConceptReferenceComponent component = new ValueSet.ConceptReferenceComponent();
			component.setCode(code);
			hapiConceptSet.addConcept(component);
		}
		for (FHIRValueSetFilter filter : orEmpty(getFilter())) {
			hapiConceptSet.addFilter(filter.getHapi());
		}
		return hapiConceptSet;
	}

	public String getSystem() {
		return system;
	}

	public void setSystem(String system) {
		this.system = system;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public List<String> getCodes() {
		return codes;
	}

	public void setCodes(List<String> codes) {
		this.codes = codes;
	}

	public List<FHIRValueSetFilter> getFilter() {
		return filter;
	}

	public void setFilter(List<FHIRValueSetFilter> filter) {
		this.filter = filter;
	}
}
