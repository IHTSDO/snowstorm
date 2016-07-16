package com.kaicube.snomed.elasticsnomed.services;

import com.kaicube.snomed.elasticsnomed.domain.*;
import com.kaicube.snomed.elasticsnomed.repositories.ConceptRepository;
import com.kaicube.snomed.elasticsnomed.repositories.DescriptionRepository;
import com.kaicube.snomed.elasticsnomed.repositories.ReferenceSetMemberRepository;
import com.kaicube.snomed.elasticsnomed.repositories.RelationshipRepository;
import org.elasticsearch.common.Strings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

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
	private ReferenceSetMemberRepository referenceSetMemberRepository;

	@Autowired
	private BranchService branchService;

	@Autowired
	private VersionControlHelper versionControlHelper;

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
		final BoolQueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(path);

		final BoolQueryBuilder builder = boolQuery()
				.must(branchCriteria);
		if (id != null) {
			builder.must(queryStringQuery(id).field("conceptId"));
		}

		final NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(builder)
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
		Map<String, Description> descriptionIdMap = new HashMap<>();
		for (Description description : descriptions) {
			descriptionIdMap.put(description.getDescriptionId(), description);
			conceptIdMap.get(description.getConceptId()).addDescription(description);
		}

		// Fetch Lang Refset Members
		queryBuilder.withQuery(boolQuery()
				.must(termsQuery("referencedComponentId", descriptionIdMap.keySet()))
				.must(termQuery("active", true))
				.must(branchCriteria))
				.withPageable(new PageRequest(0, 10000)); // FIXME: this is temporary
		final Page<LanguageReferenceSetMember> langRefsetMembers = elasticsearchTemplate.queryForPage(queryBuilder.build(), LanguageReferenceSetMember.class);
		// Join Lang Refset Members
		for (LanguageReferenceSetMember langRefsetMember : langRefsetMembers) {
			descriptionIdMap.get(langRefsetMember.getReferencedComponentId())
					.addAcceptability(langRefsetMember.getRefsetId(), langRefsetMember.getAcceptabilityId());
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

	public Page<Description> findDescriptions(String path, String term, PageRequest pageRequest) {
		final BoolQueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(path);

		final BoolQueryBuilder builder = boolQuery()
				.must(branchCriteria);
		if (!Strings.isNullOrEmpty(term)) {
			builder.must(simpleQueryStringQuery(term).field("term"));
		}

		final NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(builder)
				.withSort(SortBuilders.scoreSort())
				.withPageable(pageRequest);

		final NativeSearchQuery build = queryBuilder.build();
		return elasticsearchTemplate.queryForPage(build, Description.class);
	}

	public Concept create(Concept conceptVersion, String path) {
		final Branch branch = branchService.findBranchOrThrow(path);
		if (find(conceptVersion.getConceptId(), path) != null) {
			throw new IllegalArgumentException("Concept '" + conceptVersion.getConceptId() + "' already exists on branch '" + path + "'.");
		}
		return doSave(conceptVersion, branch);
	}

	public ReferenceSetMember create(ReferenceSetMember referenceSetMember, String path) {
		final Branch branch = branchService.findBranchOrThrow(path);
		if (find(referenceSetMember.getMemberId(), path) != null) {
			throw new IllegalArgumentException("Reference Set Member '" + referenceSetMember.getMemberId() + "' already exists on branch '" + path + "'.");
		}
		return doSave(referenceSetMember, branch);

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

	private Concept doSave(Concept concept, Branch branch) {
		final Commit commit = branchService.openCommit(branch.getFatPath());
		final Concept savedConcept = doSaveBatchConceptsAndComponents(Collections.singleton(concept), commit).iterator().next();
		branchService.completeCommit(commit);
		return savedConcept;
	}

	private ReferenceSetMember doSave(ReferenceSetMember member, Branch branch) {
		final Commit commit = branchService.openCommit(branch.getFatPath());
		final ReferenceSetMember savedMember = doSaveBatchMembers(Collections.singleton(member), commit).iterator().next();
		branchService.completeCommit(commit);
		return savedMember;
	}

	public Iterable<Concept> doSaveBatchConceptsAndComponents(Collection<Concept> concepts, Commit commit) {
		List<Description> descriptions = new ArrayList<>();
		List<Relationship> relationships = new ArrayList<>();
		for (Concept concept : concepts) {
			// Detach concept's components to be persisted separately
			descriptions.addAll(concept.getDescriptions());
			concept.getDescriptions().clear();
			relationships.addAll(concept.getRelationships());
			concept.getRelationships().clear();
		}

		final Iterable<Concept> conceptsSaved = doSaveBatchConcepts(concepts, commit);
		doSaveBatchDescriptions(descriptions, commit);
		doSaveBatchRelationships(relationships, commit);
		return conceptsSaved;
	}

	public Iterable<Concept> doSaveBatchConcepts(Collection<Concept> concepts, Commit commit) {
		if (!concepts.isEmpty()) {
			logger.info("Saving batch of {} concepts", concepts.size());
			final List<String> ids = concepts.stream().map(Concept::getConceptId).collect(Collectors.toList());
			versionControlHelper.endOldVersions(commit, "conceptId", Concept.class, ids, this.conceptRepository);
			versionControlHelper.setEntityMeta(concepts, commit);
			return conceptRepository.save(concepts);
		}
		return Collections.emptyList();
	}

	public void doSaveBatchDescriptions(Collection<Description> descriptions, Commit commit) {
		if (!descriptions.isEmpty()) {
			logger.info("Saving batch of {} descriptions", descriptions.size());
			final List<String> ids = descriptions.stream().map(Description::getDescriptionId).collect(Collectors.toList());
			versionControlHelper.endOldVersions(commit, "descriptionId", Description.class, ids, this.descriptionRepository);
			versionControlHelper.setEntityMeta(descriptions, commit);
			descriptionRepository.save(descriptions);
		}
	}

	public void doSaveBatchRelationships(Collection<Relationship> relationships, Commit commit) {
		if (!relationships.isEmpty()) {
			logger.info("Saving batch of {} relationships", relationships.size());
			final List<String> ids = relationships.stream().map(Relationship::getRelationshipId).collect(Collectors.toList());
			versionControlHelper.endOldVersions(commit, "relationshipId", Relationship.class, ids, this.relationshipRepository);
			versionControlHelper.setEntityMeta(relationships, commit);
			relationshipRepository.save(relationships);
		}
	}

	public Iterable<ReferenceSetMember> doSaveBatchMembers(Collection<ReferenceSetMember> members, Commit commit) {
		if (!members.isEmpty()) {
			logger.info("Saving batch of {} members", members.size());
			final List<String> ids = members.stream().map(ReferenceSetMember::getMemberId).collect(Collectors.toList());
			versionControlHelper.endOldVersions(commit, "memberId", ReferenceSetMember.class, ids, this.referenceSetMemberRepository);
			versionControlHelper.setEntityMeta(members, commit);
			return referenceSetMemberRepository.save(members);
		}
		return Collections.emptyList();
	}

	public void deleteAll() {
		conceptRepository.deleteAll();
		descriptionRepository.deleteAll();
		relationshipRepository.deleteAll();
		referenceSetMemberRepository.deleteAll();
	}
}
