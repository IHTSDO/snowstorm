package com.kaicube.snomed.elasticsnomed.services;

import com.kaicube.snomed.elasticsnomed.domain.Branch;
import com.kaicube.snomed.elasticsnomed.domain.Entity;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;
import org.springframework.util.Assert;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

public class VersionControlHelper {

	@Autowired
	private BranchService branchService;

	@Autowired
	private ElasticsearchTemplate elasticsearchTemplate;

	BoolQueryBuilder getBranchCriteria(String path) {
		final BoolQueryBuilder branchCriteria = boolQuery();
		final Branch branch = branchService.findLatest(path);
		if (branch == null) {
			throw new IllegalArgumentException("Branch '" + path + "' does not exist.");
		}

		branchCriteria.should(boolQuery()
				.must(queryStringQuery(branch.getFlatPath()).field("path"))
				.must(rangeQuery("start").lte(branch.getHead()))
				.must(boolQuery().mustNot(existsQuery("end")))
		);

		addParentCriteriaRecursively(branchCriteria, branch, branch.getVersionsReplaced());

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

	void endOldVersions(Branch branch, Date commit, String idField, Class<? extends Entity> clazz, Set<String> ids, ElasticsearchCrudRepository repository, ConceptService conceptService) {
		final List<? extends Entity> replacedVersions = elasticsearchTemplate.queryForList(new NativeSearchQueryBuilder().withQuery(
				new BoolQueryBuilder()
						.must(termsQuery(idField, ids))
						.must(termQuery("path", branch.getFlatPath()))
						.must(rangeQuery("start").lt(commit))
						.mustNot(existsQuery("end"))
				).build(), clazz);
		if (!replacedVersions.isEmpty()) {
			for (Entity replacedVersion : replacedVersions) {
				replacedVersion.setEnd(commit);
				branch.addVersionReplaced(replacedVersion.getInternalId());
			}
			repository.save(replacedVersions);
		}
	}

	void setEntityMeta(Entity entity, Branch branch, Date commit) {
		Assert.notNull(entity, "Entity must not be null");
		Assert.notNull(branch, "Branch must not be null");
		entity.setPath(branch.getPath());
		entity.setStart(commit);
		entity.clearInternalId();
	}
}
