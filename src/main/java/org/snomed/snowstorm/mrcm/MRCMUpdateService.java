package org.snomed.snowstorm.mrcm;

import io.kaicode.elasticvc.api.*;
import io.kaicode.elasticvc.domain.Commit;
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
import org.snomed.snowstorm.ecl.ECLQueryService;
import org.snomed.snowstorm.mrcm.model.AttributeDomain;
import org.snomed.snowstorm.mrcm.model.AttributeRange;
import org.snomed.snowstorm.mrcm.model.Domain;
import org.snomed.snowstorm.mrcm.model.MRCM;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.domain.Commit.CommitType.CONTENT;
import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.snomed.snowstorm.core.data.services.BranchMetadataHelper.INTERNAL_METADATA_KEY;

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

	@Autowired
	private ECLQueryService eclQueryService;

	private final Logger logger = LoggerFactory.getLogger(MRCMUpdateService.class);

	public static final String DISABLE_MRCM_AUTO_UPDATE_METADATA_KEY = "disableMrcmAutoUpdate";

	@Override
	public void preCommitCompletion(Commit commit) {
		if (Boolean.parseBoolean(commit.getBranch().getMetadata().getMapOrCreate(INTERNAL_METADATA_KEY).get(DISABLE_MRCM_AUTO_UPDATE_METADATA_KEY))) {
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
												   Map<String, String> conceptToTermMap, List<Long> dataAttributes) {

		List<Domain> updatedDomains = generator.generateDomainTemplates(domainMapByDomainId, domainToAttributesMap, domainToRangesMap, conceptToTermMap, dataAttributes);
		if (!updatedDomains.isEmpty()) {
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

	private void performUpdate(boolean allComponents, Commit commit) throws ServiceException {
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
		// map domains by domain id
		Map<String, Domain> domainMapByDomainId = new HashMap<>();
		for (Domain domain : mrcm.getDomains()) {
			domainMapByDomainId.put(domain.getReferencedComponentId(), domain);
		}

		for (AttributeDomain attributeDomain : mrcm.getAttributeDomains()) {
			domainIds.add(parseLong(attributeDomain.getDomainId()));
			attributeToDomainsMap.computeIfAbsent(attributeDomain.getReferencedComponentId(), v -> new ArrayList<>()).add(attributeDomain);
			domainToAttributesMap.computeIfAbsent(attributeDomain.getDomainId(), v ->  new ArrayList<>()).add(attributeDomain);
		}
		Set<Long> conceptIds = new HashSet<>(domainIds);
		Map<String, List<AttributeRange>> attributeToRangesMap = new HashMap<>();
		for (AttributeRange range : mrcm.getAttributeRanges()) {
			conceptIds.add(parseLong(range.getReferencedComponentId()));
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
		List<Long> dataAttributes = getDataAttributes(branchCriteria, branchPath);
		// Attribute rule
		toUpdate.addAll(updateAttributeRules(commit, domainMapByDomainId, attributeToDomainsMap, attributeToRangesMap, conceptToTermMap, dataAttributes));
		// domain templates
		toUpdate.addAll(updateDomainTemplates(commit, domainMapByDomainId, domainToAttributesMap, attributeToRangesMap, conceptToTermMap, dataAttributes));
		// update effective time
		toUpdate.forEach(ReferenceSetMember::updateEffectiveTime);

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
		if (!toUpdate.isEmpty()) {
			logger.info("{} reference set members updated in batch", toUpdate.size());
			referenceSetMemberService.doSaveBatchMembers(toUpdate, commit);
		}
	}

	private List<ReferenceSetMember> updateAttributeRules(Commit commit, Map<String,Domain> domainMapByDomainId,
														  Map<String, List<AttributeDomain>> attributeToDomainsMap,
														  Map<String, List<AttributeRange>> attributeToRangesMap,
														  Map<String, String> conceptToTermMap, List<Long> dataAttributes) {

		List<AttributeRange> attributeRanges = generator.generateAttributeRules(domainMapByDomainId, attributeToDomainsMap, attributeToRangesMap, conceptToTermMap, dataAttributes);
		if (!attributeRanges.isEmpty()) {
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
		try (final SearchHitsIterator<ReferenceSetMember> mrcmMembers = elasticsearchTemplate.searchForStream(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						// Must be at least one of the following should clauses:
						.must(boolQuery()
								.should(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.REFSET_MRCM_DOMAIN_INTERNATIONAL))
								.should(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN_INTERNATIONAL))
								.should(termQuery(ReferenceSetMember.Fields.REFSET_ID, Concepts.REFSET_MRCM_ATTRIBUTE_RANGE_INTERNATIONAL))
						)
				)
				.withPageable(LARGE_PAGE)
				.withFields(ReferenceSetMember.Fields.MEMBER_ID)
				.build(), ReferenceSetMember.class)) {
			mrcmMembers.forEachRemaining(hit -> result.add(hit.getContent().getMemberId()));
		}
		return result;
	}


	private String constructAdditionalFieldUpdateScript(List<String> fieldNames, ReferenceSetMember member) {
		StringBuilder scriptBuilder = new StringBuilder();
		for (String fieldName : fieldNames) {
			String value = member.getAdditionalField(fieldName);
			if (value == null) {
				continue;
			}
			if (!scriptBuilder.toString().isEmpty()) {
				scriptBuilder.append(";");
			}
			scriptBuilder.append("ctx._source.additionalFields.").append(fieldName).append("='").append(value).append("'");
		}
		return scriptBuilder.toString();
	}

	private void saveRefsetMembersViaUpdateQuery(Collection<ReferenceSetMember> referenceSetMembers) {
		List<UpdateQuery> updateQueries = new ArrayList<>();
		List<String> fieldNames = Arrays.asList("rangeConstraint", "attributeRule", "domainTemplateForPrecoordination", "domainTemplateForPostcoordination");
		for (ReferenceSetMember member : referenceSetMembers) {
			String script = constructAdditionalFieldUpdateScript(fieldNames, member);
			if (!script.isEmpty()) {
				updateQueries.add(UpdateQuery.builder(member.getInternalId()).withScript(script).build());
			}
		}
		if (!updateQueries.isEmpty()) {
			elasticsearchTemplate.bulkUpdate(updateQueries, elasticsearchTemplate.getIndexCoordinatesFor(ReferenceSetMember.class));
			elasticsearchTemplate.indexOps(ReferenceSetMember.class).refresh();
		}
	}

	private List<Long> getDataAttributes(BranchCriteria branchCriteria, String branchPath) {
		return eclQueryService.selectConceptIds("<<" + Concepts.CONCEPT_MODEL_DATA_ATTRIBUTE, branchCriteria, branchPath, true, LARGE_PAGE).getContent();
	}
}
