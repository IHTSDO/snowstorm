package org.snomed.snowstorm.validation;

import com.google.common.collect.Iterables;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.ihtsdo.drools.RuleExecutor;
import org.ihtsdo.drools.RuleExecutorFactory;
import org.ihtsdo.drools.response.InvalidContent;
import org.ihtsdo.drools.service.TestResourceProvider;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.util.SearchAfterPage;
import org.snomed.snowstorm.validation.domain.DroolsConcept;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.VersionControlHelper.LARGE_PAGE;
import static java.lang.Long.parseLong;
import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.*;
import static io.kaicode.elasticvc.helper.QueryHelper.*;

@Service
public class DroolsValidationService {

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private DescriptionService descriptionService;

	@Autowired
	private QueryService queryService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ConceptService conceptService;

	private final String droolsRulesPath;
	private final ResourceManager testResourceManager;

	private RuleExecutor ruleExecutor;
	private TestResourceProvider testResourceProvider;
	private final ExecutorService batchExecutorService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public DroolsValidationService(
			@Value("${validation.drools.rules.path}") String droolsRulesPath,
			@Autowired TestResourcesResourceManagerConfiguration resourceManagerConfiguration,
			@Autowired ResourceLoader cloudResourceLoader) {

		this.droolsRulesPath = droolsRulesPath;
		testResourceManager = new ResourceManager(resourceManagerConfiguration, cloudResourceLoader);
		newRuleExecutorAndResources();
		batchExecutorService = Executors.newFixedThreadPool(1);
	}

	public Set<String> getSemanticTags(String language) {
		if (StringUtils.hasLength(language)) {
			Set<String> languageSet = Arrays.stream(language.split(",")).map(String::trim).collect(Collectors.toSet());
			return testResourceProvider.getSemanticTagsByLanguage(languageSet);
		} else {
			return testResourceProvider.getSemanticTags();
		}
	}

	public List<InvalidContent> validateConcept(String branchPath, Concept concept) throws ServiceException {
		return validateConcepts(branchPath, Collections.singleton(concept));
	}

	public List<InvalidContent> validateConcepts(String branchPath, Set<Concept> concepts) throws ServiceException {
		// Get drools assertion groups to run
		Branch branchWithInheritedMetadata = branchService.findBranchOrThrow(branchPath, true);
		String assertionGroupNamesMetaString = branchWithInheritedMetadata.getMetadata().getString(BranchMetadataKeys.ASSERTION_GROUP_NAMES);
		Set<String> assertionExclusionList = getAssertionExclusionList(branchWithInheritedMetadata);
		if (assertionGroupNamesMetaString == null) {
			throw new ServiceException("'" + BranchMetadataKeys.ASSERTION_GROUP_NAMES + "' not set on branch metadata for Snomed-Drools validation configuration.");
		}
		Set<String> ruleSetNames = Arrays.stream(assertionGroupNamesMetaString.split(",")).map(String::trim).filter(StringUtils::hasLength).collect(Collectors.toSet());
		if (ruleSetNames.isEmpty()) {
			logger.info("Branch metadata item '{}' set as empty for {}, skipping Snomed-Drools validation.", BranchMetadataKeys.ASSERTION_GROUP_NAMES, branchPath);
			return Collections.emptyList();
		}

		// Set temp component ids if needed
		concepts.forEach(ConceptValidationHelper::generateTemporaryUUIDsIfNotSet);

		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchWithInheritedMetadata);
		Set<DroolsConcept> droolsConcepts = concepts.stream().map(DroolsConcept::new).collect(Collectors.toSet());

