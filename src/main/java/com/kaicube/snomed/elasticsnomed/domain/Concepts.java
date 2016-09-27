package com.kaicube.snomed.elasticsnomed.domain;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;

public class Concepts {

	public static final String CORE_MODULE = "900000000000207008";

	public static final String PRIMITIVE = "900000000000074008";
	public static final String FULLY_DEFINED = "900000000000073002";

	public static final String FSN = "900000000000003001";
	public static final String SYNONYM = "900000000000013009";

	public static final String ACCEPTABLE = "900000000000549004";
	public static final String PREFERRED = "900000000000548007";

	public static final String ISA = "116680003";

	public static final String ENTIRE_TERM_CASE_SENSITIVE = "900000000000017005";
	public static final String CASE_INSENSITIVE = "900000000000448009";
	public static final String INITIAL_CHARACTER_CASE_INSENSITIVE = "900000000000020002";

	public static final String DEFINING_RELATIONSHIP = "900000000000006009";
	public static final String STATED_RELATIONSHIP = "900000000000010007";
	public static final String INFERRED_RELATIONSHIP = "900000000000011006";
	public static final String QUALIFYING_RELATIONSHIP = "900000000000225001";
	public static final String ADDITIONAL_RELATIONSHIP = "900000000000227009";

	public static final String EXISTENTIAL = "900000000000451002";
	public static final String UNIVERSAL = "900000000000452009";

	public static final BiMap<String, String> definitionStatusNames = new ImmutableBiMap.Builder<String, String>()
			.put(PRIMITIVE, "PRIMITIVE")
			.put(FULLY_DEFINED, "FULLY_DEFINED")
			.build();

	public static final BiMap<String, String> descriptionTypeNames = new ImmutableBiMap.Builder<String, String>()
			.put(FSN, "FSN")
			.put(SYNONYM, "SYNONYM")
			.build();

	public static final BiMap<String, String> descriptionAcceptabilityNames = new ImmutableBiMap.Builder<String, String>()
			.put(ACCEPTABLE, "ACCEPTABLE")
			.put(PREFERRED, "PREFERRED")
			.build();

	public static final BiMap<String, String> caseSignificanceNames = new ImmutableBiMap.Builder<String, String>()
			.put(ENTIRE_TERM_CASE_SENSITIVE, "ENTIRE_TERM_CASE_SENSITIVE")
			.put(CASE_INSENSITIVE, "CASE_INSENSITIVE")
			.put(INITIAL_CHARACTER_CASE_INSENSITIVE, "INITIAL_CHARACTER_CASE_INSENSITIVE")
			.put(SYNONYM, "SYNONYM")
			.build();

	public static final BiMap<String, String> relationshipCharacteristicTypeNames = new ImmutableBiMap.Builder<String, String>()
			.put(DEFINING_RELATIONSHIP, "DEFINING_RELATIONSHIP")
			.put(STATED_RELATIONSHIP, "STATED_RELATIONSHIP")
			.put(INFERRED_RELATIONSHIP, "INFERRED_RELATIONSHIP")
			.put(QUALIFYING_RELATIONSHIP, "QUALIFYING_RELATIONSHIP")
			.put(ADDITIONAL_RELATIONSHIP, "ADDITIONAL_RELATIONSHIP")
			.build();

	public static final BiMap<String, String> relationshipModifierNames = new ImmutableBiMap.Builder<String, String>()
			.put(EXISTENTIAL, "EXISTENTIAL")
			.put(UNIVERSAL, "UNIVERSAL")
			.build();

}
