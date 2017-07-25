package org.ihtsdo.elasticsnomed.core.data.services;

import com.google.common.base.Strings;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.ComponentService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.domain.ReferenceSetType;
import org.ihtsdo.elasticsnomed.core.data.domain.ReferenceSetMember;
import org.ihtsdo.elasticsnomed.core.data.repositories.ReferenceSetMemberRepository;
import org.ihtsdo.elasticsnomed.core.data.repositories.ReferenceSetTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Service
public class ReferenceSetMemberService extends ComponentService {

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ReferenceSetMemberRepository memberRepository;

	@Autowired
	private ReferenceSetTypeRepository typeRepository;

	@Autowired
	private ReferenceSetTypesConfigurationService referenceSetTypesConfigurationService;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public Page<ReferenceSetMember> findMembers(String branch,
												String referencedComponentId,
												PageRequest pageRequest) {

		return findMembers(branch, null, null, referencedComponentId, null, pageRequest);
	}

	public Page<ReferenceSetMember> findMembers(String branch,
												Boolean active,
												String referenceSetId,
												String referencedComponentId,
												String targetComponentId,
												PageRequest pageRequest) {

		QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(branch);

		BoolQueryBuilder query = boolQuery().must(branchCriteria);

		if (active != null) {
			query.must(termQuery("active", active));
		}
		if (!Strings.isNullOrEmpty(referenceSetId)) {
			query.must(termQuery("refsetId", referenceSetId));
		}
		if (!Strings.isNullOrEmpty(referencedComponentId)) {
			query.must(termQuery("referencedComponentId", referencedComponentId));
		}
		if (!Strings.isNullOrEmpty(targetComponentId)) {
			query.must(termQuery("additionalFields.targetComponentId", targetComponentId));
		}

		return elasticsearchTemplate.queryForPage(new NativeSearchQueryBuilder()
				.withQuery(query).withPageable(pageRequest).build(), ReferenceSetMember.class);
	}

	public ReferenceSetMember findMember(String branch, String uuid) {
		QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(branch);
		BoolQueryBuilder query = boolQuery().must(branchCriteria)
				.must(termQuery("memberId", uuid));
		List<ReferenceSetMember> referenceSetMembers = elasticsearchTemplate.queryForList(new NativeSearchQueryBuilder()
				.withQuery(query).build(), ReferenceSetMember.class);
		if (!referenceSetMembers.isEmpty()) {
			return referenceSetMembers.get(0);
		}
		return null;
	}

	public void deleteMember(String branch, String uuid) {
		QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(branch);
		List<ReferenceSetMember> matches = elasticsearchTemplate.queryForList(new NativeSearchQueryBuilder().withQuery(
				boolQuery().must(branchCriteria)
						.must(termQuery("memberId", uuid))
		).build(), ReferenceSetMember.class);

		if (matches.isEmpty()) {
			throw new NotFoundException(String.format("Reference set member %s not found on branch %s", uuid, branch));
		}

		try (Commit commit = branchService.openCommit(branch)) {
			ReferenceSetMember member = matches.get(0);
			member.markDeleted();
			doSaveBatchComponents(Collections.singleton(member), commit, "memberId", memberRepository);
			commit.markSuccessful();
		}
	}

	public void init() {
		Set<ReferenceSetType> configuredTypes = referenceSetTypesConfigurationService.getConfiguredTypes();
		setupTypes(configuredTypes);
	}

	private void setupTypes(Set<ReferenceSetType> referenceSetTypes) {
		String path = "MAIN";
		if (!branchService.exists(path)) {
			branchService.create(path);
		}
		QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(path);
		NativeSearchQuery query = new NativeSearchQueryBuilder().withQuery(branchCriteria).withPageable(LARGE_PAGE).build();
		List<ReferenceSetType> existingTypes = elasticsearchTemplate.queryForList(query, ReferenceSetType.class);

		HashSet<ReferenceSetType> typesToAdd = new HashSet<>(referenceSetTypes);
		typesToAdd.removeAll(existingTypes);
		if (!typesToAdd.isEmpty()) {
			logger.info("Setting up {} reference set types.", typesToAdd.size());
			try (Commit commit = branchService.openCommit(path)) {
				doSaveBatchComponents(typesToAdd, commit, ReferenceSetType.FIELD_ID, typeRepository);
				commit.markSuccessful();
			}
		}
	}
}
