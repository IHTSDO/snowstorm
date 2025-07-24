package org.snomed.snowstorm.ecl.deserializer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.langauges.ecl.domain.filter.MemberFilterConstraint;
import org.snomed.snowstorm.ecl.SECLObjectFactory;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SSubExpressionConstraint;

import static org.junit.jupiter.api.Assertions.*;

class ECLModelDeserializerServiceTest {

	private final ECLQueryBuilder eclQueryBuilder;
	private final ECLModelDeserializerService eclModelDeserializerService;
	private final ObjectMapper objectMapper;

	public ECLModelDeserializerServiceTest() {
		eclQueryBuilder = new ECLQueryBuilder(new SECLObjectFactory(1000));
		objectMapper = new ObjectMapper();
		objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		eclModelDeserializerService = new ECLModelDeserializerService();
	}

	@Test
	public void testAll() throws JsonProcessingException {
		assertConversionTest("*");
		assertConversionTest("100000000");
		assertConversionTest("< 100000000");
		assertConversionTest("<< 100000000");
		assertConversionTest("<! 100000000");
		assertConversionTest("100000000 : 100000000 = *");
		assertConversionTest("100000000 : 100000000 = 100000000");
		assertConversionTest("100000000 : 100000000 = 100000000, 100000000 = 100000000");
		assertConversionTest("100000000 : 100000000 = 100000000 or 100000000 = 100000000");
		assertConversionTest("100000000 : { 100000000 = 100000000 }");

		// From ECL test rig
		assertConversionTest("< 404684003 |Clinical finding|");
		assertConversionTest("<< 73211009 |Diabetes mellitus|");
		assertConversionTest("<! 404684003 |Clinical finding|");
		assertConversionTest("> 40541001 |Acute pulmonary edema|");
		assertConversionTest(">> 40541001 |Acute pulmonary edema|");
		assertConversionTest(">! 40541001 |Acute pulmonary edema|");

		// # Member of
		assertConversionTest("^ 447562003 |ICD-10 complex map reference set|");

		// # Any
		assertConversionTest("*");
		assertConversionTest("<< *");
		assertConversionTest(">> *");
		assertConversionTest("< *");
		assertConversionTest("<! *");
		assertConversionTest("> *");
		assertConversionTest(">! *");
		assertConversionTest("^ *");

		// # Attributes
		assertConversionTest("< 19829001 |Disorder of lung| : 116676008 |Associated morphology| = 79654002 |Edema|");
		assertConversionTest("< 19829001 |Disorder of lung| : 116676008 |Associated morphology| = << 79654002 |Edema|");
		assertConversionTest("< 404684003 |Clinical finding| : 363698007 |Finding site| = << 39057004 |Pulmonary valve structure|, 116676008 |Associated morphology| = << 415582006 |Stenosis|");
		assertConversionTest("* : 246075003 |Causative agent| = 387517004 |Paracetamol|");

		// # Attribute Groups
		assertConversionTest("< 404684003 |Clinical finding| : { 363698007 |Finding site| = << 39057004 |Pulmonary valve structure|, " +
				"116676008 |Associated morphology| = << 415582006 |Stenosis| }, { 363698007 |Finding site| = << 53085002 |Right ventricular structure|, 116676008 |Associated " +
				"morphology| = << 56246009 |Hypertrophy| }");

		// Concrete Values
		assertConversionTest("< 27658006 |Amoxicillin| :" +
				" 411116001 |Has dose form| = << 385055001 |Tablet dose form|," +
				" { 179999999100 |Has basis of strength| = ( 219999999102 |Amoxicillin only| :" +
				" 189999999103 |Has strength magnitude| >= #200, 199999999101 |Has strength unit| = 258684004 |mg| ) }");

		// # Reverse Attributes
		assertConversionTest("< 91723000 |Anatomical structure| : R 363698007 |Finding site| = < 125605004 |Fracture of bone|");

		// # Dotted Attributes
		assertConversionTest("< 91723000 |Anatomical structure|, ( < 125605004 |Fracture of bone| . 363698007 |Finding site| )");
		assertConversionTest("< 125605004 |Fracture of bone| . 363698007 |Finding site|");
		assertConversionTest("< 105590001 |Substance| : R 127489000 |Has active ingredient| = 249999999101 |TRIPHASIL tablet|");


		// # Any Attribute Name and Value
		assertConversionTest("< 404684003 |Clinical finding| : * = 79654002 |Edema|");
		assertConversionTest("< 404684003 |Clinical finding| : 116676008 |Associated morphology| = *");
		
		// # Attribute cardinality
		assertConversionTest("< 373873005 |Pharmaceutical / biologic product| : [1..3] 127489000 |Has active ingredient| = < 105590001 |Substance|");
		assertConversionTest("< 373873005 |Pharmaceutical / biologic product| : [1..1] 127489000 |Has active ingredient| = < 105590001 |Substance|");
		// # Unconstrained Cardinalities
		assertConversionTest("< 373873005 |Pharmaceutical / biologic product| : [0..1] 127489000 |Has active ingredient| = < 105590001 |Substance|");
		// # Default Cardinalities
		assertConversionTest("< 373873005 |Pharmaceutical / biologic product| : [1..*] 127489000 |Has active ingredient| = < 105590001 |Substance|",
				// Default cardinality is dropped for neatness
				"< 373873005 |Pharmaceutical / biologic product| : 127489000 |Has active ingredient| = < 105590001 |Substance|");

		// # Non-redundant Attributes
		assertConversionTest("404684003 |Clinical finding| : { 116676008 |Associated morphology| = 72704001 |Fracture|, 363698007 |Finding site| = 299701004 |Bone of forearm|" +
				", 363698007 |Finding site| = 62413002 |Bone structure of radius| }");
		
		// # Attribute Cardinality in Groups
		assertConversionTest("< 404684003 |Clinical finding| : [2..*] 363698007 |Finding site| = < 91723000 |Anatomical structure|");
		assertConversionTest("< 404684003 |Clinical finding| : { [2..*] 363698007 |Finding site| = < 91723000 |Anatomical structure| }");

		// # Attribute Group Cardinality
		assertConversionTest("< 373873005 |Pharmaceutical / biologic product| : [1..3] { [1..*] 127489000 |Has active ingredient| = < 105590001 |Substance| }",
				// Default cardinality dropped
				"< 373873005 |Pharmaceutical / biologic product| : [1..3] { 127489000 |Has active ingredient| = < 105590001 |Substance| }");
		assertConversionTest("< 373873005 |Pharmaceutical / biologic product| : [1..3] { 127489000 |Has active ingredient| = < 105590001 |Substance| }");
		// # Unconstrained Cardinalities
		assertConversionTest("< 373873005 |Pharmaceutical / biologic product| : [0..1] { 127489000 |Has active ingredient| = < 105590001 |Substance| }");
		assertConversionTest("< 373873005 |Pharmaceutical / biologic product| : [1..*] { 127489000 |Has active ingredient| = < 105590001 |Substance| }",
				"< 373873005 |Pharmaceutical / biologic product| : { 127489000 |Has active ingredient| = < 105590001 |Substance| }");
		// # Default Cardinalities
		assertConversionTest("< 373873005 |Pharmaceutical / biologic product| : { 127489000 |Has active ingredient| = < 105590001 |Substance| }");
		assertConversionTest("< 373873005 |Pharmaceutical / biologic product| : { [1..*] 127489000 |Has active ingredient| = < 105590001 |Substance| }",
				"< 373873005 |Pharmaceutical / biologic product| : { 127489000 |Has active ingredient| = < 105590001 |Substance| }");
		
		// # Non-redundant Attribute Groups
		assertConversionTest("< 404684003 |Clinical finding| : { 363698007 |Finding site| = 299701004 |Bone of forearm| }, { 363698007 |Finding site| = 62413002 |Bone structure of radius| }");
		assertConversionTest("< 404684003 |Clinical finding| : [1..1] { 363698007 |Finding site| = < 91723000 |Anatomical structure| }");
		// # Attribute and Attribute Group Cardinalities
		assertConversionTest("< 404684003 |Clinical finding| : [0..0] { [2..*] 363698007 |Finding site| = < 91723000 |Anatomical structure| }");
		// # Reverse Cardinalities
		assertConversionTest("< 105590001 |Substance| : [3..3] R 127489000 |Has active ingredient| = *");
		// # Compound Expression Constraints
		assertConversionTest("< 19829001 |Disorder of lung| and < 301867009 |Edema of trunk|",
				"< 19829001 |Disorder of lung|, < 301867009 |Edema of trunk|");
		assertConversionTest("< 19829001 |Disorder of lung| OR < 301867009 |Edema of trunk|",
				"< 19829001 |Disorder of lung| or < 301867009 |Edema of trunk|");
		assertConversionTest("< 19829001 |Disorder of lung|, < 301867009 |Edema of trunk|, ^ 447562003 |ICD-10 complex map reference set|");
		assertConversionTest("( < 19829001 |Disorder of lung|, < 301867009 |Edema of trunk| ), ^ 447562003 |ICD-10 complex map reference set|");
		assertConversionTest("< 19829001 |Disorder of lung|, ( < 301867009 |Edema of trunk|, ^ 447562003 |ICD-10 complex map reference set| )");
		assertConversionTest("( < 19829001 |Disorder of lung|, < 301867009 |Edema of trunk| ) OR ^ 447562003 |ICD-10 complex map reference set|",
				"( < 19829001 |Disorder of lung|, < 301867009 |Edema of trunk| ) or ^ 447562003 |ICD-10 complex map reference set|");
		assertConversionTest("< 19829001 |Disorder of lung|, ( < 301867009 |Edema of trunk| or ^ 447562003 |ICD-10 complex map reference set| )");
		
		// # Attribute Conjunction and Disjunction
		assertConversionTest("< 404684003 |Clinical finding| : 363698007 |Finding site| = << 39057004 |Pulmonary valve structure|, 116676008 |Associated morphology| = << 415582006 |Stenosis|");
		assertConversionTest("< 404684003 |Clinical finding| : 116676008 |Associated morphology| = << 55641003 |Infarct| or 42752001 |Due to| = << 22298006 |Myocardial infarction|");
		assertConversionTest("< 404684003 |Clinical finding| : ( 363698007 |Finding site| = << 39057004 |Pulmonary valve structure|, 116676008 |Associated morphology| = << 415582006 |Stenosis| ), 42752001 |Due to| = << 445238008 |Malignant carcinoid tumor|");
		assertConversionTest("< 404684003 |Clinical finding| : 363698007 |Finding site| = << 39057004 |Pulmonary valve structure|, ( 116676008 |Associated morphology| = << 415582006 |Stenosis| or 42752001 |Due to| = << 445238008 |Malignant carcinoid tumor| )");
		// # Attribute Group Conjunction and Disjunction
		assertConversionTest("< 404684003 |Clinical finding| : { 363698007 |Finding site| = << 39057004 |Pulmonary valve structure|, 116676008 |Associated morphology| = << 415582006 |Stenosis| } or { 363698007 |Finding site| = << 53085002 |Right ventricular structure|, 116676008 |Associated morphology| = << 56246009 |Hypertrophy| }");
		// # Attribute Value Conjunction and Disjunction
		assertConversionTest("^ 447562003 |ICD-10 complex map reference set| : 246075003 |Causative agent| = ( < 373873005 |Pharmaceutical / biologic product| or < 105590001 " +
				"|Substance| )");
		assertConversionTest("< 404684003 |Clinical finding| : 116676008 |Associated morphology| = ( << 56208002 |Ulcer|, << 50960005 |Hemorrhage| )");
		
		// # Exclusion of Simple Expressions
		assertConversionTest("<< 19829001 |Disorder of lung| MINUS << 301867009 |Edema of trunk|",
				"<< 19829001 |Disorder of lung| minus << 301867009 |Edema of trunk|");
		assertConversionTest("<< 19829001 |Disorder of lung| minus ^ 447562003 |ICD-10 complex map reference set|");
		// # Exclusion of Attribute Values
		assertConversionTest("< 404684003 |Clinical finding| : 116676008 |Associated morphology| = ( ( << 56208002 |Ulcer|, << 50960005 |Hemorrhage| ) minus << 26036001 " +
				"|Obstruction| )");
		// # Not Equal to Attribute Value
		assertConversionTest("< 404684003 |Clinical finding| : 116676008 |Associated morphology| != << 26036001 |Obstruction|");
		assertConversionTest("< 404684003 |Clinical finding| : [0..0] 116676008 |Associated morphology| = << 26036001 |Obstruction|");
		assertConversionTest("< 404684003 |Clinical finding| : [0..0] 116676008 |Associated morphology| != << 26036001 |Obstruction|, " +
				"[1..*] 116676008 |Associated morphology| = << 26036001 |Obstruction|",
				// Default cardinality dropped
				"< 404684003 |Clinical finding| : [0..0] 116676008 |Associated morphology| != << 26036001 |Obstruction|, " +
						"116676008 |Associated morphology| = << 26036001 |Obstruction|");

		// # Nested expression constraints
		assertConversionTest("<< ( ^ 447562003 |ICD-10 complex map reference set| )");
		assertConversionTest("< ( 125605004 |Fracture of bone| . 363698007 |Finding site| )");
		assertConversionTest("( < 125605004 |Fracture of bone| ) . 363698007 |Finding site|");
		// # Compound Expression Constraints
		assertConversionTest("( < 404684003 |Clinical finding| : 363698007 |Finding site| = << 39057004 |Pulmonary valve structure| ), ^ 447562003 |ICD-10 complex map reference " +
				"set|");
		assertConversionTest("^ 447562003 |ICD-10 complex map reference set|, ( < 404684003 |Clinical finding| : 363698007 |Finding site| = << 39057004 |Pulmonary valve " +
				"structure| )");
		assertConversionTest("( < 404684003 |Clinical finding| : 363698007 |Finding site| = << 39057004 |Pulmonary valve structure| ), ( < 64572001 |Disease| : 116676008 " +
				"|Associated morphology| = << 415582006 |Stenosis| )");
		// # Dotted Attributes
		assertConversionTest("( << 17636008 |Specimen collection| : 424226004 |Using device| = << 19923001 |Catheter| ) . 363701004 |Direct substance|");
		// # Refinement
		assertConversionTest("( << 404684003 |Clinical finding (finding)| or << 272379006 |Event (event)| ) : 255234002 |After| = << 71388002 |Procedure (procedure)|");
		// # Attribute Names
		assertConversionTest("<< 125605004 |Fracture of bone| : [0..0] ( ( << 410662002 |Concept model attribute| minus 363698007 |Finding site| ) minus 116676008 |Associated " +
				"morphology| ) = *");
		assertConversionTest("( << 410662002 |Concept model attribute| minus 363698007 |Finding site| ) minus 116676008 |Associated morphology|");
		// # Attribute Values
		assertConversionTest("< 404684003 |Clinical finding| : 47429007 |Associated with| = ( " +
				"< 404684003 |Clinical finding| : 116676008 |Associated morphology| = << 55641003 |Infarct| )");

		// All concrete values
		assertConversionTest("< 404684003 |Clinical finding| : 1000000 = true");
		assertConversionTest("< 404684003 |Clinical finding| : 2000000 = #10");
		assertConversionTest("< 404684003 |Clinical finding| : 3000000 = \"Supplier A\"");
		assertConversionTest("< 404684003 |Clinical finding| : 3000000 = (\"Supplier A\" \"Supplier Z\")");

		// Concept filters
		assertConversionTest("< 404684003 |Clinical finding| {{ C active = true }}");
		assertConversionTest("< 56265001 |Heart disease| {{ C definitionStatusId = 900000000000074008 |Primitive| }}");
		assertConversionTest("< 64572001 |Disease| {{ C definitionStatus = primitive }} {{ D term = \"heart\" }}",
				"< 64572001 |Disease| {{ C definitionStatusId = 900000000000074008 |Primitive| }} {{ D term = \"heart\" }}");
		assertConversionTest("< 195967001 |Asthma| {{ C definitionStatus = primitive, moduleId = 900000000000207008 |SNOMED CT core module| }}",
				"< 195967001 |Asthma| {{ C definitionStatusId = 900000000000074008 |Primitive| moduleId = 900000000000207008 |SNOMED CT core module| }}");
		assertConversionTest("< 125605004 |Fracture of bone| {{ C effectiveTime >= \"20190731\" }}");
		assertConversionTest("< 125605004 |Fracture of bone| {{ C effectiveTime != (\"20190131\" \"20190731\" \"20200131\" \"20200731\") }}");
		assertConversionTest("^ 816080008 |International Patient Summary| {{ C active = 1 }}",
				"^ 816080008 |International Patient Summary| {{ C active = true }}");
		assertConversionTest("^ 816080008 |International Patient Summary| {{ C active = false }}");

		// Description filters
		assertConversionTest("< 64572001 |Disease| {{ D term = \"box\", type = syn, dialect = en-us (prefer) }}");
		assertConversionTest("< 404684003 |Clinical finding| {{ D term = \"heart\" }}");
		assertConversionTest("< 64572001 |Disease| {{ D term = \"hjärt\", language = sv }} {{ D term = \"heart\", language = en }}");
		assertConversionTest("< 125605004 |Fracture of bone| minus < 125605004 |Fracture of bone| {{ D term != \"fracture\" }}");
		assertConversionTest("< 64572001 |Disease| {{ term = \"heart\", term = \"att\" }}",
				"< 64572001 |Disease| {{ D term = \"heart\", term = \"att\" }}");
		assertConversionTest("< 64572001 |Disease| {{ D term = match:\"heart att\" }}",
				"< 64572001 |Disease| {{ D term = \"heart att\" }}");
		assertConversionTest("< 64572001 |Disease| {{ D term = (\"heart\" \"card\") }}");
		assertConversionTest("< 64572001 |Disease| {{ D term = wild:\"cardi*opathy\" }}");
		assertConversionTest("< 64572001 |Disease| {{ D term = (match:\"gas\" wild:\"*itis\") }}");
		assertConversionTest("< 64572001 |Disease| {{ D term = \"eye\" }} {{ D term = wild:\"*itis\" }}");
		assertConversionTest("< 56265001 |Heart disease| {{ D term = \"hjärt\", language = SV, type = syn }}");
		assertConversionTest("< 56265001 |Heart disease| {{ D term = \"heart\", typeId = ( 900000000000013009 |Synonym| 900000000000003001 |Fully specified name| ) }}",
				"< 56265001 |Heart disease| {{ D term = \"heart\", type = (syn fsn) }}");
		assertConversionTest("< 64572001 |Disease| {{ D dialect = en-au }}");
		assertConversionTest("< 64572001 |Disease| {{ D term = \"card\", dialect = (en-nhs-clinical en-nhs-pharmacy) }}");
		assertConversionTest("< 64572001 |Disease| {{ D term = \"box\", type = syn, dialect = en-nhs-clinical (prefer), dialect = en-gb (accept) }}");
		assertConversionTest("< 404684003 |Clinical finding| {{ D type = def, moduleId = 900000000000207008 |SNOMED CT core module| }}");
		assertConversionTest("< 125605004 |Fracture of bone| {{ D effectiveTime <= \"20190731\" }}");
		assertConversionTest("< 125605004 |Fracture of bone| {{ D effectiveTime != (\"20190131\" \"20190731\" \"20200131\" \"20200731\") }}");
		assertConversionTest("^ 816080008 |International Patient Summary| {{ D active = 1 }}",
				"^ 816080008 |International Patient Summary| {{ D active = true }}");
		assertConversionTest("^ 816080008 |International Patient Summary| {{ D active = false }}");
		assertConversionTest("64572001 |Disease| {{ D id = 803980011 }}");
		assertConversionTest("64572001 |Disease| {{ D id = (803980011 903980011) }}");

		// Member filters
		assertConversionTest("^ 447562003 |ICD-10 complex map reference set| {{ M mapTarget = \"J45.9\" }}");
		assertConversionTest("^ 447562003 |ICD-10 complex map reference set| {{ M active = true }}");
		assertConversionTest("^ 447562003 |ICD-10 complex map reference set| {{ M active = false }}");
		assertConversionTest("^ 447562003 |ICD-10 complex map reference set| {{ M mapTarget = wild:\"J45.9\" }}");
		assertConversionTest("^ 447562003 |ICD-10 complex map reference set| {{ M mapGroup = #2, mapPriority = #1, mapTarget = \"J45.9\" }}");
		assertConversionTest("^ [targetComponentId] 900000000000527005 |SAME AS association reference set| {{ M referencedComponentId = 67415000 |Hay asthma| }}");

		final SSubExpressionConstraint eclModel = (SSubExpressionConstraint) eclQueryBuilder.createQuery("^ 447562003 |ICD-10 complex map reference set| {{ M active = false }}");
		MemberFilterConstraint memberFilterConstraint = eclModel.getMemberFilterConstraints().get(0);
		assertNull(memberFilterConstraint.getMemberFieldFilters());
		assertNotNull(memberFilterConstraint.getActiveFilters());
		assertEquals(1, memberFilterConstraint.getActiveFilters().size());
		assertFalse(memberFilterConstraint.getActiveFilters().get(0).isActive());

		// History supplement filters
		assertConversionTest("195967001 |Asthma| {{ + HISTORY-MIN }}");
		assertConversionTest("195967001 |Asthma| {{ + HISTORY-MOD }}");
		assertConversionTest("195967001 |Asthma| {{ + HISTORY-MAX }}");
		assertConversionTest("195967001 |Asthma| {{ + HISTORY }}");
	}

	private void assertConversionTest(String inputEcl) throws JsonProcessingException {
		assertConversionTest(inputEcl, inputEcl);
	}

	private void assertConversionTest(String inputEcl, String expectedEcl) throws JsonProcessingException {
		final ExpressionConstraint eclModel = eclQueryBuilder.createQuery(inputEcl);
		final String eclModelJsonString = objectMapper.writeValueAsString(eclModel);
		String eclOutputString = eclModelDeserializerService.convertECLModelToString(eclModelJsonString);
		assertEquals(expectedEcl, eclOutputString, "Not equal using model: " + eclModelJsonString);
	}

}
