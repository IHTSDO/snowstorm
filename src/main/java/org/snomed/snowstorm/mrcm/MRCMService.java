package org.snomed.snowstorm.mrcm;

import ch.qos.logback.classic.Level;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.langauges.ecl.domain.refinement.Operator;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.data.services.identifier.IdentifierService;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.snomed.snowstorm.mrcm.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.VersionControlHelper.LARGE_PAGE;
import static java.lang.Long.parseLong;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_DIALECTS;
import static org.snomed.snowstorm.mrcm.model.MRCM.IS_A_ATTRIBUTE_DOMAIN;

@Service
public class MRCMService {

	private static final PageRequest RESPONSE_PAGE_SIZE = PageRequest.of(0, 50);
	private static final String CHILDREN = "children";

	@Autowired
	private MRCMLoader mrcmLoader;

	@Autowired
	private QueryService queryService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchRestTemplate elasticsearchTemplate;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public MRCM loadActiveMRCMFromCache(String branchPath) throws ServiceException {
		return mrcmLoader.loadActiveMRCMFromCache(branchPath);
	}

	public Collection<ConceptMini> retrieveDomainAttributeConceptMinis(ContentType contentType, boolean proximalPrimitiveModeling, Set<Long> parentIds,
			String branchPath, List<LanguageDialect> languageDialects) throws ServiceException {

		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		final MRCM branchMRCM = mrcmLoader.loadActiveMRCM(branchPath, branchCriteria);

		final List<AttributeDomain> attributeDomains = doRetrieveDomainAttributes(contentType, proximalPrimitiveModeling, parentIds, branchCriteria, branchMRCM);
		Set<String> attributeIds = attributeDomains.stream().map(AttributeDomain::getReferencedComponentId).collect(Collectors.toSet());
		Collection<ConceptMini> attributeConceptMinis = conceptService.findConceptMinis(branchCriteria, attributeIds, languageDialects).getResultsMap().values();
		if (attributeConceptMinis.size() < attributeIds.size()) {
			Set<String> foundConceptIds = attributeConceptMinis.stream().map(ConceptMini::getConceptId).collect(Collectors.toSet());
			for (String attributeId : attributeIds) {
				if (!foundConceptIds.contains(attributeId)) {
					logger.warn("The concept to represent attribute {} is in the MRCM Attribute Domain reference set but is missing from branch {}.",
							attributeId, branchPath);
				}
			}
		}
		addExtraConceptMiniFields(attributeDomains, contentType, branchMRCM, attributeConceptMinis);

		return attributeConceptMinis;
	}

	public Collection<ConceptMini> retrieveDomainAttributes(ContentType contentType, boolean proximalPrimitiveModeling, Set<Long> parentIds,
			String branchPath, BranchCriteria branchCriteria) throws ServiceException {

		// Load MRCM using active records applicable to this branch
		final MRCM branchMRCM = mrcmLoader.loadActiveMRCM(branchPath, branchCriteria);
		final List<AttributeDomain> attributeDomains = doRetrieveDomainAttributes(contentType, proximalPrimitiveModeling, parentIds, branchCriteria, branchMRCM);

		Set<String> attributeIds = attributeDomains.stream().map(AttributeDomain::getReferencedComponentId).collect(Collectors.toSet());
		Collection<ConceptMini> attributeConceptMinis = conceptService.findConceptMinis(branchCriteria, attributeIds, DEFAULT_LANGUAGE_DIALECTS).getResultsMap().values();
		if (attributeConceptMinis.size() < attributeIds.size()) {
			Set<String> foundConceptIds = attributeConceptMinis.stream().map(ConceptMini::getConceptId).collect(Collectors.toSet());
			for (String attributeId : attributeIds) {
				if (!foundConceptIds.contains(attributeId)) {
					logger.warn("The concept to represent attribute {} is in the MRCM Attribute Domain reference set but is missing from branch {}.",
							attributeId, branchPath);
				}
			}
		}
		addExtraConceptMiniFields(attributeDomains, contentType, branchMRCM, attributeConceptMinis);

		return attributeConceptMinis;
	}

