package org.snomed.snowstorm.validation;

import java.io.File;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import org.ihtsdo.drools.RuleExecutor;
import org.ihtsdo.drools.RuleExecutorFactory;
import org.ihtsdo.drools.response.InvalidContent;
import org.ihtsdo.drools.service.TestResourceProvider;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.services.BranchMetadataKeys;
import org.snomed.snowstorm.core.data.services.DescriptionService;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.validation.domain.DroolsConcept;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.*;
import java.util.stream.Collectors;

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

	private final String droolsRulesPath;
	private final ResourceManager testResourceManager;

	private RuleExecutor ruleExecutor;
	private TestResourceProvider testResourceProvider;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public DroolsValidationService(
			@Value("${validation.drools.rules.path}") String droolsRulesPath,
			@Autowired TestResourcesResourceManagerConfiguration resourceManagerConfiguration,
			@Autowired ResourceLoader cloudResourceLoader) {

		this.droolsRulesPath = droolsRulesPath;
		testResourceManager = new ResourceManager(resourceManagerConfiguration, cloudResourceLoader);
		newRuleExecutorAndResources();
	}

	public List<InvalidContent> validateConcept(String branchPath, Concept concept) throws ServiceException {
		return validateConcepts(branchPath, Collections.singleton(concept));
	}

	public List<InvalidContent> validateConcepts(String branchPath, Set<Concept> concepts) throws ServiceException {
		// Get drools assertion groups to run
		Branch branchWithInheritedMetadata = branchService.findBranchOrThrow(branchPath, true);
		String assertionGroupNamesMetaString = branchWithInheritedMetadata.getMetadata().get(BranchMetadataKeys.ASSERTION_GROUP_NAMES);
		if (assertionGroupNamesMetaString == null) {
			throw new ServiceException("'" + BranchMetadataKeys.ASSERTION_GROUP_NAMES + "' not set on branch metadata for Snomed-Drools validation configuration.");
		}
		String[] names = assertionGroupNamesMetaString.split(",");
		Set<String> ruleSetNames = new HashSet<>(Arrays.asList(names));
		if (ruleSetNames.isEmpty()) {
			logger.info("Branch metadata item '{}' set as empty for {}, skipping Snomed-Drools validation.", BranchMetadataKeys.ASSERTION_GROUP_NAMES, branchPath);
			return Collections.emptyList();
		}

		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchWithInheritedMetadata);
		Set<DroolsConcept> droolsConcepts = concepts.stream().map(DroolsConcept::new).collect(Collectors.toSet());

		ConceptDroolsValidationService conceptService = new ConceptDroolsValidationService(branchPath, branchCriteria, elasticsearchOperations, queryService);
		DescriptionDroolsValidationService descriptionService = new DescriptionDroolsValidationService(branchPath, branchCriteria, versionControlHelper, elasticsearchOperations,
				this.descriptionService, queryService, testResourceProvider);
		RelationshipDroolsValidationService relationshipService = new RelationshipDroolsValidationService(branchCriteria, elasticsearchOperations);
		return ruleExecutor.execute(ruleSetNames, droolsConcepts, conceptService, descriptionService, relationshipService, false, false);
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
}
