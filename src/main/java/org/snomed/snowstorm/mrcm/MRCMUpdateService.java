package org.snomed.snowstorm.mrcm;

import io.kaicode.elasticvc.api.*;
import io.kaicode.elasticvc.domain.Commit;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.elasticsearch.action.update.UpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.BranchMetadataHelper;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.mrcm.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.data.elasticsearch.core.query.UpdateQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.domain.Commit.CommitType.CONTENT;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Service
public class MRCMUpdateService extends ComponentService implements CommitListener {
	@Autowired
	private MRCMLoader mrcmLoader;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	@Autowired
	private BranchMetadataHelper branchMetadataHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	private Logger logger = LoggerFactory.getLogger(MRCMUpdateService.class);

	static final Comparator<AttributeDomain> ATTRIBUTE_DOMAIN_COMPARATOR = Comparator
			.comparing(AttributeDomain::getDomainId, Comparator.nullsFirst(String::compareTo).reversed());

	@Override
	public void preCommitCompletion(Commit commit) throws IllegalStateException {
		if (commit.getCommitType() == CONTENT) {
			logger.debug("Start updating MRCM domain templates and attribute rules on branch {}.", commit.getBranch().getPath());
			try {
				performUpdate(false, commit);
				logger.debug("End updating MRCM domain templates and attribute rules on branch {}.", commit.getBranch().getPath());
			} catch (Exception e) {
				throw new IllegalStateException("Failed to update MRCM domain templates and attribute rules." + e, e);
			}
		}
	}

	public void updateAllDomainTemplatesAndAttributeRules(String path) throws ServiceException {
		logger.info("Updating all MRCM domain templates and attribute rules on branch {}.", path);
		try (Commit commit = branchService.openCommit(path, branchMetadataHelper.getBranchLockMetadata("Updating all MRCM components."))) {
			performUpdate(true, commit);
			commit.markSuccessful();
		} catch (Exception e) {
			throw new ServiceException("Failed to update MRCM domain templates and attribute rules for all components.", e);
		}
		logger.info("Completed updating MRCM domain templates and attribute rules for all components on branch {}.", path);
	}

	List<Domain> updateDomainTemplates(String branchpath) {
		// TODO
		return new ArrayList<>();
	}

	List<AttributeRange> updateAttributeRules(String branchPath, Collection<Long> components) throws ServiceException {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		MRCM mrcm = mrcmLoader.loadActiveMRCM(branchPath, branchCriteria);
		Map<String, List<AttributeDomain>> attributeToDomainsMap = new HashMap<>();
		Set<Long> domainIds = new HashSet<>();
		Set<Long> conceptIds = new HashSet<>();
		// map domains by domain id
		Map<String, Domain> domainMapByDomainId = new HashMap<>();
		for (Domain domain : mrcm.getDomains()) {
			domainMapByDomainId.put(domain.getReferencedComponentId(), domain);
		}

		for (AttributeDomain attributeDomain : mrcm.getAttributeDomains()) {
			domainIds.add(new Long(attributeDomain.getDomainId()));
			attributeToDomainsMap.computeIfAbsent(attributeDomain.getReferencedComponentId(), v -> new ArrayList<>()).add(attributeDomain);
		}
		conceptIds.addAll(domainIds);
		Map<String, AttributeRange> attributeToRangeMap = new HashMap<>();
		for (AttributeRange range : mrcm.getAttributeRanges()) {
			conceptIds.add(new Long(range.getReferencedComponentId()));
			attributeToRangeMap.put(range.getReferencedComponentId(), range);
		}
		// fetch FSN for concepts
		Collection<ConceptMini> conceptMinis = conceptService.findConceptMinis(branchCriteria, conceptIds, Config.DEFAULT_LANGUAGE_DIALECTS).getResultsMap().values();

		Map<String, String> conceptToTermMap = new HashMap<>();
		for (ConceptMini conceptMini : conceptMinis) {
			if (domainIds.contains(Long.valueOf(conceptMini.getConceptId()))) {
				conceptToTermMap.put(conceptMini.getConceptId(), conceptMini.getFsnTerm());
			} else {
				conceptToTermMap.put(conceptMini.getConceptId(), conceptMini.getPt().getTerm());
			}
		}

		return generateAttributeRule(domainMapByDomainId, attributeToDomainsMap, attributeToRangeMap, conceptToTermMap);
	}

