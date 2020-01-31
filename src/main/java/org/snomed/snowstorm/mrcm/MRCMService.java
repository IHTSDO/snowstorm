package org.snomed.snowstorm.mrcm;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.langauges.ecl.domain.refinement.Operator;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.mrcm.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;

@Service
public class MRCMService {

	private static final PageRequest RESPONSE_PAGE_SIZE = PageRequest.of(0, 50);

	@Autowired
	private MRCMLoader mrcmLoader;

	@Autowired
	private QueryService queryService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	private Logger logger = LoggerFactory.getLogger(getClass());

	// Hardcoded Is a (attribute)
	// 'Is a' is not really an attribute at all but it's convenient for implementations to have this.
	private static final AttributeDomain IS_A_ATTRIBUTE_DOMAIN = new AttributeDomain(null, null, true, Concepts.ISA, Concepts.SNOMEDCT_ROOT, false,
			new Cardinality(1, null), new Cardinality(0, 0), RuleStrength.MANDATORY, ContentType.ALL);
	private static final AttributeRange IS_A_ATTRIBUTE_RANGE = new AttributeRange(null, null, true, Concepts.ISA, "*", "*", RuleStrength.MANDATORY, ContentType.ALL);

	public Collection<ConceptMini> retrieveDomainAttributes(ContentType contentType, boolean proximalPrimitiveModeling, Set<Long> parentIds, String branchPath,
			List<LanguageDialect> languageDialects) throws ServiceException {

		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);

		List<AttributeDomain> attributeDomains = new ArrayList<>();

		// Start with 'Is a' relationship which is applicable to all concept types and domains
		attributeDomains.add(IS_A_ATTRIBUTE_DOMAIN);
		
		if (!CollectionUtils.isEmpty(parentIds)) {
			// Lookup ancestors using stated parents
			Set<Long> allAncestors = queryService.findAncestorIdsAsUnion(branchCriteria, false, parentIds);
			allAncestors.addAll(parentIds);

			// Load MRCM using active records applicable to this branch
			MRCM branchMRCM = mrcmLoader.loadActiveMRCM(branchPath, branchCriteria);

			// Find matching domains
			Set<Domain> matchedDomains = branchMRCM.getDomains().stream().filter(domain -> {
				Constraint constraint = proximalPrimitiveModeling ? domain.getProximalPrimitiveConstraint() : domain.getDomainConstraint();
				Long domainConceptId = parseLong(constraint.getConceptId());
				Operator operator = constraint.getOperator();
				if ((operator == null || operator == Operator.descendantorselfof)
						&& parentIds.contains(domainConceptId)) {
					return true;
				}
				return (operator == Operator.descendantof || operator == Operator.descendantorselfof)
						&& allAncestors.contains(domainConceptId);
			}).collect(Collectors.toSet());
			Set<String> domainReferenceComponents = matchedDomains.stream().map(Domain::getReferencedComponentId).collect(Collectors.toSet());

			// Find applicable attributes
			attributeDomains.addAll(branchMRCM.getAttributeDomains().stream()
					.filter(attributeDomain -> attributeDomain.getContentType().ruleAppliesToContentType(contentType)
							&& domainReferenceComponents.contains(attributeDomain.getDomainId())).collect(Collectors.toList()));
		}

		Set<String> attributeIds = attributeDomains.stream().map(AttributeDomain::getReferencedComponentId).collect(Collectors.toSet());
		Collection<ConceptMini> attributeConceptMinis = conceptService.findConceptMinis(branchCriteria, attributeIds, languageDialects).getResultsMap().values();
		for (ConceptMini attributeConceptMini : attributeConceptMinis) {
			attributeConceptMini.addExtraField("attributeDomain",
					attributeDomains.stream().filter(attributeDomain -> attributeConceptMini.getId().equals(attributeDomain.getReferencedComponentId()))
							.collect(Collectors.toSet()));
		}

		return attributeConceptMinis;
	}

	public Collection<ConceptMini> retrieveAttributeValues(ContentType contentType, String attributeId, String termPrefix, String branchPath, List<LanguageDialect> languageDialects) throws ServiceException {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);

		MRCM branchMRCM = mrcmLoader.loadActiveMRCM(branchPath, branchCriteria);

		Set<AttributeRange> attributeRanges;
		if (Concepts.ISA.equals(attributeId)) {
			attributeRanges = Collections.singleton(IS_A_ATTRIBUTE_RANGE);
		} else {
			attributeRanges = branchMRCM.getAttributeRanges().stream()
					.filter(attributeRange -> attributeRange.getContentType().ruleAppliesToContentType(contentType)
							&& attributeRange.getRuleStrength() == RuleStrength.MANDATORY
							&& attributeRange.getReferencedComponentId().equals(attributeId)).collect(Collectors.toSet());
		}

		if (attributeRanges.isEmpty()) {
			throw new IllegalArgumentException("No MRCM Attribute Range found with Mandatory rule strength for given content type and attributeId.");
		} else if (attributeRanges.size() > 1) {
			logger.warn("Multiple Attribute Ranges found with Mandatory rule strength for content type {} and attribute {} : {}.",
					contentType, attributeId, attributeRanges.stream().map(AttributeRange::getId).collect(Collectors.toSet()));
		}

		AttributeRange attributeRange = attributeRanges.iterator().next();

		QueryService.ConceptQueryBuilder conceptQuery = queryService.createQueryBuilder(true)
				.ecl(attributeRange.getRangeConstraint())
				.resultLanguageDialects(languageDialects);

		if (IdentifierService.isConceptId(termPrefix)) {
			conceptQuery.conceptIds(Collections.singleton(termPrefix));
		} else {
			conceptQuery.descriptionCriteria(d -> d.term(termPrefix));
		}

		return queryService.search(conceptQuery, branchPath, RESPONSE_PAGE_SIZE).getContent();
	}

}
