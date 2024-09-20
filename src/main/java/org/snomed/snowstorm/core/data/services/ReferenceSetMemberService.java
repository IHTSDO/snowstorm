package org.snomed.snowstorm.core.data.services;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Iterables;
import io.kaicode.elasticvc.api.*;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.Metadata;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.langauges.ecl.domain.filter.*;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.repositories.ReferenceSetMemberRepository;
import org.snomed.snowstorm.core.data.repositories.ReferenceSetTypeRepository;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.data.services.pojo.AsyncRefsetMemberChangeBatch;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregations;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregationsFactory;
import org.snomed.snowstorm.core.util.PageHelper;
import org.snomed.snowstorm.core.util.SearchAfterPage;
import org.snomed.snowstorm.core.util.SearchAfterQueryHelper;
import org.snomed.snowstorm.ecl.ECLContentService;
import org.snomed.snowstorm.ecl.ECLQueryService;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SSubExpressionConstraint;
import org.snomed.snowstorm.fhir.pojo.FHIRSnomedConceptMapConfig;
import org.snomed.snowstorm.fhir.services.FHIRConceptMapService;
import org.snomed.snowstorm.rest.converter.SearchAfterHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.lang.Long.parseLong;
import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.*;
import static io.kaicode.elasticvc.helper.QueryHelper.*;
import static org.snomed.snowstorm.core.data.domain.Concepts.inactivationAndAssociationRefsets;
import static org.snomed.snowstorm.core.data.services.CodeSystemService.MAIN;
import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;
import static org.snomed.snowstorm.core.util.SearchAfterQueryHelper.updateQueryWithSearchAfter;
import static org.springframework.data.elasticsearch.client.elc.Queries.wildcardQuery;

@Service
public class ReferenceSetMemberService extends ComponentService {

	private static final Function<ReferenceSetMember, Object[]> REFERENCE_SET_MEMBER_ID_SEARCH_AFTER_EXTRACTOR = referenceSetMember -> {
		if (referenceSetMember == null) {
			return null;
		}

		String id = referenceSetMember.getId();
		return id == null ? null : SearchAfterHelper.convertToTokenAndBack(new Object[]{id});
	};
	private static final Set<String> LANG_REFSET_MEMBER_FIELD_SET = Collections.singleton(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID);
	private static final Set<String> OWL_REFSET_MEMBER_FIELD_SET = Collections.singleton(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION);
	public static final String AGGREGATION_MEMBER_COUNTS_BY_REFERENCE_SET = "memberCountsByReferenceSet";

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private BranchService branchService;

	@Autowired
	private SBranchService sBranchService;

	@Autowired
	private BranchMetadataHelper branchMetadataHelper;

	@Autowired
	private ReferenceSetMemberRepository memberRepository;

	@Autowired
	private ReferenceSetTypeRepository typeRepository;

	@Autowired
	private ReferenceSetTypesConfigurationService referenceSetTypesConfigurationService;

	@Autowired
	private ECLQueryService eclQueryService;

	@Value("${refset-types.initial-branch}")
	private String refsetsBranchPath;

	@Value("${search.refset.aggregation.size}")
	private int refsetAggregationSearchSize;

	@Autowired
	@Lazy
	// FHIR map service is used to pull terms from other code systems for display
	private FHIRConceptMapService fhirConceptMapService;

	private final Cache<String, AsyncRefsetMemberChangeBatch> batchChanges = CacheBuilder.newBuilder().expireAfterWrite(2, TimeUnit.HOURS).build();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public void init() {
		Set<ReferenceSetType> configuredTypes = referenceSetTypesConfigurationService.getConfiguredTypes();
		setupTypes(configuredTypes);
	}

	public Page<ReferenceSetMember> findMembers(String branch,
			String referencedComponentId,
			PageRequest pageRequest) {
		if (branch == null || referencedComponentId == null || pageRequest == null) {
			return Page.empty();
		}

		return findMembers(branch, new MemberSearchRequest().referencedComponentId(referencedComponentId), pageRequest);
	}

