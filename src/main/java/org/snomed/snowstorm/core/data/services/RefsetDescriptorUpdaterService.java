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
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.*;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool;
import static io.kaicode.elasticvc.helper.QueryHelper.termQuery;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_DIALECTS;
import static org.snomed.snowstorm.core.data.domain.ReferenceSetMember.DescriptorFields.*;

@Service
public class RefsetDescriptorUpdaterService implements CommitListener {
	private static final PageRequest PAGE_REQUEST = PageRequest.of(0, 50);

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

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

		SearchHits<QueryConcept> searchHits = elasticsearchOperations.search(
				new NativeQueryBuilder()
						.withQuery(
								bool(b -> b
										.must(termQuery(QueryConcept.Fields.START, commit.getTimepoint().getTime()))
										.must(termQuery(QueryConcept.Fields.ANCESTORS, Concepts.REFSET))
										.must(termQuery(QueryConcept.Fields.STATED, true))))
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

			List<ReferenceSetMember> members = referenceSetMemberService.findMembers(branchPath, new MemberSearchRequest().referencedComponentId(conceptIdS).referenceSet(Concepts.REFSET_DESCRIPTOR_REFSET), PAGE_REQUEST).getContent();
			if (members.isEmpty()) {
				doAddMemberToRefset(commit, conceptIdL, branchPath);
			}
		}
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
		MemberSearchRequest memberSearchRequest = new MemberSearchRequest().active(true).referenceSet(Concepts.REFSET_DESCRIPTOR_REFSET);
		String conceptIdS = String.valueOf(conceptIdL);
		String moduleId = conceptService.find(branchCriteria, branchPath, List.of(conceptIdS), DEFAULT_LANGUAGE_DIALECTS).iterator().next().getModuleId();
		List<ReferenceSetMember> descriptors = getDescriptorsRecursively(branchCriteria, conceptIdL, memberSearchRequest);

		if (descriptors == null || descriptors.isEmpty()) {
			logger.warn("A new Reference Set Concept was detected, however, the service cannot generate any RefSet Descriptor entries because existing entries for an ancestor could not be found.");
			return Collections.emptySet();
		}

		return clone(descriptors, moduleId, conceptIdS);
	}

	private List<ReferenceSetMember> getDescriptorsRecursively(BranchCriteria branchCriteria, long conceptId, MemberSearchRequest memberSearchRequest) {
		Set<Long> parentIds = queryService.findParentIds(branchCriteria, true, List.of(conceptId));
		return doGetDescriptorsRecursively(branchCriteria, parentIds, memberSearchRequest);
	}

	private List<ReferenceSetMember> doGetDescriptorsRecursively(BranchCriteria branchCriteria, Set<Long> parentIds, MemberSearchRequest memberSearchRequest) {
		if (parentIds.isEmpty()) {
			return Collections.emptyList();
		}

		// Parents
		Map<Long, List<ReferenceSetMember>> descriptors = new HashMap<>();
		for (Long parentId : parentIds) {
			memberSearchRequest.referencedComponentId(String.valueOf(parentId));
			Page<ReferenceSetMember> memberSearchResponse = referenceSetMemberService.findMembers(branchCriteria, memberSearchRequest, PAGE_REQUEST);
			if (memberSearchResponse != null && !memberSearchResponse.isEmpty()) {
				descriptors.put(parentId, memberSearchResponse.getContent());
			}
		}

		if (!descriptors.isEmpty()) {
			if (!sameStructure(descriptors)) {
				return Collections.emptyList();
			}

			return descriptors.values().iterator().next();
		}

		// Grandparents
		for (Long parentId : parentIds) {
			Set<Long> grandparentIds = queryService.findParentIds(branchCriteria, true, List.of(parentId));
			List<ReferenceSetMember> referenceSetMembers = doGetDescriptorsRecursively(branchCriteria, grandparentIds, memberSearchRequest);
			if (referenceSetMembers != null && !referenceSetMembers.isEmpty()) {
				return referenceSetMembers;
			}
		}

		return Collections.emptyList();
	}

	private boolean sameStructure(Map<Long, List<ReferenceSetMember>> descriptors) {
		// No compatibility check required
		if (descriptors.size() == 1) {
			return true;
		}

		// Check if each parent has the same number of descriptors
		int expectedSize = descriptors.values().iterator().next().size();
		for (Map.Entry<Long, List<ReferenceSetMember>> entrySet : descriptors.entrySet()) {
			List<ReferenceSetMember> value = entrySet.getValue();
			if (!value.isEmpty() && value.size() != expectedSize) {
				return false;
			}
		}

		// Check if each parent has the same attributes for each given descriptor
		Map<String, List<String>> additionalFields = getAdditionalFields(descriptors);
		for (Map.Entry<String, List<String>> entrySet : additionalFields.entrySet()) {
			List<String> values = entrySet.getValue();

			String current = values.iterator().next();
			for (String value : values) {
				if (!current.equals(value)) {
					return false;
				}

				current = value;
			}
		}

		return true;
	}

	private Map<String, List<String>> getAdditionalFields(Map<Long, List<ReferenceSetMember>> descriptors) {
		Map<String, List<String>> additionalFields = new HashMap<>();
		for (Map.Entry<Long, List<ReferenceSetMember>> entrySet : descriptors.entrySet()) {
			for (ReferenceSetMember referenceSetMember : entrySet.getValue()) {
				String attributeOrder = referenceSetMember.getAdditionalField(ATTRIBUTE_ORDER);
				String attributeDescription = referenceSetMember.getAdditionalField(ATTRIBUTE_DESCRIPTION);
				String attributeType = referenceSetMember.getAdditionalField(ATTRIBUTE_TYPE);
				List<String> attributeOrders = additionalFields.get(attributeOrder);
				if (attributeOrders == null) {
					attributeOrders = new ArrayList<>();
				}

				attributeOrders.add(String.join("|", attributeDescription, attributeType, attributeOrder));
				additionalFields.put(attributeOrder, attributeOrders);
			}
		}

		return additionalFields;
	}

	private Set<ReferenceSetMember> clone(List<ReferenceSetMember> membersExisting, String moduleId, String conceptIdS) {
		Set<ReferenceSetMember> membersNew = new HashSet<>();
		for (ReferenceSetMember memberExisting : membersExisting) {
			ReferenceSetMember memberNew = new ReferenceSetMember(moduleId, Concepts.REFSET_DESCRIPTOR_REFSET, conceptIdS);
			memberNew.setAdditionalFields(
					Map.of(
							ATTRIBUTE_DESCRIPTION, memberExisting.getAdditionalField(ATTRIBUTE_DESCRIPTION),
							ATTRIBUTE_TYPE, memberExisting.getAdditionalField(ATTRIBUTE_TYPE),
							ATTRIBUTE_ORDER, memberExisting.getAdditionalField(ATTRIBUTE_ORDER)
					)
			);
			memberNew.markChanged();
			memberNew.setConceptId(conceptIdS);
			membersNew.add(memberNew);
		}

		return membersNew;
	}
}