	public List<AttributeDomain> doRetrieveDomainAttributes(ContentType contentType, boolean proximalPrimitiveModeling, Set<Long> parentIds,
			BranchCriteria branchCriteria, MRCM branchMRCM) throws ServiceException {

		List<AttributeDomain> attributeDomains = new ArrayList<>();

		// Start with 'Is a' relationship which is applicable to all concept types and domains
		attributeDomains.add(IS_A_ATTRIBUTE_DOMAIN);

		if (!CollectionUtils.isEmpty(parentIds)) {
			// Lookup ancestors using stated parents
			Set<Long> allAncestors = queryService.findAncestorIdsAsUnion(branchCriteria, false, parentIds);
			allAncestors.addAll(parentIds);

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

		return attributeDomains;
	}

	private void addExtraConceptMiniFields(final List<AttributeDomain> attributeDomains, final ContentType contentType, final MRCM branchMRCM, final Collection<ConceptMini> attributeConceptMinis) {
		attributeConceptMinis.forEach(attributeConceptMini -> {
			attributeConceptMini.addExtraField("attributeDomain",
					attributeDomains.stream().filter(attributeDomain -> attributeConceptMini.getId().equals(attributeDomain.getReferencedComponentId()))
							.collect(Collectors.toSet()));
			addAttributeRangesToExtraConceptMiniFields(attributeConceptMini, attributeDomains, contentType, branchMRCM);
		});
	}

	private void addAttributeRangesToExtraConceptMiniFields(final ConceptMini conceptMini, final List<AttributeDomain> attributeDomains, final ContentType contentType, final MRCM branchMRCM) {
		final List<AttributeRange> attributeRanges = new ArrayList<>();
		branchMRCM.getAttributeRanges().forEach(attributeRange -> attributeDomains.stream()
				.filter(attributeDomain -> attributeRange.getReferencedComponentId().equals(attributeDomain.getReferencedComponentId()) && attributeRange.getContentType().ruleAppliesToContentType(contentType))
				.map(attributeDomain -> attributeRange).forEach(attributeRanges::add));
		conceptMini.addExtraField("attributeRange", attributeRanges);
	}

	public Collection<ConceptMini> retrieveAttributeValues(ContentType contentType, String attributeId, String termPrefix, String branchPath, List<LanguageDialect> languageDialects) throws ServiceException {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		MRCM branchMRCM = mrcmLoader.loadActiveMRCM(branchPath, branchCriteria);
		return retrieveAttributeValues(contentType, attributeId, termPrefix, branchPath, languageDialects, branchMRCM);
	}

	public Collection<ConceptMini> retrieveAttributeValues(ContentType contentType, String attributeId, String termPrefix, String branchPath,
			List<LanguageDialect> languageDialects, MRCM branchMRCM) {

		QueryService.ConceptQueryBuilder conceptQuery = createAttributeValuesQuery(contentType, attributeId, termPrefix, languageDialects, branchMRCM);
		return queryService.search(conceptQuery, branchPath, RESPONSE_PAGE_SIZE).getContent();
	}

	public Collection<Long> retrieveAttributeValueIds(ContentType contentType, String attributeId, String termPrefix, String branchPath,
			List<LanguageDialect> languageDialects, MRCM branchMRCM, BranchCriteria branchCriteria) {

		QueryService.ConceptQueryBuilder conceptQuery = createAttributeValuesQuery(contentType, attributeId, termPrefix, languageDialects, branchMRCM);
		return queryService.searchForIds(conceptQuery, branchPath, branchCriteria, RESPONSE_PAGE_SIZE).getContent();
	}

	private QueryService.ConceptQueryBuilder createAttributeValuesQuery(ContentType contentType, String attributeId, String termPrefix, List<LanguageDialect> languageDialects, MRCM branchMRCM) {
		Set<AttributeRange> attributeRanges = branchMRCM.getMandatoryAttributeRanges(attributeId, contentType);

		if (attributeRanges.isEmpty()) {
			throw new IllegalArgumentException("No MRCM Attribute Range found with Mandatory rule strength for given content type and attributeId.");
		} else if (attributeRanges.size() > 1) {
			logger.warn("Multiple Attribute Ranges found with Mandatory rule strength for content type {} and attribute {} : {}.",
					contentType, attributeId, attributeRanges.stream().map(AttributeRange::getId).collect(Collectors.toSet()));
		}

		AttributeRange attributeRange = attributeRanges.iterator().next();

		QueryService.ConceptQueryBuilder conceptQuery = queryService.createQueryBuilder(Relationship.CharacteristicType.inferred)
				.ecl(attributeRange.getRangeConstraint())
				.resultLanguageDialects(languageDialects);

		if (IdentifierService.isConceptId(termPrefix)) {
			conceptQuery.conceptIds(Collections.singleton(termPrefix));
		} else {
			conceptQuery.descriptionCriteria(d -> d.term(termPrefix).active(true));
		}
		return conceptQuery;
	}

	public ConceptMini retrieveConceptModelAttributeHierarchy(String branch, List<LanguageDialect> languageDialects) {
		logger.info("Loading concept model attribute hierarchy.");
		TimerUtil timer = new TimerUtil("attribute-tree", Level.INFO);
		String topId = Concepts.CONCEPT_MODEL_ATTRIBUTE;
		long topIdLong = parseLong(topId);

		// Load all attributes including terms
		List<ConceptMini> allAttributes = ecl("<<" + topId, branch, languageDialects);
		timer.checkpoint("load all with terms");
		Map<Long, ConceptMini> attributeMap = allAttributes.stream().collect(Collectors.toMap(ConceptMini::getConceptIdAsLong, Function.identity()));
		if (!attributeMap.containsKey(topIdLong)) {
			throw new IllegalStateException("Concept not found: " + topId + " | Concept model attribute (attribute) |.");
		}
		Set<Long> remainingAttributes = new HashSet<>(attributeMap.keySet());
		remainingAttributes.remove(topIdLong);

		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branch);

		NativeSearchQueryBuilder queryConceptQuery = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(QueryConcept.class))
						.must(termQuery(QueryConcept.Fields.STATED, false))
						.filter(termsQuery(QueryConcept.Fields.CONCEPT_ID, remainingAttributes))
				)
				.withFields(QueryConcept.Fields.CONCEPT_ID, QueryConcept.Fields.PARENTS)
				.withPageable(LARGE_PAGE);
		try (SearchHitsIterator<QueryConcept> queryConcepts = elasticsearchTemplate.searchForStream(queryConceptQuery.build(), QueryConcept.class)) {
			queryConcepts.forEachRemaining(hit -> {
				for (Long parent : hit.getContent().getParents()) {
					ConceptMini parentMini = attributeMap.get(parent);
					if (parentMini.getExtraFields() == null || parentMini.getExtraFields().get(CHILDREN) == null) {
						parentMini.addExtraField(CHILDREN, new ArrayList<>());
					}
					@SuppressWarnings("unchecked")
					List<ConceptMini> children = (List<ConceptMini>) parentMini.getExtraFields().get(CHILDREN);
					children.add(attributeMap.get(hit.getContent().getConceptIdL()));
					children.sort(Comparator.comparing(ConceptMini::getFsnTerm));
				}
			});
		}
		timer.finish();

		return attributeMap.get(topIdLong);
	}

	private List<ConceptMini> ecl(String ecl, String branch, List<LanguageDialect> languageDialects) {
		return queryService.search(queryService.createQueryBuilder(false).resultLanguageDialects(languageDialects).ecl(ecl), branch, PageRequest.of(0, 1_000)).getContent();
	}
}
