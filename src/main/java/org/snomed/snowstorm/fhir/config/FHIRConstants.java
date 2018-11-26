package org.snomed.snowstorm.fhir.config;

import org.hl7.fhir.dstu3.model.StringType;
import org.snomed.snowstorm.fhir.services.FHIRHelper;

public interface FHIRConstants {
	final String DEFAULT_BRANCH = "MAIN";

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


	public enum SnomedEdition {
		INTERNATIONAL("900000000000207008", "en-US", ""),
		AUSTRALIA("32506021000036107", "en-AU", "SNOMEDCT-AU"),
		SWEDEN("45991000052106", "sv-SE", "SNOMEDCT-SE"),
		BELGIUM("11000172109", "fr-BE", "SNOMEDCT-BE"),
		DENMARK("554471000005108", "da-DK", "SNOMEDCT-DK"),
		CANADA_ENGLISH("20621000087109", "en-CA", "SNOMEDCT-CAE"),
		CANADA_FRENCH("20611000087101", "fr-CA", "SNOMEDCT-CAF"),
		NETHERLANDS("11000146104", "nl-NL", "SNOMEDCT-NL"),
		NEW_ZEALAND("21000210109", "en-NZ", "SNOMEDCT-NZ"),
		SPAIN("449081005", "es-ES", "SNOMEDCT-ES"),
		UK_CLINICAL("999000021000000109", "en-GB", "SNOMEDCT-UKC"),
		UK("999000031000000106", "en-GB", "SNOMEDCT-UK"),
		URUGUAY("5631000179106", "es-UY", "SNOMEDCT-UY"),
		US("731000124108", "en-US", "SNOMEDCT-US"),
		US_ICD10("5991000124107", "en-US", "SNOMEDCT-ICD10");

		private final String sctid;   // defined in https://confluence.ihtsdotools.org/display/DOC/List+of+SNOMED+CT+Edition+URIs
		private final String language; // default language and locale
		private final String shortName; // CodeSystem ShortName

		SnomedEdition(String sctid, String language, String shortName) {
			this.sctid = sctid;
			this.language = language;
			this.shortName = shortName;
		}

		public String sctid() {
			return sctid;
		}

		public String language() {
			return language;
		}

		public String shortName() {
			return shortName;
		}

		public String languageCode() {return language.substring(0, 2);}

		public String uri() {
			return FHIRConstants.SNOMED_URI + "/" + sctid();
		}

		public String branch() {
			return this == SnomedEdition.INTERNATIONAL ? DEFAULT_BRANCH : DEFAULT_BRANCH + "/" + sctid();
		}

		public static SnomedEdition lookup(String moduleId){
			for (SnomedEdition s : SnomedEdition.values()){
				if(s.sctid.equals(moduleId)){
					return s;
				}
			}
			return SnomedEdition.INTERNATIONAL;
		}
	}

}