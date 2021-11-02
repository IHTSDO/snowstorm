package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.CommitListener;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Commit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.pojo.MemberSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.*;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_DIALECTS;

@Service
public class RefsetDescriptorUpdaterService implements CommitListener {
	private static final PageRequest PAGE_REQUEST = PageRequest.of(0, 50);

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private QueryService queryService;

	/**
	 * If the commit is creating a new Reference Set, update the 900000000000456007 |Reference set descriptor reference set (foundation metadata concept)|
	 * Reference Set by adding a new entry to the Reference Set for the new Reference Set being created.
	 *
	 * @param commit Commit to process.
	 * @throws IllegalStateException When commit should fail.
	 */
	@Override
	public void preCommitCompletion(Commit commit) throws IllegalStateException {
		if (BranchMetadataHelper.isImportingCodeSystemVersion(commit)) {
			logger.info("RefSet Descriptor auto update is disabled on branch {}", commit.getBranch().getPath());
			return;
		}

		if (Commit.CommitType.CONTENT != commit.getCommitType()) {
			logger.debug("CommitType is not CONTENT. Nothing to do.");
			return;
		}

		SearchHits<QueryConcept> searchHits = elasticsearchTemplate.search(
				new NativeSearchQueryBuilder()
						.withQuery(
								boolQuery()
										.must(termQuery(QueryConcept.Fields.START, commit.getTimepoint().getTime()))
										.must(termQuery(QueryConcept.Fields.ANCESTORS, Concepts.REFSET))
										.must(termQuery(QueryConcept.Fields.STATED, true)))
						.build(),
				QueryConcept.class
		);
		boolean creatingRefSet = searchHits.hasSearchHits();
		if (!creatingRefSet) {
			return;
		}

		String branchPath = commit.getBranch().getPath();
		for (SearchHit<QueryConcept> searchHit : searchHits.getSearchHits()) {
			long conceptIdL = searchHit.getContent().getConceptIdL();
			String conceptIdS = String.valueOf(conceptIdL);

			// Returns unmodifiable collection, thus, create new collection.
			List<ReferenceSetMember> members = new ArrayList<>(referenceSetMemberService.findMembers(branchPath, new MemberSearchRequest().referencedComponentId(conceptIdS).referenceSet(Concepts.REFSET_DESCRIPTOR_REFSET), PAGE_REQUEST).getContent());
			if (!members.isEmpty()) {
				doUpdateRefsetMembers(commit, conceptIdS, branchPath, members);
			} else {
				doAddMemberToRefset(commit, conceptIdL, branchPath);
			}
		}
	}

	private void doUpdateRefsetMembers(Commit commit, String conceptIdS, String branchPath, List<ReferenceSetMember> members) {
		logger.info("Commit is updating a new Reference Set (conceptId: {}). {} |Reference set descriptor| will be updated accordingly on branch {}.", conceptIdS, Concepts.REFSET_DESCRIPTOR_REFSET, branchPath);

		// Inactivate or remove previous
		for (ReferenceSetMember member : members) {
			if (member.isReleased()) {
				member.setActive(false);
			} else {
				member.markDeleted();
			}
			member.markChanged();
		}

		// Create new entries
		members.addAll(getNewMembersInspiredByAncestors(commit, Long.parseLong(conceptIdS), branchPath));

		Set<String> referenceSetMembersSaved = new HashSet<>();
		referenceSetMemberService.doSaveBatchMembers(members, commit).forEach(s -> referenceSetMembersSaved.add(s.getId()));
		logger.info("New ReferenceSetMember(s) updated for {} |Reference set descriptor| on branch {}: {}", Concepts.REFSET_DESCRIPTOR_REFSET, branchPath, referenceSetMembersSaved);
	}

