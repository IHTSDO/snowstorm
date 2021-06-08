package org.snomed.snowstorm.ecl;

import com.google.common.collect.Sets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.ServiceTestUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

class ECLQueryServiceFilterTest extends ECLQueryServiceTest {

	private ServiceTestUtil testUtil;

	@BeforeEach
	void setup() throws ServiceException {
		testUtil = new ServiceTestUtil(conceptService);
		List<Concept> allConcepts = new ArrayList<>();

		allConcepts.add(new Concept(DISORDER).addDescription(new Description("Disease(disorder)")));

		allConcepts.add(new Concept("100001").addDescription(new Description("Athlete's heart (disorder)"))
				.addRelationship(ISA, DISORDER));
		allConcepts.add(new Concept("100002")
				.addDescription(new Description( "Heart disease (disorder)").setLanguageCode("en").setType("FSN")
						.addLanguageRefsetMember(US_EN_LANG_REFSET, PREFERRED)
						.addLanguageRefsetMember(GB_EN_LANG_REFSET, PREFERRED))
				.addDescription(new Description("hjärtsjukdom").setLanguageCode("sv").setType("SYNONYM")
						.addLanguageRefsetMember("46011000052107", PREFERRED))
				.addRelationship(ISA, DISORDER));
		allConcepts.add(new Concept("100003").addDescription(new Description( "Cardiac arrest (disorder)"))
				.addRelationship(ISA, DISORDER));


		conceptService.batchCreate(allConcepts, MAIN);
		allConceptIds = allConcepts.stream().map(Concept::getId).collect(Collectors.toSet());
		branchCriteria = versionControlHelper.getBranchCriteria(MAIN);
	}

	@Test
	void testTermFilters() {
		String ecl = "< 64572001 |Disease|  {{ term = \"heart ath\"}}";
		assertEquals(Sets.newHashSet("100001"), strings(selectConceptIds(ecl)));

		ecl = "< 64572001 |Disease|  {{ term = \"ath heart\"}}";
		assertEquals(Sets.newHashSet("100001"), strings(selectConceptIds(ecl)));

		// two filters
		ecl = "< 64572001 |Disease|  {{ term = \"heart\"}} {{ term = \"ath\"}}";
		assertEquals(Sets.newHashSet("100001"), strings(selectConceptIds(ecl)));

		// search type is match by default
		ecl = "< 64572001 |Disease|  {{ term = match:\"heart\"}} {{ term = match:\"ath\"}}";
		assertEquals(Sets.newHashSet("100001"), strings(selectConceptIds(ecl)));

		// mixed search type
		ecl = "< 64572001 |Disease|  {{ term = \"heart\"}} {{ term = wild:\"*ease\"}}";
		assertEquals(Sets.newHashSet("100002"), strings(selectConceptIds(ecl)));

		// search term set
		ecl = "< 64572001 |Disease|  {{ term = (\"heart\" \"card\")}}";
		assertEquals(Sets.newHashSet("100001", "100002", "100003"), strings(selectConceptIds(ecl)));

		// mixed type in term set
		ecl = "< 64572001 |Disease|  {{ term = ( match:\"heart\" wild:\"Card*\")}}";
		assertEquals(Sets.newHashSet("100001", "100002", "100003"), strings(selectConceptIds(ecl)));

	}

	@Test
	void testLanguageFilters() {
		// sv language only
		String ecl = "< 64572001 |Disease|  {{ term = \"hjärt\", language = sv }}";
		assertEquals(Sets.newHashSet("100002"), strings(selectConceptIds(ecl)));

		// both in sv and en
		ecl = "< 64572001 |Disease|  {{ term = \"hjärt\", language = sv }} {{ term = \"heart\", language = en }}";
		assertEquals(Sets.newHashSet("100002"), strings(selectConceptIds(ecl)));


		// no results
		ecl = "< 64572001 |Disease|  {{ term = \"hjärt\", language = sv }} {{ term = \"Cardiac\", language = en }}";
		assertEquals(Sets.newHashSet(), strings(selectConceptIds(ecl)));
	}


	@Test
	void testDescriptionTypeFilters() {
		// type syn
		String ecl = "< 64572001 |Disease| {{ term = \"hjärt\", language = sv, type = syn }}";
		assertEquals(Sets.newHashSet("100002"), strings(selectConceptIds(ecl)));

		// type id for synonym
		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", language = sv, typeId = 900000000000013009 |synonym| }}";
		assertEquals(Sets.newHashSet("100002"), strings(selectConceptIds(ecl)));

		// no results for text definition
		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", language = sv, typeId = 900000000000550004 |Definition| }}";
		assertEquals(Sets.newHashSet(), strings(selectConceptIds(ecl)));

		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", language = sv, type = syn }} {{ term = \"heart\", language = en, type = fsn}}";
		assertEquals(Sets.newHashSet("100002"), strings(selectConceptIds(ecl)));

		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", language = sv, type = syn }} {{ term = \"heart\", language = en, type = syn}}";
		assertEquals(Sets.newHashSet(), strings(selectConceptIds(ecl)));

		// description type set
		ecl = "< 64572001 |Disease| {{ term = \"heart\", type = (syn fsn) }}";
		assertEquals(Sets.newHashSet("100001", "100002"), strings(selectConceptIds(ecl)));

		// type id
		ecl = "< 64572001 |Disease| {{ term = \"heart\", typeId = (900000000000013009 |Synonym| 900000000000003001 |Fully specified name|)}}";
		assertEquals(Sets.newHashSet("100001", "100002"), strings(selectConceptIds(ecl)));
	}


