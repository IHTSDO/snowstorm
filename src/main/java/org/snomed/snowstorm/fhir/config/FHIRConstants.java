package org.snomed.snowstorm.fhir.config;

import java.util.*;

import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.StringType;

public interface FHIRConstants {

	//Constant words and phrases 
	StringType CHILD = new StringType("child");
	String CODE = "code";
	String DESIGNATION = "designation";
	String DISPLAY = "display";
	StringType EQUIVALENT_CONCEPT = new StringType("equivalentConcept");
	StringType EFFECTIVE_TIME = new StringType("effectiveTime");
	
	static final int NOT_SET = -1;

	String FHIR = "FHIR";

	String LANG_EN = "en";
	String LANGUAGE = "language";
	StringType MODULE_ID = new StringType("moduleId");
	String NAME = "name";
	String PROPERTY = "property";
	StringType PARENT = new StringType("parent");
	
	String ACCEPT_LANGUAGE_HEADER = "Accept-Language";
	int MAX_LANGUAGE_CODE_LENGTH = 5;
	String PIPE = "\\|";

	String SNOMED_EDITION = "SNOMED CT International Edition";
	String SNOMED_INTERNATIONAL = "SNOMED International";
	String SNOMED_URI = "http://snomed.info/sct";
	String SNOMED_URI_UNVERSIONED = "http://snomed.info/xsct";
	String UNVERSIONED = "UNVERSIONED";
	int UNVERSIONED_SCT_VERSION = -1;
	String VERSION = "/version/";
	String SNOMED_URI_DEFAULT_MODULE = "http://snomed.info/sct/900000000000207008";
	String ICD10 = "ICD-10";
	String ICD10_URI = "http://hl7.org/fhir/sid/icd-10";
	String ICDO = "ICD-O";
	String ICDO_URI = "http://hl7.org/fhir/sid/icd-o";
	String MAP_INDICATOR = "?fhir_cm=";
	String SNOMED_CT = "SNOMED_CT";
	//String SNOMED_CONCEPTMAP = SNOMED_URI + MAP_INDICATOR;

	// Copied from https://www.hl7.org/fhir/snomedct.html
	String SNOMED_VALUESET_COPYRIGHT = "This value set includes content from SNOMED CT, which is copyright © 2002+ International Health Terminology Standards Development " +
			"Organisation (SNOMED International), and distributed by agreement between SNOMED International and HL7. Implementer use of SNOMED CT is not covered by this " +
			"agreement.";
	String HL7_DESIGNATION_USAGE = "http://terminology.hl7.org/CodeSystem/designation-usage";
	String LOINC_ORG = "http://loinc.org";
	String HL_7_ORG_FHIR_SID_ICD_10 = "http://hl7.org/fhir/sid/icd-10";

	enum Validation { EQUALS, STARTS_WITH }
	
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
		INACTVE ("inactive"),
		SUFFICIENTLY_DEFINED("sufficientlyDefined"),
		MODULE_ID("moduleId"),
		NORMAL_FORM("normalForm"),
		NORMAL_FORM_TERSE("normalFormTerse"),
		ALL_PROPERTIES("*");

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
