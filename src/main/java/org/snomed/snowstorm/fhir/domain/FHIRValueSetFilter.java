package org.snomed.snowstorm.fhir.domain;

import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

public class FHIRValueSetFilter {

    @Field(type = FieldType.Keyword)
    private String property;

    @Field(type = FieldType.Keyword)
    private String op;

    @Field(type = FieldType.Keyword)
    private String value;

    public FHIRValueSetFilter() {
    }

    public FHIRValueSetFilter(String property, String op, String value) {
        this.property = property;
        this.op = op;
        this.value = value;
    }

    public FHIRValueSetFilter(ValueSet.ConceptSetFilterComponent hapiFilter) {
        property = hapiFilter.getProperty();
        op = hapiFilter.getOp().toCode();
        value = hapiFilter.getValue();
    }

    public ValueSet.ConceptSetFilterComponent getHapi() {
        ValueSet.ConceptSetFilterComponent component = new ValueSet.ConceptSetFilterComponent();
        component.setProperty(property);
        component.setOp(ValueSet.FilterOperator.fromCode(op));
        component.setValue(value);
        return component;
    }

    public String getProperty() {
        return property;
    }

    public void setProperty(String property) {
        this.property = property;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
