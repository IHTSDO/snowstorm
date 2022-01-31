package org.snomed.snowstorm.core.data.domain;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;

import java.util.Set;

public class Concepts {

	public static final String SNOMEDCT_ROOT = "138875005";

	// Modules
	public static final String CORE_MODULE = "900000000000207008";
	public static final String MODEL_MODULE = "900000000000012004";
	public static final String ICD10_MODULE = "449080006";
	public static final String COMMON_FRENCH_MODULE = "11000241103";

	public static final String PRIMITIVE = "900000000000074008";
	public static final String FULLY_DEFINED = "900000000000073002";
	public static final String SUFFICIENTLY_DEFINED = FULLY_DEFINED;

	public static final String FSN = "900000000000003001";
	public static final String SYNONYM = "900000000000013009";
	public static final String TEXT_DEFINITION = "900000000000550004";

	public static final String ACCEPTABLE = "900000000000549004";
	public static final String ACCEPTABLE_CONSTANT = "ACCEPTABLE";
	public static final String PREFERRED = "900000000000548007";
	public static final String PREFERRED_CONSTANT = "PREFERRED";

	public static final String LANG_REFSET = "900000000000506000";
	public static final String US_EN_LANG_REFSET = "900000000000509007";
	public static final String GB_EN_LANG_REFSET = "900000000000508004";

	public static final String ISA = "116680003";
	public static final Long IS_A_LONG = 116680003L;

	public static final String CONCEPT_MODEL_ATTRIBUTE = "410662002";
	public static final String CONCEPT_MODEL_OBJECT_ATTRIBUTE = "762705008";
	public static final String CONCEPT_MODEL_DATA_ATTRIBUTE = "762706009";

	public static final String ENTIRE_TERM_CASE_SENSITIVE = "900000000000017005";
	public static final String CASE_INSENSITIVE = "900000000000448009";
	public static final String INITIAL_CHARACTER_CASE_INSENSITIVE = "900000000000020002";

	public static final String OWL_EXPRESSION_TYPE_REFERENCE_SET = "762676003";
	public static final String OWL_AXIOM_REFERENCE_SET = "733073007";
	public static final String LATERALIZABLE_BODY_STRUCTURE_REFERENCE_SET = "723264001";
	public static final String DEFINING_RELATIONSHIP = "900000000000006009";
	public static final String STATED_RELATIONSHIP = "900000000000010007";
	public static final String INFERRED_RELATIONSHIP = "900000000000011006";
	public static final String QUALIFYING_RELATIONSHIP = "900000000000225001";
	public static final String ADDITIONAL_RELATIONSHIP = "900000000000227009";

	public static final String EXISTENTIAL = "900000000000451002";
	public static final String UNIVERSAL = "900000000000452009";

	public static final String NON_EXISTENT_CONCEPT = "100000005";

	// Inactivation
	public static final String CONCEPT_INACTIVATION_INDICATOR_REFERENCE_SET = "900000000000489007";
	public static final String DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET = "900000000000490003";

	// Component inactivation reasons
	public static final String DUPLICATE = "900000000000482003";
	public static final String OUTDATED = "900000000000483008";
	public static final String AMBIGUOUS = "900000000000484002";
	public static final String ERRONEOUS = "900000000000485001";
	public static final String LIMITED = "900000000000486000";
	public static final String MOVED_ELSEWHERE = "900000000000487009";
	public static final String PENDING_MOVE = "900000000000492006";
	public static final String INAPPROPRIATE = "900000000000494007";
	public static final String CONCEPT_NON_CURRENT = "900000000000495008";
	public static final String NONCONFORMANCE_TO_EDITORIAL_POLICY = "723277005";
	public static final String NOT_SEMANTICALLY_EQUIVALENT = "723278000";
	public static final String CLASSIFICATION_DERIVED_COMPONENT = "1186917008";
	public static final String MEANING_OF_COMPONENT_UNKNOWN = "1186919006";

	public static final String FOUNDATION_METADATA = "900000000000454005";
	public static final String REFSET = "900000000000455006";
	public static final String REFSET_SIMPLE = "446609009";
	public static final String REFSET_MODULE_DEPENDENCY = "900000000000534007";
	public static final String REFSET_DESCRIPTOR_REFSET = "900000000000456007";

	// Historical reference sets
	public static final String REFSET_HISTORICAL_ASSOCIATION = "900000000000522004";
	public static final String REFSET_POSSIBLY_EQUIVALENT_TO_ASSOCIATION = "900000000000523009";
	public static final String REFSET_MOVED_TO_ASSOCIATION = "900000000000524003";
	public static final String REFSET_MOVED_FROM_ASSOCIATION = "900000000000525002";
	public static final String REFSET_REPLACED_BY_ASSOCIATION = "900000000000526001";
	public static final String REFSET_SAME_AS_ASSOCIATION = "900000000000527005";
	public static final String REFSET_WAS_A_ASSOCIATION = "900000000000528000";
	public static final String REFSET_SIMILAR_TO_ASSOCIATION = "900000000000529008";
	public static final String REFSET_ALTERNATIVE_ASSOCIATION = "900000000000530003";
	public static final String REFSET_REFERS_TO_ASSOCIATION = "900000000000531004";
	public static final String REFSET_PARTIALLY_EQUIVALENT_TO_ASSOCIATION = "1186924009";
	public static final String REFSET_POSSIBLY_REPLACED_BY_ASSOCIATION = "1186921001";

