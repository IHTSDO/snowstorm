package org.snomed.snowstorm.fhir.config;

import org.hl7.fhir.dstu3.model.StringType;

public interface FHIRConstants {

	//Constant words and phrases 
	final StringType CHILD = new StringType("child");
	final String CODE = "code";
	final String COPYRIGHT = "© Copyright YEAR International Health Terminology Standards Development Organisation, all rights reserved.";
	final String DESIGNATION = "designation";
	final StringType DISPLAY = new StringType("display");
	final StringType EFFECTIVE_TIME = new StringType("effectiveTime");
	
	final String FHIR = "FHIR";
	final String FHIR_DTSU3_ROOT = "fhir/dtsu3/";
	
	final String LANG_EN = "en";
	final String LANGUAGE = "language";
	final StringType MODULE_ID = new StringType("moduleId");
	final String NAME = "name";
	final String PROPERTY = "property";
	final StringType PARENT = new StringType("parent");
	
	final String SNOMED_EDITION = "SNOMED CT International Edition";
	final String SNOMED_INTERNATIONAL = "SNOMED International";
	final String SNOMED_URI = "http://snomed.info/sct";
	final StringType SUFFICIENTLY_DEFINED = new StringType("sufficientlyDefined");
	final String URL = "url";
	final String USE = "use";
	final String VALUE = "value";
	final String VALUE_BOOLEAN = "valueBoolean";
	final String VALUE_STRING = "valueString";
	final String VERSION = "version";
}
