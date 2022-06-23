package org.snomed.snowstorm.ecl;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static io.kaicode.elasticvc.domain.Branch.MAIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.snomed.snowstorm.core.data.domain.Concepts.REFSET_SAME_AS_ASSOCIATION;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ECLQueryServiceFilterTestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ECLQueryServiceFilterTest {

	@Autowired
	private ECLQueryService eclQueryService;

	@Autowired
	private QueryService queryService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ReferenceSetMemberService memberService;

	protected Collection<String> allConceptIds = new HashSet<>();
	protected BranchCriteria branchCriteria;

	private static final PageRequest PAGE_REQUEST = PageRequest.of(0, 10000);

	@BeforeEach
	void setup() {
		branchCriteria = versionControlHelper.getBranchCriteria(MAIN);
		allConceptIds = eclQueryService.selectConceptIds("*", branchCriteria, false, PAGE_REQUEST)
				.getContent().stream().map(Object::toString).collect(Collectors.toSet());
	}

	@Test
	void testTermFilters() {
		String ecl = "< 64572001 |Disease|  {{ term = \"heart ath\"}}";
		assertEquals(newHashSet("100001"), select(ecl));

		ecl = "( < 64572001 |Disease|  {{ term = \"heart ath\"}} )";
		assertEquals(newHashSet("100001"), select(ecl));

		ecl = "( < 64572001 |Disease|  {{ term = \"heart ath\", dialect = en-nz }} )";
		assertEquals(newHashSet(), select(ecl));

		assertEquals(newHashSet("100001"), select("* {{ D term = \"heart ath\" }}"));
		assertEquals(newHashSet("100002"), select("* {{ D term = \"Heart\", term = wild:\"* dis*\" }}"));

		ecl = "< 64572001 |Disease|  {{ D term != \"heart ath\"}}";
		assertEquals(newHashSet("100002", "100003", "698271000"), select(ecl));

		ecl = "< 64572001 |Disease| minus ( < 64572001 |Disease| {{ D term != \"heart ath\"}} )";
		assertEquals(newHashSet("100001"), select(ecl));

		ecl = "< 64572001 |Disease| minus < 64572001 |Disease| {{ D term != \"heart ath\"}}";
		assertEquals(newHashSet("100001"), select(ecl));

		ecl = "< 64572001 |Disease|  {{ D term = \"ath heart\"}}";
		assertEquals(newHashSet("100001"), select(ecl));

		// two filters
		ecl = "< 64572001 |Disease|  {{ term = \"heart\"}} {{ term = \"ath\"}}";
		assertEquals(newHashSet("100001"), select(ecl));

		// search type is match by default
		ecl = "< 64572001 |Disease|  {{ term = match:\"heart\"}} {{ term = match:\"ath\"}}";
		assertEquals(newHashSet("100001"), select(ecl));

		// mixed search type
		ecl = "< 64572001 |Disease|  {{ term = \"heart\"}} {{ term = wild:\"*ease\"}}";
		assertEquals(newHashSet("100002"), select(ecl));

		ecl = "< 64572001 |Disease|  {{ term = \"heart\"}} {{ term = wild:\"disea*\"}}";// Should not match because wildcard is a whole term match
		assertEquals(newHashSet(), select(ecl));

		// search term set, any one of the terms
		ecl = "< 64572001 |Disease|  {{ term = (\"heart\" \"card\")}}";
		assertEquals(newHashSet("100001", "100002", "100003", "698271000"), select(ecl));

		// mixed type in term set, any one of the terms
		ecl = "< 64572001 |Disease|  {{ term = ( match:\"heart\" wild:\"Card*\")}}";
		assertEquals(newHashSet("100001", "100002", "100003", "698271000"), select(ecl));

		ecl = "< 64572001 |Disease| {{ term = wild:\"card*pathy\"}}";
		assertEquals(newHashSet("698271000"), select(ecl));
	}

	@Test
	// ISTO-42
	void testNotOverEagerCaching() {
		assertEquals(4, select("< 64572001 |Disease| {{ D active = 1 }}").size());
		assertEquals(1, select("< 64572001 |Disease| {{ D active = 0 }}").size());
	}

	@Test
	void testLanguageFilters() {
		// sv language only
		String ecl = "< 64572001 |Disease|  {{ term = \"hjärt\", language = sv }}";
		assertEquals(newHashSet("100002"), select(ecl));

		ecl = "< 64572001 |Disease|  {{ term = \"hjärt\", language = en }}";
		assertEquals(Collections.emptySet(), select(ecl));

		// both in sv and en
		ecl = "< 64572001 |Disease|  {{ term = \"hjärt\", language = sv }} {{ term = \"heart\", language = en }}";
		assertEquals(newHashSet("100002"), select(ecl));

		// no results because heart is not in sv
		ecl = "< 64572001 |Disease|  {{ term = \"hjärt\", language = sv }} {{ term = \"heart\", language = sv }}";
		assertEquals(Collections.emptySet(), select(ecl));

		// no results because the two matching descriptions are on different concepts
		ecl = "< 64572001 |Disease|  {{ term = \"hjärt\", language = sv }} {{ term = \"Cardiac\", language = en }}";
		assertEquals(Collections.emptySet(), select(ecl));
	}

	@Test
	void testDescriptionTypeFilters() {
		// type syn
		String ecl = "< 64572001 |Disease| {{ term = \"hjärt\", language = sv, type = syn }}";
		assertEquals(newHashSet("100002"), select(ecl));

		// type id for synonym
		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", language = sv, typeId = 900000000000013009 |synonym| }}";
		assertEquals(newHashSet("100002"), select(ecl));

		// no results for text definition
		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", language = sv, typeId = 900000000000550004 |Definition| }}";
		assertEquals(newHashSet(), select(ecl));

		ecl = "< 64572001 |Disease| {{ D term = \"hjärt\", language = sv, type = syn }} {{ term = \"heart\", language = en, type = fsn}}";
		assertEquals(newHashSet("100002"), select(ecl));

		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", language = sv, type = syn }} {{ term = \"heart\", language = en, type = syn}}";
		assertEquals(newHashSet("100002"), select(ecl));

		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", language = sv, type = syn }} {{ term = \"heart\", language = en, type = (fsn syn)}}";
		assertEquals(newHashSet("100002"), select(ecl));

		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", language = sv, type = syn }} {{ term = \"disorder\", language = en, type = (fsn syn)}}";
		assertEquals(newHashSet("100002"), select(ecl));

		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", language = sv, type = syn }} {{ term = \"disorder\", language = en, type = (syn def)}}";
		assertEquals(newHashSet(), select(ecl));

		// description type set
		ecl = "< 64572001 |Disease| {{ term = \"heart\", type = (syn fsn) }}";
		assertEquals(newHashSet("100001", "100002"), select(ecl));

		// type id
		ecl = "< 64572001 |Disease| {{ term = \"heart\", typeId = (900000000000013009 |Synonym| 900000000000003001 |Fully specified name|)}}";
		assertEquals(newHashSet("100001", "100002"), select(ecl));

		ecl = "<  56265001 |Heart disease|  {{ term = \"hjärta\", language = sv, typeId =  900000000000013009 |synonym|  }}";
		assertEquals(newHashSet(), select(ecl));
	}

	@Test
	void testDialectFilters() {
		// dialect id
		String ecl = "< 64572001 |Disease| {{ dialectId = 46011000052107 }}";
		assertEquals(newHashSet("100002"), select(ecl));

		// with term and language
		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", language = sv, dialectId = 46011000052107 }}";
		assertEquals(newHashSet("100002"), select(ecl));

		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", dialectId = 46011000052107 }}";
		assertEquals(newHashSet("100002"), select(ecl));

		// dialect alias
		ecl = "< 64572001 |Disease|  {{ term = \"hjärt\", dialect = sv-se }}";
		assertEquals(newHashSet("100002"), select(ecl));

		// dialect alias with no content
		ecl = "< 64572001 |Disease| {{ dialect = en-au }}";
		assertEquals(newHashSet(), select(ecl));
	}

	@Test
	void testAcceptabilityFilters() {
		String ecl = "< 64572001 |Disease| {{ term = \"Heart disease\", type = syn, dialect = en-gb (accept), dialect = en-us (prefer) }}";
		assertEquals(newHashSet("100002"), select(ecl));

		// dialectId and acceptability keyword
		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", type = syn, dialectId = 46011000052107(prefer) }}";
		assertEquals(newHashSet("100002"), select(ecl));

		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", type = syn, dialectId = 46011000052107(accept) }}";
		assertEquals(newHashSet(), select(ecl));

		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", type = syn, dialectId = <46011000052107(accept) }}";
		assertEquals(newHashSet(), select(ecl));

		// dialectId and acceptability id
		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", type = syn, dialectId = 46011000052107(900000000000548007 |Preferred|) }}";
		assertEquals(newHashSet("100002"), select(ecl));

		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", type = syn, dialectId = 46011000052107(900000000000549004 |Acceptable|) }}";
		assertEquals(newHashSet(), select(ecl));

		// dialect alias and acceptability
		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", type = syn, dialect = sv-se(prefer) }}";
		assertEquals(newHashSet("100002"), select(ecl));

		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", type = syn, dialect = sv-se(accept) }}";
		assertEquals(newHashSet(), select(ecl));

		// dialect alias  and acceptability id
		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", type = syn, dialect = sv-se(900000000000548007 |Preferred|) }}";
		assertEquals(newHashSet("100002"), select(ecl));

		ecl = "< 64572001 |Disease| {{ term = \"hjärt\", type = syn, dialect = sv-se(900000000000549004 |Acceptable|) }}";
		assertEquals(newHashSet(), select(ecl));

		// dialect set
		// en-gb accept or en-us preferred
		ecl = "< 64572001 |Disease| {{ term = \"Heart disease\", type = (syn fsn), dialect = (en-gb(accept) en-us(prefer)) }}";
		assertEquals(newHashSet("100002"), select(ecl));

		// en-gb or en-us preferred
		ecl = "< 64572001 |Disease| {{ term = \"Heart disease\", typeId = (900000000000013009 900000000000003001), dialect = (en-gb en-us) (prefer) }}";
		assertEquals(newHashSet("100002"), select(ecl));


		// multiple dialects
		// en-gb preferred and en-us preferred
		ecl = "< 64572001 |Disease| {{ term = \"Heart disease\", type = (syn fsn), dialect = en-gb (prefer), dialect = en-us (prefer) }}";
		assertEquals(newHashSet("100002"), select(ecl));

		ecl = "< 64572001 |Disease| {{ term = \"Heart disease\", type = (syn fsn), dialect = en-gb (accept), dialect = en-us (prefer) }}";
		assertEquals(newHashSet("100002"), select(ecl));

		// multiple dialects with dialect set
		ecl = "< 64572001 |Disease| {{ term = \"Heart disease\", type = (syn fsn), dialect = en-nhs-clinical (accept), dialect = (en-us en-gb) (prefer) }}";
		assertEquals(newHashSet(), select(ecl));

		// en-gb preferred and preferred in (en-us or en-nhs-clinical)
		ecl = "< 64572001 |Disease| {{ term = \"Heart disease\", type = (syn fsn), dialect = en-gb (prefer), dialect = (en-us en-nhs-clinical) (prefer) }}";
		assertEquals(newHashSet("100002"), select(ecl));

		ecl = "< 64572001 |Disease| {{ term = \"Heart disease\", type = (syn), dialect = en-us (prefer), dialect = (en-gb en-nhs-clinical) (prefer) }}";
		assertEquals(newHashSet(), select(ecl));

		// multiple dialect sets
		// preferred in en-uk-ext or en-nhs-clinical AND preferred in en-us or en-gb
		ecl = "< 64572001 |Disease| {{ term = \"Heart disease\", type = (syn fsn), dialect = (en-uk-ext en-nhs-clinical) (prefer), dialect = (en-us en-gb) (prefer) }}";
		assertEquals(newHashSet(), select(ecl));

		ecl = "< 64572001 |Disease| {{ term = \"Heart disease\", type = (syn fsn), dialect = (en-us en-nhs-clinical) (prefer), dialect = (en-gb en-uk-ext) (prefer) }}";
		assertEquals(newHashSet("100002"), select(ecl));
	}

	@Test
	public void testDefinitionStatusFilter() {
		assertEquals(newHashSet("100001", "100002", "698271000"), select("< 64572001 |Disease| {{ C definitionStatus = primitive }}"));
		assertEquals(newHashSet("100001", "100002", "698271000"), select("< 64572001 |Disease| {{ C definitionStatus != defined }}"));
		assertEquals(newHashSet("100001", "100002", "698271000"), select("< 64572001 |Disease| {{ C definitionStatusId = 900000000000074008 |Primitive| }}"));
		assertEquals(newHashSet("100001", "100002", "698271000"), select("< 64572001 |Disease| {{ C definitionStatusId = << 900000000000074008 |Primitive| }}"));
		assertEquals(newHashSet("100003"), select("< 64572001 |Disease| {{ C definitionStatus = defined }}"));
		assertEquals(newHashSet("100003"), select("< 64572001 |Disease| {{ C definitionStatus != primitive }}"));
		assertEquals(newHashSet("100003"), select("< 64572001 |Disease| {{ C definitionStatusId = 900000000000073002 |Defined| }}"));
		assertEquals(newHashSet("100003"), select("< 64572001 |Disease| {{ C definitionStatusId = << 900000000000073002 |Defined| }}"));
	}

	@Test
	public void testModuleFilter() {
		assertEquals(newHashSet("100001", "100002", "698271000"), select("< 64572001 |Disease| {{ C moduleId = 900000000000207008 }}"));
		assertEquals(newHashSet("100001", "100002", "698271000"), select("< 64572001 |Disease| {{ C moduleId = << 900000000000207008 }}"));
		assertEquals(newHashSet(), select("< 64572001 |Disease| {{ C moduleId = < 900000000000207008 }}"));
		assertEquals(newHashSet("100003"), select("< 64572001 |Disease| {{ C moduleId = 25000001 }}"));
		assertEquals(newHashSet("100003"), select("< 64572001 |Disease| {{ C moduleId = << 25000001 }}"));
		assertEquals(newHashSet("100003"), select("< 64572001 |Disease| {{ C moduleId != 900000000000207008 }}"));
	}

	@Test
	public void testEffectiveTimeFilter() {
		assertEquals(newHashSet("64572001"), select("<< 64572001 |Disease| {{ C effectiveTime = \"20200131\" }}"));
		assertEquals(newHashSet("100001", "100002", "100003", "698271000"), select("<< 64572001 |Disease| {{ C effectiveTime != \"20200131\" }}"));
		assertEquals(newHashSet("100001", "100002", "100003", "698271000"), select("<< 64572001 |Disease| {{ C effectiveTime > \"20200131\" }}"));
		assertEquals(newHashSet("100003"), select("<< 64572001 |Disease| {{ C effectiveTime > \"20200131\" }} {{ C effectiveTime = \"20220131\" }}"));
		assertEquals(newHashSet("100001", "100002", "698271000"), select("<< 64572001 |Disease| {{ C effectiveTime > \"20200131\" }} {{ C effectiveTime < \"20220131\" }}"));
	}

	@Test
	public void testMemberActiveFilter() {
		assertEquals(newHashSet("100001", "100002", "200001", "200002"), select("^ 816080008"));
		assertEquals(newHashSet("100001", "100002"), select("^ 816080008 {{ C active = 1 }}"));
		assertEquals(newHashSet("100001", "100002"), select("^ 816080008 {{ C active = true }}"));
		assertEquals(newHashSet("200001", "200002"), select("^ 816080008 {{ C active = 0 }}"));
		assertEquals(newHashSet("200001", "200002"), select("^ 816080008 {{ C active = false }}"));

		String ecl = "^ 816080008";
		assertEquals(newHashSet("100001", "100002"), select(queryService.createQueryBuilder(false)
				.ecl(ecl)
				.activeFilter(true)
		));
	}

	@Test
	public void testMemberFieldFilter() {
		assertEquals(newHashSet("427603009"), select("^ 447562003 |ICD-10 complex map reference set| {{ M mapTarget = \"J45.9\" }}"));
		assertEquals(newHashSet("427603009", "708094006"), select("^ 447562003 |ICD-10 complex map reference set| {{ M mapTarget = \"J45\" }}"));
		assertEquals(newHashSet(), select("^ 447562003 |ICD-10 complex map reference set| {{ M mapTarget = wild:\"J45\" }}"));
		assertEquals(newHashSet("427603009", "708094006"), select("^ 447562003 |ICD-10 complex map reference set| {{ M mapTarget = wild:\"J45*\" }}"));
		assertEquals(newHashSet("427603009"), select("^ 447562003 |ICD-10 complex map reference set| {{ M mapGroup = #2, mapPriority = #1, mapTarget = \"J45.9\" }}"));
		assertEquals(newHashSet("427603009"), select("^ 447562003 |ICD-10 complex map reference set| {{ M mapGroup = #2, mapPriority = #1 }}"));
		assertEquals(newHashSet("708094006"), select("^ 447562003 |ICD-10 complex map reference set| {{ M mapGroup = #1, mapPriority = #1 }}"));
		assertEquals(newHashSet("427603009", "708094006"), select("^ 447562003 |ICD-10 complex map reference set| {{ M active = 1 }}"));
		assertEquals(newHashSet("698940002"), select("^ 447562003 |ICD-10 complex map reference set| {{ M active = false }}"));

		// Find referencedComponentId of refset members that refer to inactive or missing concepts.
		assertEquals(newArrayList("101010101001"), selectList("^ [referencedComponentId] 447562003 |ICD-10 complex map reference set| {{ M referencedComponentId != * {{ C active = true }} }}"));
	}

	@Test
	public void testMemberSelectFields() {
		assertEquals(newArrayList("200001", "200002"), selectList("^ (< 900000000000522004 |historical association|)"));
		assertEquals(newArrayList("200001", "200002", "200002"), selectList("^ [referencedComponentId] (< 900000000000522004 |historical association|)"));
	}

	@Test
	public void historySupplement() {
		Page<ReferenceSetMember> members = memberService.findMembers(MAIN, new MemberSearchRequest().referenceSet(REFSET_SAME_AS_ASSOCIATION), PageRequest.of(0, 10));
		for (ReferenceSetMember member : members) {
			System.out.println(member);
		}
		assertEquals(newHashSet("100001", "200001"), select("100001 {{ + HISTORY-MIN }}"));
		assertEquals(newHashSet("100001", "200001"), select("( 100001 {{ + HISTORY-MIN }} )"));
		assertEquals(newHashSet("100001", "200001", "200002"), select("100001 {{ + HISTORY-MAX }}"));
		assertEquals(newHashSet("100001", "200001", "200002"), select("100001 {{ + HISTORY }}"));
	}

	protected Set<String> select(String ecl) {
		return queryService.eclSearch(ecl, false, MAIN, PAGE_REQUEST)
				.getContent().stream().map(ConceptMini::getConceptId).collect(Collectors.toSet());
	}

	protected List<String> selectList(String ecl) {
		Page<ConceptMini> conceptMinis = queryService.eclSearch(ecl, false, MAIN, PAGE_REQUEST);
		return conceptMinis
				.getContent().stream().map(mini -> mini.getConceptId() != null ? mini.getConceptId() :
						(String) mini.getExtraField(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID))
				.sorted().collect(Collectors.toList());
	}

	@NotNull
	private Set<String> select(QueryService.ConceptQueryBuilder queryBuilder) {
		return queryService.search(queryBuilder, MAIN, PAGE_REQUEST).getContent().stream().map(ConceptMini::getConceptId).collect(Collectors.toSet());
	}

}
