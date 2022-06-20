package org.snomed.snowstorm.fhir.services;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.IdType;
import org.jetbrains.annotations.NotNull;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.fhir.config.FHIRConceptMapImplicitConfig;
import org.snomed.snowstorm.fhir.domain.*;
import org.snomed.snowstorm.fhir.pojo.FHIRCodeSystemVersionParams;
import org.snomed.snowstorm.fhir.pojo.FHIRSnomedConceptMapConfig;
import org.snomed.snowstorm.fhir.repositories.FHIRConceptMapRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.snomed.snowstorm.fhir.config.FHIRConstants.SNOMED_URI;

@Service
public class FHIRConceptMapService {

	public static final String WHOLE_SYSTEM_VALUE_SET_URI_POSTFIX = "?fhir_vs";

	private static final PageRequest PAGE_OF_ONE_THOUSAND = PageRequest.of(0, 1_000);

	@Autowired
	private FHIRConceptMapRepository conceptMapRepository;

	@Autowired
	private ElasticsearchRestTemplate elasticsearchTemplate;

	@Autowired
	private FHIRCodeSystemService fhirCodeSystemService;

	@Autowired
	private ReferenceSetMemberService snomedRefsetMemberService;

	@Autowired
	private ConceptService snomedConceptService;

	@Autowired
	private FHIRConceptMapImplicitConfig implicitMapConfig;

	// Implicit ConceptMaps - format http://snomed.info/sct[/(module)[/version/(version)]]?fhir_cm=(sctid)
	private List<FHIRSnomedConceptMapConfig> snomedMaps;

	@PostConstruct
	public void init() {
		snomedMaps = implicitMapConfig.getImplicitMaps();
	}

	public List<FHIRConceptMap> findAll() {
		// Load first 1000 until we can figure out pagination
		List<FHIRConceptMap> maps = getSnomedMaps();
		PageRequest pageRequest = PageRequest.of(0, PAGE_OF_ONE_THOUSAND.getPageSize() - maps.size());
		maps.addAll(conceptMapRepository.findAll(pageRequest).getContent());
		return maps;
	}

	private List<FHIRConceptMap> getSnomedMaps() {
		List<FHIRConceptMap> generatedMaps = new ArrayList<>();
		for (FHIRSnomedConceptMapConfig snomedMap : snomedMaps) {
			String refsetId = snomedMap.getReferenceSetId();

			FHIRConceptMap map = new FHIRConceptMap();
			map.setId("snomed_implicit_map_" + refsetId);
			map.setUrl("http://snomed.info/sct?fhir_cm=" + refsetId);
			map.setName(snomedMap.getName());
			map.setSourceUri(snomedMap.getSourceSystem() + WHOLE_SYSTEM_VALUE_SET_URI_POSTFIX);
			map.setTargetUri(snomedMap.getTargetSystem() + WHOLE_SYSTEM_VALUE_SET_URI_POSTFIX);

			// For internal use
			map.setImplicitSnomedMap(true);
			map.setSnomedRefsetId(refsetId);
			map.setSnomedRefsetEquivalence(snomedMap.getRefsetEquivalence());

			generatedMaps.add(map);
		}
		return generatedMaps;
	}

	Collection<FHIRConceptMap> findMaps(String url, Coding coding, String targetSystem, String sourceValueSet, String targetValueSet) {
		BoolQueryBuilder query = boolQuery();
		List<Predicate<FHIRConceptMap>> snomedPredicates = new ArrayList<>();
		if (url != null) {
			if (FHIRHelper.isSnomedUri(url) && url.contains("?")) {
				url = SNOMED_URI + url.substring(url.indexOf("?"));
			}
			query.must(termQuery(FHIRConceptMap.Fields.URL, url));
			String finalUrl = url;
			snomedPredicates.add(map -> finalUrl.equals(map.getUrl()));
		}
		if (coding != null) {
			query.must(termQuery(FHIRConceptMap.Fields.GROUP_SOURCE, coding.getSystem()));
			snomedPredicates.add(map -> (map.getSourceUri() == null || map.getSourceUri().startsWith(coding.getSystem().replace("/xsct", "/sct"))));
		}
		if (targetSystem != null) {
			query.must(termQuery(FHIRConceptMap.Fields.GROUP_TARGET, targetSystem));
			snomedPredicates.add(map -> map.getTargetUri().equals(targetSystem + WHOLE_SYSTEM_VALUE_SET_URI_POSTFIX));
		}
		if (sourceValueSet != null) {
			query.must(boolQuery()
					// Map either has no source (value set) or it matches the param
					.should(boolQuery().mustNot(existsQuery(FHIRConceptMap.Fields.SOURCE)))
					.should(termQuery(FHIRConceptMap.Fields.SOURCE, sourceValueSet))
			);
			snomedPredicates.add(map -> map.getSourceUri().equals(sourceValueSet));
		}
		if (targetValueSet != null) {
			query.must(boolQuery()
					// Map either has no target (value set) or it matches the param
					.should(boolQuery().mustNot(existsQuery(FHIRConceptMap.Fields.TARGET)))
					.should(termQuery(FHIRConceptMap.Fields.TARGET, targetValueSet))
			);
			snomedPredicates.add(map -> map.getTargetUri().equals(targetValueSet));
		}
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(query)
				.withPageable(PageRequest.of(0, 100));

		// Grab maps from store
		List<FHIRConceptMap> maps = new ArrayList<>(searchForList(queryBuilder, FHIRConceptMap.class));

		// Grab generated snomed maps
		maps.addAll(getSnomedMaps().stream()
				.filter(map -> snomedPredicates.stream().allMatch(predicate -> predicate.test(map))).collect(Collectors.toList()));

		return maps;
	}

