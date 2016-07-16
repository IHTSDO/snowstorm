package com.kaicube.snomed.elasticsnomed.services;

import com.google.common.collect.Lists;
import com.kaicube.snomed.elasticsnomed.domain.Branch;
import com.kaicube.snomed.elasticsnomed.domain.Commit;
import com.kaicube.snomed.elasticsnomed.repositories.BranchRepository;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Date;
import java.util.List;

import static com.kaicube.snomed.elasticsnomed.services.PathUtil.getParentPath;
import static org.elasticsearch.index.query.QueryBuilders.*;

@Service
public class BranchService {

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private ElasticsearchTemplate elasticsearchTemplate;

	final Logger logger = LoggerFactory.getLogger(getClass());

	public Branch create(String path) {
		Assert.notNull(path, "Branch path can not be null.");
		Assert.isTrue(!path.contains("_"), "Branch path may not contain the underscore character.");

		logger.debug("Creating branch {}", path);
		Date commit = new Date();
		if (findLatest(path) != null) {
			throw new IllegalArgumentException("Branch '" + path + "' already exists.");
		}
		final String parentPath = getParentPath(path);
		if (parentPath != null) {
			final Branch parentBranch = findLatest(parentPath);
			if (parentBranch == null) {
				throw new IllegalStateException("Parent branch '" + parentPath + "' does not exist.");
			}
			logger.debug("Parent branch {}", parentBranch);
		}

		final Branch branch = new Branch(path);
		branch.setBase(commit);
		branch.setHead(commit);
		branch.setStart(commit);
		logger.debug("Persisting branch {}", branch);
		return branchRepository.save(branch);
	}

	public void deleteAll() {
		branchRepository.deleteAll();
	}

	public Branch findLatest(String path) {
		final List<Branch> branches = elasticsearchTemplate.queryForList(new NativeSearchQueryBuilder().withQuery(
				new BoolQueryBuilder()
						.must(termQuery("path", PathUtil.flaten(path)))
						.mustNot(existsQuery("end"))
		).build(), Branch.class);

		Assert.isTrue(branches.size() < 2, "There should not be more than one version of a branch with no end date.");
		return branches.isEmpty() ? null : branches.get(0);
	}

	public Branch findBranchOrThrow(String path) {
		final Branch branch = findLatest(path);
		if (branch == null) {
			throw new IllegalArgumentException("Branch '" + path + "' does not exist.");
		}
		return branch;
	}

	public Branch findAtTimepointOrThrow(String path, Date base) {
		final List<Branch> branches = elasticsearchTemplate.queryForList(new NativeSearchQueryBuilder().withQuery(
				new BoolQueryBuilder()
						.must(termQuery("path", PathUtil.flaten(path)))
						.must(rangeQuery("start").lte(base))
						.must(boolQuery()
								.should(boolQuery().mustNot(existsQuery("end")))
								.should(rangeQuery("end").gt(base)))
			).build(), Branch.class);
		Assert.isTrue(branches.size() < 2, "There should not be more than one version of a branch at a single timepoint.");
		if (branches.isEmpty()) {
			throw new IllegalStateException("Branch '" + path + "' does not exist at timepoint " + base + ".");
		}

		return branches.get(0);
	}

	public List<Branch> findAll() {
		return elasticsearchTemplate.queryForList(new NativeSearchQueryBuilder()
				.withQuery(new BoolQueryBuilder().mustNot(existsQuery("end")))
				.withSort(new FieldSortBuilder("path"))
				.build(), Branch.class);
	}

	public List<Branch> findChildren(String path) {
		return elasticsearchTemplate.queryForList(new NativeSearchQueryBuilder()
				.withQuery(new BoolQueryBuilder().mustNot(existsQuery("end")).must(prefixQuery("path", PathUtil.flaten(path + "/"))))
				.withSort(new FieldSortBuilder("path"))
				.build(), Branch.class);
	}

	public Commit openCommit(String path) {
		Branch branch = findLatest(path);
		if (branch.isLocked()) {
			throw new IllegalStateException("Branch already locked");
		}

		branch.setLocked(true);
		branch = branchRepository.save(branch);

		return new Commit(branch);
	}

	public void completeCommit(Commit commit) {
		final Date timepoint = commit.getTimepoint();
		final Branch oldBranchTimespan = commit.getBranch();
		oldBranchTimespan.setEnd(timepoint);
		oldBranchTimespan.setLocked(false);

		final Branch newBranchTimespan = new Branch(oldBranchTimespan.getPath());
		newBranchTimespan.setBase(oldBranchTimespan.getBase());
		newBranchTimespan.setStart(timepoint);
		newBranchTimespan.setHead(timepoint);
		newBranchTimespan.addVersionsReplaced(oldBranchTimespan.getVersionsReplaced());
		newBranchTimespan.addVersionsReplaced(commit.getEntityVersionsReplaced());
		newBranchTimespan.addEntitiesRemoved(oldBranchTimespan.getEntitiesRemoved());
		branchRepository.save(Lists.newArrayList(oldBranchTimespan, newBranchTimespan));
	}

	// TODO - Implement commit rollback; simply delete all entities at commit timepoint from branch and remove write lock.
}
