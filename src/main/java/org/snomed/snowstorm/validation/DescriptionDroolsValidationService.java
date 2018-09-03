package org.snomed.snowstorm.validation;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.drools.domain.Concept;
import org.ihtsdo.drools.domain.Constants;
import org.ihtsdo.drools.domain.Description;
import org.ihtsdo.drools.domain.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.services.DescriptionService;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.validation.domain.DroolsDescription;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.*;

public class DescriptionDroolsValidationService implements org.ihtsdo.drools.service.DescriptionService {

	private final VersionControlHelper versionControlHelper;
	private String branchPath;
	private final BranchCriteria branchCriteria;
	private ElasticsearchOperations elasticsearchTemplate;
	private final DescriptionService descriptionService;
	private final QueryService queryService;

	@Value("${validation.resourceFiles.path}")
	private String testResourcesPath;

	private static Set<String> hierarchyRootIds;
	private static final Map<String, Set<String>> refsetToLanguageSpecificWordsMap = new HashMap<>();
	private static final Set<String> caseSignificantWords = new HashSet<>();
	private static final Logger LOGGER = LoggerFactory.getLogger(DescriptionDroolsValidationService.class);

	DescriptionDroolsValidationService(String branchPath,
			BranchCriteria branchCriteria,
			VersionControlHelper versionControlHelper,
			ElasticsearchOperations elasticsearchTemplate,
			DescriptionService descriptionService,
			QueryService queryService) {

		this.branchPath = branchPath;
		this.branchCriteria = branchCriteria;
		this.versionControlHelper = versionControlHelper;
		this.elasticsearchTemplate = elasticsearchTemplate;
		this.descriptionService = descriptionService;
		this.queryService = queryService;
	}

	@PostConstruct
	public void init() {
		loadRefsetSpecificWords(Concepts.GB_EN_LANG_REFSET, testResourcesPath, "gbTerms.txt");
		loadRefsetSpecificWords(Concepts.US_EN_LANG_REFSET, testResourcesPath, "usTerms.txt");
		loadCaseSignificantWords(testResourcesPath);
	}

	@Override
	public Set<String> getFSNs(Set<String> conceptIds, String... languageRefsetIds) {
		return descriptionService.fetchDescriptions(branchPath, conceptIds).stream()
				.filter(d -> d.getTypeId().equals(Concepts.FSN))
				.map(org.snomed.snowstorm.core.data.domain.Description::getTerm)
				.collect(Collectors.toSet());
	}

	@Override
	public Set<org.ihtsdo.drools.domain.Description> findActiveDescriptionByExactTerm(String exactTerm) {
		return findDescriptionByExactTerm(exactTerm, true);
	}

	@Override
	public Set<org.ihtsdo.drools.domain.Description> findInactiveDescriptionByExactTerm(String exactTerm) {
		return findDescriptionByExactTerm(exactTerm, false);
	}

