package org.snomed.snowstorm.ecl;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.collect.Sets.newHashSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.snomed.snowstorm.TestConcepts.DISORDER;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class ECLQueryServiceFilterTest extends AbstractTest {

	private static final String MODULE_A = "25000001";
	public static final String IPS_REFSET = "816080008";

	@Autowired
	protected ECLQueryService eclQueryService;

	@Autowired
	protected ConceptService conceptService;

	@Autowired
	protected VersionControlHelper versionControlHelper;

	@Autowired
	protected CodeSystemService codeSystemService;

	@Autowired
	protected ReferenceSetMemberService memberService;


	protected Collection<String> allConceptIds = new HashSet<>();
	protected BranchCriteria branchCriteria;

	@BeforeEach
	void setup() throws ServiceException {
		List<Concept> allConcepts = new ArrayList<>();

		allConcepts.add(new Concept(DISORDER).addDescription(new Description("Disease(disorder)")).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.addAll(newMetadataConcepts(CORE_MODULE, MODULE_A, PRIMITIVE, DEFINED, PREFERRED, ACCEPTABLE, FSN, SYNONYM, TEXT_DEFINITION, "46011000052107"));
		allConcepts.add(new Concept(REFSET_HISTORICAL_ASSOCIATION).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(REFSET_SAME_AS_ASSOCIATION).addRelationship(new Relationship(ISA, REFSET_HISTORICAL_ASSOCIATION)));
		allConcepts.add(new Concept(REFSET_SIMILAR_TO_ASSOCIATION).addRelationship(new Relationship(ISA, REFSET_HISTORICAL_ASSOCIATION)));
		createConceptsAndVersionCodeSystem(allConcepts, 20200131);

		allConcepts.add(new Concept("100001").addDescription(new Description("Athlete's heart (disorder)"))
				.addRelationship(ISA, DISORDER));
		allConcepts.add(new Concept("100002")
				.addDescription(new Description( "Heart disease (disorder)").setLanguageCode("en").setType("FSN")
						.addLanguageRefsetMember(US_EN_LANG_REFSET, PREFERRED)
						.addLanguageRefsetMember(GB_EN_LANG_REFSET, PREFERRED))
				.addDescription(new Description("Heart disease").setLanguageCode("en").setType("SYNONYM")
						.addLanguageRefsetMember(US_EN_LANG_REFSET, PREFERRED)
						.addLanguageRefsetMember(GB_EN_LANG_REFSET, ACCEPTABLE))
				.addDescription(new Description("hjärtsjukdom").setLanguageCode("sv").setType("SYNONYM")
						.addLanguageRefsetMember("46011000052107", PREFERRED))
				.addRelationship(ISA, DISORDER));
		createConceptsAndVersionCodeSystem(allConcepts, 20210131);
		allConcepts.add(new Concept("100003").setModuleId(MODULE_A).setDefinitionStatusId(DEFINED).addDescription(new Description( "Cardiac arrest (disorder)"))
				.addRelationship(ISA, DISORDER));
		// IPS refset
		allConcepts.add(new Concept(IPS_REFSET).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		// Two inactive concepts
		allConcepts.add(new Concept("200001").setActive(false).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept("200002").setActive(false).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT)));
		createConceptsAndVersionCodeSystem(allConcepts, 20220131);

		// Some active and some inactive concepts are active members of the IPS refset.
		memberService.createMembers(MAIN, createSimpleRefsetMembers(IPS_REFSET, "100001", "100002", "200001", "200002"));

		// Historical associations
		memberService.createMembers(MAIN, Set.of(
				new ReferenceSetMember(CORE_MODULE, REFSET_SAME_AS_ASSOCIATION, "200001")
						.setAdditionalField(ReferenceSetMember.AssociationFields.TARGET_COMP_ID, "100001"),
				new ReferenceSetMember(CORE_MODULE, REFSET_SIMILAR_TO_ASSOCIATION, "200002")
						.setAdditionalField(ReferenceSetMember.AssociationFields.TARGET_COMP_ID, "100001")
		));

		allConceptIds = allConcepts.stream().map(Concept::getId).collect(Collectors.toSet());
		branchCriteria = versionControlHelper.getBranchCriteria(MAIN);
	}

	@NotNull
	private Set<ReferenceSetMember> createSimpleRefsetMembers(String refsetId, String... referencedComponentIds) {
		return Arrays.stream(referencedComponentIds).map(id -> new ReferenceSetMember(CORE_MODULE, refsetId, id)).collect(Collectors.toSet());
	}

	private void createConceptsAndVersionCodeSystem(List<Concept> allConcepts, int effectiveDate) throws ServiceException {
		conceptService.batchCreate(allConcepts, MAIN);
		allConceptIds = CollectionUtils.union(allConceptIds, allConcepts.stream().map(Concept::getId).collect(Collectors.toSet()));
		allConcepts.clear();
		CodeSystem codeSystem = codeSystemService.findByBranchPath("MAIN").orElse(null);
		if (codeSystem == null) {
			codeSystem = new CodeSystem("SNOMEDCT", "MAIN");
			codeSystemService.createCodeSystem(codeSystem);
		}
		codeSystemService.createVersion(codeSystemService.find("SNOMEDCT"), effectiveDate, "");
	}

	private List<Concept> newMetadataConcepts(String... conceptIds) {
		return Arrays.stream(conceptIds).map(id -> new Concept(id).addRelationship(new Relationship(ISA, SNOMEDCT_ROOT))).collect(Collectors.toList());
	}

	@Test
	void testTermFilters() {
		String ecl = "< 64572001 |Disease|  {{ term = \"heart ath\"}}";
		assertEquals(newHashSet("100001"), select(ecl));

		ecl = "< 64572001 |Disease|  {{ D term != \"heart ath\"}}";
		assertEquals(newHashSet("100002", "100003"), select(ecl));

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

		// search term set
		ecl = "< 64572001 |Disease|  {{ term = (\"heart\" \"card\")}}";
		assertEquals(newHashSet("100001", "100002", "100003"), select(ecl));

		// mixed type in term set
		ecl = "< 64572001 |Disease|  {{ term = ( match:\"heart\" wild:\"Card*\")}}";
		assertEquals(newHashSet("100001", "100002", "100003"), select(ecl));
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
		assertEquals(newHashSet("100001", "100002"), select("< 64572001 |Disease| {{ C definitionStatus = primitive }}"));
		assertEquals(newHashSet("100001", "100002"), select("< 64572001 |Disease| {{ C definitionStatus != defined }}"));
		assertEquals(newHashSet("100001", "100002"), select("< 64572001 |Disease| {{ C definitionStatusId = 900000000000074008 |Primitive| }}"));
		assertEquals(newHashSet("100001", "100002"), select("< 64572001 |Disease| {{ C definitionStatusId = << 900000000000074008 |Primitive| }}"));
		assertEquals(newHashSet("100003"), select("< 64572001 |Disease| {{ C definitionStatus = defined }}"));
		assertEquals(newHashSet("100003"), select("< 64572001 |Disease| {{ C definitionStatus != primitive }}"));
		assertEquals(newHashSet("100003"), select("< 64572001 |Disease| {{ C definitionStatusId = 900000000000073002 |Defined| }}"));
		assertEquals(newHashSet("100003"), select("< 64572001 |Disease| {{ C definitionStatusId = << 900000000000073002 |Defined| }}"));
	}

	@Test
	public void testModuleFilter() {
		assertEquals(newHashSet("100001", "100002"), select("< 64572001 |Disease| {{ C moduleId = 900000000000207008 }}"));
		assertEquals(newHashSet("100001", "100002"), select("< 64572001 |Disease| {{ C moduleId = << 900000000000207008 }}"));
		assertEquals(newHashSet(), select("< 64572001 |Disease| {{ C moduleId = < 900000000000207008 }}"));
		assertEquals(newHashSet("100003"), select("< 64572001 |Disease| {{ C moduleId = 25000001 }}"));
		assertEquals(newHashSet("100003"), select("< 64572001 |Disease| {{ C moduleId = << 25000001 }}"));
		assertEquals(newHashSet("100003"), select("< 64572001 |Disease| {{ C moduleId != 900000000000207008 }}"));
	}

	@Test
	public void testEffectiveTimeFilter() {
		assertEquals(newHashSet("64572001"), select("<< 64572001 |Disease| {{ C effectiveTime = \"20200131\" }}"));
		assertEquals(newHashSet("100001", "100002", "100003"), select("<< 64572001 |Disease| {{ C effectiveTime != \"20200131\" }}"));
		assertEquals(newHashSet("100001", "100002", "100003"), select("<< 64572001 |Disease| {{ C effectiveTime > \"20200131\" }}"));
		assertEquals(newHashSet("100003"), select("<< 64572001 |Disease| {{ C effectiveTime > \"20200131\" }} {{ C effectiveTime = \"20220131\" }}"));
		assertEquals(newHashSet("100001", "100002"), select("<< 64572001 |Disease| {{ C effectiveTime > \"20200131\" }} {{ C effectiveTime < \"20220131\" }}"));
	}

	@Test
	public void testActiveFilter() {
		assertEquals(newHashSet("100001", "100002", "200001", "200002"), select("^ 816080008"));
		assertEquals(newHashSet("100001", "100002"), select("^ 816080008 {{ C active = 1 }}"));
		assertEquals(newHashSet("100001", "100002"), select("^ 816080008 {{ C active = true }}"));
		assertEquals(newHashSet("200001", "200002"), select("^ 816080008 {{ C active = 0 }}"));
		assertEquals(newHashSet("200001", "200002"), select("^ 816080008 {{ C active = false }}"));
	}

	@Test
	public void historySupplement() {
		Page<ReferenceSetMember> members = memberService.findMembers(MAIN, new MemberSearchRequest().referenceSet(REFSET_SAME_AS_ASSOCIATION), PageRequest.of(0, 10));
		for (ReferenceSetMember member : members) {
			System.out.println(member);
		}
		assertEquals(newHashSet("100001", "200001"), select("100001 {{ + HISTORY-MIN }}"));
		assertEquals(newHashSet("100001", "200001", "200002"), select("100001 {{ + HISTORY-MAX }}"));
	}

	protected Set<String> select(String ecl) {
		return eclQueryService.selectConceptIds(ecl, branchCriteria, MAIN, false, PageRequest.of(0, 10000))
				.getContent().stream().map(Object::toString).collect(Collectors.toSet());
	}

}
