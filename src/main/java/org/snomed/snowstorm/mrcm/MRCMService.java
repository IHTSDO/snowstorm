package org.snomed.snowstorm.mrcm;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.mrcm.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

@Service
public class MRCMService {
	
	@Autowired
	private QueryService queryService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	private Map<String, MRCM> branchMrcmMap;

	@Value("${validation.mrcm.xml.path}")
	private String mrcmXmlPath;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public void loadFromFiles() throws ServiceException {
		this.branchMrcmMap = new MRCMLoader(mrcmXmlPath).loadFromFiles();
	}

	public Collection<ConceptMini> retrieveDomainAttributes(String branchPath, Set<Long> parentIds, List<String> languageCodes) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		Set<Long> allMatchedAttributeIds; 
		
		//If no parents are specified, we can always at least return ISA as a valid option
		if (parentIds == null || parentIds.isEmpty()) {
			allMatchedAttributeIds = Collections.singleton(Concepts.IS_A_LONG);
		} else {
			Set<Long> allAncestors = queryService.findAncestorIdsAsUnion(branchCriteria, false, parentIds);
			allAncestors.addAll(parentIds);
	
			Set<Domain> matchedDomains = getClosestMrcm(branchPath).getDomainMap().values().stream().filter(d -> {
				Long domainConceptId = d.getConceptId();
				InclusionType inclusionType = d.getInclusionType();
				if ((inclusionType == InclusionType.SELF || inclusionType == InclusionType.SELF_OR_DESCENDANT)
						&& parentIds.contains(domainConceptId)) {
					return true;
				}
				if ((inclusionType == InclusionType.DESCENDANT || inclusionType == InclusionType.SELF_OR_DESCENDANT)
						&& allAncestors.contains(domainConceptId)) {
					return true;
				}
				return false;
			}).collect(Collectors.toSet());
	
			Set<Attribute> matchedAttributes = matchedDomains.stream().map(Domain::getAttributes).flatMap(Collection::stream).collect(Collectors.toSet());
	
			allMatchedAttributeIds = matchedAttributes.stream().map(Attribute::getConceptId).collect(Collectors.toSet());
			Set<Long> descendantTypeAttributes = matchedAttributes.stream().filter(attribute -> attribute.getInclusionType() == InclusionType.DESCENDANT).map(Attribute::getConceptId).collect(Collectors.toSet());
			Set<Long> selfOrDescendantTypeAttributes = matchedAttributes.stream().filter(attribute -> attribute.getInclusionType() == InclusionType.SELF_OR_DESCENDANT).map(Attribute::getConceptId).collect(Collectors.toSet());
	
			List<Long> descendantAttributes = queryService.findDescendantIdsAsUnion(branchCriteria, false, Sets.union(descendantTypeAttributes, selfOrDescendantTypeAttributes));
	
			allMatchedAttributeIds.removeAll(descendantAttributes);
			allMatchedAttributeIds.addAll(descendantAttributes);
		}

		return conceptService.findConceptMinis(branchCriteria, allMatchedAttributeIds, languageCodes).getResultsMap().values();
	}

	public Collection<ConceptMini> retrieveAttributeValues(String branchPath, String attributeId, String termPrefix, List<String> languageCodes) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		Attribute attribute = findSelfOrFirstAncestorAttribute(branchPath, branchCriteria, attributeId);
		if (attribute == null) {
			throw new IllegalArgumentException("MRCM Attribute " + attributeId + " not found.");
		}
		
		QueryService.ConceptQueryBuilder queryBuilder = queryService.createQueryBuilder(false)
				.termPrefix(termPrefix)
				.languageCodes(languageCodes);

		for (Range range : attribute.getRangeSet()) {
			Long conceptId = range.getConceptId();
			InclusionType inclusionType = range.getInclusionType();
			switch (inclusionType) {
				case SELF:
					queryBuilder.self(conceptId);
					break;
				case DESCENDANT:
					queryBuilder.descendant(conceptId);
					break;
				case SELF_OR_DESCENDANT:
					queryBuilder.selfOrDescendant(conceptId);
					break;
			}
		}

		return queryService.search(queryBuilder, branchPath, PageRequest.of(0, 50)).getContent();
	}

	private Attribute findSelfOrFirstAncestorAttribute(String branchPath, BranchCriteria branchCriteria, String attributeIdStr) {
		//Breadth first scan of ancestors requires use of Queue
		Queue<Long> ancestorIds = new LinkedBlockingQueue<>(); 
		ancestorIds.add(Long.parseLong(attributeIdStr));
		
		Attribute attribute = null;
		while (!ancestorIds.isEmpty()) {
			Long attributeId = ancestorIds.poll();
			attribute = getClosestMrcm(branchPath).getAttributeMap().get(attributeId);
			if (attribute != null) {
				break;
			}
			//If we haven't found an attribute, then add all immediate inferred parents to the queue.
			//But we'll check the same level parents first, since they'll be earlier in the queue
			ancestorIds.addAll(queryService.findParentIds(branchCriteria, false, attributeId.toString()));
		}
		return attribute;
	}

	private MRCM getClosestMrcm(final String branchPath) {
		String searchPath = branchPath;
		boolean searchedRoot = false;
		while (!searchedRoot) {
			MRCM mrcm = branchMrcmMap.get(searchPath);
			if (mrcm != null) {
				if (searchPath.contains("/")) {
					logger.debug("MRCM branch match {}", searchPath);
				}
				return mrcm;
			}
			if (searchPath.contains("/")) {
				searchPath = searchPath.substring(0, searchPath.lastIndexOf("/"));
			} else {
				searchedRoot = true;
			}
		}
		throw new RuntimeServiceException("Failed to find any relevant MRCM for branch path " + branchPath);
	}

	// Test method
	public static void main(String[] args) throws ServiceException {
		new MRCMService().loadFromFiles();
	}
}
