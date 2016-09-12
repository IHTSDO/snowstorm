package com.kaicube.elasticversioncontrol.api;

import com.kaicube.elasticversioncontrol.domain.Branch;
import com.kaicube.elasticversioncontrol.domain.Commit;
import com.kaicube.elasticversioncontrol.domain.Component;
import com.kaicube.elasticversioncontrol.domain.Entity;
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
		return getBranchCriteria(commit.getBranch(), commit.getTimepoint(), commit.getEntityVersionsReplaced());
	}

	public QueryBuilder getBranchCriteria(String path) {
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
								.must(getBranchCriteria(commit.getBranch().getFatPath()))
								.must(rangeQuery("start").lt(commit.getTimepoint()))
								.mustNot(existsQuery("end"))
				)
				.withFilter(
						new BoolQueryBuilder()
								.must(termsQuery(idField, ids))
				)
				.build();

		AtomicLong replacedVersionsCount = new AtomicLong(0);
		try (final CloseableIterator<T> replacedVersions = elasticsearchTemplate.stream(query2, clazz)) {
			replacedVersions.forEachRemaining(version -> {
				commit.addVersionReplaced(version.getInternalId());
				toSave.add(version);
				replacedVersionsCount.incrementAndGet();
			});
		}
		if (!toSave.isEmpty()) {
			repository.save(toSave);// TODO: Why is this needed?
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
		entity.clearInternalId();
	}

	public <C extends Component> void removeDeleted(Collection<C> entities) {
		entities.removeAll(entities.stream().filter(Entity::isDeleted).collect(Collectors.toSet()));
	}
}
