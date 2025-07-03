package org.snomed.snowstorm.fhir.services;


import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.OperationOutcome;
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
import org.snomed.snowstorm.fhir.repositories.FHIRMapElementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Comparator.*;
import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.*;
import static io.kaicode.elasticvc.helper.QueryHelper.*;
import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;
import static org.snomed.snowstorm.fhir.config.FHIRConstants.SNOMED_URI;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.exception;

@Service
public class FHIRConceptMapService {

	public static final String WHOLE_SYSTEM_VALUE_SET_URI_POSTFIX = "?fhir_vs";

	private static final PageRequest PAGE_OF_ONE_THOUSAND = PageRequest.of(0, 1_000);

	@Autowired
	private FHIRConceptMapRepository conceptMapRepository;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private FHIRMapElementRepository mapElementRepository;

	@Autowired
	private FHIRCodeSystemService fhirCodeSystemService;

	@Autowired
	private ReferenceSetMemberService snomedRefsetMemberService;

	@Autowired
	private ConceptService snomedConceptService;

	@Autowired
	private FHIRConceptMapImplicitConfig implicitMapConfig;

	@Autowired
	private FHIRConceptService conceptService;

	@Autowired
	private FHIRSnomedModelTermCache snomedModelTermCache;

	// Implicit ConceptMaps - format http://snomed.info/sct[/(module)[/version/(version)]]?fhir_cm=(sctid)
	private List<FHIRSnomedConceptMapConfig> snomedMaps;

	// Map of SNOMED CT map correlation concepts to FHIR equivalence codes - http://hl7.org/fhir/concept-map-equivalence
	private Map<String, Enumerations.ConceptMapEquivalence> snomedCorrelationToFhirEquivalenceMap;

	@PostConstruct
	public void init() {
		snomedMaps = implicitMapConfig.getImplicitMaps();
		snomedCorrelationToFhirEquivalenceMap = implicitMapConfig.getSnomedCorrelationToFhirEquivalenceMap();
	}

	public FHIRConceptMap createOrUpdate(FHIRConceptMap conceptMap) {
		if (conceptMap.getUrl().contains("?fhir_cm")) {
			throw exception("ConceptMap url must not contain 'fhir_cm', this is reserved for implicit concept maps.", OperationOutcome.IssueType.INVARIANT, 400);
		}

		if (conceptMap.getVersion() == null) {
			conceptMap.setVersion("0");
		}

		// Delete existing maps with the same URL and version
		conceptMapRepository.findAllByUrl(conceptMap.getUrl())
				.stream().filter(map -> conceptMap.getVersion().equals(map.getVersion()))
						.forEach(map -> {
							// Delete map group elements
							for (FHIRConceptMapGroup mapGroup : map.getGroup()) {
								if (mapGroup.getElement() != null) {
									mapElementRepository.deleteAll(mapGroup.getElement());
								}
							}
							conceptMapRepository.delete(map);
						});

		// Save concept map and groups
		FHIRConceptMap saved = conceptMapRepository.save(conceptMap);
		for (FHIRConceptMapGroup mapGroup : conceptMap.getGroup()) {
			// Save elements within each group
			mapElementRepository.saveAll(mapGroup.getElement());
		}
		return saved;
	}

	public FHIRConceptMap findByIdWithGroups(String idPart) {
		Optional<FHIRConceptMap> conceptMap = conceptMapRepository.findById(idPart);
		if (conceptMap.isPresent()) {
			FHIRConceptMap map = conceptMap.get();
			for (FHIRConceptMapGroup group : orEmpty(map.getGroup())) {
				List<FHIRMapElement> elements = mapElementRepository.findAllByGroupId(group.getGroupId());
				group.setElement(elements);
			}
			return map;
		}

		return null;
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
		BoolQuery.Builder query = bool();
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
			query.must(bool(b -> b
					// Map either has no source (value set) or it matches the param
					.should(bool(bq -> bq.mustNot(existsQuery(FHIRConceptMap.Fields.SOURCE))))
					.should(termQuery(FHIRConceptMap.Fields.SOURCE, sourceValueSet))
			));
			snomedPredicates.add(map -> map.getSourceUri().equals(sourceValueSet));
		}
		if (targetValueSet != null) {
			query.must(bool(b -> b
					// Map either has no target (value set) or it matches the param
					.should(bool(bq -> bq.mustNot(existsQuery(FHIRConceptMap.Fields.TARGET))))
					.should(termQuery(FHIRConceptMap.Fields.TARGET, targetValueSet))
			));
			snomedPredicates.add(map -> map.getTargetUri().equals(targetValueSet));
		}
		NativeQueryBuilder queryBuilder = new NativeQueryBuilder()
				.withQuery(query.build()._toQuery())
				.withPageable(PageRequest.of(0, 100));

