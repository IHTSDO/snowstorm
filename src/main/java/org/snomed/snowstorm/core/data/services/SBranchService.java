package org.snomed.snowstorm.core.data.services;

import co.elastic.clients.json.JsonData;
import io.kaicode.elasticvc.api.BranchNotFoundException;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.DomainEntity;
import io.kaicode.elasticvc.domain.Metadata;
import io.kaicode.elasticvc.repositories.BranchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.services.classification.BranchClassificationStatusService;
import org.snomed.snowstorm.core.data.services.servicehook.CommitServiceHookClient;
import org.snomed.snowstorm.rest.pojo.SetAuthorFlag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool;
import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.range;
import static java.lang.String.format;
import static org.snomed.snowstorm.core.data.services.BranchMetadataHelper.AUTHOR_FLAGS_METADATA_KEY;
import static org.snomed.snowstorm.core.data.services.BranchMetadataHelper.INTERNAL_METADATA_KEY;
import static org.snomed.snowstorm.core.data.services.IntegrityService.INTEGRITY_ISSUE_METADATA_KEY;
import static io.kaicode.elasticvc.helper.QueryHelper.termsQuery;
import static io.kaicode.elasticvc.helper.QueryHelper.termQuery;

@Service
// Snowstorm branch service has some methods in addition to the ElasticVC library service.
public class SBranchService {

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private DomainEntityConfiguration domainEntityConfiguration;

	@Autowired
	private AdminOperationsService adminOperationsService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private CommitServiceHookClient commitServiceHookClient;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public Branch create(String branch) {
		return create(branch, null);
	}

	public Branch create(String branchPath, Map<String, Object> metadataMap) {
		if (StringUtils.hasLength(branchPath)) {
			branchPath = branchPath.toUpperCase();
		}

		// Copy classification state from parent branch
		final String parentPath = PathUtil.getParentPath(branchPath);
		final Metadata metadata = new Metadata(metadataMap);
		if (parentPath != null) {
			final Branch latest = branchService.findLatest(parentPath);
			if (latest != null) {
				final Boolean classificationStatus = BranchClassificationStatusService.getClassificationStatus(latest);
				BranchClassificationStatusService.setClassificationStatus(metadata, classificationStatus != null && classificationStatus);

				final String integrityFlag = latest.getMetadata().getMapOrCreate(INTERNAL_METADATA_KEY).get(INTEGRITY_ISSUE_METADATA_KEY);
				if (Boolean.parseBoolean(integrityFlag)) {
					metadata.getMapOrCreate(INTERNAL_METADATA_KEY).put(INTEGRITY_ISSUE_METADATA_KEY, integrityFlag);
				}
			}
		}
		final Branch branch = branchService.create(branchPath, metadata.getAsMap());

		// Simulate an empty commit for the service-hook
		commitServiceHookClient.preCommitCompletion(new Commit(branch, Commit.CommitType.CONTENT, null, null));

		return branch;
	}

	public Page<Branch> findAllVersionsAfterOrEqualToTimestampAsLightCommits(String path, Date timestamp, Pageable pageable) {
		NativeQueryBuilder queryBuilder = new NativeQueryBuilder()
				.withQuery(bool(bq -> bq
						.must(termQuery("path", path))
						.must(range(rq -> rq.field("start").gte(JsonData.of(timestamp.getTime()))))))
				.withSourceFilter(new FetchSourceFilter(new String[]{"path", "start", "end", "head", "base", "locked"}, null))
				.withSort(s -> s.field(fb -> fb.field("start")))
				.withPageable(pageable);

		SearchHits<Branch> searchHits = elasticsearchOperations.search(queryBuilder.build(), Branch.class);
		return new PageImpl<>(searchHits.get().map(SearchHit::getContent).collect(Collectors.toList()),
				pageable, searchHits.getTotalHits());
	}

	public List<Branch> findByPathAndBaseTimepoint(Set<String> path, Date baseTimestamp, Sort sort) {
		NativeQueryBuilder queryBuilder = new NativeQueryBuilder()
				.withQuery(bool(bq -> bq
						.must(termsQuery("path", path))
						.must(termQuery("base", baseTimestamp.getTime()))))
				.withSort(s -> s.field(fb -> fb.field("start")))
				.withPageable(PageRequest.of(0, path.size(), sort));
		return elasticsearchOperations.search(queryBuilder.build(), Branch.class)
				.stream().map(SearchHit::getContent).collect(Collectors.toList());
	}

	public Branch findByPathAndHeadTimepoint(String path, long head) {
		SearchHits<Branch> query = elasticsearchOperations.search(
				new NativeQueryBuilder()
						.withQuery(
								bool(bq -> bq
										.must(termQuery("path", path))
										.must(termQuery("head", head))
						))
						.withPageable(PageRequest.of(0, 1))
						.build(), Branch.class
		);

		if (query.isEmpty()) {
			return null;
		}

		return query.getSearchHit(0).getContent();
	}

