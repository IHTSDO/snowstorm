package com.kaicube.snomed.elasticsnomed.services;

import com.kaicube.snomed.elasticsnomed.domain.Branch;
import com.kaicube.snomed.elasticsnomed.domain.Commit;
import com.kaicube.snomed.elasticsnomed.domain.Entity;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;
import org.springframework.util.Assert;

import java.util.*;

import static org.elasticsearch.index.query.QueryBuilders.*;

public class VersionControlHelper {

	@Autowired
	private BranchService branchService;

	@Autowired
	private ElasticsearchTemplate elasticsearchTemplate;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	QueryBuilder getBranchCriteriaWithinOpenCommit(Commit commit) {
		return getBranchCriteria(commit.getBranch(), commit.getTimepoint(), commit.getEntityVersionsReplaced());
	}

	QueryBuilder getBranchCriteria(String path) {
		final Branch branch = branchService.findLatest(path);
		if (branch == null) {
			throw new IllegalArgumentException("Branch '" + path + "' does not exist.");
		}
		return getBranchCriteria(branch, branch.getHead(), branch.getVersionsReplaced());
	}

	private BoolQueryBuilder getBranchCriteria(Branch branch, Date timepoint, Set<String> versionsReplaced) {
		final BoolQueryBuilder branchCriteria = boolQuery();
		branchCriteria.should(
							boolQuery()
								.must(queryStringQuery(branch.getFlatPath()).field("path"))
								.must(rangeQuery("start").lte(timepoint))
								.mustNot(existsQuery("end"))
					);

		addParentCriteriaRecursively(branchCriteria, branch, versionsReplaced);

		return branchCriteria;
	}

	void addParentCriteriaRecursively(BoolQueryBuilder branchCriteria, Branch branch, Set<String> versionsReplaced) {
		String parentPath = PathUtil.getParentPath(branch.getFatPath());
		if (parentPath != null) {
			final Branch parentBranch = branchService.findAtTimepointOrThrow(parentPath, branch.getBase());
			versionsReplaced = new HashSet<>(versionsReplaced);
			versionsReplaced.addAll(parentBranch.getVersionsReplaced());
			final Date base = branch.getBase();
			branchCriteria.should(boolQuery()
					.must(queryStringQuery(parentBranch.getFlatPath()).field("path"))
					.must(rangeQuery("start").lte(base))
					.must(boolQuery()
							.should(boolQuery().mustNot(existsQuery("end")))
							.should(rangeQuery("end").gt(base)))
					.mustNot(termsQuery("internalId", versionsReplaced))
			);
			addParentCriteriaRecursively(branchCriteria, parentBranch, versionsReplaced);
		}
	}

	<T extends Entity> void endOldVersions(Commit commit, String idField, Class<T> clazz, Collection<? extends Object> ids, ElasticsearchCrudRepository repository) {
		// Find versions replaced across all paths
		final String path = commit.getBranch().getFatPath();
		final List<T> localVersionsToEnd = elasticsearchTemplate.queryForList(new NativeSearchQueryBuilder()
				.withQuery(
					new BoolQueryBuilder()
						.must(termsQuery(idField, ids))
						.must(termQuery("path", commit.getFlatBranchPath()))
						.must(rangeQuery("start").lt(commit.getTimepoint()))
						.mustNot(existsQuery("end"))
				)
				.withPageable(new PageRequest(0, 10000)) // FIXME: this is temporary
				.build(), clazz);
		if (!localVersionsToEnd.isEmpty()) {
			for (Entity replacedVersion : localVersionsToEnd) {
				replacedVersion.setEnd(commit.getTimepoint());
			}
			repository.save(localVersionsToEnd);
		}

		// Find versions to end across all paths
		final List<? extends Entity> replacedVersions = elasticsearchTemplate.queryForList(new NativeSearchQueryBuilder().withQuery(
				new BoolQueryBuilder()
						.must(termsQuery(idField, ids))
						.must(getBranchCriteria(commit.getBranch().getFatPath()))
						.must(rangeQuery("start").lt(commit.getTimepoint()))
						.mustNot(existsQuery("end"))
				)
				.withPageable(new PageRequest(0, 10000)) // FIXME: this is temporary
				.build(), clazz);
		if (!replacedVersions.isEmpty()) {
			for (Entity replacedVersion : replacedVersions) {
				commit.addVersionReplaced(replacedVersion.getInternalId());
			}
			repository.save(replacedVersions);
		}
		logger.info("{} old versions of {} replaced.", replacedVersions.size(), clazz);
	}

	void setEntityMeta(Entity entity, Commit commit) {
		Assert.notNull(entity, "Entity must not be null");
		Assert.notNull(commit, "Commit must not be null");
		doSetEntityMeta(commit, entity);
	}

	void setEntityMeta(Collection<? extends Entity> entities, Commit commit) {
		Assert.notNull(entities, "Entities must not be null");
		Assert.notNull(commit, "Commit must not be null");
		for (Entity entity : entities) {
			doSetEntityMeta(commit, entity);
		}
	}

	private void doSetEntityMeta(Commit commit, Entity entity) {
		entity.setPath(commit.getFlatBranchPath());
		entity.setStart(commit.getTimepoint());
		entity.clearInternalId();
	}
}
