package com.kaicube.snomed.elasticsnomed.services;

import com.kaicube.snomed.elasticsnomed.domain.Branch;
import com.kaicube.snomed.elasticsnomed.domain.Concept;
import com.kaicube.snomed.elasticsnomed.repositories.ConceptRepository;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static org.elasticsearch.index.query.QueryBuilders.*;

@Service
public class ConceptService {

	@Autowired
	private ConceptRepository conceptRepository;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ElasticsearchTemplate elasticsearchTemplate;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public Concept find(Long id, String path) {

		final BoolQueryBuilder branchCriteria = boolQuery();
		final Branch branch = branchService.find(path);
		if (branch == null) {
			return null;
		}

		branchCriteria.should(boolQuery()
				.must(queryStringQuery(branch.getPath()).field("path"))
		);

		final String parentPath = PathUtil.getParentPath(path);
		if (parentPath != null) {
			final Branch parentBranch = branchService.find(parentPath);
			branchCriteria.should(boolQuery()
					.must(queryStringQuery(parentBranch.getPath()).field("path"))
					.must(rangeQuery("commit").lte(branch.getBase()))
			);
		}

		final BoolQueryBuilder builder = boolQuery()
				.must(termQuery("conceptId", id))
				.must(branchCriteria);

		final NativeSearchQuery query = new NativeSearchQueryBuilder()
				.withQuery(builder)
				.build();
		final List<Concept> concepts = elasticsearchTemplate.queryForList(query, Concept.class);

		final Iterator<Concept> iterator = concepts.iterator();
		final Concept concept = iterator.hasNext() ? iterator.next() : null;
		logger.info("Find id:{}, path:{} found:{}", id, path, concept);
		return concept;
	}

	public void create(Concept conceptVersion, String path) {
		final Branch branch = branchService.find(path);
		if (branch == null) {
			throw new IllegalArgumentException("Branch '" + path + "' does not exist.");
		}
		Date commit = new Date();
		conceptVersion.setId(UUID.randomUUID().toString());
		conceptVersion.setPath(path);
		conceptVersion.setCommit(commit);
		conceptRepository.save(conceptVersion);
		branchService.updateBranchHead(branch, commit);
	}

	public void deleteAll() {
		conceptRepository.deleteAll();
	}

	public Iterable<Concept> findAll(String path) {
		return conceptRepository.findByPath(path);
	}
}