	public void rollbackCommit(String branchPath, long timepoint) {
		logger.info("Preparing to roll back commit {} on {}.", timepoint, branchPath);

		Branch branchVersion = branchService.findAtTimepointOrThrow(branchPath, new Date(timepoint));
		if (branchVersion.getEnd() != null) {
			throw new IllegalStateException(format("Branch %s at timepoint %s is already ended, it's not the latest commit.", branchPath, timepoint));
		}

		final Optional<CodeSystem> codeSystem = codeSystemService.findByBranchPath(branchPath);

		final List<Branch> children = branchService.findChildren(branchPath, true);
		for (Branch child : children) {
			if (child.getBase().equals(branchVersion.getHead())) {
				final String childPath = child.getPath();
				boolean childDeleted = false;
				if (codeSystem.isPresent()) {
					// May be a version branch
					final String childBranchName = childPath.substring(childPath.lastIndexOf("/") + 1);
					final CodeSystemVersion version = codeSystemService.findVersion(codeSystem.get().getShortName(), childBranchName);
					if (version != null) {
						adminOperationsService.hardDeleteBranch(childPath);
						codeSystemService.deleteVersion(codeSystem.get(), version);
						childDeleted= true;
					}
				}
				if (!childDeleted) {
					logger.warn("Child branch {} is using the commit being rolled back. Be sure to rebase or delete this branch afterwards.", childPath);
				}
			}
		}

		branchService.rollbackCompletedCommit(branchVersion, new ArrayList<>(domainEntityConfiguration.getAllDomainEntityTypes()));
	}

	public void rollbackPartialCommit(String branchPath) {
		Branch latest = branchService.findBranchOrThrow(branchPath);
		if (!latest.isLocked()) {
			throw new IllegalStateException("Branch is not locked so no there is no partial commit.");
		}

		Date partialCommitTimestamp = getPartialCommitTimestamp(branchPath);
		if (partialCommitTimestamp == null) {
			throw new IllegalStateException("No partial commits found on this branch.");
		}

		// Temporarily complete partial commit to support rollback.
		// This does not complete the commit properly because we don't know what content is missing.
		// Rollback is still the best option.
		latest.setEnd(partialCommitTimestamp);
		Branch tempCompleteCommit = new Branch(branchPath);
		tempCompleteCommit.setCreation(latest.getCreation());
		tempCompleteCommit.setBase(latest.getBase());
		tempCompleteCommit.setStart(partialCommitTimestamp);
		tempCompleteCommit.setHead(partialCommitTimestamp);
		tempCompleteCommit.setMetadata(latest.getMetadata());
		tempCompleteCommit.setContainsContent(true);
		branchRepository.save(tempCompleteCommit);
		branchRepository.save(latest);

		logger.info("Found partial commit on {} at {}. Closing the branch at {} and then rolling back.", branchPath, partialCommitTimestamp.getTime(), partialCommitTimestamp.getTime());

		rollbackCommit(branchPath, partialCommitTimestamp.getTime());

		latest = branchService.findBranchOrThrow(branchPath);
		if (latest.isLocked()) {
			branchService.unlock(branchPath);
		}
	}

	public Date getPartialCommitTimestamp(String branchPath) {
		Branch latestCompleteCommit = branchService.findLatest(branchPath);
		for (Class<? extends DomainEntity> entityType : domainEntityConfiguration.getAllDomainEntityTypes()) {
			NativeQuery query = new NativeQueryBuilder().withQuery(
					bool(bq -> bq
							.must(termQuery("path", branchPath))
							.must(range(rq -> rq.field("start").gt(JsonData.of(latestCompleteCommit.getStart().getTime()))))))
					.build();
			List<? extends DomainEntity> domainEntities = elasticsearchOperations.search(query, entityType).stream().map(SearchHit::getContent).collect(Collectors.toList());
			if (!domainEntities.isEmpty()) {
				return domainEntities.get(0).getStart();
			}
		}
		return null;
	}

	public Branch setAuthorFlag(String branchPath, SetAuthorFlag setAuthorFlag) {
		Branch branch = branchService.findBranchOrThrow(branchPath);

		Metadata metadata = branch.getMetadata();
		Map<String, String> authFlagMap = metadata.getMapOrCreate(AUTHOR_FLAGS_METADATA_KEY);
		authFlagMap.put(setAuthorFlag.getName(), String.valueOf(setAuthorFlag.isValue()));
		metadata.putMap(AUTHOR_FLAGS_METADATA_KEY, authFlagMap);

		return branchService.updateMetadata(branchPath, metadata);
	}

	/**
	 * Return the configured module identifiers for the given branch path.
	 *
	 * @param branchPath The branch path to find module identifiers.
	 * @return The configured module identifiers for the given branch path.
	 */
	public Set<String> getModules(String branchPath) {
		Branch branch = getBranchNullable(branchPath);
		if (branch == null) {
			return Collections.emptySet();
		}

		CodeSystem codeSystem = codeSystemService.findClosestCodeSystemUsingAnyBranch(branchPath, true);
		if (codeSystem != null && "MAIN".equals(codeSystem.getBranchPath())) {
			return Set.of(Concepts.CORE_MODULE, Concepts.MODEL_MODULE, Concepts.ICD10_MODULE);
		}

		Metadata metadata = branch.getMetadata();
		if (metadata == null || metadata.size() == 0) {
			return Collections.emptySet();
		}

		Set<String> moduleIds = new HashSet<>();
		if (metadata.containsKey(BranchMetadataKeys.DEFAULT_MODULE_ID)) {
			moduleIds.add(metadata.getString(BranchMetadataKeys.DEFAULT_MODULE_ID));
		}

		if (metadata.containsKey(BranchMetadataKeys.EXPECTED_EXTENSION_MODULES)) {
			moduleIds.addAll(metadata.getList(BranchMetadataKeys.EXPECTED_EXTENSION_MODULES));
		}

		return moduleIds;
	}

	private Branch getBranchNullable(String branchPath) {
		try {
			return branchService.findBranchOrThrow(branchPath, true);
		} catch (BranchNotFoundException | IllegalArgumentException e) {
			return null;
		}
	}
}