	private Set<org.ihtsdo.drools.domain.Description> findDescriptionByExactTerm(String exactTerm, boolean active) {
		NativeSearchQuery query = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(org.snomed.snowstorm.core.data.domain.Description.class))
						.must(termQuery("active", active))
						.must(termQuery("term", exactTerm))
				)
				.build();
		List<org.snomed.snowstorm.core.data.domain.Description> matches = elasticsearchTemplate.queryForList(query, org.snomed.snowstorm.core.data.domain.Description.class);
		return matches.stream().map(DroolsDescription::new).collect(Collectors.toSet());
	}

	@Override
	public Set<org.ihtsdo.drools.domain.Description> findMatchingDescriptionInHierarchy(org.ihtsdo.drools.domain.Concept concept, org.ihtsdo.drools.domain.Description description) {
		Set<Description> matchingDescriptions = findActiveDescriptionByExactTerm(description.getTerm())
				.stream().filter(d -> d.getLanguageCode().equals(description.getLanguageCode())).collect(Collectors.toSet());

		if (!matchingDescriptions.isEmpty()) {
			// Filter matching descriptions by hierarchy

			// Find root for this concept
			String conceptHierarchyRootId = findStatedHierarchyRootId(concept);
			if (conceptHierarchyRootId != null) {
				LOGGER.info("Found stated hierarchy id {}", conceptHierarchyRootId);

				return matchingDescriptions.stream().filter(d -> {
					Set<Long> matchingDescriptionAncestors = queryService.retrieveAncestors(d.getConceptId(), branchPath, true);
					return matchingDescriptionAncestors.contains(new Long(conceptHierarchyRootId));
				}).collect(Collectors.toSet());
			}
		}
		return Collections.emptySet();
	}

	@Override
	public String getLanguageSpecificErrorMessage(org.ihtsdo.drools.domain.Description description) {
		if (description == null || description.getAcceptabilityMap() == null || description.getTerm() == null) {
			return "";
		}

		String errorMessage = "";

		String[] words = description.getTerm().split("\\s+");

		// convenience variables
		String usAcc = description.getAcceptabilityMap().get(Concepts.US_EN_LANG_REFSET);
		String gbAcc = description.getAcceptabilityMap().get(Concepts.GB_EN_LANG_REFSET);

		// NOTE: Supports international only at this point
		// Only check active synonyms
		if (description.isActive() && Concepts.SYNONYM.equals(description.getTypeId())) {
			for (String word : words) {

				// Step 1: Check en-us preferred synonyms for en-gb spellings
				if (usAcc != null && refsetToLanguageSpecificWordsMap.containsKey(Concepts.GB_EN_LANG_REFSET)
						&& refsetToLanguageSpecificWordsMap.get(Concepts.GB_EN_LANG_REFSET)
						.contains(word.toLowerCase())) {
					errorMessage += "Synonym is preferred in the en-us refset but refers to a word that has en-gb spelling: "
							+ word + "\n";
				}

				// Step 2: Check en-gb preferred synonyms for en-en spellings
				if (gbAcc != null && refsetToLanguageSpecificWordsMap.containsKey(Concepts.US_EN_LANG_REFSET)
						&& refsetToLanguageSpecificWordsMap.get(Concepts.US_EN_LANG_REFSET)
						.contains(word.toLowerCase())) {
					errorMessage += "Synonym is preferred in the en-gb refset but refers to a word that has en-us spelling: "
							+ word + "\n";
				}
			}
		}

		return errorMessage;
	}

	@Override
	public String getCaseSensitiveWordsErrorMessage(org.ihtsdo.drools.domain.Description description) {
		String result = "";

		// return immediately if description or term null
		if (description == null || description.getTerm() == null) {
			return result;
		}

		String[] words = description.getTerm().split("\\s+");

		for (String word : words) {

			// NOTE: Simple test to see if a case-sensitive term exists as
			// written. Original check for mis-capitalization, but false
			// positives, e.g. "oF" appears in list but spuriously reports "of"
			// Map preserved for lower-case matching in future
			if (caseSignificantWords.contains(word)) {

				// term starting with case sensitive word must be ETCS
				if (description.getTerm().startsWith(word)
						&& !Concepts.ENTIRE_TERM_CASE_SENSITIVE.equals(description.getCaseSignificanceId())) {
					result += "Description starts with case-sensitive word but is not marked entire term case " +
							"sensitive: " + word + ".\n";
				}

				// term containing case sensitive word (not at start) must be
				// ETCS or OICCI
				else if (!Concepts.ENTIRE_TERM_CASE_SENSITIVE.equals(description.getCaseSignificanceId())
						&& !Concepts.INITIAL_CHARACTER_CASE_INSENSITIVE.equals(description.getCaseSignificanceId())) {
					result += "Description contains case-sensitive word but is not marked entire term case sensitive " +
							"or only initial character case insensitive: " + word + ".\n";
				}
			}
		}
		return result;
	}

	@Override
	public Set<String> findParentsNotContainSematicTag(Concept concept, String termSemanticTag, String... languageRefsetIds) {
		Set<String> statedParents = new HashSet<>();
		for (Relationship relationship : concept.getRelationships()) {
			if (Constants.IS_A.equals(relationship.getTypeId())
					&& relationship.isActive()
					&& Constants.STATED_RELATIONSHIP.equals(relationship.getCharacteristicTypeId())) {
				statedParents.add(relationship.getDestinationId());
			}
		}

		NativeSearchQuery query = new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(org.snomed.snowstorm.core.data.domain.Description.class))
						.must(termsQuery(org.snomed.snowstorm.core.data.domain.Description.Fields.CONCEPT_ID, statedParents))
						.must(termQuery(org.snomed.snowstorm.core.data.domain.Description.Fields.ACTIVE, true))
						.must(termQuery(org.snomed.snowstorm.core.data.domain.Description.Fields.TYPE_ID, Concepts.FSN))
						.mustNot(termQuery(org.snomed.snowstorm.core.data.domain.Description.Fields.TAG, termSemanticTag))
				)
				.build();
		List<Description> descriptions = elasticsearchTemplate.queryForList(query, Description.class);
		return descriptions.stream().map(Description::getConceptId).collect(Collectors.toSet());
	}

	private String findStatedHierarchyRootId(org.ihtsdo.drools.domain.Concept concept) {
		Set<? extends org.ihtsdo.drools.domain.Relationship> statedIsARelationships = concept.getRelationships().stream().filter(r -> r.isActive()
				&& Concepts.STATED_RELATIONSHIP.equals(r.getCharacteristicTypeId())
				&& Concepts.ISA.equals(r.getTypeId())).collect(Collectors.toSet());

		if (statedIsARelationships.isEmpty()) {
			return null;
		}

		Set<String> hierarchyRootIds = findHierarchyRootsOnMAIN();
		Sets.SetView<String> statedHierarchyRoot = Sets.intersection(hierarchyRootIds, statedIsARelationships);
		if (!statedHierarchyRoot.isEmpty()) {
			return statedHierarchyRoot.iterator().next();
		}

		// Search ancestors of stated is-a relationships
		String firstStatedParentId = statedIsARelationships.iterator().next().getDestinationId();
		Set<Long> statedAncestors = queryService.retrieveAncestors(firstStatedParentId, branchPath, true);
		statedHierarchyRoot = Sets.intersection(hierarchyRootIds, statedAncestors);
		if (!statedHierarchyRoot.isEmpty()) {
			return statedHierarchyRoot.iterator().next();
		}

		return null;
	}

	private Set<String> findHierarchyRootsOnMAIN() {
		if (hierarchyRootIds == null) {
			synchronized (DescriptionDroolsValidationService.class) {
				QueryBuilder mainBranchCriteria = versionControlHelper.getBranchCriteria("MAIN").getEntityBranchCriteria(org.snomed.snowstorm.core.data.domain.Description.class);
				NativeSearchQuery query = new NativeSearchQueryBuilder()
						.withQuery(boolQuery()
								.must(mainBranchCriteria)
								.must(termQuery("active", true))
								.must(termQuery("characteristicTypeId", Concepts.INFERRED_RELATIONSHIP))
								.must(termQuery("destinationId", Concepts.SNOMEDCT_ROOT)))
						.withPageable(PageRequest.of(0, 1000))
						.build();
				List<Relationship> relationships = elasticsearchTemplate.queryForList(query, Relationship.class);
				hierarchyRootIds = relationships.stream().map(Relationship::getSourceId).collect(Collectors.toSet());
			}
		}
		return hierarchyRootIds;
	}

	private void loadRefsetSpecificWords(String refsetId, String testResourcesPath, String fileName) {
		Set<String> words = new HashSet<>();
		File file = new File(testResourcesPath, fileName);
		try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {
			// skip header line
			bufferedReader.readLine();
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				words.add(line.toLowerCase());
			}
			LOGGER.info("Loaded {} language-specific spellings into cache for refset {} from file {}",
					words.size(), refsetId, fileName);
			refsetToLanguageSpecificWordsMap.put(refsetId, words);
		} catch (IOException e) {
			LOGGER.error("Failed to load language-specific terms for refset {} in file {}", refsetId, fileName);
		}
	}

	private void loadCaseSignificantWords(String testResourcesPath) {
		String fileName = "cs_words.txt";
		File file = new File(testResourcesPath, fileName);
		try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {
			// skip header line
			bufferedReader.readLine();
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				String[] words = line.split("\\s+");
				// format: 0: word, 1: type (only use type 1 words)
				if (words[1].equals("1")) {
					caseSignificantWords.add(words[0]);
				}
			}
			LOGGER.info("Loaded {} case sensitive words into cache from file {}", caseSignificantWords.size(), fileName);
		} catch (IOException e) {
			LOGGER.error("Failed to load case sensitive words file {}", fileName);
		}
	}
}