	// MRCM reference sets
	public static final String REFSET_MRCM = "723564002";
	public static final String REFSET_MRCM_DOMAIN = "723589008";
	public static final String REFSET_MRCM_DOMAIN_INTERNATIONAL = "723560006";
	public static final String REFSET_MRCM_ATTRIBUTE_DOMAIN = "723604009";
	public static final String REFSET_MRCM_ATTRIBUTE_DOMAIN_INTERNATIONAL = "723561005";
	public static final String REFSET_MRCM_ATTRIBUTE_RANGE = "723592007";
	public static final String REFSET_MRCM_ATTRIBUTE_RANGE_INTERNATIONAL = "723562003";
	public static final String REFSET_MRCM_MODULE_SCOPE = "723563008";
	public static final Set<String> MRCM_INTERNATIONAL_REFSETS = new ImmutableSet.Builder<String>()
			.add(REFSET_MRCM_DOMAIN_INTERNATIONAL)
			.add(REFSET_MRCM_ATTRIBUTE_DOMAIN_INTERNATIONAL)
			.add(REFSET_MRCM_ATTRIBUTE_RANGE_INTERNATIONAL)
			.add(REFSET_MRCM_MODULE_SCOPE)
			.build();

	public static final BiMap<String, String> inactivationIndicatorNames = new ImmutableBiMap.Builder<String, String>()
			.put(DUPLICATE, "DUPLICATE")
			.put(OUTDATED, "OUTDATED")
			.put(AMBIGUOUS, "AMBIGUOUS")
			.put(ERRONEOUS, "ERRONEOUS")
			.put(LIMITED, "LIMITED")
			.put(MOVED_ELSEWHERE, "MOVED_ELSEWHERE")
			.put(PENDING_MOVE, "PENDING_MOVE")
			.put(INAPPROPRIATE, "INAPPROPRIATE")
			.put(CONCEPT_NON_CURRENT, "CONCEPT_NON_CURRENT")
			.put(NONCONFORMANCE_TO_EDITORIAL_POLICY, "NONCONFORMANCE_TO_EDITORIAL_POLICY")
			.put(NOT_SEMANTICALLY_EQUIVALENT, "NOT_SEMANTICALLY_EQUIVALENT")
			.put(CLASSIFICATION_DERIVED_COMPONENT, "CLASSIFICATION_DERIVED_COMPONENT")
			.put(MEANING_OF_COMPONENT_UNKNOWN, "MEANING_OF_COMPONENT_UNKNOWN")

			.build();

	public static final BiMap<String, String> historicalAssociationNames = new ImmutableBiMap.Builder<String, String>()
			.put(REFSET_POSSIBLY_EQUIVALENT_TO_ASSOCIATION, "POSSIBLY_EQUIVALENT_TO")
			.put(REFSET_MOVED_TO_ASSOCIATION, "MOVED_TO")
			.put(REFSET_MOVED_FROM_ASSOCIATION, "MOVED_FROM")
			.put(REFSET_REPLACED_BY_ASSOCIATION, "REPLACED_BY")
			.put(REFSET_SAME_AS_ASSOCIATION, "SAME_AS")
			.put(REFSET_WAS_A_ASSOCIATION, "WAS_A")
			.put(REFSET_SIMILAR_TO_ASSOCIATION, "SIMILAR_TO")
			.put(REFSET_ALTERNATIVE_ASSOCIATION, "ALTERNATIVE")
			.put(REFSET_REFERS_TO_ASSOCIATION, "REFERS_TO")
			.put(REFSET_PARTIALLY_EQUIVALENT_TO_ASSOCIATION, "PARTIALLY_EQUIVALENT_TO")
			.put(REFSET_POSSIBLY_REPLACED_BY_ASSOCIATION, "POSSIBLY_REPLACED_BY")
			.build();

	public static final Set<String> inactivationAndAssociationRefsets = new ImmutableSet.Builder<String>()
			.add(Concepts.CONCEPT_INACTIVATION_INDICATOR_REFERENCE_SET)
			.add(Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET)
			.addAll(Concepts.historicalAssociationNames.keySet())
			.build();

	public static final BiMap<String, String> definitionStatusNames = new ImmutableBiMap.Builder<String, String>()
			.put(PRIMITIVE, "PRIMITIVE")
			.put(FULLY_DEFINED, "FULLY_DEFINED")
			.build();

	public static final BiMap<String, String> descriptionTypeNames = new ImmutableBiMap.Builder<String, String>()
			.put(FSN, "FSN")
			.put(SYNONYM, "SYNONYM")
			.put(TEXT_DEFINITION, "TEXT_DEFINITION")
			.build();

	public static final BiMap<String, String> descriptionAcceptabilityNames = new ImmutableBiMap.Builder<String, String>()
			.put(ACCEPTABLE, "ACCEPTABLE")
			.put(PREFERRED, "PREFERRED")
			.build();

	public static final BiMap<String, String> caseSignificanceNames = new ImmutableBiMap.Builder<String, String>()
			.put(ENTIRE_TERM_CASE_SENSITIVE, "ENTIRE_TERM_CASE_SENSITIVE")
			.put(CASE_INSENSITIVE, "CASE_INSENSITIVE")
			.put(INITIAL_CHARACTER_CASE_INSENSITIVE, "INITIAL_CHARACTER_CASE_INSENSITIVE")
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

	// Notable concepts
	public static final String CLINICAL_FINDING = "404684003";
	public static final String SUBSTANCE = "105590001";
	public static final String FINDING_SITE = "363698007";
	public static final String HEART_STRUCTURE = "80891009";
	public static final String REFERENCED_COMPONENT = "449608002";
	public static final String CONCEPT_TYPE_COMPONENT = "900000000000461009";

}
