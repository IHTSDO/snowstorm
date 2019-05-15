package org.snomed.snowstorm.fhir.config;

import java.util.*;

import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.StringType;

public interface FHIRConstants {

	//Constant words and phrases 
	StringType CHILD = new StringType("child");
	String CODE = "code";
	String COPYRIGHT = "© Copyright YEAR International Health Terminology Standards Development Organisation, all rights reserved.";
	String DESIGNATION = "designation";
	StringType DISPLAY = new StringType("display");
	StringType EFFECTIVE_TIME = new StringType("effectiveTime");

	String FHIR = "FHIR";

	String LANG_EN = "en";
	String LANGUAGE = "language";
	StringType MODULE_ID = new StringType("moduleId");
	String NAME = "name";
	String PROPERTY = "property";
	StringType PARENT = new StringType("parent");

	String SNOMED_EDITION = "SNOMED CT International Edition";
	String SNOMED_INTERNATIONAL = "SNOMED International";
	String SNOMED_URI = "http://snomed.info/sct";
	String SNOMED_CONCEPTMAP = SNOMED_URI + "?fhir_cm=";
	
	enum Validation { EQUALS, STARTS_WITH }
	
	String MAIN = "MAIN";
	String URL = "url";
	String USE = "use";
	String VALUE = "value";
	String VALUE_BOOLEAN = "valueBoolean";
	String VALUE_STRING = "valueString";
	String VERSION = "version";
	
	String IMPLICIT_ISA = "?fhir_vs=isa/";
	String IMPLICIT_REFSET = "?fhir_vs=refset/";
	String IMPLICIT_ECL= "?fhir_vs=ecl/";
	
	public enum FhirSctProperty {
		INACTVE ("inactive"),
		SUFFICIENTLY_DEFINED("sufficientlyDefined"),
		MODULE_ID("moduleId"),
		NORMAL_FORM("normalForm"),
		NORMAL_FORM_TERSE("normalFormTerse");

		private final String name;

		private FhirSctProperty(String s) {
			name = s;
		}

		public boolean equalsName(String otherName) {
			return name.equals(otherName);
		}
		

		public String toString() {
			return this.name;
		}
		
		public StringType toStringType() {
			return new StringType(this.name);
		}
		
		public static FhirSctProperty parse(CodeType code) {
			for (FhirSctProperty property : FhirSctProperty.values()) {
				if (property.equalsName(code.getValue())) {
					return property;
				}
			}
			return null;
		}
		
		public static Set<FhirSctProperty> parse (List<CodeType> codeTypes) {
			Set<FhirSctProperty> results = new HashSet<>();
			if (codeTypes != null) {
				for (CodeType code : codeTypes) {
					FhirSctProperty property = parse (code);
					if (property != null) {
						results.add(property);
					}
				}
			}
			return results;
		}
	}

}