		// Look-up release hashes from the store to set/update the component effectiveTimes
		setReleaseHashAndEffectiveTime(concepts, branchCriteria);
		Set<String> inferredTopLevelHierarchies = getTopLevelHierarchies();
		DisposableQueryService disposableQueryService = new DisposableQueryService(queryService, branchPath, branchCriteria);
		ConceptDroolsValidationService droolsConceptService = new ConceptDroolsValidationService(branchCriteria, elasticsearchOperations, disposableQueryService, inferredTopLevelHierarchies);
		DescriptionDroolsValidationService droolsDescriptionService = new DescriptionDroolsValidationService(branchPath, branchCriteria, elasticsearchOperations,
				this.descriptionService, disposableQueryService, testResourceProvider, inferredTopLevelHierarchies);
		RelationshipDroolsValidationService relationshipService = new RelationshipDroolsValidationService(disposableQueryService);
		final List<InvalidContent> invalidContents = ruleExecutor.execute(ruleSetNames, assertionExclusionList, droolsConcepts, droolsConceptService, droolsDescriptionService, relationshipService, false, false);

		return invalidContents;
	}

	public void validateBatch(String branch, String ecl, boolean afterClassification) {
		batchExecutorService.submit(() -> {
			try {
				final PageRequest page = PageRequest.of(0, 2_000);
				long startTime = new Date().getTime();
				final SearchAfterPage<Long> conceptIds = queryService.searchForIds(queryService.createQueryBuilder(false).ecl(ecl), branch, page);
				final int total = conceptIds.getNumberOfElements();
				long doneCount = 0;
				final String fileName = "validation-bulk-" + new Date().getTime() + ".tsv";
				logger.info("Validating batch of {} concepts using ECL {} on branch {}, writing to {}", total, ecl, branch, fileName);

				try (PrintWriter writer = new PrintWriter(fileName)) {
					writer.println("conceptId\tfsn\terrorCount\tmessages\ttsv-duration\ttotal-duration");
					writer.printf("-\t-\t-\tValidating batch of %s concepts using ECL %s on branch %s\t0\t0%n", total, ecl, branch);
					for (List<Long> partition : Iterables.partition(conceptIds, 100)) {
						final Page<Concept> concepts = conceptService.find(partition, Config.DEFAULT_LANGUAGE_DIALECTS, branch, page);
						for (Concept concept : concepts) {
							doneCount++;
							if (doneCount % 10 == 0) {
								logger.info("Validating concept {} of {}.", doneCount, total);
							}
						}
					}
					long end = new Date().getTime() - startTime;
					writer.printf("-\t-\t-\tValidated batch of %s concepts using ECL %s on branch %s\t0\t%s%n", total, ecl, branch, end);
				} catch (FileNotFoundException e) {
					logger.error("Failed to write validation batch to file {}.", fileName, e);
				}

				logger.info("Validated batch of {} concepts using ECL {} on branch {}, written to {}", total, ecl, branch, fileName);
			} catch (Exception e) {
				logger.error("Failed to validate batch using ECL {} on branch {}", ecl, branch, e);
				throw e;
			}
		});
	}

	@Nullable
	private Set<String> getAssertionExclusionList(Branch branch) {
		Set<String> assertionExclusionList = null;
		if (branch.getMetadata() != null && branch.getMetadata().containsKey(BranchMetadataKeys.ASSERTION_EXCLUSION_LIST)) {
			assertionExclusionList = new HashSet<>(branch.getMetadata().getList(BranchMetadataKeys.ASSERTION_EXCLUSION_LIST));
		}
		return assertionExclusionList;
	}

	private void setReleaseHashAndEffectiveTime(Set<Concept> concepts, BranchCriteria branchCriteria) {
		Map<Long, Concept> conceptMap = new Long2ObjectOpenHashMap<>();
		Map<Long, Description> descriptionMap = new Long2ObjectOpenHashMap<>();
		Map<Long, Relationship> relationshipMap = new Long2ObjectOpenHashMap<>();
		concepts.forEach(concept -> {
			if (newComponent(concept)) {
				return;
			}
			conceptMap.put(concept.getConceptIdAsLong(), concept);
			concept.getDescriptions().forEach(description -> {
				if (newComponent(description)) {
					return;
				}
				descriptionMap.put(parseLong(description.getId()), description);
			});
			concept.getRelationships().forEach(relationship -> {
				if (newComponent(relationship)) {
					return;
				}
				relationshipMap.put(parseLong(relationship.getId()), relationship);
			});
		});
		try (SearchHitsIterator<Concept> conceptStream = elasticsearchOperations.searchForStream(new NativeQueryBuilder()
				.withQuery(bool(b -> b
						.must(branchCriteria.getEntityBranchCriteria(Concept.class))
						.must(termQuery(Concept.Fields.RELEASED, true))
						.must(termsQuery(Concept.Fields.CONCEPT_ID, conceptMap.keySet())))
				)
				.withPageable(LARGE_PAGE)
				.build(), Concept.class)) {
			conceptStream.forEachRemaining(hit -> {
				Concept conceptFromStore = hit.getContent();
				Concept concept = conceptMap.get(conceptFromStore.getConceptIdAsLong());
				concept.setReleasedEffectiveTime(conceptFromStore.getReleasedEffectiveTime());
				concept.setReleaseHash(conceptFromStore.getReleaseHash());
				concept.updateEffectiveTime();
			});
		}
		try (SearchHitsIterator<Description> descriptionStream = elasticsearchOperations.searchForStream(new NativeQueryBuilder()
				.withQuery(bool(b -> b
						.must(branchCriteria.getEntityBranchCriteria(Description.class))
						.must(termQuery(Concept.Fields.RELEASED, true))
						.must(termsQuery(Description.Fields.DESCRIPTION_ID, descriptionMap.keySet())))
				)
				.withPageable(LARGE_PAGE)
				.build(), Description.class)) {
			descriptionStream.forEachRemaining(hit -> {
				Description descriptionFromStore = hit.getContent();
				Description description = descriptionMap.get(parseLong(descriptionFromStore.getId()));
				description.setReleasedEffectiveTime(descriptionFromStore.getReleasedEffectiveTime());
				description.setReleaseHash(descriptionFromStore.getReleaseHash());
				description.updateEffectiveTime();
			});
		}
		try (SearchHitsIterator<Relationship> relationshipsFromStore = elasticsearchOperations.searchForStream(new NativeQueryBuilder()
				.withQuery(bool(b -> b
						.must(branchCriteria.getEntityBranchCriteria(Relationship.class))
						.must(termQuery(Concept.Fields.RELEASED, true))
						.must(termsQuery(Relationship.Fields.RELATIONSHIP_ID, relationshipMap.keySet())))
				)
				.withPageable(LARGE_PAGE)
				.build(), Relationship.class)) {
			relationshipsFromStore.forEachRemaining(hit -> {
				Relationship relationshipFromStore = hit.getContent();
				Relationship relationship = relationshipMap.get(parseLong(relationshipFromStore.getId()));
				relationship.setReleasedEffectiveTime(relationshipFromStore.getReleasedEffectiveTime());
				relationship.setReleaseHash(relationshipFromStore.getReleaseHash());
				relationship.updateEffectiveTime();
			});
		}
	}

	private boolean newComponent(SnomedComponent component) {
		return component.getId() == null || component.getId().contains("-");
	}

	public void newRuleExecutorAndResources() {
		Assert.notNull(droolsRulesPath, "Path to drools rules is required.");
		File dir = new File(droolsRulesPath);
		if (!dir.isDirectory()) {
			if (!dir.mkdirs()) {
				logger.warn("Failed to create directory {}", droolsRulesPath);
			}
		}
		this.ruleExecutor = new RuleExecutorFactory().createRuleExecutor(droolsRulesPath);
		this.testResourceProvider = ruleExecutor.newTestResourceProvider(testResourceManager);
	}

	private Long topLevelHierarchiesLastFetched;

	private Set<String> topLevelHierarchies;

	private Set<String> getTopLevelHierarchies() {
		Branch latestMainBranch = branchService.findLatest("MAIN");
		if (topLevelHierarchiesLastFetched == null || latestMainBranch.getHeadTimestamp() > topLevelHierarchiesLastFetched) {
			topLevelHierarchies = queryService.findChildrenIdsAsUnion(versionControlHelper.getBranchCriteria("MAIN"), false, Collections.singleton(Long.valueOf(Concepts.SNOMEDCT_ROOT)))
					.stream().map(Object::toString).collect(Collectors.toSet());
			topLevelHierarchiesLastFetched = latestMainBranch.getHeadTimestamp();
		}
		return topLevelHierarchies;
	}
}
