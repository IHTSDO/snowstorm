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
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

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
	private ReferenceSetMemberRepository referenceSetMemberRepository;

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
		final BoolQueryBuilder branchCriteria = getBranchCriteria(path);

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

	private BoolQueryBuilder getBranchCriteria(String path) {
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

	private void addParentCriteriaRecursively(BoolQueryBuilder branchCriteria, Branch branch, Set<String> versionsReplaced) {
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

	public Concept create(Concept conceptVersion, String path) {
		final Branch branch = branchService.findBranchOrThrow(path);
		if (find(conceptVersion.getConceptId(), path) != null) {
			throw new IllegalArgumentException("Concept '" + conceptVersion.getConceptId() + "' already exists on branch '" + path + "'.");
		}
		return doSave(conceptVersion, branch, new Date());
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
		final Date commit = new Date();
		final Concept existingConcept = find(conceptId, path);
		if (existingConcept == null) {
			throw new IllegalArgumentException("Concept '" + conceptId + "' does not exist on branch '" + path + "'.");
		}

		return doSave(conceptVersion, branch, commit);
	}

	public void bulkImport(List<Concept> concepts, List<ReferenceSetMember> members, String path) {
		final Date commit = new Date();
		final Branch branch = branchService.findBranchOrThrow(path);

		final int chunkSize = 10000;
		int start, end;
		int size = concepts.size();
		for (int i = 0; i < size && i < chunkSize * 3; i += chunkSize) {
			start = i;
			end = start + chunkSize;
			if (end > size) {
				end = size;
			}
			logger.info("Bulk Import Saving Concepts Chunk {} - {} of {}", start, end, size);
			doSaveConceptBatch(concepts.subList(start, end), branch, commit);
		}

		size = members.size();
		for (int i = 0; i < size; i += chunkSize) {
			start = i;
			end = start + chunkSize;
			if (end > size) {
				end = size;
			}
			logger.info("Bulk Import Saving Reference Set Members Chunk {} - {} of {}", start, end, size);
			doSaveMembersBatch(members.subList(start, end), branch, commit);
		}

		branchService.updateBranch(branch, commit);
	}

	private Concept doSave(Concept concept, Branch branch, Date commit) {
		final Concept savedConcept = doSaveConceptBatch(Collections.singleton(concept), branch, commit).iterator().next();
		branchService.updateBranch(branch, commit);
		return savedConcept;
	}

	private ReferenceSetMember doSave(ReferenceSetMember member, Branch branch) {
		final Date commit = new Date();
		final ReferenceSetMember savedMember = doSaveMembersBatch(Collections.singleton(member), branch, commit).iterator().next();
		branchService.updateBranch(branch, commit);
		return savedMember;
	}

	private Iterable<Concept> doSaveConceptBatch(Iterable<Concept> concepts, Branch branch, Date commit) {
		List<Description> descriptions = new ArrayList<>();
		List<Relationship> relationships = new ArrayList<>();
		Set<String> conceptIds = new HashSet<>();
		Set<String> descriptionIds = new HashSet<>();
		int conceptCount = 0;
		for (Concept concept : concepts) {
			conceptCount++;
			conceptIds.add(concept.getConceptId());
			setConceptMeta(concept, branch, commit);
			descriptions.addAll(concept.getDescriptions());
			for (Description description : concept.getDescriptions()) {
				descriptionIds.add(description.getDescriptionId());
			}
			concept.getDescriptions().clear();
			relationships.addAll(concept.getRelationships());
			concept.getRelationships().clear();
		}

		// End old versions
		endOldVersions(branch, commit, "conceptId", Concept.class, conceptIds, this.conceptRepository);
		endOldVersions(branch, commit, "descriptionId", Description.class, descriptionIds, this.descriptionRepository);

		logger.info("Saving batch of {} concepts", conceptCount);
		final Iterable<Concept> saved = conceptRepository.save(concepts);
		logger.info("Saving batch of {} descriptions", descriptions.size());
		if (!descriptions.isEmpty()) {
			descriptionRepository.save(descriptions);
		}
		logger.info("Saving batch of {} relationships", relationships.size());
		if (!relationships.isEmpty()) {
			relationshipRepository.save(relationships);
		}
		return saved;
	}

	private void endOldVersions(Branch branch, Date commit, String idField, Class<? extends Entity> clazz, Set<String> ids, ElasticsearchCrudRepository repository) {
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

	private Iterable<ReferenceSetMember> doSaveMembersBatch(Iterable<ReferenceSetMember> members, Branch branch, Date commit) {
		int memberCount = 0;
		for (ReferenceSetMember member : members) {
			if (member != null) {
				memberCount++;
				setEntityMeta(member, branch, commit);
			} else {
				logger.warn("Member null {}", memberCount);
			}
		}
		logger.info("Saving batch of {} reference set members", memberCount);
		return referenceSetMemberRepository.save(members);
	}

	private void setConceptMeta(Concept concept, Branch branch, Date commit) {
		setEntityMeta(concept, branch, commit);
		for (Description description : concept.getDescriptions()) {
			setEntityMeta(description, branch, commit);
		}
		for (Relationship relationship : concept.getRelationships()) {
			setEntityMeta(relationship, branch, commit);
		}
	}

	private void setEntityMeta(Entity entity, Branch branch, Date commit) {
		Assert.notNull(entity, "Entity must not be null");
		Assert.notNull(branch, "Branch must not be null");
		entity.setPath(branch.getPath());
		entity.setStart(commit);
		entity.clearInternalId();
	}

	public void deleteAll() {
		conceptRepository.deleteAll();
		descriptionRepository.deleteAll();
		relationshipRepository.deleteAll();
		referenceSetMemberRepository.deleteAll();
	}
}