	@Test
	void testDialectFilters() {
		// dialect id
		String ecl = "< 64572001 |Disease| {{ dialectId = 46011000052107 }}";
		assertEquals(Sets.newHashSet("100002"), strings(selectConceptIds(ecl)));

		// with term and language
		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", language = sv, dialectId = 46011000052107 }}";
		assertEquals(Sets.newHashSet("100002"), strings(selectConceptIds(ecl)));

		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", dialectId = 46011000052107 }}";
		assertEquals(Sets.newHashSet("100002"), strings(selectConceptIds(ecl)));

		// dialect alias
		ecl = "< 64572001 |Disease|  {{ term = \"hjärt\", dialect = sv-se }}";
		assertEquals(Sets.newHashSet("100002"), strings(selectConceptIds(ecl)));

	}

	@Test
	void testAcceptabilityFilters() {
		// dialectId and acceptability keyword
//		String ecl = "< 64572001 |Disease| {{ term = \"hjärt\", type = syn, dialectId = 46011000052107(prefer) }}";
		String ecl = "< 64572001 |Disease| {{ term = \"Heart disease\", type = (syn fsn), dialect = (en-gb(accept) en-us(prefer)) }}";
		assertEquals(Sets.newHashSet("100002"), strings(selectConceptIds(ecl)));

		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", type = syn, dialectId = 46011000052107(accept) }}";
		assertEquals(Sets.newHashSet(), strings(selectConceptIds(ecl)));

		// dialectId and acceptability id
		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", type = syn, dialectId = 46011000052107(900000000000548007 |Preferred|) }}";
		assertEquals(Sets.newHashSet("100002"), strings(selectConceptIds(ecl)));

		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", type = syn, dialectId = 46011000052107(900000000000549004 |Acceptable|) }}";
		assertEquals(Sets.newHashSet(), strings(selectConceptIds(ecl)));

		// dialect alias and acceptability
		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", type = syn, dialect = sv-se(prefer) }}";
		assertEquals(Sets.newHashSet("100002"), strings(selectConceptIds(ecl)));

		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", type = syn, dialect = sv-se(accept) }}";
		assertEquals(Sets.newHashSet(), strings(selectConceptIds(ecl)));

		// dialect alias  and acceptability id
		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", type = syn, dialect = sv-se(900000000000548007 |Preferred|) }}";
		assertEquals(Sets.newHashSet("100002"), strings(selectConceptIds(ecl)));

		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", type = syn, dialect = sv-se(900000000000549004 |Acceptable|) }}";
		assertEquals(Sets.newHashSet(), strings(selectConceptIds(ecl)));

		// dialect set
		// en-gb accept or en-us preferred
		ecl = "< 64572001 |Disease| {{ term = \"Heart disease\", type = (syn fsn), dialect = (en-gb(accept) en-us(prefer)) }}";
		assertEquals(Sets.newHashSet("100002"), strings(selectConceptIds(ecl)));

		// en-gb or en-us preferred
		ecl = "< 64572001 |Disease| {{ term = \"Heart disease\", typeId = (900000000000013009 900000000000003001), dialect = (en-gb en-us) (prefer) }}";
		assertEquals(Sets.newHashSet("100002"), strings(selectConceptIds(ecl)));


		// multiple dialects
		// en-gb preferred and en-us preferred
		ecl = "< 64572001 |Disease| {{ term = \"Heart disease\", type = (syn fsn), dialect = en-gb (prefer), dialect = en-gb (prefer) }}";
		assertEquals(Sets.newHashSet("100002"), strings(selectConceptIds(ecl)));

		// en-gb acceptable and en-us preferred
		ecl = "< 64572001 |Disease| {{ term = \"Heart disease\", type = (syn fsn), dialect = en-gb (accept), dialect = en-gb (prefer) }}";
		assertEquals(Sets.newHashSet(), strings(selectConceptIds(ecl)));

		// multiple dialects with dialect set
		ecl = "< 64572001 |Disease| {{ term = \"Heart disease\", type = (syn fsn), dialect = en-nhs-clinical (accept), dialect = (en-us en-gb) (prefer) }}";
		assertEquals(Sets.newHashSet("100002"), strings(selectConceptIds(ecl)));

		// multiple dialect sets
		ecl = "< 64572001 |Disease| {{ term = \"Heart disease\", type = (syn fsn), dialect = (en-uk-ext en-nhs-clinical) (prefer), dialect = (en-us en-gb) (prefer) }}";
		assertEquals(Sets.newHashSet(), strings(selectConceptIds(ecl)));
	}

}

