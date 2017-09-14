package org.ihtsdo.elasticsnomed.mrcm;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.domain.ConceptMini;
import org.ihtsdo.elasticsnomed.core.data.services.ConceptService;
import org.ihtsdo.elasticsnomed.core.data.services.QueryService;
import org.ihtsdo.elasticsnomed.mrcm.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;

@Service
public class MRCMService {

	@Autowired
	private QueryService queryService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	private MRCM mrcm;

	public void load() throws IOException {
		this.mrcm = new MRCMLoader().load();
	}

	public Collection<ConceptMini> retrieveDomainAttributes(String branchPath, Set<Long> parentIds) {
		QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		Set<Long> allAncestors = queryService.retrieveAllAncestors(branchCriteria, false, parentIds);

		Set<Domain> matchedDomains = mrcm.getDomainMap().values().stream().filter(d -> {
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

		Set<Long> allMatchedAttributeIds = matchedAttributes.stream().map(Attribute::getConceptId).collect(Collectors.toSet());
		Set<Long> descendantTypeAttributes = matchedAttributes.stream().filter(attribute -> attribute.getInclusionType() == InclusionType.DESCENDANT).map(Attribute::getConceptId).collect(Collectors.toSet());
		Set<Long> selfOrDescendantTypeAttributes = matchedAttributes.stream().filter(attribute -> attribute.getInclusionType() == InclusionType.SELF_OR_DESCENDANT).map(Attribute::getConceptId).collect(Collectors.toSet());

		Set<Long> descendantAttributes = queryService.retrieveAllDescendants(branchCriteria, false, Sets.union(descendantTypeAttributes, selfOrDescendantTypeAttributes));

		allMatchedAttributeIds.removeAll(descendantAttributes);
		allMatchedAttributeIds.addAll(descendantAttributes);

		return conceptService.findConceptMinis(branchCriteria, allMatchedAttributeIds).values();
	}


	public Collection<ConceptMini> retrieveAttributeValues(String branchPath, String attributeId, String termPrefix) {
		Attribute attribute = mrcm.getAttributeMap().get(parseLong(attributeId));
		if (attribute == null) {
			throw new IllegalArgumentException("MRCM Attribute " + attributeId + " not found.");
		}

		QueryService.ConceptQueryBuilder queryBuilder = queryService.createQueryBuilder(false);
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

		queryBuilder.termPrefix(termPrefix);
		return queryService.search(queryBuilder, branchPath, 50);
	}

	public static void main(String[] args) throws IOException {
		new MRCMService().load();
	}
}
