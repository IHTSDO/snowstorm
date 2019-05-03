package org.snomed.snowstorm.fhir.config;

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
	
	StringType SUFFICIENTLY_DEFINED = new StringType("sufficientlyDefined");
	String MAIN = "MAIN";
	String URL = "url";
	String USE = "use";
	String VALUE = "value";
	String VALUE_BOOLEAN = "valueBoolean";
	String VALUE_STRING = "valueString";
	String VERSION = "version";

}