	private void doAddMemberToRefset(Commit commit, long conceptIdL, String branchPath) {
		Set<ReferenceSetMember> referenceSetMembersToSave = getNewMembersInspiredByAncestors(commit, conceptIdL, branchPath);
		if (referenceSetMembersToSave.isEmpty()) {
			logger.info("Cannot proceed with updating {} |Reference set descriptor| on branch {} as relevant properties cannot be inherited from parent/grandparent.", Concepts.REFSET_DESCRIPTOR_REFSET, branchPath);
			return;
		}

		logger.info("Commit is creating a new Reference Set (conceptId: {}). {} |Reference set descriptor| will be updated accordingly on branch {}.", conceptIdL, Concepts.REFSET_DESCRIPTOR_REFSET, branchPath);
		Set<String> referenceSetMembersSaved = new HashSet<>();
		referenceSetMemberService.doSaveBatchMembers(referenceSetMembersToSave, commit).forEach(s -> referenceSetMembersSaved.add(s.getId()));
		logger.info("New ReferenceSetMember(s) created for {} |Reference set descriptor| on branch {}: {}", Concepts.REFSET_DESCRIPTOR_REFSET, branchPath, referenceSetMembersSaved);
	}

	private Set<ReferenceSetMember> getNewMembersInspiredByAncestors(Commit commit, long conceptIdL, String branchPath) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit);
		Page<ReferenceSetMember> membersExisting;
		MemberSearchRequest memberSearchRequest = new MemberSearchRequest();
		memberSearchRequest.active(true);
		memberSearchRequest.referenceSet(Concepts.REFSET_DESCRIPTOR_REFSET);
		String conceptIdS = String.valueOf(conceptIdL);
		String moduleId = conceptService.find(branchCriteria, branchPath, List.of(conceptIdS), DEFAULT_LANGUAGE_DIALECTS).iterator().next().getModuleId();

		// Find entry in RefSet for parent
		Long parentId = queryService.findParentIds(branchCriteria, true, List.of(conceptIdL)).iterator().next();
		memberSearchRequest.referencedComponentId(String.valueOf(parentId));
		membersExisting = referenceSetMemberService.findMembers(branchPath, branchCriteria, memberSearchRequest, PAGE_REQUEST);

		// Find entry in RefSet for grandparent
		if (membersExisting == null || membersExisting.isEmpty()) {
			Iterator<Long> iterator = queryService.findParentIds(branchCriteria, true, List.of(parentId)).iterator();
			if (iterator.hasNext()) {
				Long grandparentId = iterator.next();
				String grandParentIdS = String.valueOf(grandparentId);
				if (Concepts.REFSET.equals(grandParentIdS) || Concepts.FOUNDATION_METADATA.equals(grandParentIdS)) {
					// Creating top-level Concept for Reference Sets; cannot proceed.
					return Collections.emptySet();
				}
				memberSearchRequest.referencedComponentId(grandParentIdS);
				membersExisting = referenceSetMemberService.findMembers(branchPath, branchCriteria, memberSearchRequest, PAGE_REQUEST);
			}
		}

		if (membersExisting == null || membersExisting.isEmpty()) {
			logger.warn("A new Reference Set Concept was detected, however, the service cannot generate any RefSet Descriptor entries because existing entries for an ancestor could not be found.");
			return Collections.emptySet();
		}

		// Copy ReferenceSetMembers from parent/grandparent with mild differences
		Set<ReferenceSetMember> membersNew = new HashSet<>();
		for (ReferenceSetMember memberExisting : membersExisting) {
			ReferenceSetMember memberNew = new ReferenceSetMember(moduleId, Concepts.REFSET_DESCRIPTOR_REFSET, conceptIdS);
			memberNew.setAdditionalFields(
					Map.of(
							"attributeDescription", memberExisting.getAdditionalField("attributeDescription"),
							"attributeType", memberExisting.getAdditionalField("attributeType"),
							"attributeOrder", memberExisting.getAdditionalField("attributeOrder")
					)
			);
			memberNew.markChanged();
			memberNew.setConceptId(conceptIdS);
			membersNew.add(memberNew);
		}

		return membersNew;
	}
}
