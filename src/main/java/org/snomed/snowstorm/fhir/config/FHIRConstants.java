package org.snomed.snowstorm.fhir.config;

import java.util.*;

import org.hl7.fhir.r4.model.CodeType;

public interface FHIRConstants {

	//Constant words and phrases 
	String ACCEPT_LANGUAGE_HEADER = "Accept-Language";
	String ACTIVE = "active";
	String CODE = "code";
	String CODING = "coding";
	String CODEABLE_CONCEPT = "codeableConcept";
	String CONCEPT = "concept";
	String DATE = "date";
	String DESIGNATION = "designation";
	String DISPLAY = "display";
	String DISPLAY_LANGUAGE = "displayLanguage";
	String FHIR = "FHIR";
	String ICD10_URI = "http://hl7.org/fhir/sid/icd-10";
	String INACTIVE = "inactive";
	String LANGUAGE = "language";
	String MAIN = "MAIN";
	String MESSAGE = "message";
	String NOT_IN_VS = "not-in-vs";
	String OUTCOME = "outcome";
	String OUTCOME_CODING = "Coding";
	String OUTCOME_CODING_CODE = "Coding.code";
	String OUTCOME_CODING_DISPLAY = "Coding.display";
	String OUTCOME_CODING_SYSTEM = "Coding.system";
	String PIPE = "\\|";
	String PARAMETER = "parameter";
	String PREFERRED_FOR_LANGUAGE = "preferredForLanguage";
	String PROPERTY = "property";
	String RESULT = "result";
	String SNOMED_CT = "SNOMED_CT";
	String SNOMED_INTERNATIONAL = "SNOMED International";
	String SNOMED_URI = "http://snomed.info/sct";
	String SNOMED_URI_UNVERSIONED = "http://snomed.info/xsct";
	String SYSTEM = "system";
	String UNVERSIONED = "UNVERSIONED";
	String URL = "url";
	String USE = "use";
	String VALUE = "value";
	String VALUE_BOOLEAN = "valueBoolean";
	String VALUE_STRING = "valueString";
	String VERSION = "version";
	String VERSION_SLASH = "/version/";

	int MAX_LANGUAGE_CODE_LENGTH = 5;

	// Copied from https://www.hl7.org/fhir/snomedct.html
	String SNOMED_VALUESET_COPYRIGHT = "This value set includes content from SNOMED CT, which is copyright © 2002+ International Health Terminology Standards Development " +
			"Organisation (SNOMED International), and distributed by agreement between SNOMED International and HL7. Implementer use of SNOMED CT is not covered by this " +
			"agreement.";
	String HL7_CS_DESIGNATION_USAGE = "http://terminology.hl7.org/CodeSystem/designation-usage";
	String HL7_CS_TERM_INFRA = "http://terminology.hl7.org/CodeSystem/hl7TermMaintInfra";
	String LOINC_ORG = "http://loinc.org";
	String HL_7_ORG_FHIR_SID_ICD_10 = "http://hl7.org/fhir/sid/icd-10";

	String FHIR_VS = "?fhir_vs";
	String IMPLICIT_ISA = FHIR_VS + "=isa/";
	String IMPLICIT_REFSET = FHIR_VS + "=refset/";
	String IMPLICIT_ECL= FHIR_VS + "=ecl/";
	
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