	List<AttributeRange> generateAttributeRule(Map<String, Domain> domainMapByDomainId, Map<String, List<AttributeDomain>> attributeToDomainsMap, Map<String, AttributeRange> attributeToRangeMap, Map<String, String> conceptToFsnMap) {
		List<AttributeRange> updatedRanges = new ArrayList<>();
		// generate attribute rule
		for (String attributeId : attributeToDomainsMap.keySet()) {
			// domain
			int counter = 0;
			StringBuilder ruleBuilder = new StringBuilder();
			List<AttributeDomain> sorted = attributeToDomainsMap.get(attributeId);
			Collections.sort(sorted, ATTRIBUTE_DOMAIN_COMPARATOR);
			for (AttributeDomain attributeDomain : sorted) {
				if (RuleStrength.MANDATORY != attributeDomain.getRuleStrength()) {
					continue;
				}
				// TODO
				if (ContentType.ALL != attributeDomain.getContentType() && attributeToRangeMap.get(attributeId).getContentType() != attributeDomain.getContentType()) {
					logger.debug("Content type defined in attribute range is " + attributeToRangeMap.get(attributeId).getContentType().name()
							+ " but attribute domain content type is " +  attributeDomain.getContentType().name()
							+ " for domain " + attributeDomain.getDomainId() + " and attribute " + attributeDomain.getReferencedComponentId());
					continue;
				}
				// TODO to make the following code better
				if (counter++ > 0) {
					ruleBuilder.insert(0, "(");
					ruleBuilder.append(")");
					ruleBuilder.append(" OR (");
				}
				ruleBuilder.append(domainMapByDomainId.get(attributeDomain.getDomainId()).getDomainConstraint().getExpression());
				ruleBuilder.append(":");
				// attribute group and attribute cardinality
				if (attributeDomain.isGrouped()) {
					ruleBuilder.append(" [" + attributeDomain.getAttributeCardinality().getValue() + "]" + " {");
					ruleBuilder.append(" [" + attributeDomain.getAttributeInGroupCardinality().getValue() + "]");
				} else {
					ruleBuilder.append(" [" + attributeDomain.getAttributeCardinality().getValue() + "]");
				}

				ruleBuilder.append(" " + attributeId + " |" + conceptToFsnMap.get(attributeId) + "|" +  " = ");
				// range constraint
				AttributeRange range = attributeToRangeMap.get(attributeId);
				if (range.getRangeConstraint().contains("OR")) {
					ruleBuilder.append("(" + range.getRangeConstraint() + ")");
				} else {
					ruleBuilder.append(range.getRangeConstraint());
				}
				if (attributeDomain.isGrouped()) {
					ruleBuilder.append( " }");
				}
				if (counter > 1) {
					ruleBuilder.append(")");
				}
			}

			AttributeRange range = attributeToRangeMap.get(attributeId);
			if (!range.getAttributeRule().equals(ruleBuilder.toString())) {
				logger.info("before = " + range.getAttributeRule());
				logger.info("after = " + ruleBuilder.toString());

				updatedRanges.add(new AttributeRange(range.getId(), null, range.isActive(), range.getReferencedComponentId(),
						range.getRangeConstraint(), ruleBuilder.toString(), range.getRuleStrength(), range.getContentType()));
			}
		}
		return updatedRanges;
	}

	private void performUpdate(boolean allComponents, Commit commit) throws IOException, ServiceException {
		String branchPath = commit.getBranch().getPath();
		if (allComponents) {
			List<AttributeRange> attributeRanges = updateAttributeRules(branchPath, null);
			Set<String> rangeMemberIds = attributeRanges.stream().map(r -> r.getId()).collect(Collectors.toSet());
			List<ReferenceSetMember> rangeMembers = referenceSetMemberService.findMembers(branchPath, rangeMemberIds);
			Map<String, AttributeRange> memberIdToRangeMap = new HashMap<>();
			List<ReferenceSetMember> toSave = new ArrayList<>();
			for (AttributeRange range : attributeRanges) {
				memberIdToRangeMap.put(range.getId(), range);
			}
			for (ReferenceSetMember rangeMember : rangeMembers) {
				rangeMember.setAdditionalField("attributeRule", memberIdToRangeMap.get(rangeMember.getMemberId()).getAttributeRule());
			}
			toSave.addAll(rangeMembers);
			List<Domain> domains = updateDomainTemplates(branchPath);
			Set<String> domainMemberIds = domains.stream().map(d -> d.getId()).collect(Collectors.toSet());
			List<ReferenceSetMember> domainMembers = referenceSetMemberService.findMembers(branchPath, domainMemberIds);
			Map<String, Domain> memberIdToDomainMap = new HashMap<>();
			for (Domain domain : domains) {
				memberIdToDomainMap.put(domain.getId(), domain);
			}
			referenceSetMemberService.doSaveBatchMembers(toSave, commit);
		} else {
			Set<Long> componentsToUpdate =  getComponentsChanged(commit);
			if (!componentsToUpdate.isEmpty()) {
				logger.info("Checking {} MRCM components.", componentsToUpdate.size());
				if (!componentsToUpdate.isEmpty()) {
					saveChanges(componentsToUpdate, commit);
				}
			}
		}
	}

