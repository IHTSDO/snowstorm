package org.snomed.snowstorm.fhir.config;

import java.util.*;

import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.StringType;

public interface FHIRConstants {

	//Constant words and phrases 
	String ACCEPT_LANGUAGE_HEADER = "Accept-Language";
	String CODE = "code";
	String DESIGNATION = "designation";
	String DISPLAY = "display";
	String FHIR = "FHIR";
	String ICD10_URI = "http://hl7.org/fhir/sid/icd-10";
	String LANGUAGE = "language";
	String PIPE = "\\|";
	String PROPERTY = "property";
	String SNOMED_CT = "SNOMED_CT";
	String SNOMED_INTERNATIONAL = "SNOMED International";
	String SNOMED_URI = "http://snomed.info/sct";
	String SNOMED_URI_UNVERSIONED = "http://snomed.info/xsct";
	String UNVERSIONED = "UNVERSIONED";
	String VERSION = "/version/";

	int MAX_LANGUAGE_CODE_LENGTH = 5;

	// Copied from https://www.hl7.org/fhir/snomedct.html
	String SNOMED_VALUESET_COPYRIGHT = "This value set includes content from SNOMED CT, which is copyright © 2002+ International Health Terminology Standards Development " +
			"Organisation (SNOMED International), and distributed by agreement between SNOMED International and HL7. Implementer use of SNOMED CT is not covered by this " +
			"agreement.";
	String HL7_DESIGNATION_USAGE = "http://terminology.hl7.org/CodeSystem/designation-usage";
	String LOINC_ORG = "http://loinc.org";
	String HL_7_ORG_FHIR_SID_ICD_10 = "http://hl7.org/fhir/sid/icd-10";

	String MAIN = "MAIN";
	String URL = "url";
	String USE = "use";
	String VALUE = "value";
	String VALUE_BOOLEAN = "valueBoolean";
	String VALUE_STRING = "valueString";
	
	String IMPLICIT_ISA = "?fhir_vs=isa/";
	String IMPLICIT_REFSET = "?fhir_vs=refset/";
	String IMPLICIT_ECL= "?fhir_vs=ecl/";
	
	enum FhirSctProperty {

		ALL_PROPERTIES("*"),
		CHILD("child"),
		EFFECTIVE_TIME("effectiveTime"),
		EQUIVALENT_CONCEPT("equivalentConcept"),
		INACTVE("inactive"),
		MODULE_ID("moduleId"),
		NORMAL_FORM_TERSE("normalFormTerse"),
		NORMAL_FORM("normalForm"),
		PARENT("parent"),
		SUFFICIENTLY_DEFINED("sufficientlyDefined");

		private final String name;

		FhirSctProperty(String s) {
			name = s;
		}

		public boolean equalsName(String otherName) {
			return name.equals(otherName);
		}
		
		@Override
		public String toString() {
			return this.name;
		}
		
		public CodeType toCodeType() {
			return new CodeType(this.name);
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
