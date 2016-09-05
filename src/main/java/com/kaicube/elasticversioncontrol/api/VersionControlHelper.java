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
import org.springframework.data.domain.PageRequest;
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
		// Find versions to end on this branch
		final NativeSearchQuery componentsToEndQuery = new NativeSearchQueryBuilder()
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
				.withPageable(new PageRequest(0, 10000))
				.build();
		List<Entity> componentVersionsToEnd = new ArrayList<>();
		try (final CloseableIterator<T> localVersionsToEnd = elasticsearchTemplate.stream(componentsToEndQuery, clazz)) {
			localVersionsToEnd.forEachRemaining(versionToEnd -> {
				versionToEnd.setEnd(commit.getTimepoint());
				componentVersionsToEnd.add(versionToEnd);
			});
		}
		long componentVersionsEnded = componentVersionsToEnd.size();
		if (!componentVersionsToEnd.isEmpty()) {
			repository.save(componentVersionsToEnd);
			componentVersionsToEnd.clear();
		}

		// Find versions replaced across all paths
		final NativeSearchQuery componentsReplacedQuery = new NativeSearchQueryBuilder()
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
				.withPageable(new PageRequest(0, 10000))
				.build();
		AtomicLong componentVersionsReplacedCount = new AtomicLong(0);
		try (final CloseableIterator<T> componentVersionsReplaced = elasticsearchTemplate.stream(componentsReplacedQuery, clazz)) {
			componentVersionsReplaced.forEachRemaining(replacedVersion -> {
				commit.addVersionReplaced(replacedVersion.getInternalId());
				componentVersionsReplacedCount.incrementAndGet();
			});
		}
		logger.info(" - {} versions of {} replaced, {} ended on this branch.", componentVersionsReplacedCount.get(), clazz, componentVersionsEnded);
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
