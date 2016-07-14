package com.kaicube.snomed.elasticsnomed.services;

import com.kaicube.snomed.elasticsnomed.domain.Branch;
import com.kaicube.snomed.elasticsnomed.repositories.BranchRepository;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

	public void updateBranch(Branch branch, Date commit) {
		branch.setHead(commit);

		Set<Branch> toSave = new HashSet<>();
		toSave.add(branch);

		final String internalId = branch.getInternalId();
		branch.clearInternalId();
		branch.setStart(commit);

		if (internalId != null) {
			final Branch oldBranch = branchRepository.findOne(internalId);
			oldBranch.setEnd(commit);
			toSave.add(oldBranch);
		}

		branchRepository.save(toSave);
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
}
