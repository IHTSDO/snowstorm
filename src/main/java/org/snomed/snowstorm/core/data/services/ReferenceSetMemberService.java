package org.snomed.snowstorm.core.data.services;

import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Iterables;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.Metadata;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RegexpQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.langauges.ecl.domain.filter.MemberFieldFilter;
import org.snomed.langauges.ecl.domain.filter.MemberFilterConstraint;
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
import org.snomed.snowstorm.ecl.ECLQueryService;
import org.snomed.snowstorm.rest.converter.SearchAfterHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.snomed.snowstorm.config.Config.AGGREGATION_SEARCH_SIZE;
import static org.snomed.snowstorm.core.data.domain.Concepts.inactivationAndAssociationRefsets;
import static org.snomed.snowstorm.core.data.services.CodeSystemService.MAIN;
import static org.snomed.snowstorm.core.util.CollectionUtils.orEmpty;

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
	private ElasticsearchOperations elasticsearchTemplate;

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

	private final Cache<String, AsyncRefsetMemberChangeBatch> batchChanges = CacheBuilder.newBuilder().expireAfterWrite(2, TimeUnit.HOURS).build();

	private final Logger logger = LoggerFactory.getLogger(getClass());

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
		NativeSearchQuery query = new NativeSearchQueryBuilder().withQuery(buildMemberQuery(searchRequest, branch, branchCriteria)).withPageable(pageRequest).build();
		query.setTrackTotalHits(true);
		SearchHits<ReferenceSetMember> searchHits = elasticsearchTemplate.search(query, ReferenceSetMember.class);
		PageImpl<ReferenceSetMember> referenceSetMembers = new PageImpl<>(searchHits.get().map(SearchHit::getContent).collect(Collectors.toList()), query.getPageable(), searchHits.getTotalHits());

		return PageHelper.toSearchAfterPage(referenceSetMembers, REFERENCE_SET_MEMBER_ID_SEARCH_AFTER_EXTRACTOR);
	}

	public Page<ReferenceSetMember> findMembers(String branch, BranchCriteria branchCriteria, MemberSearchRequest searchRequest, PageRequest pageRequest) {
		NativeSearchQuery query = new NativeSearchQueryBuilder().withQuery(buildMemberQuery(searchRequest, branch, branchCriteria)).withPageable(pageRequest).build();
		SearchHits<ReferenceSetMember> searchHits = elasticsearchTemplate.search(query, ReferenceSetMember.class);
		return new PageImpl<>(searchHits.get().map(SearchHit::getContent).collect(Collectors.toList()), pageRequest, searchHits.getTotalHits());
	}

	private BoolQueryBuilder buildMemberQuery(MemberSearchRequest searchRequest, String branch, BranchCriteria branchCriteria) {
		BoolQueryBuilder query = boolQuery().must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class));

		if (searchRequest.getActive() != null) {
			query.must(termQuery(ReferenceSetMember.Fields.ACTIVE, searchRequest.getActive()));
		}
		
		if (searchRequest.isNullEffectiveTime() != null) {
			if (searchRequest.isNullEffectiveTime()) {
				query.mustNot(existsQuery(SnomedComponent.Fields.EFFECTIVE_TIME));
			} else {
				query.must(existsQuery(SnomedComponent.Fields.EFFECTIVE_TIME));
			}
		}
		
		String referenceSet = searchRequest.getReferenceSet();
		if (!Strings.isNullOrEmpty(referenceSet)) {
			List<Long> conceptIds = getConceptIds(branch, branchCriteria, referenceSet);
			query.must(termsQuery(ReferenceSetMember.Fields.REFSET_ID, conceptIds));
		}
		String module = searchRequest.getModule();
		if (!Strings.isNullOrEmpty(module)) {
			List<Long> conceptIds = getConceptIds(branch, branchCriteria, module);
			query.must(termsQuery(ReferenceSetMember.Fields.MODULE_ID, conceptIds));
		}
		Set<String> referencedComponentIds = searchRequest.getReferencedComponentIds();
		if (referencedComponentIds != null && referencedComponentIds.size() > 0) {
			query.must(termsQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, referencedComponentIds));
		}
		
		Map<String, String> additionalFields = searchRequest.getAdditionalFields();
		for (String additionalFieldName : additionalFields.keySet()) {
			String additionalFieldNameValue = additionalFields.get(additionalFieldName);
			if (!Strings.isNullOrEmpty(additionalFieldNameValue)) {
				String fieldKeyword = ReferenceSetMember.Fields.getAdditionalFieldKeywordTypeMapping(additionalFieldName);
				query.must(termQuery(fieldKeyword, additionalFieldNameValue));
			}
		}
		
		Map<String, Set<String>> additionalFieldSets = searchRequest.getAdditionalFieldSets();
		for (String additionalFieldName : additionalFieldSets.keySet()) {
			Set<String> additionalFieldNameValues = additionalFieldSets.get(additionalFieldName);
			if (additionalFieldNameValues != null && additionalFieldNameValues.size() > 0) {
				String fieldKeyword = ReferenceSetMember.Fields.getAdditionalFieldKeywordTypeMapping(additionalFieldName);
				query.must(termsQuery(fieldKeyword, additionalFieldNameValues));
			}
		}
		
		String owlExpressionConceptId = searchRequest.getOwlExpressionConceptId();
		if (!Strings.isNullOrEmpty(owlExpressionConceptId)) {
			query.must(regexpQuery(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION_KEYWORD_FIELD_PATH, String.format(".*:%s[^0-9].*", owlExpressionConceptId)));
		}
		Boolean owlExpressionGCI = searchRequest.getOwlExpressionGCI();
		if (owlExpressionGCI != null) {
			RegexpQueryBuilder gciClause = regexpQuery(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION_KEYWORD_FIELD_PATH, "SubClassOf\\(Object.*");
			if (owlExpressionGCI) {
				query.must(gciClause);
			} else {
				query.mustNot(gciClause);
			}
		}
		return query;
	}

	private List<Long> getConceptIds(String branch, BranchCriteria branchCriteria, String conceptIdOrECL) {
		List<Long> conceptIds;
		if (conceptIdOrECL.matches("\\d+")) {
			conceptIds = Collections.singletonList(parseLong(conceptIdOrECL));
		} else {
			conceptIds = eclQueryService.selectConceptIds(conceptIdOrECL, branchCriteria, branch, true, LARGE_PAGE).getContent();
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
		BoolQueryBuilder query = boolQuery()
				.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
				.must(termsQuery(ReferenceSetMember.Fields.MEMBER_ID, uuids));
		return elasticsearchTemplate.search(new NativeSearchQueryBuilder().withQuery(query).withPageable(LARGE_PAGE).build(), ReferenceSetMember.class)
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
				member.setMemberId(UUID.randomUUID().toString());
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
		NativeSearchQuery query = new NativeSearchQueryBuilder().withQuery(boolQuery()
				.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
				.must(termsQuery(ReferenceSetMember.Fields.MEMBER_ID, uuids)))
				.withPageable(PageRequest.of(0, uuids.size()))
				.build();
		List<ReferenceSetMember> matches = elasticsearchTemplate.search(query, ReferenceSetMember.class).stream().map(SearchHit::getContent).collect(Collectors.toList());
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

		} catch (IllegalArgumentException | IllegalStateException e) {
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
			final NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();

			Long2ObjectMap<Description> descriptionsFromStore = new Long2ObjectOpenHashMap<>();
			for (List<Long> descriptionIdsSegment : Iterables.partition(descriptionIds, CLAUSE_LIMIT)) {
				queryBuilder
						.withQuery(boolQuery()
								.must(termsQuery(Description.Fields.DESCRIPTION_ID, descriptionIdsSegment))
								.must(versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit).getEntityBranchCriteria(Description.class)))
						.withFields(Description.Fields.CONCEPT_ID, Description.Fields.DESCRIPTION_ID)
						.withPageable(LARGE_PAGE);
				try (final SearchHitsIterator<Description> descriptions = elasticsearchTemplate.searchForStream(queryBuilder.build(), Description.class)) {
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

	public Set<Long> findConceptsInReferenceSet(String referenceSetId, List<MemberFilterConstraint> memberFilterConstraints, BranchCriteria branchCriteria) {
		// Build query

		BoolQueryBuilder memberQuery = boolQuery().must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class));

		if (memberFilterConstraints != null) {
			for (MemberFilterConstraint memberFilterConstraint : memberFilterConstraints) {
				for (MemberFieldFilter fieldFilter : orEmpty(memberFilterConstraint.getMemberFieldFilters())) {
					String fieldName = fieldFilter.getFieldName();

					if (fieldFilter.getExpressionComparisonOperator() != null) {
						// TODO
					} else if (fieldFilter.getNumericComparisonOperator() != null) {
						
					} else if (fieldFilter.getStringComparisonOperator() != null) {

					} else if (fieldFilter.getTimeComparisonOperator() != null) {

					}
				}
			}
		}



		memberQuery
				.must(termQuery(SnomedComponent.Fields.ACTIVE, true))
				.must(regexpQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, ".*0."));// Matches the concept partition identifier
		// Allow searching across all refsets
		if (referenceSetId != null) {
			memberQuery.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, referenceSetId));
		}

		// Build search query
		NativeSearchQuery query = new NativeSearchQueryBuilder()
				.withQuery(memberQuery)
				.withFields(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID)
				.withSort(new FieldSortBuilder("_doc"))// Fastest unordered sort
				.withPageable(LARGE_PAGE)
				.build();

		// Stream results
		Set<Long> conceptIds = new LongArraySet();
		try (SearchHitsIterator<ReferenceSetMember> stream = elasticsearchTemplate.searchForStream(query, ReferenceSetMember.class)) {
			stream.forEachRemaining(member -> conceptIds.add(parseLong(member.getContent().getReferencedComponentId())));
		}
		return conceptIds;
	}

	public void init() {
		Set<ReferenceSetType> configuredTypes = referenceSetTypesConfigurationService.getConfiguredTypes();
		setupTypes(configuredTypes);
	}

	private void setupTypes(Set<ReferenceSetType> referenceSetTypes) {
		String path = MAIN;
		if (!branchService.exists(path)) {
			sBranchService.create(path);
		}
		List<ReferenceSetType> existingTypes = findConfiguredReferenceSetTypes(path);
		Set<ReferenceSetType> typesToRemove = new HashSet<>(existingTypes);
		typesToRemove.removeAll(referenceSetTypes);
		if (!typesToRemove.isEmpty()) {
			String message = String.format("Removing reference set types: %s", typesToRemove.toString());
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
		QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(path).getEntityBranchCriteria(ReferenceSetType.class);
		NativeSearchQuery query = new NativeSearchQueryBuilder().withQuery(branchCriteria).withPageable(LARGE_PAGE).build();
		return elasticsearchTemplate.search(query, ReferenceSetType.class).stream().map(SearchHit::getContent).collect(Collectors.toList());
	}

	//TODO If this could be called during a rebase, include the source branch and pass existingRebaseSourceMember into copyReleaseDetails
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
		BoolQueryBuilder query = buildMemberQuery(searchRequest, branch, branchCriteria);
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(query)
				.withPageable(pageRequest)
				.addAggregation(AggregationBuilders.terms(AGGREGATION_MEMBER_COUNTS_BY_REFERENCE_SET).field(ReferenceSetMember.Fields.REFSET_ID).size(AGGREGATION_SEARCH_SIZE))
				.build();

		SearchHits<ReferenceSetMember> pageResults = elasticsearchTemplate.search(searchQuery, ReferenceSetMember.class);
		return PageWithBucketAggregationsFactory.createPage(pageResults, pageResults.getAggregations(), pageRequest);
	}

	public Map<String, String> findRefsetTypes(Set<String> referenceSetIds, BranchCriteria branchCriteria, String branch) {
		List<Long> allRefsetTypes = eclQueryService.selectConceptIds("<!" + Concepts.REFSET, branchCriteria, branch, false, LARGE_PAGE).getContent();

		final NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
						.must(termsQuery(QueryConcept.Fields.CONCEPT_ID, referenceSetIds))
						.must(termQuery("stated", false))
				)
				.withPageable(LARGE_PAGE)
				.build();
		final List<QueryConcept> concepts = elasticsearchTemplate.search(searchQuery, QueryConcept.class).stream().map(SearchHit::getContent).collect(Collectors.toList());

		Map<String, String> refsetTypes = new HashMap<>();
		for (QueryConcept concept : concepts) {
			String conceptId = concept.getConceptIdL().toString();
			if (allRefsetTypes.contains(concept.getConceptIdL())) {
				refsetTypes.put(conceptId, conceptId);
			} else {
				for (Long ancestor : concept.getAncestors()) {
					if (allRefsetTypes.contains(ancestor)) {
						refsetTypes.put(conceptId, ancestor.toString());
					}
				}
			}
		}
		return refsetTypes;
	}
}