	/**
	 * Find members of reference sets.
	 * @param branch                The branch to search on.
	 * @param pageRequest           Pagination.
	 * @return	A page of matched reference set members.
	 */
	public Page<ReferenceSetMember> findMembers(String branch, MemberSearchRequest searchRequest, PageRequest pageRequest) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);
		NativeQuery query = new NativeQueryBuilder().withQuery(buildMemberQuery(searchRequest, branchCriteria)).withPageable(pageRequest).build();
		query.setTrackTotalHits(true);
		updateQueryWithSearchAfter(query, pageRequest);
		SearchHits<ReferenceSetMember> searchHits = elasticsearchOperations.search(query, ReferenceSetMember.class);
		PageImpl<ReferenceSetMember> referenceSetMembers = new PageImpl<>(searchHits.get().map(SearchHit::getContent).collect(Collectors.toList()), query.getPageable(), searchHits.getTotalHits());
		if (searchRequest.isIncludeNonSnomedMapTerms()) {
			joinMapTargetTerms(referenceSetMembers.getContent());
		}
		return PageHelper.toSearchAfterPage(referenceSetMembers, REFERENCE_SET_MEMBER_ID_SEARCH_AFTER_EXTRACTOR);
	}

	private void joinMapTargetTerms(List<ReferenceSetMember> referenceSetMembers) {
		Set<String> refsetIds = referenceSetMembers.stream().map(ReferenceSetMember::getRefsetId).collect(Collectors.toSet());
		Set<FHIRSnomedConceptMapConfig> maps = fhirConceptMapService.getConfiguredMapsWithNonSnomedTarget(refsetIds);

		for (FHIRSnomedConceptMapConfig map : maps) {
			String targetSystem = map.getTargetSystem();
			Map<String, List<ReferenceSetMember>> codesToMembers = new HashMap<>();
			referenceSetMembers.stream()
					.filter(member -> member.getRefsetId().equals(map.getReferenceSetId()))
					.forEach(member -> {
						String targetCode = member.getAdditionalField(ReferenceSetMember.AssociationFields.MAP_TARGET);
						if (targetCode != null) {
							codesToMembers.computeIfAbsent(targetCode, key -> new ArrayList<>()).add(member);
						}
					});
			Map<String, String> codeDisplayTerms = fhirConceptMapService.getCodeDisplayTerms(codesToMembers.keySet(), targetSystem);
			for (Map.Entry<String, String> codeDisplay : codeDisplayTerms.entrySet()) {
				String targetCode = codeDisplay.getKey();
				codesToMembers.get(targetCode).forEach(member -> member.setTargetCoding(targetSystem, targetCode, codeDisplay.getValue()));
			}
		}
	}

	public Page<ReferenceSetMember> findMembers(BranchCriteria branchCriteria, MemberSearchRequest searchRequest, PageRequest pageRequest) {
		NativeQuery query = new NativeQueryBuilder().withQuery(buildMemberQuery(searchRequest, branchCriteria)).withPageable(pageRequest).build();
		SearchHits<ReferenceSetMember> searchHits = elasticsearchOperations.search(query, ReferenceSetMember.class);
		return new PageImpl<>(searchHits.get().map(SearchHit::getContent).collect(Collectors.toList()), pageRequest, searchHits.getTotalHits());
	}

	private Query buildMemberQuery(MemberSearchRequest searchRequest, BranchCriteria branchCriteria) {
		BoolQuery.Builder builder = bool().must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class));

		if (searchRequest.getActive() != null) {
			builder.must(termQuery(ReferenceSetMember.Fields.ACTIVE, searchRequest.getActive()));
		}
		
		if (searchRequest.isNullEffectiveTime() != null) {
			if (searchRequest.isNullEffectiveTime()) {
				builder.mustNot(existsQuery(SnomedComponent.Fields.EFFECTIVE_TIME));
			} else {
				builder.must(existsQuery(SnomedComponent.Fields.EFFECTIVE_TIME));
			}
		}
		
		String referenceSet = searchRequest.getReferenceSet();
		if (!Strings.isNullOrEmpty(referenceSet)) {
			List<Long> conceptIds = getConceptIds(branchCriteria, referenceSet);
			builder.must(termsQuery(ReferenceSetMember.Fields.REFSET_ID, conceptIds));
		}
		String module = searchRequest.getModule();
		if (!Strings.isNullOrEmpty(module)) {
			List<Long> conceptIds = getConceptIds(branchCriteria, module);
			builder.must(termsQuery(ReferenceSetMember.Fields.MODULE_ID, conceptIds));
		}
		Collection<? extends Serializable> referencedComponentIds = searchRequest.getReferencedComponentIds();
		if (referencedComponentIds != null && !referencedComponentIds.isEmpty()) {
			builder.must(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, referencedComponentIds));
		}
		
		Map<String, String> additionalFields = searchRequest.getAdditionalFields();
		for (String additionalFieldName : additionalFields.keySet()) {
			String additionalFieldNameValue = additionalFields.get(additionalFieldName);
			if (!Strings.isNullOrEmpty(additionalFieldNameValue)) {
				String fieldKeyword = ReferenceSetMember.Fields.getAdditionalFieldKeywordTypeMapping(additionalFieldName);
				builder.must(termQuery(fieldKeyword, additionalFieldNameValue));
			}
		}
		
		Map<String, Set<String>> additionalFieldSets = searchRequest.getAdditionalFieldSets();
		for (String additionalFieldName : additionalFieldSets.keySet()) {
			Set<String> additionalFieldNameValues = additionalFieldSets.get(additionalFieldName);
			if (additionalFieldNameValues != null && !additionalFieldNameValues.isEmpty()) {
				String fieldKeyword = ReferenceSetMember.Fields.getAdditionalFieldKeywordTypeMapping(additionalFieldName);
				builder.must(termsQuery(fieldKeyword, additionalFieldNameValues));
			}
		}
		
		String owlExpressionConceptId = searchRequest.getOwlExpressionConceptId();
		if (!Strings.isNullOrEmpty(owlExpressionConceptId)) {
			builder.must(regexpQuery(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION_KEYWORD_FIELD_PATH, String.format(".*:%s[^0-9].*", owlExpressionConceptId)));
		}
		Boolean owlExpressionGCI = searchRequest.getOwlExpressionGCI();
		if (owlExpressionGCI != null) {
			Query gciClause = regexpQuery(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION_KEYWORD_FIELD_PATH, "SubClassOf\\(Object.*");
			if (owlExpressionGCI) {
				builder.must(gciClause);
			} else {
				builder.mustNot(gciClause);
			}
		}
		return builder.build()._toQuery();
	}

	private List<Long> getConceptIds(BranchCriteria branchCriteria, String conceptIdOrECL) {
		List<Long> conceptIds;
		if (conceptIdOrECL.matches("\\d+")) {
			conceptIds = Collections.singletonList(parseLong(conceptIdOrECL));
		} else {
			conceptIds = eclQueryService.selectConceptIds(conceptIdOrECL, branchCriteria, true, LARGE_PAGE).getContent();
		}
		return conceptIds;
	}

	public ReferenceSetMember findMember(String branch, String uuid) {
		List<ReferenceSetMember> result = findMembers(branch, Collections.singletonList(uuid));
		if (result.size() > 1) {
			throw new IllegalStateException(String.format("Found more than one referenceSetMembers with uuid %s on branch %s", uuid, branch));
		}
		if (!result.isEmpty()) {
			return result.get(0);
		}
		return null;
	}

	public List<ReferenceSetMember> findMembers(BranchCriteria branchCriteria, Collection<String> uuids) {
		Query query = bool(b -> b
				.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
				.must(termsQuery(ReferenceSetMember.Fields.MEMBER_ID, uuids)));
		return elasticsearchOperations.search(new NativeQueryBuilder().withQuery(query).withPageable(LARGE_PAGE).build(), ReferenceSetMember.class)
				.stream()
				.map(SearchHit::getContent).collect(Collectors.toList());
	}

	public List<ReferenceSetMember> findMembers(String branch, Collection<String> uuids) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);
		return findMembers(branchCriteria, uuids);
	}

	public ReferenceSetMember createMember(String branch, ReferenceSetMember member) {
		Iterator<ReferenceSetMember> members = createMembers(branch, Collections.singleton(member)).iterator();
		return members.hasNext() ? members.next() : null;
	}

	public Iterable<ReferenceSetMember> createMembers(String branch, Set<ReferenceSetMember> members) {
		try (final Commit commit = branchService.openCommit(branch, branchMetadataHelper.getBranchLockMetadata(String.format("Creating %s reference set members.", members.size())))) {

			// Grab branch metadata including values inherited from ancestor branches
			final Metadata metadata = branchService.findBranchOrThrow(commit.getBranch().getPath(), true).getMetadata();
			String defaultModuleId = metadata.getString(Config.DEFAULT_MODULE_ID_KEY);
			members.forEach(member -> {
				if (member.getMemberId() == null) {
					member.setMemberId(UUID.randomUUID().toString());
				}
				if (member.getModuleId() == null) {
					member.setModuleId(defaultModuleId);
				}
				member.markChanged();
			});
			final Iterable<ReferenceSetMember> savedMembers = doSaveBatchMembers(members, commit);
			commit.markSuccessful();
			return savedMembers;
		}
	}

	public void deleteMember(String branch, String uuid) {
		deleteMember(branch, uuid, false);
	}

	public void deleteMember(String branch, String uuid, boolean force) {
		deleteMembers(branch, Collections.singleton(uuid), force);
	}

	public void deleteMembers(String branch, Set<String> uuids, boolean force) {
		if (uuids.isEmpty()) {
			return;
		}
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);
		NativeQuery query = new NativeQueryBuilder().withQuery(bool(b -> b
				.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
				.must(termsQuery(ReferenceSetMember.Fields.MEMBER_ID, uuids))))
				.withPageable(PageRequest.of(0, uuids.size()))
				.build();
		List<ReferenceSetMember> matches = elasticsearchOperations.search(query, ReferenceSetMember.class).stream().map(SearchHit::getContent).collect(Collectors.toList());
		if (matches.size() != uuids.size()) {
			List<String> matchedIds = matches.stream().map(ReferenceSetMember::getMemberId).collect(Collectors.toList());
			Set<String> missingIds = new HashSet<>(uuids);
			missingIds.removeAll(matchedIds);
			throw new NotFoundException(String.format("%s reference set members not found on branch %s: %s", missingIds.size(), branch, missingIds));
		}

		for (ReferenceSetMember member : matches) {
			if (member.isReleased() && !force) {
				throw new IllegalStateException(String.format("Reference set member %s has been released and can't be deleted on branch %s.", member.getMemberId(), branch));
			}
		}

		try (Commit commit = branchService.openCommit(branch, branchMetadataHelper.getBranchLockMetadata(String.format("Deleting %s reference set members.", matches.size())))) {
			for (ReferenceSetMember member : matches) {
				member.markDeleted();
			}
			doSaveBatchComponents(matches, commit, ReferenceSetMember.Fields.MEMBER_ID, memberRepository);
			commit.markSuccessful();
		}
	}

	public String newCreateUpdateAsyncJob() {
		final AsyncRefsetMemberChangeBatch batchChange = new AsyncRefsetMemberChangeBatch();
		synchronized (batchChanges) {
			batchChanges.put(batchChange.getId(), batchChange);
		}
		return batchChange.getId();
	}

	@Async
	public void createUpdateAsync(String batchId, String branch, List<ReferenceSetMember> members, SecurityContext securityContext) {
		SecurityContextHolder.setContext(securityContext);
		final AsyncRefsetMemberChangeBatch changeBatch = batchChanges.getIfPresent(batchId);
		if (changeBatch == null) {
			logger.error("Batch member change {} not found.", batchId);
			return;
		}
		try {
			Iterable<ReferenceSetMember> savedMembers;
			try (final Commit commit = branchService.openCommit(branch, branchMetadataHelper.getBranchLockMetadata(String.format("Saving %s refset members.", members.size())))) {

				// Set missing moduleIds
				Metadata metadata = branchService.findBranchOrThrow(commit.getBranch().getPath(), true).getMetadata();
				String defaultModuleId = metadata.getString(Config.DEFAULT_MODULE_ID_KEY);
				members.stream().filter(member -> member.getModuleId() == null).forEach(member -> member.setModuleId(defaultModuleId));

				// Mark new/updated members. Existing members that have not changed will not be persisted.
				final Map<String, ReferenceSetMember> membersWithIds =
						members.stream().filter(member -> member.getMemberId() != null).collect(Collectors.toMap(ReferenceSetMember::getMemberId, Function.identity()));
				final Map<String, ReferenceSetMember> existingMembers =
						findMembers(branch, membersWithIds.keySet()).stream().collect(Collectors.toMap(ReferenceSetMember::getMemberId, Function.identity()));
				membersWithIds.forEach((key, batchMember) -> {
					final ReferenceSetMember existingMember = existingMembers.get(key);
					if (batchMember.isComponentChanged(existingMember)) {
						batchMember.markChanged();
					}
					if (existingMember != null) {
						batchMember.copyReleaseDetails(existingMember);
					}
				});

				// Set missing ids
				members.stream().filter(member -> member.getMemberId() == null).forEach(member -> {
					member.markChanged();
					member.setMemberId(UUID.randomUUID().toString());
				});

				savedMembers = doSaveBatchMembers(members, commit);
				commit.markSuccessful();
			}
			changeBatch.setMemberIds(StreamSupport.stream(savedMembers.spliterator(), false).map(ReferenceSetMember::getMemberId).collect(Collectors.toList()));
			changeBatch.setStatus(AsyncRefsetMemberChangeBatch.Status.COMPLETED);

		} catch (IllegalArgumentException | IllegalStateException | ElasticVCRuntimeException e) {
			changeBatch.setStatus(AsyncRefsetMemberChangeBatch.Status.FAILED);
			changeBatch.setMessage(e.getMessage());
			logger.error("Batch member change failed, id:{}, branch:{}", changeBatch.getId(), branch, e);
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	public AsyncRefsetMemberChangeBatch getBatchChange(String bulkChangeId) {
		return batchChanges.getIfPresent(bulkChangeId);
	}

	/**
	 * Persists members updates within commit.
	 * Inactive members which have not been released will be deleted
	 * @return List of persisted components with updated metadata and filtered by deleted status.
	 */
	public Iterable<ReferenceSetMember> doSaveBatchMembers(Collection<ReferenceSetMember> members, Commit commit) {
		// Delete inactive unreleased members
		members.stream()
				.filter(member -> !member.isActive() && !member.isReleased())
				.forEach(ReferenceSetMember::markDeleted);

		members.forEach(ReferenceSetMember::updateEffectiveTime);

		// Set conceptId on those members which are considered part of the concept or its components
		List<ReferenceSetMember> descriptionMembers = new ArrayList<>();
		LongSet descriptionIds = new LongArraySet();
		members.stream()
				.filter(member -> !member.isDeleted())
				.filter(member -> member.getConceptId() == null)
				.forEach(member -> {
					if (IdentifierService.isDescriptionId(member.getReferencedComponentId())
							&& (member.getAdditionalFields().keySet().equals(LANG_REFSET_MEMBER_FIELD_SET) || inactivationAndAssociationRefsets.contains(member.getRefsetId()))) {
						// Lang refset or description inactivation indicator / historical association
						// Save member so we can load it to lookup the conceptId
						descriptionMembers.add(member);
						descriptionIds.add(parseLong(member.getReferencedComponentId()));

					} else if (IdentifierService.isConceptId(member.getReferencedComponentId())
							&& (member.getAdditionalFields().keySet().equals(OWL_REFSET_MEMBER_FIELD_SET)
							|| Concepts.inactivationAndAssociationRefsets.contains(member.getRefsetId()))) {
						// Axiom, inactivation or historical association
						member.setConceptId(member.getReferencedComponentId());
					}
				});

		if (!descriptionIds.isEmpty()) {
			// Lookup the conceptId of members which are considered part of the description
			final NativeQueryBuilder queryBuilder = new NativeQueryBuilder();

			Long2ObjectMap<Description> descriptionsFromStore = new Long2ObjectOpenHashMap<>();
			for (List<Long> descriptionIdsSegment : Iterables.partition(descriptionIds, CLAUSE_LIMIT)) {
				queryBuilder
						.withQuery(bool(b -> b
								.must(termsQuery(Description.Fields.DESCRIPTION_ID, descriptionIdsSegment))
								.must(versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit).getEntityBranchCriteria(Description.class))))
						.withSourceFilter(new FetchSourceFilter(new String[]{Description.Fields.CONCEPT_ID, Description.Fields.DESCRIPTION_ID}, null))
						.withPageable(LARGE_PAGE);
				try (final SearchHitsIterator<Description> descriptions = elasticsearchOperations.searchForStream(queryBuilder.build(), Description.class)) {
					descriptions.forEachRemaining(description ->
							descriptionsFromStore.put(parseLong(description.getContent().getDescriptionId()), description.getContent()));
				}
			}

			descriptionMembers.parallelStream().forEach(member -> {
				Description description = descriptionsFromStore.get(parseLong(member.getReferencedComponentId()));
				if (description == null) {
					logger.warn("Refset member refers to description which does not exist, this will not be persisted {} -> {}", member.getId(), member.getReferencedComponentId());
					members.remove(member);
					return;
				}
				member.setConceptId(description.getConceptId());
			});
		}

		return doSaveBatchComponents(members, commit, ReferenceSetMember.Fields.MEMBER_ID, memberRepository);
	}

	public SearchAfterPage<ReferenceSetMember> findMembersForECLResponse(BoolQuery.Builder memberQueryBuilder, List<MemberFilterConstraint> memberFilterConstraints,
			List<String> memberFieldsToReturn, boolean stated, BranchCriteria branchCriteria, PageRequest pageRequest, ECLContentService eclContentService) {

		addECLMemberConstraints(memberQueryBuilder, memberFilterConstraints, stated, branchCriteria, eclContentService);

		NativeQueryBuilder queryBuilder = new NativeQueryBuilder()
				.withQuery(memberQueryBuilder.build()._toQuery())
				.withPageable(pageRequest);

		if (memberFieldsToReturn != null) {
			Set<String> elasticFieldNames = new HashSet<>();
			elasticFieldNames.add(ReferenceSetMember.Fields.ACTIVE);
			for (String eclFieldName : memberFieldsToReturn) {
				if (!eclFieldName.equals(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID)) {
					eclFieldName = ReferenceSetMember.Fields.getAdditionalFieldTextTypeMapping(eclFieldName);
				}
				elasticFieldNames.add(eclFieldName);
			}
			String[] fieldsToInclude = new String[elasticFieldNames.size()];
			elasticFieldNames.toArray(fieldsToInclude);
			queryBuilder.withSourceFilter(new FetchSourceFilter(fieldsToInclude, null));
		}

		NativeQuery query = queryBuilder.build();
		query.setTrackTotalHits(true);
		SearchHits<ReferenceSetMember> searchHits = elasticsearchOperations.search(query, ReferenceSetMember.class);
		return PageHelper.toSearchAfterPage(searchHits, pageRequest);
	}

	public Set<Long> findConceptsInReferenceSet(Collection<Long> referenceSetIds, List<MemberFilterConstraint> memberFilterConstraints, RefinementBuilder refinementBuilder,
			BoolQuery.Builder masterMemberQuery) {

		BranchCriteria branchCriteria = refinementBuilder.getBranchCriteria();
		BoolQuery.Builder memberQueryBuilder = bool()
				.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
				.must(masterMemberQuery.build()._toQuery());
		memberQueryBuilder.must(regexpQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, ".*0."));// Matches concept SCTIDs only

		addECLMemberConstraints(memberQueryBuilder, memberFilterConstraints, refinementBuilder.isStated(), branchCriteria, refinementBuilder.getEclContentService());

		// Allow searching across all refsets
		if (referenceSetIds != null) {
			memberQueryBuilder.must(termsQuery(ReferenceSetMember.Fields.REFSET_ID, referenceSetIds));
		}

		// Build search query
		NativeQuery query = new NativeQueryBuilder()
				.withQuery(memberQueryBuilder.build()._toQuery())
				.withSourceFilter(new FetchSourceFilter(new String[]{ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID}, null))
				.withSort(SortOptions.of(s -> s.field(f -> f.field("_doc"))))// Fastest unordered sort
				.withPageable(LARGE_PAGE)
				.build();

		// Stream results
		Set<Long> conceptIds = new LongArraySet();
		try (SearchHitsIterator<ReferenceSetMember> stream = elasticsearchOperations.searchForStream(query, ReferenceSetMember.class)) {
			stream.forEachRemaining(member -> conceptIds.add(parseLong(member.getContent().getReferencedComponentId())));
		}
		return conceptIds;
	}

	private void addECLMemberConstraints(BoolQuery.Builder memberQuery, List<MemberFilterConstraint> memberFilterConstraints, boolean stated,
			BranchCriteria branchCriteria, ECLContentService eclContentService) {

		if (memberFilterConstraints != null) {
			for (MemberFilterConstraint memberFilterConstraint : memberFilterConstraints) {
				for (MemberFieldFilter fieldFilter : orEmpty(memberFilterConstraint.getMemberFieldFilters())) {
					String fieldName = fieldFilter.getFieldName();
					fieldName = fieldName.equals(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID) ?
							fieldName : "additionalFields." + fieldName + ".keyword";

					if (fieldFilter.getExpressionComparisonOperator() != null) {
						SSubExpressionConstraint subExpressionConstraint = (SSubExpressionConstraint) fieldFilter.getSubExpressionConstraint();
						List<Long> conceptIds = eclContentService
								.fetchAllIdsWithCaching(subExpressionConstraint, branchCriteria, stated);
						Query termsQuery = termsQuery(fieldName, conceptIds);
						if (fieldFilter.getExpressionComparisonOperator().equals("=")) {
							memberQuery.filter(termsQuery);
						} else {
							memberQuery.filter(bool(b -> b.mustNot(termsQuery)));
						}
					} else if (fieldFilter.getNumericComparisonOperator() != null) {
						NumericComparisonOperator operator = fieldFilter.getNumericComparisonOperator();
						if (operator == NumericComparisonOperator.EQUAL) {
							memberQuery.must(termQuery(fieldName, fieldFilter.getNumericValue()));
						} else if (operator == NumericComparisonOperator.NOT_EQUAL) {
							memberQuery.mustNot(termQuery(fieldName, fieldFilter.getNumericValue()));
						} else {
							float value = Float.parseFloat(fieldFilter.getNumericValue());
							ECLContentService.addNumericConstraint(operator, fieldName, Collections.singleton(value), memberQuery);
						}
					} else if (fieldFilter.getStringComparisonOperator() != null) {
						boolean must = fieldFilter.getStringComparisonOperator().equals("=");
						for (TypedSearchTerm searchTerm : fieldFilter.getSearchTerms()) {
							Query query;
							if (searchTerm.getType() == SearchType.WILDCARD) {
								// There will only be one term for wildcard
								String regex = searchTerm.getTerm().replace("*", ".*");
								query = regexpQuery(fieldName, regex);
							} else {
								// Prefix query
								query = wildcardQuery(fieldName, searchTerm.getTerm() + "*")._toQuery();
							}
							if (must) {
								memberQuery.must(query);
							} else {
								memberQuery.mustNot(query);
							}
						}
					} else if (fieldFilter.getTimeComparisonOperator() != null) {
						ECLContentService.addNumericConstraint(fieldFilter.getTimeComparisonOperator(), fieldName, fieldFilter.getTimeValues(), memberQuery);
					}
				}
			}
		}
	}

	private void setupTypes(Set<ReferenceSetType> referenceSetTypes) {
		String path = refsetsBranchPath;
		if (!branchService.exists(MAIN)) {
			sBranchService.create(MAIN);
		}
		if (branchService.findLatest(path).isLocked()) {
			logger.warn("{} branch is locked. Unable to verify reference set type configuration.", path);
			return;
		}
		logger.info("Reference set types are configured against branch: '{}'.", path);
		List<ReferenceSetType> existingTypes = findConfiguredReferenceSetTypes(path);
		Set<ReferenceSetType> typesToRemove = new HashSet<>(existingTypes);
		typesToRemove.removeAll(referenceSetTypes);
		if (!typesToRemove.isEmpty()) {
			String message = String.format("Removing reference set types: %s", typesToRemove);
			logger.info(message);
			try (Commit commit = branchService.openCommit(path, branchMetadataHelper.getBranchLockMetadata(message))) {
				typesToRemove.forEach(ReferenceSetType::markDeleted);
				doSaveBatchComponents(typesToRemove, commit, ReferenceSetType.FIELD_ID, typeRepository);
				commit.markSuccessful();
			}
		}

		Set<ReferenceSetType> typesToAdd = new HashSet<>(referenceSetTypes);
		typesToAdd.removeAll(existingTypes);
		if (!typesToAdd.isEmpty()) {
			String message = String.format("Setting up reference set types: %s", typesToAdd.toString());
			logger.info(message);
			try (Commit commit = branchService.openCommit(path, branchMetadataHelper.getBranchLockMetadata(message))) {
				doSaveBatchComponents(typesToAdd, commit, ReferenceSetType.FIELD_ID, typeRepository);
				commit.markSuccessful();
			}
		}
	}

	public List<ReferenceSetType> findConfiguredReferenceSetTypes(String path) {
		NativeQuery query = new NativeQueryBuilder()
				.withQuery(versionControlHelper.getBranchCriteria(path).getEntityBranchCriteria(ReferenceSetType.class))
				.withPageable(LARGE_PAGE).build();
		return elasticsearchOperations.search(query, ReferenceSetType.class).stream().map(SearchHit::getContent).collect(Collectors.toList());
	}

	public ReferenceSetMember updateMember(String branch, ReferenceSetMember member) {

		ReferenceSetMember existingMember = findMember(branch, member.getMemberId());
		if (existingMember == null) {
			throw new NotFoundException("No existing reference set member found with uuid " + member.getMemberId());
		}
		member.copyReleaseDetails(existingMember);
		if (!member.isReleased() && !member.isActive()) {
			throw new IllegalStateException("Unpublished reference set member " + member.getMemberId() + " can not be made inactive, it must be deleted.");
		}

		if (existingMember.isComponentChanged(member)) {
			try (Commit commit = branchService.openCommit(branch, branchMetadataHelper.getBranchLockMetadata("Updating reference set member " + member.getMemberId()))) {
				member.markChanged();
				doSaveBatchMembers(Collections.singletonList(member), commit);
				commit.markSuccessful();
			}
		}
		return member;
	}

	public PageWithBucketAggregations<ReferenceSetMember> findReferenceSetMembersWithAggregations(String branch, PageRequest pageRequest, MemberSearchRequest searchRequest) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);
		Query query = buildMemberQuery(searchRequest, branchCriteria);
		NativeQuery searchQuery = new NativeQueryBuilder()
				.withQuery(query)
				.withPageable(pageRequest)
				.withAggregation(AGGREGATION_MEMBER_COUNTS_BY_REFERENCE_SET, AggregationBuilders.terms()
						.field(ReferenceSetMember.Fields.REFSET_ID).size(refsetAggregationSearchSize).build()._toAggregation())
				.build();

		SearchAfterQueryHelper.updateQueryWithSearchAfter(searchQuery, pageRequest);
		SearchHits<ReferenceSetMember> pageResults = elasticsearchOperations.search(searchQuery, ReferenceSetMember.class);
		return PageWithBucketAggregationsFactory.createPage(pageResults, pageResults.getAggregations(), pageRequest);
	}

	public Map<String, String> findRefsetTypes(Set<String> referenceSetIds, BranchCriteria branchCriteria) {
		// Refset types are either first level children of 900000000000455006 |Refset| or are specifically configured in the Snowstorm config. For example each MRCM refset type.
		List<Long> refsetTypesFromHierarchy = eclQueryService.selectConceptIds("<!" + Concepts.REFSET, branchCriteria, false, LARGE_PAGE).getContent();
		Set<String> refsetTypesFromConfig = getConfiguredTypesMap().keySet();

		// Load the semantic index entry for all the refsets we are interested in, so we can check their ancestors
		final NativeQuery refsetQuery = new NativeQueryBuilder()
				.withQuery(bool(b -> b
						.must(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
						.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, referenceSetIds))
						.must(termQuery("stated", false)))
				)
				.withPageable(LARGE_PAGE)
				.build();
		final List<QueryConcept> refsetConceptIndexEntry = elasticsearchOperations.search(refsetQuery, QueryConcept.class).stream()
				.map(SearchHit::getContent).toList();

		// Build map of refset id to type id
		Map<String, String> refsetToTypeMap = new HashMap<>();
		for (QueryConcept semanticIndexRefsetConcept : refsetConceptIndexEntry) {
			String conceptId = semanticIndexRefsetConcept.getConceptIdL().toString();
			if (refsetTypesFromHierarchy.contains(semanticIndexRefsetConcept.getConceptIdL())) {
				// Refset is actually a type, map to itself
				refsetToTypeMap.put(conceptId, conceptId);
			} else {
				String type = null;
				if (refsetTypesFromConfig.contains(conceptId)) {
					// Refset matches one of the configured types. These are generally more specific than those in the hierarchy.
					type = conceptId;
				} else {
					for (Long ancestor : semanticIndexRefsetConcept.getAncestors()) {
						if (refsetTypesFromConfig.contains(ancestor.toString())) {
							// Refset ancestor matches one of the configured types. These are generally more specific than those in the hierarchy.
							type = ancestor.toString();
						}
					}
				}
				if (type == null) {
					for (Long ancestor : semanticIndexRefsetConcept.getAncestors()) {
						if (refsetTypesFromHierarchy.contains(ancestor)) {
							// Refset ancestor matches on of the "type" refsets from the hierarchy
							type = ancestor.toString();
						}
					}
				}
				refsetToTypeMap.put(conceptId, type);
			}
		}
		return refsetToTypeMap;
	}

	public Map<String, ReferenceSetType> getConfiguredTypesMap() {
		return referenceSetTypesConfigurationService.getConfiguredTypes().stream().collect(Collectors.toMap(ReferenceSetType::getConceptId, Function.identity()));
	}
}
