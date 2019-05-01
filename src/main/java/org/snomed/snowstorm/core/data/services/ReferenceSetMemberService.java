package org.snomed.snowstorm.core.data.services;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RegexpQueryBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.repositories.ReferenceSetMemberRepository;
import org.snomed.snowstorm.core.data.repositories.ReferenceSetTypeRepository;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.snomed.snowstorm.ecl.ECLQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.*;

import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.snomed.snowstorm.core.data.services.CodeSystemService.MAIN;

@Service
public class ReferenceSetMemberService extends ComponentService {

	private static final Set<String> LANG_REFSET_MEMBER_FIELD_SET = Collections.singleton(ReferenceSetMember.LanguageFields.ACCEPTABILITY_ID);
	private static final Set<String> OWL_REFSET_MEMBER_FIELD_SET = Collections.singleton(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION);

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMetadataHelper branchMetadataHelper;

	@Autowired
	private ReferenceSetMemberRepository memberRepository;

	@Autowired
	private ReferenceSetTypeRepository typeRepository;

	@Autowired
	private ReferenceSetTypesConfigurationService referenceSetTypesConfigurationService;

	@Autowired
	private ApplicationContext applicationContext;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public Page<ReferenceSetMember> findMembers(String branch,
			String referencedComponentId,
			PageRequest pageRequest) {

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

		BoolQueryBuilder query = boolQuery().must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class));

		if (searchRequest.getActive() != null) {
			query.must(termQuery(ReferenceSetMember.Fields.ACTIVE, searchRequest.getActive()));
		}
		String referenceSet = searchRequest.getReferenceSet();
		if (!Strings.isNullOrEmpty(referenceSet)) {
			List<Long> conceptIds;
			if (referenceSet.matches("\\d+")) {
				conceptIds = Collections.singletonList(parseLong(referenceSet));
			} else {
				conceptIds = getEclQueryService().selectConceptIds(referenceSet, branchCriteria, branch, true, LARGE_PAGE).getContent();
			}
			query.must(termsQuery(ReferenceSetMember.Fields.REFSET_ID, conceptIds));
		}
		String referencedComponentId = searchRequest.getReferencedComponentId();
		if (!Strings.isNullOrEmpty(referencedComponentId)) {
			query.must(termQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, referencedComponentId));
		}
		String targetComponentId = searchRequest.getTargetComponentId();
		if (!Strings.isNullOrEmpty(targetComponentId)) {
			query.must(termQuery(ReferenceSetMember.Fields.getAdditionalFieldKeywordTypeMapping("targetComponentId"), targetComponentId));
		}
		String mapTarget = searchRequest.getMapTarget();
		if (!Strings.isNullOrEmpty(mapTarget)) {
			query.must(termQuery(ReferenceSetMember.Fields.getAdditionalFieldKeywordTypeMapping("mapTarget"), mapTarget));
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

		return elasticsearchTemplate.queryForPage(new NativeSearchQueryBuilder()
				.withQuery(query).withPageable(pageRequest).build(), ReferenceSetMember.class);
	}

	private ECLQueryService getEclQueryService() {
		return applicationContext.getBeansOfType(ECLQueryService.class).values().iterator().next();
	}

	public ReferenceSetMember findMember(String branch, String uuid) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);
		BoolQueryBuilder query = boolQuery().must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
				.must(termQuery(ReferenceSetMember.Fields.MEMBER_ID, uuid));
		List<ReferenceSetMember> referenceSetMembers = elasticsearchTemplate.queryForList(new NativeSearchQueryBuilder()
				.withQuery(query).build(), ReferenceSetMember.class);
		if (!referenceSetMembers.isEmpty()) {
			return referenceSetMembers.get(0);
		}
		return null;
	}

	public ReferenceSetMember createMember(String branch, ReferenceSetMember member) {
		Iterator<ReferenceSetMember> members = createMembers(branch, Collections.singleton(member)).iterator();
		return members.hasNext() ? members.next() : null;
	}

	public Iterable<ReferenceSetMember> createMembers(String branch, Set<ReferenceSetMember> members) {
		try (final Commit commit = branchService.openCommit(branch, branchMetadataHelper.getBranchLockMetadata(String.format("Creating %s reference set members.", members.size())))) {
			members.forEach(member -> {
				member.setMemberId(UUID.randomUUID().toString());
				member.markChanged();
			});
			final Iterable<ReferenceSetMember> savedMembers = doSaveBatchMembers(members, commit);
			commit.markSuccessful();
			return savedMembers;
		}
	}

	public void deleteMember(String branch, String uuid) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);
		List<ReferenceSetMember> matches = elasticsearchTemplate.queryForList(new NativeSearchQueryBuilder().withQuery(
				boolQuery().must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						.must(termQuery(ReferenceSetMember.Fields.MEMBER_ID, uuid))
		).build(), ReferenceSetMember.class);

		if (matches.isEmpty()) {
			throw new NotFoundException(String.format("Reference set member %s not found on branch %s", uuid, branch));
		}

		try (Commit commit = branchService.openCommit(branch, branchMetadataHelper.getBranchLockMetadata("Deleting reference set member " + uuid))) {
			ReferenceSetMember member = matches.get(0);
			member.markDeleted();
			doSaveBatchComponents(Collections.singleton(member), commit, ReferenceSetMember.Fields.MEMBER_ID, memberRepository);
			commit.markSuccessful();
		}
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
							&& (member.getAdditionalFields().keySet().equals(LANG_REFSET_MEMBER_FIELD_SET))
							|| Concepts.DESCRIPTION_INACTIVATION_INDICATOR_REFERENCE_SET.equals(member.getRefsetId())) {
						// Lang refset or description inactivation indicator
						descriptionMembers.add(member);
						descriptionIds.add(parseLong(member.getReferencedComponentId()));

					} else if (IdentifierService.isConceptId(member.getReferencedComponentId())
							&& (member.getAdditionalFields().keySet().equals(OWL_REFSET_MEMBER_FIELD_SET)
							|| Concepts.inactivationAndAssociationRefsets.contains(member.getRefsetId()))) {
						// Axiom, inactivation or historical association
						member.setConceptId(member.getReferencedComponentId());
					}
				});

		if (descriptionIds.size() != 0) {
			final NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();

			Long2ObjectMap<Description> descriptionMap = new Long2ObjectOpenHashMap<>();
			for (List<Long> descriptionIdsSegment : Iterables.partition(descriptionIds, CLAUSE_LIMIT)) {
				queryBuilder
						.withQuery(boolQuery()
								.must(termsQuery("descriptionId", descriptionIdsSegment))
								.must(versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit).getEntityBranchCriteria(Description.class)))
						.withPageable(LARGE_PAGE);
				try (final CloseableIterator<Description> descriptions = elasticsearchTemplate.stream(queryBuilder.build(), Description.class)) {
					descriptions.forEachRemaining(description ->
							descriptionMap.put(parseLong(description.getDescriptionId()), description));
				}
			}

			descriptionMembers.parallelStream().forEach(member -> {
				Description description = descriptionMap.get(parseLong(member.getReferencedComponentId()));
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

	Set<Long> findConceptsInReferenceSet(BranchCriteria branchCriteria, String referenceSetId) {
		// Build query
		BoolQueryBuilder boolQuery = boolQuery().must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
				.must(termQuery(SnomedComponent.Fields.ACTIVE, true))
				.must(regexpQuery(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID, ".*0."));// Matches the concept partition identifier
		// Allow searching across all refsets
		if (referenceSetId != null) {
			boolQuery.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, referenceSetId));
		}

		// Build search query
		NativeSearchQuery query = new NativeSearchQueryBuilder()
				.withQuery(boolQuery)
				.withFields(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID)// Triggers FastResultsMapper
				.withSort(new FieldSortBuilder("_doc"))// Fastest unordered sort
				.withPageable(LARGE_PAGE)
				.build();

		// Stream results
		Set<Long> conceptIds = new LongArraySet();
		try (CloseableIterator<ReferenceSetMember> stream = elasticsearchTemplate.stream(query, ReferenceSetMember.class)) {
			stream.forEachRemaining(member -> conceptIds.add(parseLong(member.getReferencedComponentId())));
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
			branchService.create(path);
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
		return elasticsearchTemplate.queryForList(query, ReferenceSetType.class);
	}

}
