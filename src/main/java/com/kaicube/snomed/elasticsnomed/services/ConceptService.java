package com.kaicube.snomed.elasticsnomed.services;

import com.kaicube.snomed.elasticsnomed.domain.*;
import com.kaicube.snomed.elasticsnomed.repositories.ConceptRepository;
import com.kaicube.snomed.elasticsnomed.repositories.DescriptionRepository;
import com.kaicube.snomed.elasticsnomed.repositories.RelationshipRepository;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.*;

import static org.elasticsearch.index.query.QueryBuilders.*;

@Service
public class ConceptService {

	@Autowired
	private ConceptRepository conceptRepository;

	@Autowired
	private DescriptionRepository descriptionRepository;

	@Autowired
	private RelationshipRepository relationshipRepository;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ElasticsearchTemplate elasticsearchTemplate;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public Concept find(String id, String path) {
		final Page<Concept> concepts = doFind(id, path, new PageRequest(0, 1));
		Concept concept = concepts.getTotalElements() == 0 ? null : concepts.iterator().next();
		logger.info("Find id:{}, path:{} found:{}", id, path, concept);
		return concept;
	}

	public Page<Concept> findAll(String path, PageRequest pageRequest) {
		return doFind(null, path, pageRequest);
	}

	private Page<Concept> doFind(String id, String path, PageRequest pageRequest) {
		final BoolQueryBuilder branchCriteria = getBranchCriteria(path);

		final BoolQueryBuilder builder = boolQuery()
				.must(branchCriteria);
		if (id != null) {
			builder.must(queryStringQuery(id).field("conceptId"));
		}

		final NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(builder)
				.withSort(SortBuilders.fieldSort("path").order(SortOrder.DESC))
				.withSort(SortBuilders.fieldSort("commit").order(SortOrder.DESC))
				.withSort(SortBuilders.fieldSort("conceptId").order(SortOrder.ASC))
				.withPageable(pageRequest);

		final Page<Concept> concepts = elasticsearchTemplate.queryForPage(queryBuilder.build(), Concept.class);

		Map<String, Concept> conceptIdMap = new HashMap<>();
		for (Concept concept : concepts) {
			conceptIdMap.put(concept.getConceptId(), concept);
			concept.getDescriptions().clear();
			concept.getRelationships().clear();
		}

		// Fetch Descriptions
		queryBuilder.withQuery(boolQuery()
				.must(termsQuery("conceptId", conceptIdMap.keySet()))
				.must(branchCriteria))
				.withPageable(new PageRequest(0, 10000)); // FIXME: this is temporary
		final Page<Description> descriptions = elasticsearchTemplate.queryForPage(queryBuilder.build(), Description.class);
		// Join Descriptions
		for (Description description : descriptions) {
			conceptIdMap.get(description.getConceptId()).addDescription(description);
		}

		// Fetch Relationships
		queryBuilder.withQuery(boolQuery()
				.must(termsQuery("sourceId", conceptIdMap.keySet()))
				.must(branchCriteria))
				.withPageable(new PageRequest(0, 10000)); // FIXME: this is temporary
		final List<Relationship> relationships = elasticsearchTemplate.queryForList(queryBuilder.build(), Relationship.class);
		// Join Relationships
		for (Relationship relationship : relationships) {
			conceptIdMap.get(relationship.getSourceId()).addRelationship(relationship);
		}

		return concepts;
	}

	private BoolQueryBuilder getBranchCriteria(String path) {
		final BoolQueryBuilder branchCriteria = boolQuery();
		final Branch branch = branchService.find(path);
		if (branch == null) {
			throw new IllegalArgumentException("Branch '" + path + "' does not exist.");
		}

		branchCriteria.should(boolQuery()
				.must(queryStringQuery(branch.getFlatPath()).field("path"))
		);

		final String parentPath = PathUtil.getParentPath(path);
		if (parentPath != null) {
			final Branch parentBranch = branchService.find(parentPath);
			branchCriteria.should(boolQuery()
					.must(queryStringQuery(parentBranch.getFlatPath()).field("path"))
					.must(rangeQuery("commit").lte(branch.getBase().getTime()))
			);
		}
		return branchCriteria;
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
		for (int i = 0; i < size && i < chunkSize * 3; i += chunkSize) {
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
		return doSave(Collections.singleton(concept), branch).iterator().next();
	}

	private Iterable<Concept> doSave(Iterable<Concept> concepts, Branch branch) {
		Date commit = new Date();
		List<Description> descriptions = new ArrayList<>();
		List<Relationship> relationships = new ArrayList<>();
		int conceptCount = 0;
		for (Concept concept : concepts) {
			conceptCount++;
			setConceptMeta(concept, branch, commit);
			descriptions.addAll(concept.getDescriptions());
			concept.getDescriptions().clear();
			relationships.addAll(concept.getRelationships());
			concept.getRelationships().clear();
		}
		logger.info("Concepts {}", conceptCount);
		logger.info("descriptions {}", descriptions.size());
		logger.info("relationships {}", relationships.size());
		final Iterable<Concept> saved = conceptRepository.save(concepts);
		if (!descriptions.isEmpty()) {
			descriptionRepository.save(descriptions);
		}
		if (!relationships.isEmpty()) {
			relationshipRepository.save(relationships);
		}
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
		descriptionRepository.deleteAll();
		relationshipRepository.deleteAll();
	}
}
