package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.DomainEntity;
import io.kaicode.elasticvc.repositories.BranchRepository;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.elasticsearch.index.query.QueryBuilders.*;

@Service
// Snowstorm branch service has some methods in addition to the ElasticVC library service.
public class SBranchService {

	@Autowired
	private ElasticsearchRestTemplate elasticsearchTemplate;

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

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public Page<Branch> findAllVersionsAfterOrEqualToTimestamp(String path, Date timestamp, Pageable pageable) {
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(QueryBuilders.termQuery("path", path))
						.must(QueryBuilders.rangeQuery("start").gte(timestamp.getTime())))
				.withSort(SortBuilders.fieldSort("start"))
				.withPageable(pageable);
		SearchHits<Branch> searchHits = elasticsearchTemplate.search(queryBuilder.build(), Branch.class);
		return new PageImpl<>(searchHits.get().map(SearchHit::getContent).collect(Collectors.toList()),
				pageable, searchHits.getTotalHits());
	}

	public List<Branch> findByPathAndBaseTimepoint(Set<String> path, Date baseTimestamp) {
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(termsQuery("path", path))
						.must(termQuery("base", baseTimestamp.getTime())))
				.withSort(SortBuilders.fieldSort("start"))
				.withPageable(PageRequest.of(0, path.size()));
		return elasticsearchTemplate.search(queryBuilder.build(), Branch.class)
				.stream().map(SearchHit::getContent).collect(Collectors.toList());
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
		//Unlock the branch we're going to end.  Rollback commit will want to lock it anew
		latest = branchService.unlock(branchPath);

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
		if (latestCompleteCommit == null) {
			throw new IllegalStateException("Branch " + branchPath + " either not found, or has been marked as ended.");
		}
		for (Class<? extends DomainEntity> entityType : domainEntityConfiguration.getAllDomainEntityTypes()) {
			NativeSearchQuery query = new NativeSearchQueryBuilder().withQuery(
					boolQuery()
							.must(termQuery("path", branchPath))
							.must(rangeQuery("start").gt(latestCompleteCommit.getStart().getTime())))
					.build();
			List<? extends DomainEntity> domainEntities = elasticsearchTemplate.search(query, entityType).stream().map(SearchHit::getContent).collect(Collectors.toList());
			if (!domainEntities.isEmpty()) {
				return domainEntities.get(0).getStart();
			}
		}
		return null;
	}

	public void unEndIfAllBranchesEnded(String branchPath, long endTimePoint) {
		//We must FAIL to find an ended branch for this function to work
		Branch latestBranch = branchService.findLatest(branchPath);
		if (latestBranch != null) {
			throw new IllegalStateException("Branch " + branchPath + " is not in an 'all-ended' state.  Function not required.");
		}
		
		latestBranch = branchService.findLatest(branchPath, true); //Include ended branches
		if (latestBranch == null) {
			throw new IllegalStateException("Branch " + branchPath + " does not exist.");
		}
		
		if (!latestBranch.getEnd().equals(new Date(endTimePoint))) {
			throw new IllegalStateException("Incorrect end time supplied, unwilling to proceed.");
		}
		
		logger.warn("Removing end time on ended branch {}. Previously ended at {}. No non-ended branch exists.", branchPath, latestBranch.getEnd());
		latestBranch.setEnd(null);
		branchRepository.save(latestBranch);
	}
}