	private Set<Long> getComponentsChanged(Commit commit) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaChangesAndDeletionsWithinOpenCommitOnly(commit);
		Set<Long> result = new LongOpenHashSet();
		try (final CloseableIterator<ReferenceSetMember> mrcms = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						.should(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.REFSET_MRCM_DOMAIN_INTERNATIONAL))
						.should(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN_INTERNATIONAL))
						.should(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.REFSET_MRCM_ATTRIBUTE_RANGE_INTERNATIONAL))
				)
				.withPageable(ConceptService.LARGE_PAGE)
				.withFields(ReferenceSetMember.Fields.REFERENCED_COMPONENT_ID)
				.build(), ReferenceSetMember.class)) {
			while (mrcms.hasNext()) {
				result.add(Long.parseLong(mrcms.next().getReferencedComponentId()));
			}
		}
		return result;
	}


	private void saveChanges(Collection<Long> componentsToUpdate, Commit commit) throws IllegalStateException, IOException, ServiceException {
		if (!componentsToUpdate.isEmpty()) {
			List<AttributeRange> rangeRulesUpdated = updateAttributeRules(commit.getBranch().getPath(), componentsToUpdate);
			Set<String> rangeMemberIds = rangeRulesUpdated.stream().map(r -> r.getId()).collect(Collectors.toSet());
			List<ReferenceSetMember> rangeMembersToUpdate = referenceSetMemberService.findMembers(commit.getBranch().getPath(), rangeMemberIds);

			// Find refset components where new versions have already been created in the current commit.
			// Update these documents to avoid having two versions of the same concepts in the commit.
			Set<ReferenceSetMember> editedMembers = rangeMembersToUpdate.stream()
					.filter(member -> member.getStart().equals(commit.getTimepoint()))
					.collect(Collectors.toSet());
			Map<String, String> memberIdToRuleValueMap = new HashMap<>();
			for (AttributeRange range : rangeRulesUpdated) {
				memberIdToRuleValueMap.put(range.getId(), range.getAttributeRule());
			}
			updateRefsetMembersViaUpdateQuery(editedMembers, "attributeRule", memberIdToRuleValueMap);

			// refset members which were not just saved/updated in the commit can go through the normal commit process.
			Set<ReferenceSetMember> toSave = rangeMembersToUpdate.stream()
					.filter(member -> !rangeMembersToUpdate.contains(member))
					.collect(Collectors.toSet());
			for (ReferenceSetMember rangeMember : toSave) {
				rangeMember.setAdditionalField("attributeRule", memberIdToRuleValueMap.get(rangeMember.getMemberId()));
			}
			// TODO add domain template changes
			referenceSetMemberService.doSaveBatchMembers(toSave, commit);
		}
	}

	private void updateRefsetMembersViaUpdateQuery(Set<ReferenceSetMember> editedMembers, String additionalField, Map<String, String> memberIdToValueMap) throws IOException {
		List<UpdateQuery> updateQueries = new ArrayList<>();
		for (ReferenceSetMember refsetMember : editedMembers) {
			UpdateRequest updateRequest = new UpdateRequest();
			updateRequest.doc(jsonBuilder()
					.startObject()
					.field(additionalField, memberIdToValueMap.get(refsetMember.getMemberId()))
					.endObject());

			updateQueries.add(new UpdateQueryBuilder()
					.withClass(ReferenceSetMember.class)
					.withId(refsetMember.getInternalId())
					.withUpdateRequest(updateRequest)
					.build());
		}
		if (!updateQueries.isEmpty()) {
			elasticsearchTemplate.bulkUpdate(updateQueries);
			elasticsearchTemplate.refresh(ReferenceSetMember.class);
		}
	}
}
