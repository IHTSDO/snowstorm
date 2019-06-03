package org.snomed.snowstorm.fhir.domain;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "valueset")
public class FHIRValueSet {
	
	@Field(type = FieldType.keyword, store = true)
	String id;
	
	String url;
	
	public static FHIRValueSet fromValueSet(ValueSet vs) {
		FHIRValueSet fvs = new FHIRValueSet();
		fvs.url = vs.getUrl();
		fvs.id = vs.getId();
		if (fvs.id != null && fvs.id.startsWith("ValueSet/")) {
			fvs.id = fvs.id.substring(9);
		}
		return fvs;
	}
	
	public ValueSet toValueSet() {
		ValueSet vs = new ValueSet();
		vs.setUrl(url);
		vs.setId(new IdType(id));
		return vs;
	}

	public static List<ValueSet> toValueSet(Iterable<FHIRValueSet> iterable) {
		return StreamSupport.stream(iterable.spliterator(), false)
			.map(fvs -> fvs.toValueSet())
			.collect(Collectors.toList());
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

}
