package org.snomed.snowstorm.mrcm;

import io.kaicode.elasticvc.api.*;
import io.kaicode.elasticvc.domain.Commit;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

	@Autowired
	private MRCMDomainTemplatesAndRuleGenerator generator;

	private Logger logger = LoggerFactory.getLogger(MRCMUpdateService.class);

	public static final String DISABLE_MRCM_AUTO_UPDATE_METADATA_KEY = "disableMrcmAutoUpdate";

	@Override
	public void preCommitCompletion(Commit commit) throws IllegalStateException {
		 boolean isMRCMAutoUpdatedDisabled = commit.getBranch().getMetadata() != null
				&& "true".equals(commit.getBranch().getMetadata().get(DISABLE_MRCM_AUTO_UPDATE_METADATA_KEY));
		if (isMRCMAutoUpdatedDisabled) {
			logger.info("MRCM auto update is disabled on branch {}", commit.getBranch().getPath());
			return;
		}
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

	private List<ReferenceSetMember> updateDomainTemplates(Commit commit, Map<String, Domain> domainMapByDomainId,
												   Map<String, List<AttributeDomain>> domainToAttributesMap,
												   Map<String, List<AttributeRange>> domainToRangesMap,
												   Map<String, String> conceptToTermMap) {

		List<Domain> updatedDomains = generator.generateDomainTemplates(domainMapByDomainId, domainToAttributesMap, domainToRangesMap, conceptToTermMap);
		if (updatedDomains.size() > 0) {
			logger.info("{} domain templates updated.", updatedDomains.size());
		}
		// add diff report if required
		Set<String> domainMemberIds = updatedDomains.stream().map(Domain::getId).collect(Collectors.toSet());
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit);
		List<ReferenceSetMember> domainMembers = referenceSetMemberService.findMembers(branchCriteria, domainMemberIds);
		Map<String, Domain> memberIdToDomainMap = new HashMap<>();
		for (Domain domain : updatedDomains) {
			memberIdToDomainMap.put(domain.getId(), domain);
		}
		for (ReferenceSetMember member : domainMembers) {
			member.setAdditionalField("domainTemplateForPrecoordination", memberIdToDomainMap.get(member.getMemberId()).getDomainTemplateForPrecoordination());
			member.setAdditionalField("domainTemplateForPostcoordination", memberIdToDomainMap.get(member.getMemberId()).getDomainTemplateForPostcoordination());
			member.markChanged();
		}
		return domainMembers;
	}

	private void performUpdate(boolean allComponents, Commit commit) throws IOException, ServiceException {
		String branchPath = commit.getBranch().getPath();
		Set<String> mrcmComponentsChangedOnTask =  getMRCMRefsetComponentsChanged(commit);
		if (!allComponents) {
			if (mrcmComponentsChangedOnTask.isEmpty()) {
				logger.debug("No MRCM refset component changes found on branch {}", branchPath);
				return;
			} else {
				logger.info("{} MRCM component changes found on branch {}", mrcmComponentsChangedOnTask.size(), branchPath);
			}
		}

		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit);
		MRCM mrcm = mrcmLoader.loadActiveMRCM(branchPath, branchCriteria);
		Map<String, List<AttributeDomain>> attributeToDomainsMap = new HashMap<>();
		Map<String, List<AttributeDomain>> domainToAttributesMap = new HashMap<>();
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
			domainToAttributesMap.computeIfAbsent(attributeDomain.getDomainId(), v ->  new ArrayList<>()).add(attributeDomain);
		}
		conceptIds.addAll(domainIds);
		Map<String, List<AttributeRange>> attributeToRangesMap = new HashMap<>();
		for (AttributeRange range : mrcm.getAttributeRanges()) {
			conceptIds.add(new Long(range.getReferencedComponentId()));
			attributeToRangesMap.computeIfAbsent(range.getReferencedComponentId(), ranges -> new ArrayList<>()).add(range);
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

		List<ReferenceSetMember> toUpdate = new ArrayList<>();
		// Attribute rule
		toUpdate.addAll(updateAttributeRules(commit, domainMapByDomainId, attributeToDomainsMap, attributeToRangesMap, conceptToTermMap));
		// domain templates
		toUpdate.addAll(updateDomainTemplates(commit, domainMapByDomainId, domainToAttributesMap, attributeToRangesMap, conceptToTermMap));
		// update effective time
		toUpdate.stream().forEach(ReferenceSetMember :: updateEffectiveTime);

		// Find MRCM members where new versions have already been created in the current commit.
		// Update these documents to avoid having two versions of the same concepts in the commit.
		Set<ReferenceSetMember> editedMembers = toUpdate.stream()
				.filter(m -> m.getStart().equals(commit.getTimepoint()))
				.collect(Collectors.toSet());

		if (!editedMembers.isEmpty()) {
			logger.info("{} reference set members updated via update query", editedMembers.size());
			saveRefsetMembersViaUpdateQuery(editedMembers);
		}

		// saving in batch
		toUpdate.removeAll(editedMembers);
		if (toUpdate.size() > 0) {
			logger.info("{} reference set members updated in batch", toUpdate.size());
		}
		referenceSetMemberService.doSaveBatchMembers(toUpdate, commit);
	}

	private List<ReferenceSetMember> updateAttributeRules(Commit commit, Map<String,Domain> domainMapByDomainId,
														  Map<String,List<AttributeDomain>> attributeToDomainsMap,
														  Map<String, List<AttributeRange>> attributeToRangesMap,
														  Map<String,String> conceptToTermMap) {

		List<AttributeRange> attributeRanges = generator.generateAttributeRule(domainMapByDomainId, attributeToDomainsMap, attributeToRangesMap, conceptToTermMap);
		if (attributeRanges.size() > 0) {
			logger.info("{} changes generated for attribute rules.", attributeRanges.size());
		}

		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaIncludingOpenCommit(commit);
		Set<String> rangeMemberIds = attributeRanges.stream().map(AttributeRange::getId).collect(Collectors.toSet());
		List<ReferenceSetMember> rangeMembers = referenceSetMemberService.findMembers(branchCriteria, rangeMemberIds);
		if (rangeMemberIds.size() != rangeMembers.size()) {
			throw new IllegalStateException(String.format("Not all attribute range members found as expecting %d but only got %d", rangeMemberIds.size(), rangeMembers.size()));
		}

		Map<String, AttributeRange> memberIdToRangeMap = new HashMap<>();
		for (AttributeRange range : attributeRanges) {
			memberIdToRangeMap.put(range.getId(), range);
		}

		for (ReferenceSetMember rangeMember : rangeMembers) {
			rangeMember.markChanged();
			rangeMember.setAdditionalField("attributeRule", memberIdToRangeMap.get(rangeMember.getMemberId()).getAttributeRule());
			rangeMember.setAdditionalField("rangeConstraint", memberIdToRangeMap.get(rangeMember.getMemberId()).getRangeConstraint());
		}
		return rangeMembers;
	}

	private Set<String> getMRCMRefsetComponentsChanged(Commit commit) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteriaChangesAndDeletionsWithinOpenCommitOnly(commit);
		Set<String> result = new HashSet<>();
		try (final CloseableIterator<ReferenceSetMember> mrcmMembers = elasticsearchTemplate.stream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						// Must be at least one of the following should clauses:
						.must(boolQuery()
								.should(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.REFSET_MRCM_DOMAIN_INTERNATIONAL))
								.should(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN_INTERNATIONAL))
								.should(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.REFSET_MRCM_ATTRIBUTE_RANGE_INTERNATIONAL))
						)
				)
				.withPageable(ConceptService.LARGE_PAGE)
				.withFields(ReferenceSetMember.Fields.MEMBER_ID)
				.build(), ReferenceSetMember.class)) {
			while (mrcmMembers.hasNext()) {
				result.add(mrcmMembers.next().getMemberId());
			}
		}
		return result;
	}

	private void saveRefsetMembersViaUpdateQuery(Collection<ReferenceSetMember> referenceSetMembers) throws IOException {
		List<UpdateQuery> updateQueries = new ArrayList<>();
		for (ReferenceSetMember member : referenceSetMembers) {
			StringBuilder inlineBuilder = new StringBuilder();
			String rangeConstraint = member.getAdditionalField("rangeConstraint");
			if (rangeConstraint != null) {
				inlineBuilder.append("ctx._source.additionalFields.rangeConstraint='" + rangeConstraint + "'");
			}

			String attributeRule = member.getAdditionalField("attributeRule");
			if (attributeRule != null) {
				if (!inlineBuilder.toString().isEmpty()) {
					inlineBuilder.append(";");
				}
				inlineBuilder.append("ctx._source.additionalFields.attributeRule='" + attributeRule + "'");
			}

			String precoordinate = member.getAdditionalField("domainTemplateForPrecoordination");
			if (precoordinate != null) {
				if (!inlineBuilder.toString().isEmpty()) {
					inlineBuilder.append(";");
				}
				inlineBuilder.append("ctx._source.additionalFields.domainTemplateForPrecoordination='"+ precoordinate + "'");
			}

			String postcoordinate = member.getAdditionalField("domainTemplateForPostcoordination");
			if (precoordinate != null) {
				if (!inlineBuilder.toString().isEmpty()) {
					inlineBuilder.append(";");
				}
				inlineBuilder.append("ctx._source.additionalFields.domainTemplateForPostcoordination='"+ postcoordinate + "'");
			}

			if (!inlineBuilder.toString().isEmpty()) {
				UpdateRequest updateRequest = new UpdateRequest();
				updateRequest.script(new Script(inlineBuilder.toString()));
				updateQueries.add(new UpdateQueryBuilder()
						.withClass(ReferenceSetMember.class)
						.withId(member.getInternalId())
						.withUpdateRequest(updateRequest)
						.build());
			}
		}
		if (!updateQueries.isEmpty()) {
			elasticsearchTemplate.bulkUpdate(updateQueries);
			elasticsearchTemplate.refresh(ReferenceSetMember.class);
		}
	}
}