	public Collection<FHIRMapElement> findMapElements(FHIRConceptMap map, Coding coding, String targetSystem, List<LanguageDialect> languageDialects) {
		if (map.isImplicitSnomedMap()) {
			return generateImplicitSnomedMapElements(map, coding, languageDialects);
		}

		List<FHIRConceptMapGroup> groups = map.getGroup().stream()
				.filter(group -> group.getSource().equals(coding.getSystem()))
				.filter(group -> group.getTarget().equals(targetSystem))
				.collect(Collectors.toList());
		BoolQueryBuilder query = boolQuery()
				.must(termsQuery(FHIRMapElement.Fields.GROUP_ID, groups.stream().map(FHIRConceptMapGroup::getGroupId).collect(Collectors.toList())))
				.must(termQuery(FHIRMapElement.Fields.CODE, coding.getCode()));
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(query)
				.withPageable(PAGE_OF_ONE_THOUSAND);
		return searchForList(queryBuilder, FHIRMapElement.class);
	}

	private Collection<FHIRMapElement> generateImplicitSnomedMapElements(FHIRConceptMap map, Coding coding, List<LanguageDialect> languageDialects) {
		FHIRCodeSystemVersionParams versionParams = FHIRHelper.getCodeSystemVersionParams((IdType) null, null, null, coding);
		FHIRCodeSystemVersion snomedVersion = fhirCodeSystemService.findCodeSystemVersionOrThrow(versionParams);

		map.setUrl(map.getUrl().replace(SNOMED_URI + "?", snomedVersion.getVersion() + "?"));

		MemberSearchRequest memberSearchRequest = new MemberSearchRequest()
				.referenceSet(map.getSnomedRefsetId());
		if (map.getTargetUri().startsWith(SNOMED_URI) && !map.getSourceUri().startsWith(SNOMED_URI)) {
			memberSearchRequest.mapTarget(coding.getCode());
		} else {
			memberSearchRequest.referencedComponentId(coding.getCode());
		}
		Page<ReferenceSetMember> members = snomedRefsetMemberService.findMembers(snomedVersion.getSnomedBranch(), memberSearchRequest, PAGE_OF_ONE_THOUSAND);

		// Collect map targets for filling PTs
		Map<String, List<FHIRMapTarget>> conceptMapTargets = new HashMap<>();

		List<FHIRMapElement> generatedElements = members.stream()
				.map(referenceSetMember -> {
					String targetCode = referenceSetMember.getAdditionalField(ReferenceSetMember.AssociationFields.TARGET_COMP_ID);
					if (targetCode == null) {
						targetCode = referenceSetMember.getAdditionalField(ReferenceSetMember.AssociationFields.MAP_TARGET);
					}
					if (targetCode == null) {
						targetCode = referenceSetMember.getAdditionalField("valueId");
					}
					String equivalence = map.getSnomedRefsetEquivalence();
					FHIRMapTarget mapTarget = new FHIRMapTarget(targetCode, equivalence, null);
					conceptMapTargets.computeIfAbsent(targetCode, key -> new ArrayList<>()).add(mapTarget);
					return new FHIRMapElement()
							.setCode(coding.getCode())
							.setTarget(Collections.singletonList(mapTarget));
				})
				.filter(element -> element.getTarget().get(0).getCode() != null)
				.collect(Collectors.toList());

		// Grap PTs
		Map<String, ConceptMini> conceptMiniMap = snomedConceptService.findConceptMinis(snomedVersion.getSnomedBranch(), conceptMapTargets.keySet(), languageDialects)
				.getResultsMap();
		for (Map.Entry<String, ConceptMini> entry : conceptMiniMap.entrySet()) {
			conceptMapTargets.get(entry.getKey()).forEach(mapTarget -> mapTarget.setDisplay(entry.getValue().getPt().getTerm()));
		}

		return generatedElements;
	}

	@NotNull
	private <T> List<T> searchForList(NativeSearchQueryBuilder queryBuilder, Class<T> clazz) {
		return elasticsearchTemplate.search(queryBuilder.build(), clazz).stream()
				.map(SearchHit::getContent).collect(Collectors.toList());
	}
}
