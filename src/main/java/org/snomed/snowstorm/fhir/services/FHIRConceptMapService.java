package org.snomed.snowstorm.fhir.services;


import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Identifier;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.CodeSystemDefaultConfigurationService;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.IdentifierComponentService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.pojo.CodeSystemDefaultConfiguration;
import org.snomed.snowstorm.core.data.services.pojo.IdentifierSearchRequest;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.fhir.config.FHIRConceptMapImplicitConfig;
import org.snomed.snowstorm.fhir.domain.*;
import org.snomed.snowstorm.fhir.pojo.FHIRCodeSystemVersionParams;
import org.snomed.snowstorm.fhir.pojo.FHIRSnomedConceptMapConfig;
import org.snomed.snowstorm.fhir.repositories.FHIRConceptMapRepository;
import org.snomed.snowstorm.fhir.repositories.FHIRMapElementRepository;
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

	// Not a reference set, the map is generated from the alternate identifiers of the loaded extensions
	public static final String ALTERNATE_IDENTIFIER_MAP_KEY = "equivalentConcept";

	public static final String ALTERNATE_IDENTIFIER_MAP_URL = SNOMED_URI + "?fhir_cm=" + ALTERNATE_IDENTIFIER_MAP_KEY;

	private static final PageRequest PAGE_OF_ONE_THOUSAND = PageRequest.of(0, 1_000);

	private final FHIRConceptMapRepository conceptMapRepository;

	private final ElasticsearchOperations elasticsearchOperations;

	private final FHIRMapElementRepository mapElementRepository;

	private final FHIRCodeSystemService fhirCodeSystemService;

	private final CodeSystemService codeSystemService;

	private final ReferenceSetMemberService snomedRefsetMemberService;

	private final ConceptService snomedConceptService;

	private final FHIRConceptMapImplicitConfig implicitMapConfig;

	private final FHIRConceptService conceptService;

	private final FHIRSnomedModelTermCache snomedModelTermCache;

	private final CodeSystemDefaultConfigurationService codeSystemDefaultConfigurationService;

	private final IdentifierComponentService identifierComponentService;

	// Implicit ConceptMaps - format http://snomed.info/sct[/(module)[/version/(version)]]?fhir_cm=(sctid)
	private List<FHIRSnomedConceptMapConfig> snomedMaps;

	// Map of SNOMED CT map correlation concepts to FHIR equivalence codes - http://hl7.org/fhir/concept-map-equivalence
	private Map<String, Enumerations.ConceptMapEquivalence> snomedCorrelationToFhirEquivalenceMap;

	public FHIRConceptMapService(FHIRConceptMapRepository conceptMapRepository, ElasticsearchOperations elasticsearchOperations, FHIRMapElementRepository mapElementRepository, FHIRCodeSystemService fhirCodeSystemService, CodeSystemService codeSystemService, ReferenceSetMemberService snomedRefsetMemberService, ConceptService snomedConceptService, FHIRConceptMapImplicitConfig implicitMapConfig, FHIRConceptService conceptService, FHIRSnomedModelTermCache snomedModelTermCache,
			CodeSystemDefaultConfigurationService codeSystemDefaultConfigurationService, IdentifierComponentService identifierComponentService) {
		this.conceptMapRepository = conceptMapRepository;
		this.elasticsearchOperations = elasticsearchOperations;
		this.mapElementRepository = mapElementRepository;
		this.fhirCodeSystemService = fhirCodeSystemService;
		this.codeSystemService = codeSystemService;
		this.snomedRefsetMemberService = snomedRefsetMemberService;
		this.snomedConceptService = snomedConceptService;
		this.implicitMapConfig = implicitMapConfig;
		this.conceptService = conceptService;
		this.snomedModelTermCache = snomedModelTermCache;
		this.codeSystemDefaultConfigurationService = codeSystemDefaultConfigurationService;
		this.identifierComponentService = identifierComponentService;
	}

	@PostConstruct
	public void init() {
		snomedMaps = implicitMapConfig.getImplicitMaps();
		snomedCorrelationToFhirEquivalenceMap = implicitMapConfig.getSnomedCorrelationToFhirEquivalenceMap();
	}

	public FHIRConceptMap createOrUpdate(FHIRConceptMap conceptMap) {
		// FHIR ConceptMap canonical is `url|version` and both are required for persistence.
		String url = conceptMap.getUrl();
		if (url == null || url.isBlank()) {
			throw exception("ConceptMap 'url' is required (canonical is `url|version`).", OperationOutcome.IssueType.INVARIANT, 400);
		}

		String version = conceptMap.getVersion();
		if (version == null || version.isBlank()) {
			throw exception("ConceptMap 'version' is required (canonical is `url|version`).", OperationOutcome.IssueType.INVARIANT, 400);
		}

		if (url.contains("?fhir_cm")) {
			throw exception("ConceptMap url must not contain 'fhir_cm', this is reserved for implicit concept maps.", OperationOutcome.IssueType.INVARIANT, 400);
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
		List<FHIRConceptMap> maps = new ArrayList<>(hasAnyImportedSnomedVersion() ? getSnomedMaps() : List.of());
		if (!findAlternateSchemaBranches().isEmpty()) {
			maps.add(buildAlternateIdentifierMap());
		}
		PageRequest pageRequest = PageRequest.of(0, PAGE_OF_ONE_THOUSAND.getPageSize() - maps.size());
		maps.addAll(conceptMapRepository.findAll(pageRequest).getContent());
		return maps;
	}

	private boolean hasAnyImportedSnomedVersion() {
		for (CodeSystem edition : codeSystemService.findAll()) {
			CodeSystemVersion version = codeSystemService.findLatestImportedVersion(edition.getShortName());
			if (version != null && !CodeSystemService.isEmpty2000Version(version)) {
				return true;
			}
		}
		return false;
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

	private FHIRConceptMap buildAlternateIdentifierMap() {
		FHIRConceptMap map = new FHIRConceptMap();
		map.setId("snomed_implicit_map_" + ALTERNATE_IDENTIFIER_MAP_KEY);
		map.setUrl(ALTERNATE_IDENTIFIER_MAP_URL);
		map.setName("SNOMED CT alternate identifiers");
		map.setAlternateIdentifierMap(true);
		return map;
	}

	// Branch of the latest version of each loaded code system that is configured with an alternate identifier schema
	private Map<CodeSystemDefaultConfiguration, String> findAlternateSchemaBranches() {
		Map<CodeSystemDefaultConfiguration, String> branches = new LinkedHashMap<>();
		orEmpty(codeSystemDefaultConfigurationService.getConfigurations()).stream()
				.filter(config -> config.alternateSchemaUri() != null && config.alternateSchemaSctid() != null)
				.sorted(comparing(CodeSystemDefaultConfiguration::shortName))
				.forEach(config -> {
					CodeSystemVersion version = findLatestNonEmptyVersion(config.shortName());
					if (version != null) {
						branches.put(config, version.getBranchPath());
					}
				});
		return branches;
	}

	private CodeSystemVersion findLatestNonEmptyVersion(String shortName) {
		CodeSystemVersion version = codeSystemService.findLatestVisibleVersion(shortName);
		if (version == null || CodeSystemService.isEmpty2000Version(version)) {
			version = codeSystemService.findLatestImportedVersion(shortName);
		}
		return version == null || CodeSystemService.isEmpty2000Version(version) ? null : version;
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

		// Grab generated snomed maps when a SNOMED CT release is loaded
		if (hasAnyImportedSnomedVersion()) {
			maps.addAll(getSnomedMaps().stream()
					.filter(map -> snomedPredicates.stream().allMatch(predicate -> predicate.test(map))).toList());
		}

		if (url == null || url.equals(ALTERNATE_IDENTIFIER_MAP_URL)) {
			Set<String> alternateSchemaUris = findAlternateSchemaBranches().keySet().stream()
					.map(CodeSystemDefaultConfiguration::alternateSchemaUri)
					.collect(Collectors.toSet());
			if (!alternateSchemaUris.isEmpty() && isAlternateIdentifierMapMatch(coding, targetSystem, sourceValueSet, targetValueSet, alternateSchemaUris)) {
				maps.add(buildAlternateIdentifierMap());
			}
		}

		return maps;
	}

	private boolean isAlternateIdentifierMapMatch(Coding coding, String targetSystem, String sourceValueSet, String targetValueSet, Set<String> alternateSchemaUris) {
		if ((sourceValueSet != null && !sourceValueSet.endsWith(WHOLE_SYSTEM_VALUE_SET_URI_POSTFIX))
				|| (targetValueSet != null && !targetValueSet.endsWith(WHOLE_SYSTEM_VALUE_SET_URI_POSTFIX))) {
			return false;
		}
		String source = coding != null ? coding.getSystem() : wholeSystemOf(sourceValueSet);
		String target = targetSystem != null ? targetSystem : wholeSystemOf(targetValueSet);
		boolean snomedSource = FHIRHelper.isSnomedUri(source);
		if (source != null && !snomedSource && !alternateSchemaUris.contains(source)) {
			return false;
		}
		if (target == null) {
			return true;
		}
		boolean snomedTarget = FHIRHelper.isSnomedUri(target);
		if (!snomedTarget && !alternateSchemaUris.contains(target)) {
			return false;
		}
		// One side must be SNOMED CT and the other an alternate identifier schema
		return source == null || snomedSource != snomedTarget;
	}

	private static String wholeSystemOf(String valueSet) {
		return valueSet != null ? valueSet.replace(WHOLE_SYSTEM_VALUE_SET_URI_POSTFIX, "") : null;
	}

	public Collection<FHIRMapElement> findMapElements(FHIRConceptMap map, Coding coding, String targetSystem, List<LanguageDialect> languageDialects) {
		if (map.isImplicitSnomedMap()) {
			return generateImplicitSnomedMapElements(map, coding, targetSystem, languageDialects);
		}
		if (map.isAlternateIdentifierMap()) {
			return generateAlternateIdentifierMapElements(coding, targetSystem, languageDialects);
		}

		List<FHIRConceptMapGroup> groups = map.getGroup().stream()
				.filter(group -> group.getSource().equals(coding.getSystem()))
				.filter(group -> targetSystem == null || group.getTarget().equals(targetSystem))
				.toList();
		BoolQuery.Builder query = bool()
				.must(termsQuery(FHIRMapElement.Fields.GROUP_ID, groups.stream().map(FHIRConceptMapGroup::getGroupId).toList()))
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
				.map(referenceSetMember -> buildImplicitSnomedMapElement(referenceSetMember, map, coding,
						hasSnomedSource, hasSnomedTarget, snomedVersion, languageDialects, mapTargetsByCode))
				.filter(Objects::nonNull)
				.filter(element -> element.getTarget().get(0).getCode() != null)
				.toList();

		// Grab target display terms
		fillMapTargetDisplayTerms(mapTargetsByCode, hasSnomedTarget, targetSystem, snomedVersion.getSnomedBranch(), languageDialects);

		return generatedElements;
	}

	private Collection<FHIRMapElement> generateAlternateIdentifierMapElements(Coding coding, String targetSystem, List<LanguageDialect> languageDialects) {
		Map<CodeSystemDefaultConfiguration, String> alternateSchemaBranches = findAlternateSchemaBranches();
		if (FHIRHelper.isSnomedUri(coding.getSystem())) {
			return generateAlternateIdentifierTargets(coding, selectBranchesForSnomedSource(coding, targetSystem, alternateSchemaBranches));
		}
		return alternateSchemaBranches.entrySet().stream()
				.filter(entry -> entry.getKey().alternateSchemaUri().equals(coding.getSystem()))
				.findFirst()
				.map(entry -> generateSnomedTargets(coding, entry.getKey(), entry.getValue(), languageDialects))
				.orElse(Collections.emptyList());
	}

	private Map<CodeSystemDefaultConfiguration, String> selectBranchesForSnomedSource(Coding coding, String targetSystem,
			Map<CodeSystemDefaultConfiguration, String> alternateSchemaBranches) {
		Map<CodeSystemDefaultConfiguration, String> selected = new LinkedHashMap<>(alternateSchemaBranches);
		selected.keySet().removeIf(config -> targetSystem != null && !config.alternateSchemaUri().equals(targetSystem));
		if (coding.getVersion() == null) {
			return selected;
		}
		// A version of an alternate schema code system limits the search to that version, any other version searches all of them
		FHIRCodeSystemVersion requestedVersion = fhirCodeSystemService.findCodeSystemVersionOrThrow(
				FHIRHelper.getCodeSystemVersionParams((IdType) null, null, null, coding));
		String requestedShortName = requestedVersion.getSnomedCodeSystem() != null ? requestedVersion.getSnomedCodeSystem().getShortName() : null;
		for (CodeSystemDefaultConfiguration config : alternateSchemaBranches.keySet()) {
			if (config.shortName().equalsIgnoreCase(requestedShortName)) {
				return selected.containsKey(config) ? Map.of(config, requestedVersion.getSnomedBranch()) : Collections.emptyMap();
			}
		}
		return selected;
	}

	private List<FHIRMapElement> generateAlternateIdentifierTargets(Coding coding, Map<CodeSystemDefaultConfiguration, String> branches) {
		List<FHIRMapElement> elements = new ArrayList<>();
		for (Map.Entry<CodeSystemDefaultConfiguration, String> entry : branches.entrySet()) {
			CodeSystemDefaultConfiguration config = entry.getKey();
			IdentifierSearchRequest searchRequest = new IdentifierSearchRequest()
					.active(true)
					.identifierSchemeId(config.alternateSchemaSctid())
					.referencedComponentId(coding.getCode());
			Map<String, List<FHIRMapTarget>> mapTargetsByCode = new HashMap<>();
			for (Identifier identifier : identifierComponentService.findIdentifiers(entry.getValue(), searchRequest, PAGE_OF_ONE_THOUSAND)) {
				FHIRMapTarget mapTarget = new FHIRMapTarget(identifier.getAlternateIdentifier(), Enumerations.ConceptMapEquivalence.EQUIVALENT.toCode(), null)
						.setSystem(config.alternateSchemaUri());
				mapTargetsByCode.computeIfAbsent(mapTarget.getCode(), key -> new ArrayList<>()).add(mapTarget);
				elements.add(new FHIRMapElement().setCode(coding.getCode()).setTarget(Collections.singletonList(mapTarget)));
			}
			fillMapTargetDisplayTerms(mapTargetsByCode, false, config.alternateSchemaUri(), entry.getValue(), null);
		}
		return elements;
	}

	private List<FHIRMapElement> generateSnomedTargets(Coding coding, CodeSystemDefaultConfiguration config, String branch, List<LanguageDialect> languageDialects) {
		IdentifierSearchRequest searchRequest = new IdentifierSearchRequest()
				.active(true)
				.identifierSchemeId(config.alternateSchemaSctid())
				.alternateIdentifier(coding.getCode());
		List<FHIRMapElement> elements = new ArrayList<>();
		Map<String, List<FHIRMapTarget>> mapTargetsByCode = new HashMap<>();
		for (Identifier identifier : identifierComponentService.findIdentifiers(branch, searchRequest, PAGE_OF_ONE_THOUSAND)) {
			FHIRMapTarget mapTarget = new FHIRMapTarget(identifier.getReferencedComponentId(), Enumerations.ConceptMapEquivalence.EQUIVALENT.toCode(), null)
					.setSystem(SNOMED_URI);
			mapTargetsByCode.computeIfAbsent(mapTarget.getCode(), key -> new ArrayList<>()).add(mapTarget);
			elements.add(new FHIRMapElement().setCode(coding.getCode()).setTarget(Collections.singletonList(mapTarget)));
		}
		fillMapTargetDisplayTerms(mapTargetsByCode, true, SNOMED_URI, branch, languageDialects);
		return elements;
	}

	private FHIRMapElement buildImplicitSnomedMapElement(ReferenceSetMember referenceSetMember, FHIRConceptMap map, Coding coding,
			boolean hasSnomedSource, boolean hasSnomedTarget, FHIRCodeSystemVersion snomedVersion,
			List<LanguageDialect> languageDialects, Map<String, List<FHIRMapTarget>> mapTargetsByCode) {
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
	}

	private void fillMapTargetDisplayTerms(Map<String, List<FHIRMapTarget>> mapTargetsByCode, boolean hasSnomedTarget,
			String targetSystem, String snomedBranch, List<LanguageDialect> languageDialects) {
		if (mapTargetsByCode.isEmpty()) {
			return;
		}
		if (hasSnomedTarget) {
			Map<String, ConceptMini> conceptMiniMap = snomedConceptService.findConceptMinis(snomedBranch, mapTargetsByCode.keySet(), languageDialects)
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
				.map(SearchHit::getContent).toList();
	}
}