		// Grab maps from store
		List<FHIRConceptMap> maps = new ArrayList<>(searchForList(queryBuilder, FHIRConceptMap.class));

		// Grab generated snomed maps
		maps.addAll(getSnomedMaps().stream()
				.filter(map -> snomedPredicates.stream().allMatch(predicate -> predicate.test(map))).toList());

		return maps;
	}

	public Collection<FHIRMapElement> findMapElements(FHIRConceptMap map, Coding coding, String targetSystem, List<LanguageDialect> languageDialects) {
		if (map.isImplicitSnomedMap()) {
			return generateImplicitSnomedMapElements(map, coding, targetSystem, languageDialects);
		}

		List<FHIRConceptMapGroup> groups = map.getGroup().stream()
				.filter(group -> group.getSource().equals(coding.getSystem()))
				.filter(group -> targetSystem == null || group.getTarget().equals(targetSystem))
				.toList();
		BoolQuery.Builder query = bool()
				.must(termsQuery(FHIRMapElement.Fields.GROUP_ID, groups.stream().map(FHIRConceptMapGroup::getGroupId).collect(Collectors.toList())))
				.must(termQuery(FHIRMapElement.Fields.CODE, coding.getCode()));
		NativeQueryBuilder queryBuilder = new NativeQueryBuilder()
				.withQuery(query.build()._toQuery())
				.withPageable(PAGE_OF_ONE_THOUSAND);
		return searchForList(queryBuilder, FHIRMapElement.class);
	}

	private Collection<FHIRMapElement> generateImplicitSnomedMapElements(FHIRConceptMap map, Coding coding, String targetSystem, List<LanguageDialect> languageDialects) {
		FHIRCodeSystemVersionParams versionParams = FHIRHelper.getCodeSystemVersionParams((IdType) null, null, null, coding);
		FHIRCodeSystemVersion snomedVersion = fhirCodeSystemService.findCodeSystemVersionOrThrow(versionParams);

		map.setUrl(map.getUrl().replace(SNOMED_URI + "?", snomedVersion.getVersion() + "?"));

		MemberSearchRequest memberSearchRequest = new MemberSearchRequest()
				.referenceSet(map.getSnomedRefsetId())
				.active(true);
		boolean hasSnomedSource = FHIRHelper.isSnomedUri(map.getSourceUri());
		boolean hasSnomedTarget = FHIRHelper.isSnomedUri(map.getTargetUri());
		if (!hasSnomedSource) {
			memberSearchRequest.additionalField(ReferenceSetMember.AssociationFields.MAP_TARGET, coding.getCode());
		} else {
			memberSearchRequest.referencedComponentId(coding.getCode());
		}
		Page<ReferenceSetMember> members = snomedRefsetMemberService.findMembers(snomedVersion.getSnomedBranch(), memberSearchRequest, PAGE_OF_ONE_THOUSAND);

		// Collect map targets for filling terms
		Map<String, List<FHIRMapTarget>> mapTargetsByCode = new HashMap<>();

		Comparator<ReferenceSetMember> mapComparator =
				comparing(ReferenceSetMember::getMapGroup, Comparator.nullsFirst(naturalOrder()))
						.thenComparing(ReferenceSetMember::getMapPriority, Comparator.nullsFirst(naturalOrder()));

		List<FHIRMapElement> generatedElements = members.stream()
				.sorted(mapComparator)
				.map(referenceSetMember -> {
					String targetCode = getTargetCode(hasSnomedSource, hasSnomedTarget, referenceSetMember);
					if (targetCode == null) return null;
					String equivalence = map.getSnomedRefsetEquivalence();
					FHIRMapTarget mapTarget = new FHIRMapTarget(targetCode, equivalence, null);
					mapTargetsByCode.computeIfAbsent(targetCode, key -> new ArrayList<>()).add(mapTarget);
					String message = null;
					String mapGroup = referenceSetMember.getAdditionalField("mapGroup");
					if (mapGroup != null) {
						String mapPriority = referenceSetMember.getAdditionalField("mapPriority");
						String mapRule = referenceSetMember.getAdditionalField("mapRule");
						String mapAdvice = referenceSetMember.getAdditionalField("mapAdvice");
						String correlationId = referenceSetMember.getAdditionalField("correlationId");
						Enumerations.ConceptMapEquivalence mapEquivalence = snomedCorrelationToFhirEquivalenceMap.get(correlationId);
						mapTarget.setEquivalence(mapEquivalence != null ? mapEquivalence.toCode() : null);
						String mapCategoryId = referenceSetMember.getAdditionalField("mapCategoryId");
						String mapCategoryMessage = "";

						// mapCategoryId null for complex map, only used in extended map
						if (mapCategoryId != null) {
							String mapCategoryTerm = snomedModelTermCache.getSnomedTerm(mapCategoryId, snomedVersion, languageDialects);
							mapCategoryMessage = format(", Map Category:'%s'", mapCategoryTerm);
						}

						message = format("Please observe the following map advice. Group:%s, Priority:%s, Rule:%s, Advice:'%s'%s.",
								mapGroup, mapPriority, mapRule, mapAdvice, mapCategoryMessage);
					}
					return new FHIRMapElement()
							.setCode(coding.getCode())
							.setTarget(Collections.singletonList(mapTarget))
							.setMessage(message);
				})
				.filter(Objects::nonNull)
				.filter(element -> element.getTarget().get(0).getCode() != null)
				.collect(Collectors.toList());

		// Grab target display terms
		if (!mapTargetsByCode.isEmpty()) {
			if (hasSnomedTarget) {
				Map<String, ConceptMini> conceptMiniMap = snomedConceptService.findConceptMinis(snomedVersion.getSnomedBranch(), mapTargetsByCode.keySet(), languageDialects)
						.getResultsMap();
				for (Map.Entry<String, ConceptMini> entry : conceptMiniMap.entrySet()) {
					mapTargetsByCode.get(entry.getKey()).forEach(mapTarget -> mapTarget.setDisplay(entry.getValue().getPt().getTerm()));
				}
			} else {
				Map<String, String> codeDisplayTerms = getCodeDisplayTerms(mapTargetsByCode.keySet(), targetSystem);
				for (Map.Entry<String, String> entry : codeDisplayTerms.entrySet()) {
					mapTargetsByCode.get(entry.getKey()).forEach(mapTarget -> mapTarget.setDisplay(entry.getValue()));
				}
			}
		}

		return generatedElements;
	}

	public Set<FHIRSnomedConceptMapConfig> getConfiguredMapsWithNonSnomedTarget(Set<String> refsetIds) {
		return snomedMaps.stream()
				.filter(map -> refsetIds.contains(map.getReferenceSetId()))
				.filter(map -> !FHIRHelper.isSnomedUri(map.getTargetSystem()))
				.collect(Collectors.toSet());
	}

	@NotNull
	public Map<String, String> getCodeDisplayTerms(Set<String> codes, String systemUrl) {
		if (codes == null || codes.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<String, String> codeDisplayTerms = new HashMap<>();
		FHIRCodeSystemVersion targetCodeSystemLatestVersion = fhirCodeSystemService.findCodeSystemVersion(new FHIRCodeSystemVersionParams(systemUrl));
		if (targetCodeSystemLatestVersion != null) {
			Page<FHIRConcept> targetConcepts = conceptService.findConcepts(codes, targetCodeSystemLatestVersion, PageRequest.of(0, 1_000));
			for (FHIRConcept targetConcept : targetConcepts.getContent()) {
				codeDisplayTerms.put(targetConcept.getCode(), targetConcept.getDisplay());
			}
		}
		return codeDisplayTerms;
	}

	private String getTargetCode(boolean hasSnomedSource, boolean hasSnomedTarget, ReferenceSetMember referenceSetMember) {
		String targetCode;
		if (hasSnomedTarget) {
			if (hasSnomedSource) {
				// Association refsets use targetComponentId
				targetCode = referenceSetMember.getAdditionalField(ReferenceSetMember.AssociationFields.TARGET_COMP_ID);
			} else {
				targetCode = referenceSetMember.getReferencedComponentId();
			}
		} else {
			// Target is non-snomed code system
			targetCode = referenceSetMember.getAdditionalField(ReferenceSetMember.AssociationFields.MAP_TARGET);
			if (targetCode == null) {
				// Attribute value refsets use valueId
				targetCode = referenceSetMember.getAdditionalField(ReferenceSetMember.AssociationFields.VALUE_ID);
			}
		}
		return targetCode;
	}

	@NotNull
	private <T> List<T> searchForList(NativeQueryBuilder queryBuilder, Class<T> clazz) {
		return elasticsearchOperations.search(queryBuilder.build(), clazz).stream()
				.map(SearchHit::getContent).collect(Collectors.toList());
	}
}
