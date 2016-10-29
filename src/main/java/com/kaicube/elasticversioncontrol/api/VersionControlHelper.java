package com.kaicube.elasticversioncontrol.api;

import com.kaicube.elasticversioncontrol.domain.Branch;
import com.kaicube.elasticversioncontrol.domain.Commit;
import com.kaicube.elasticversioncontrol.domain.DomainEntity;
import com.kaicube.elasticversioncontrol.domain.Entity;
import com.kaicube.snomed.elasticsnomed.domain.LanguageReferenceSetMember;
import com.kaicube.snomed.elasticsnomed.domain.ReferenceSetMember;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;
import org.springframework.data.util.CloseableIterator;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.*;

public class VersionControlHelper {

	@Autowired
	private BranchService branchService;

	@Autowired
	private ElasticsearchTemplate elasticsearchTemplate;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public QueryBuilder getBranchCriteriaWithinOpenCommit(Commit commit) {
		return getBranchCriteria(commit.getBranch(), commit.getTimepoint(), commit.getEntityVersionsReplaced(), false);
	}

	public QueryBuilder getBranchCriteria(String path) {
		return getBranchCriteria(getBranchOrThrow(path));
	}

	public QueryBuilder getBranchCriteria(Branch branch) {
		return getBranchCriteria(branch, branch.getHead(), branch.getVersionsReplaced(), false);
	}

	public QueryBuilder getChangesOnBranchCriteria(String path) {
		final Branch branch = getBranchOrThrow(path);
		return getBranchCriteria(branch, branch.getHead(), branch.getVersionsReplaced(), true);
	}

	public BoolQueryBuilder getUpdatesOnBranchDuringRangeCriteria(String path, Date start, Date end) {
		final Branch branch = getBranchOrThrow(path);
		return boolQuery()
				.must(termQuery("path", branch.getFlatPath()))
				.must(boolQuery()
						.should(rangeQuery("start").gte(start).lte(end))
						.should(rangeQuery("end").gte(start).lte(end))
				);
	}

	private Branch getBranchOrThrow(String path) {
		final Branch branch = branchService.findLatest(path);
		if (branch == null) {
			throw new IllegalArgumentException("Branch '" + path + "' does not exist.");
		}
		return branch;
	}

	private BoolQueryBuilder getBranchCriteria(Branch branch, Date timepoint, Set<String> versionsReplaced, boolean onlyChangesOnBranch) {
		final BoolQueryBuilder branchCriteria = boolQuery();
		branchCriteria.should(
				boolQuery()
						.must(termQuery("path", branch.getFlatPath()))
						.must(rangeQuery("start").lte(timepoint))
						.mustNot(existsQuery("end"))
		);

		if (!onlyChangesOnBranch) {
			addParentCriteriaRecursively(branchCriteria, branch, versionsReplaced);
		}

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
					.must(termQuery("path", parentBranch.getFlatPath()))
					.must(rangeQuery("start").lte(base))
					.must(boolQuery()
							.should(boolQuery().mustNot(existsQuery("end")))
							.should(rangeQuery("end").gt(base)))
					.mustNot(termsQuery("_id", versionsReplaced))
			);
			addParentCriteriaRecursively(branchCriteria, parentBranch, versionsReplaced);
		}
	}

	<T extends Entity> void endOldVersions(Commit commit, String idField, Class<T> clazz, Collection<? extends Object> ids, ElasticsearchCrudRepository repository) {
		// End versions of the entity on this path by setting end date
		final NativeSearchQuery query = new NativeSearchQueryBuilder()
				.withQuery(
						new BoolQueryBuilder()
								.must(termQuery("path", commit.getFlatBranchPath()))
								.must(rangeQuery("start").lt(commit.getTimepoint()))
								.mustNot(existsQuery("end"))
				)
				.withFilter(
						new BoolQueryBuilder()
								.must(termsQuery(idField, ids))
				)
				.build();

		List<T> toSave = new ArrayList<>();
		try (final CloseableIterator<T> localVersionsToEnd = elasticsearchTemplate.stream(query, clazz)) {
			localVersionsToEnd.forEachRemaining(version -> {
				version.setEnd(commit.getTimepoint());
				toSave.add(version);
			});
		}
		if (!toSave.isEmpty()) {
			repository.save(toSave);
			toSave.clear();
		}

		// Hide versions of the entity on other paths from this branch
		final NativeSearchQuery query2 = new NativeSearchQueryBuilder()
				.withQuery(
						new BoolQueryBuilder()
								.must(getBranchCriteriaWithinOpenCommit(commit))
								.must(rangeQuery("start").lt(commit.getTimepoint()))
								.mustNot(existsQuery("end"))
				)
				.withFilter(
						new BoolQueryBuilder()
								.must(termsQuery(idField, ids))
				)
				.build();

		AtomicLong replacedVersionsCount = new AtomicLong(0);

		Class classToLoad = clazz.equals(ReferenceSetMember.class) ? LanguageReferenceSetMember.class : clazz; // TODO: how can we do this implicitly

		try (final CloseableIterator<T> replacedVersions = elasticsearchTemplate.stream(query2, classToLoad)) {
			replacedVersions.forEachRemaining(version -> {
				commit.addVersionReplaced(version.getInternalId());
				replacedVersionsCount.incrementAndGet();
			});
		}
		logger.info("{} old versions of {} replaced.", replacedVersionsCount.get(), clazz);
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
		entity.setEnd(null);
		entity.clearInternalId();
	}

	public <C extends DomainEntity> void removeDeleted(Collection<C> entities) {
		entities.removeAll(entities.stream().filter(Entity::isDeleted).collect(Collectors.toSet()));
	}
}
