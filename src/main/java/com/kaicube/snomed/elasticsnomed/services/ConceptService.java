package com.kaicube.snomed.elasticsnomed.services;

import com.kaicube.snomed.elasticsnomed.domain.*;
import com.kaicube.snomed.elasticsnomed.repositories.ConceptRepository;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.*;

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

	public Concept find(String id, String path) {

		final BoolQueryBuilder branchCriteria = boolQuery();
		final Branch branch = branchService.find(path);
		if (branch == null) {
			return null;
		}

		branchCriteria.should(boolQuery()
				.must(queryStringQuery(branch.getFlatPath()).field("path"))
		);

		final String parentPath = PathUtil.getParentPath(path);
		if (parentPath != null) {
			final Branch parentBranch = branchService.find(parentPath);
			branchCriteria.should(boolQuery()
					.must(queryStringQuery(parentBranch.getFlatPath()).field("path"))
					.must(rangeQuery("commit").lte(branch.getBase()))
			);
		}

		final BoolQueryBuilder builder = boolQuery()
				.must(queryStringQuery(id).field("conceptId"))
				.must(branchCriteria);

		final NativeSearchQuery query = new NativeSearchQueryBuilder()
				.withQuery(builder)
				.withSort(SortBuilders.fieldSort("path").order(SortOrder.DESC))
				.withSort(SortBuilders.fieldSort("commit").order(SortOrder.DESC))
				.withPageable(new PageRequest(0, 1))
				.build();
		final List<Concept> concepts = elasticsearchTemplate.queryForList(query, Concept.class);

		final Concept concept = !concepts.isEmpty() ? concepts.get(0) : null;

		logger.info("Find id:{}, path:{} found:{}", id, path, concept);
		return concept;
	}

	public Concept create(Concept conceptVersion, String path) {
		final Branch branch = branchService.findBranchOrThrow(path);
		if (find(conceptVersion.getConceptId(), path) != null) {
			throw new IllegalArgumentException("Concept '" + conceptVersion.getConceptId() + "' already exists on branch '" + path + "'.");
		}

		return doSave(conceptVersion, branch);
	}

	public Concept update(Concept conceptVersion, String path) {
		final Branch branch = branchService.findBranchOrThrow(path);
		final String conceptId = conceptVersion.getConceptId();
		if (conceptId == null) {
			throw new IllegalArgumentException("conceptId must not be null.");
		}
		final Concept existingConcept = find(conceptId, path);
		if (existingConcept == null) {
			throw new IllegalArgumentException("Concept '" + conceptId + "' does not exist on branch '" + path + "'.");
		}

		return doSave(conceptVersion, branch);
	}

	public void bulkImport(Collection<Concept> concepts, String path) {
		final Branch branch = branchService.findBranchOrThrow(path);

		List<Concept> conceptList = new ArrayList<>(concepts);
		final int size = concepts.size();
		final int chunkSize = 10000;
		int start, end;
		for (int i = 0; i < size; i += chunkSize) {
			start = i;
			end = start + chunkSize;
			if (end > size - 1) {
				end = size - 1;
			}
			logger.info("Bulk Import Saving Chunk {} - {} of {}", start, end, size);
			doSave(conceptList.subList(start, end), branch);
		}
	}

	private Concept doSave(Concept concept, Branch branch) {
		Date commit = new Date();
		setConceptMeta(concept, branch, commit);
		final Concept saved = conceptRepository.save(concept);
		branchService.updateBranchHead(branch, commit);
		return saved;
	}

	private Iterable<Concept> doSave(Iterable<Concept> concepts, Branch branch) {
		Date commit = new Date();
		for (Concept concept : concepts) {
			setConceptMeta(concept, branch, commit);
		}
		final Iterable<Concept> saved = conceptRepository.save(concepts);
		branchService.updateBranchHead(branch, commit);
		return saved;
	}

	private void setConceptMeta(Concept concept, Branch branch, Date commit) {
		setComponentMeta(concept, branch, commit);
		for (Description description : concept.getDescriptions()) {
			setComponentMeta(description, branch, commit);
		}
		for (Relationship relationship : concept.getRelationships()) {
			setComponentMeta(relationship, branch, commit);
		}
	}

	private void setComponentMeta(Component component, Branch branch, Date commit) {
		component.setPath(branch.getPath());
		component.setCommit(commit);
	}

	public void deleteAll() {
		conceptRepository.deleteAll();
	}

	public Iterable<Concept> findAll(String path) {
		return conceptRepository.findByPath(PathUtil.flaten(path));
	}

	public Iterable<Concept> findAll(String path, PageRequest pageRequest) {
		return conceptRepository.findByPath(PathUtil.flaten(path), pageRequest);
	}
}
