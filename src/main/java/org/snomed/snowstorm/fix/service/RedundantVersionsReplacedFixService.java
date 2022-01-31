package org.snomed.snowstorm.fix.service;

import com.google.common.collect.Iterables;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Commit;
import io.kaicode.elasticvc.domain.Entity;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.repositories.ReferenceSetMemberRepository;
import org.snomed.snowstorm.core.data.services.NotFoundException;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.*;

@Service
public class RedundantVersionsReplacedFixService {

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ReferenceSetMemberRepository memberRepository;

	@Autowired
	private VersionControlHelper versionControlHelper;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public String reduceMembersReplaced(String branchPath) {
		if (PathUtil.isRoot(branchPath)) {
			throw new IllegalArgumentException("This fix can is not relevant to the root branch");
		}

		// Get latest version of the branch
		SearchHits<Branch> hits = elasticsearchOperations.search(new NativeSearchQueryBuilder().withQuery(
				boolQuery()
						.must(termQuery(Branch.Fields.PATH, branchPath))
						.mustNot(existsQuery("end")))
				.build(), Branch.class);
		if (hits.isEmpty()) {
			throw new NotFoundException("Branch not found.");
		}
		Branch branch = hits.getSearchHit(0).getContent();

		// Get branch version and parent branch version of the members replaced
		Set<String> membersReplacedInternalIds = branch.getVersionsReplaced().getOrDefault(ReferenceSetMember.class.getSimpleName(), Collections.emptySet());

		Set<String> membersReplacedToClear = new HashSet<>();
		List<ReferenceSetMember> membersToEnd = new ArrayList<>();

		// Work in batches
		int batchSize = 1000;
		long batchStart = 0;
		long clearedTotalCount = 0;
		long endedTotalCount = 0;
		for (List<String> internalIdBatch : Iterables.partition(membersReplacedInternalIds, batchSize)) {
			logger.info("Checking {}-{} of {}", batchStart, batchStart + internalIdBatch.size(), membersReplacedInternalIds.size());
			batchStart += internalIdBatch.size();

			Map<String, List<ReferenceSetMember>> membersReplacedMap = new HashMap<>();
			elasticsearchOperations.search(
					new NativeSearchQueryBuilder()
							.withQuery(termsQuery("_id", internalIdBatch))
							.withSort(SortBuilders.fieldSort("start").order(SortOrder.DESC))
							.withPageable(PageRequest.of(0, batchSize)).build(),
					ReferenceSetMember.class).stream().map(SearchHit::getContent)
					.forEach(member -> membersReplacedMap.computeIfAbsent(member.getMemberId(), key -> new ArrayList<>()).add(member));


			for (List<ReferenceSetMember> membersReplaced : membersReplacedMap.values()) {
				for (ReferenceSetMember memberReplaced : membersReplaced) {
					// If the member hidden is on the same branch and already ended then the entry is redundant
					if (memberReplaced.getPath().equals(branchPath) && memberReplaced.getEnd() != null) {
						membersReplacedToClear.add(memberReplaced.getInternalId());
					}
				}
			}

			List<ReferenceSetMember> currentVersionMembersOnThisBranch = referenceSetMemberService.findMembers(branchPath, membersReplacedMap.keySet()).stream()
					.filter(member -> member.getPath().equals(branchPath))
					.collect(Collectors.toList());

			List<Branch> visibleTimeSlice = versionControlHelper.getTimeSlice(branchPath, branch.getHead());

			// Do comparison and collect documents to clear
			for (ReferenceSetMember currentVersionMember : currentVersionMembersOnThisBranch) {
				List<ReferenceSetMember> membersReplaced = membersReplacedMap.get(currentVersionMember.getMemberId());
				for (ReferenceSetMember memberReplaced : membersReplaced) {
					if (!memberReplaced.getInternalId().equals(currentVersionMember.getInternalId()) &&
							memberReplaced.buildReleaseHash().equals(currentVersionMember.buildReleaseHash()) &&
							withinVisibleTimeSlice(memberReplaced, visibleTimeSlice)) {
						// Current version on branch is redundant.
						// End version of member on this branch.
						membersToEnd.add(currentVersionMember);
						// Restore visibility of the existing document
						membersReplacedToClear.add(memberReplaced.getInternalId());
					}
				}
			}
		}

		if (!membersReplacedToClear.isEmpty() || !membersToEnd.isEmpty()) {
			try (Commit commit = branchService.openCommit(branchPath, "Admin fix: redundant member entries in versionsReplaced map.")) {
				Map<String, Set<String>> versionsReplaced = commit.getBranch().getVersionsReplaced();
				versionsReplaced.getOrDefault(ReferenceSetMember.class.getSimpleName(), Collections.emptySet()).removeAll(membersReplacedToClear);
				commit.getBranch().setVersionsReplaced(versionsReplaced);

				membersToEnd.forEach(member -> member.setEnd(commit.getTimepoint()));
				for (List<ReferenceSetMember> membersToEndBatch : Iterables.partition(membersToEnd, 1000)) {
					memberRepository.saveAll(membersToEndBatch);
				}
				commit.markSuccessful();
			}
			clearedTotalCount += membersReplacedToClear.size();
			endedTotalCount += membersToEnd.size();
		}

		String message = String.format("Cleared %s versionsReplaced, ended %s documents.", clearedTotalCount, endedTotalCount);
		logger.info(message);
		return message;
	}

	private boolean withinVisibleTimeSlice(Entity entity, List<Branch> visibleTimeSlice) {
		for (Branch branchVersion : visibleTimeSlice) {
			if (branchVersion.getPath().equals(entity.getPath())) {
				return (entity.getStart().before(branchVersion.getStart()) || entity.getStart().equals(branchVersion.getStart()))
						&& (entity.getEnd() == null || entity.getEnd().after(branchVersion.getStart()));
			}
		}
		return false;
	}

}
