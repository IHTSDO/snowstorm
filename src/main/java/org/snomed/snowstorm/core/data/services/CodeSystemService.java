package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.repositories.CodeSystemRepository;
import org.snomed.snowstorm.core.data.repositories.CodeSystemVersionRepository;
import org.snomed.snowstorm.core.data.services.pojo.CodeSystemConfiguration;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregationsFactory;
import org.snomed.snowstorm.core.util.DateUtil;
import org.snomed.snowstorm.core.util.LangUtil;
import org.snomed.snowstorm.rest.pojo.CodeSystemUpdateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_CODES;

@Service
public class CodeSystemService {

	public static final String SNOMEDCT = "SNOMEDCT";
	public static final String MAIN = "MAIN";

	@Autowired
	private CodeSystemRepository repository;

	@Autowired
	private CodeSystemVersionRepository versionRepository;

	@Autowired
	private CodeSystemConfigurationService codeSystemConfigurationService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ReleaseService releaseService;

	@Autowired
	private BranchMergeService branchMergeService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ValidatorService validatorService;

	@Autowired
	private ModelMapper modelMapper;

	@Value("${codesystem.all.latest-version.allow-future}")
	private boolean latestVersionCanBeFuture;

	// Cache to prevent expensive aggregations. Entry per branch. Expires if there is a new commit.
	private final ConcurrentHashMap<String, Pair<Date, CodeSystem>> contentInformationCache = new ConcurrentHashMap<>();

	private Logger logger = LoggerFactory.getLogger(getClass());

	public synchronized void init() {
		// Create default code system if it does not yet exist
		if (!repository.findById(SNOMEDCT).isPresent()) {
			createCodeSystem(new CodeSystem(SNOMEDCT, MAIN));
		}
		logger.info("{} code system configurations available.", codeSystemConfigurationService.getConfigurations().size());
		for (CodeSystemConfiguration configuration : codeSystemConfigurationService.getConfigurations()) {
			System.out.println(configuration);
		}
	}

	public boolean codeSystemExistsOnBranch(String branchPath) {
		return findOneByBranchPath(branchPath) != null;
	}

	public synchronized void createCodeSystem(CodeSystem codeSystem) {
		validatorService.validate(codeSystem);
		if (repository.findById(codeSystem.getShortName()).isPresent()) {
			throw new IllegalArgumentException("A code system already exists with this short name.");
		}
		String branchPath = codeSystem.getBranchPath();
		if (findByBranchPath(branchPath).isPresent()) {
			throw new IllegalArgumentException("A code system already exists with this branch path.");
		}
		String parentPath = PathUtil.getParentPath(codeSystem.getBranchPath());
		CodeSystem parentCodeSystem = null;
		if (parentPath != null) {
			Integer dependantVersion = codeSystem.getDependantVersion();
			parentCodeSystem = findByBranchPath(parentPath).orElse(null);
			if (dependantVersion != null) {
				// Check dependant version exists on parent path
				if (parentCodeSystem != null) {
					if (findVersion(parentCodeSystem.getShortName(), dependantVersion) == null) {
						throw new IllegalArgumentException(String.format("No code system version found matching dependantVersion '%s' on the parent branch path '%s'.", dependantVersion, parentPath));
					}
				} else {
					throw new IllegalArgumentException(String.format("No code system found on the parent branch path '%s' so dependantVersion property is not required.", parentPath));
				}
			} else if (parentCodeSystem != null) {
				// Find latest version on parent path
				CodeSystemVersion latestVersion = findLatestImportedVersion(parentCodeSystem.getShortName());
				if (latestVersion != null) {
					codeSystem.setDependantVersion(latestVersion.getEffectiveDate());
				}
			}
		}
		Integer dependantVersion = codeSystem.getDependantVersion();
		boolean branchExists = branchService.exists(branchPath);
		if (parentCodeSystem != null && dependantVersion != null) {
			if (branchExists) {
				throw new IllegalStateException(String.format("Unable to create code system branch with correct base timepoint because branch '%s' already exists!", branchPath));
			}
			// Create branch with base timepoint matching dependant version branch base timepoint
			String releaseBranchPath = getReleaseBranchPath(parentCodeSystem.getBranchPath(), dependantVersion);
			Branch dependantVersionBranch = branchService.findLatest(releaseBranchPath);
			if (dependantVersionBranch == null) {
				throw new IllegalStateException(String.format("Dependant version branch '%s' is missing.", releaseBranchPath));
			}
			branchService.createAtBaseTimepoint(branchPath, dependantVersionBranch.getBase());

		} else if (!branchExists) {
			logger.info("Creating Code System branch '{}'.", branchPath);
			branchService.create(branchPath);
		}
		repository.save(codeSystem);
		logger.info("Code System '{}' created.", codeSystem.getShortName());
	}

	private Optional<CodeSystem> findByBranchPath(String branchPath) {
		List<CodeSystem> codeSystems = elasticsearchOperations.queryForList(
				new NativeSearchQueryBuilder()
						.withQuery(boolQuery().must(termsQuery(CodeSystem.Fields.BRANCH_PATH, branchPath)))
						.build(),
				CodeSystem.class);

		return codeSystems.isEmpty() ? Optional.empty() : Optional.of(codeSystems.get(0));
	}

	public synchronized String createVersion(CodeSystem codeSystem, Integer effectiveDate, String description) {

		if (effectiveDate == null || effectiveDate.toString().length() != 8) {
			throw new IllegalArgumentException("Effective Date must have format yyyymmdd");
		}
		String version = getHyphenatedVersionString(effectiveDate);
		String branchPath = codeSystem.getBranchPath();
		String releaseBranchPath = getReleaseBranchPath(branchPath, effectiveDate);

		CodeSystemVersion codeSystemVersion = versionRepository.findOneByShortNameAndEffectiveDate(codeSystem.getShortName(), effectiveDate);
		if (codeSystemVersion != null) {
			logger.warn("Aborting Code System Version creation. This version already exists.");
			throw new IllegalStateException("Aborting Code System Version creation. This version already exists.");
		}

		logger.info("Creating Code System version - Code System: {}, Version: {}, Release Branch: {}", codeSystem.getShortName(), version, releaseBranchPath);
		logger.info("Versioning content...");
		releaseService.createVersion(effectiveDate, branchPath);

		logger.info("Creating version branch content...");
		Branch branch = branchService.create(releaseBranchPath);

		logger.info("Persisting Code System Version...");
		versionRepository.save(new CodeSystemVersion(codeSystem.getShortName(), branch.getHead(), branchPath, effectiveDate, version, description));

		logger.info("Versioning complete.");

		return version;
	}

	private String getHyphenatedVersionString(Integer effectiveDate) {
		String effectiveDateString = effectiveDate.toString();
		return effectiveDateString.substring(0, 4) + "-" + effectiveDateString.substring(4, 6) + "-" + effectiveDateString.substring(6, 8);
	}

	private String getReleaseBranchPath(String branchPath, Integer effectiveDate) {
		return branchPath + "/" + getHyphenatedVersionString(effectiveDate);
	}

	public synchronized void createVersionIfCodeSystemFoundOnPath(String branchPath, Integer releaseDate) {
		List<CodeSystem> codeSystems = elasticsearchOperations.queryForList(new NativeSearchQuery(termQuery(CodeSystem.Fields.BRANCH_PATH, branchPath)), CodeSystem.class);
		if (!codeSystems.isEmpty()) {
			CodeSystem codeSystem = codeSystems.get(0);
			createVersion(codeSystem, releaseDate, String.format("%s %s import.", codeSystem.getShortName(), releaseDate));
		}
	}

	public List<CodeSystem> findAll() {
		List<CodeSystem> codeSystems = repository.findAll(PageRequest.of(0, 1000, Sort.by(CodeSystem.Fields.SHORT_NAME))).getContent();
		joinContentInformation(codeSystems);
		return codeSystems;
	}

	private void joinContentInformation(List<CodeSystem> codeSystems) {
		for (CodeSystem codeSystem : codeSystems) {
			String branchPath = codeSystem.getBranchPath();

			Branch latestBranch = branchService.findLatest(branchPath);
			if (latestBranch == null) continue;

			// Lookup latest version with an effective date equal or less than today
			codeSystem.setLatestVersion(findLatestEffectiveVersion(codeSystem.getShortName()));

			// Pull from cache
			Pair<Date, CodeSystem> dateCodeSystemPair = contentInformationCache.get(branchPath);
			if (dateCodeSystemPair != null) {
				if (dateCodeSystemPair.getFirst().equals(latestBranch.getHead())) {
					copyDetailsFromCacheEntry(codeSystem, dateCodeSystemPair);
					continue;
				} else {
					// Remove expired cache entry
					contentInformationCache.remove(branchPath);
				}
			}

			doJoinContentInformation(codeSystem, branchPath, latestBranch);
		}
	}

	private void copyDetailsFromCacheEntry(CodeSystem codeSystem, Pair<Date, CodeSystem> cachEntry) {
		CodeSystem cachedCodeSystem = cachEntry.getSecond();
		codeSystem.setLanguages(cachedCodeSystem.getLanguages());
		codeSystem.setModules(cachedCodeSystem.getModules());
	}

	private synchronized void doJoinContentInformation(CodeSystem codeSystem, String branchPath, Branch latestBranch) {

		// Pull from cache again in case this just ran in a different thread
		Pair<Date, CodeSystem> dateCodeSystemPair = contentInformationCache.get(branchPath);
		if (dateCodeSystemPair != null) {
			copyDetailsFromCacheEntry(codeSystem, dateCodeSystemPair);
			return;
		}

		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);

		List<String> acceptableLanguageCodes = new ArrayList<>(DEFAULT_LANGUAGE_CODES);

		// Add list of languages using Description aggregation
		AggregatedPage<Description> descriptionPage = (AggregatedPage<Description>) elasticsearchOperations.queryForPage(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Description.class))
						.must(termQuery(Description.Fields.ACTIVE, true)))
				.withPageable(PageRequest.of(0, 1))
				.addAggregation(AggregationBuilders.terms("language").field(Description.Fields.LANGUAGE_CODE))
				.build(), Description.class);
		if (descriptionPage.hasContent()) {
			// Collect other languages for concept mini lookup
			List<? extends Terms.Bucket> language = ((ParsedStringTerms) descriptionPage.getAggregation("language")).getBuckets();
			List<String> languageCodesSorted = language.stream()
					// sort by number of active descriptions in each language
					.sorted(Comparator.comparing(MultiBucketsAggregation.Bucket::getDocCount).reversed())
					.map(MultiBucketsAggregation.Bucket::getKeyAsString)
					.collect(Collectors.toList());

			// Push english to the bottom to show any translated content first in browsers.
			languageCodesSorted.remove("en");
			languageCodesSorted.add("en");

			// Pull default language code to top if any specified
			String defaultLanguageCode = codeSystem.getDefaultLanguageCode();
			if (languageCodesSorted.contains(defaultLanguageCode)) {
				languageCodesSorted.remove(defaultLanguageCode);
				languageCodesSorted.add(0, defaultLanguageCode);
			}

			acceptableLanguageCodes = languageCodesSorted;

			Map<String, String> langs = new LinkedHashMap<>();
			for (String languageCode : languageCodesSorted) {
				langs.put(languageCode, LangUtil.convertLanguageCodeToName(languageCode));
			}
			codeSystem.setLanguages(langs);
		}

		// Add list of modules using refset member aggregation
		AggregatedPage<ReferenceSetMember> memberPage = (AggregatedPage<ReferenceSetMember>) elasticsearchOperations.queryForPage(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						.must(termQuery(ReferenceSetMember.Fields.ACTIVE, true)))
				.withPageable(PageRequest.of(0, 1))
				.addAggregation(AggregationBuilders.terms("module").field(ReferenceSetMember.Fields.MODULE_ID))
				.build(), ReferenceSetMember.class);
		if (memberPage.hasContent()) {
			Map<String, Long> modulesOfActiveMembers = PageWithBucketAggregationsFactory.createPage(memberPage, memberPage.getAggregations().asList())
					.getBuckets().get("module");
			codeSystem.setModules(conceptService.findConceptMinis(branchCriteria, modulesOfActiveMembers.keySet(), acceptableLanguageCodes).getResultsMap().values());
		}

		// Add to cache
		contentInformationCache.put(branchPath, Pair.of(latestBranch.getHead(), codeSystem));
	}

	public CodeSystem find(String codeSystemShortName) {
		Optional<CodeSystem> codeSystem = repository.findById(codeSystemShortName);
		codeSystem.ifPresent(c -> joinContentInformation(Collections.singletonList(c)));
		return codeSystem.orElse(null);
	}

	public CodeSystem findByDefaultModule(String moduleId) {
		CodeSystemConfiguration codeSystemConfiguration = codeSystemConfigurationService.findByModule(moduleId);
		if (codeSystemConfiguration == null) {
			return null;
		}
		return find(codeSystemConfiguration.getShortName());
	}

	public CodeSystemVersion findVersion(String shortName, int effectiveTime) {
		return versionRepository.findOneByShortNameAndEffectiveDate(shortName, effectiveTime);
	}

	public List<CodeSystemVersion> findAllVersions(String shortName, Boolean showFutureVersions) {
		return findAllVersions(shortName, true, showFutureVersions);
	}

	private List<CodeSystemVersion> findAllVersions(String shortName, boolean ascOrder, Boolean showFutureVersions) {
		List<CodeSystemVersion> content;
		if (ascOrder) {
			content = versionRepository.findByShortNameOrderByEffectiveDate(shortName, LARGE_PAGE).getContent();
		} else {
			content = versionRepository.findByShortNameOrderByEffectiveDateDesc(shortName, LARGE_PAGE).getContent();
		}
		if (showFutureVersions != null && showFutureVersions) {
			return content;
		} else {
			int todaysEffectiveTime = DateUtil.getTodaysEffectiveTime();
			return content.stream().filter(version -> version.getEffectiveDate() <= todaysEffectiveTime).collect(Collectors.toList());
		}
	}

	public CodeSystemVersion findLatestImportedVersion(String shortName) {
		List<CodeSystemVersion> versions = findAllVersions(shortName, false, true);
		if (versions != null && versions.size() > 0) {
			return versions.get(0);
		}
		return null;
	}

	public CodeSystemVersion findLatestEffectiveVersion(String shortName) {
		List<CodeSystemVersion> versions = findAllVersions(shortName, false, false);
		if (!versions.isEmpty()) {
			int todayEffectiveTime = DateUtil.getTodaysEffectiveTime();
			for (CodeSystemVersion version : versions) {
				if (latestVersionCanBeFuture || todayEffectiveTime >= version.getEffectiveDate()) {
					return version;
				}
			}
		}
		return null;
	}

	public void deleteAll() {
		repository.deleteAll();
		versionRepository.deleteAll();
	}

	public void upgrade(String shortName, Integer newDependantVersion) {
		CodeSystem codeSystem = find(shortName);
		if (codeSystem == null) {
			throw new NotFoundException(String.format("Code System with short name '%s' does not exist.", shortName));
		}
		String branchPath = codeSystem.getBranchPath();
		String parentPath = PathUtil.getParentPath(branchPath);
		if (parentPath == null) {
			throw new IllegalArgumentException("The root Code System can not be upgraded.");
		}
		CodeSystem parentCodeSystem = findOneByBranchPath(parentPath);
		if (parentCodeSystem == null) {
			throw new IllegalStateException(String.format("The Code System to be upgraded must be on a branch which is the direct child of another Code System. " +
					"There is no Code System on parent branch '%s'.", parentPath));
		}
		CodeSystemVersion newParentVersion = findVersion(parentCodeSystem.getShortName(), newDependantVersion);
		if (newParentVersion == null) {
			throw new IllegalArgumentException(String.format("Parent Code System %s has no version with effectiveTime '%s'.", parentCodeSystem.getShortName(), newDependantVersion));
		}

		Branch newParentVersionBranch = branchService.findLatest(newParentVersion.getBranchPath());
		Date newParentBaseTimepoint = newParentVersionBranch.getBase();
		branchMergeService.rebaseToSpecificTimepointAndRemoveDuplicateContent(parentPath, newParentBaseTimepoint, branchPath, String.format("Upgrading extension to %s@%s.", parentPath, newParentVersion.getVersion()));
	}

	@Deprecated// Deprecated in favour of upgrade operation.
	public void migrateDependantCodeSystemVersion(CodeSystem codeSystem, String dependantCodeSystem, Integer newDependantVersion, boolean copyMetadata) throws ServiceException {
		try {
			CodeSystemVersion newDependantCodeSystemVersion = versionRepository.findOneByShortNameAndEffectiveDate(dependantCodeSystem, newDependantVersion);
			if (newDependantCodeSystemVersion == null) {
				throw new IllegalStateException("No matching Code System version found for " + dependantCodeSystem + " at " + newDependantVersion);
			}
			if (newDependantCodeSystemVersion.getShortName().equals(codeSystem.getShortName())) {
				throw new IllegalArgumentException("Code System can not depend on itself.");
			}

			logger.info("Migrating code system {} to depend on {} release {}", codeSystem.getShortName(), newDependantCodeSystemVersion.getShortName(),
					newDependantCodeSystemVersion.getEffectiveDate());

			String sourceBranchPath = codeSystem.getBranchPath();
			String targetBranchPath = newDependantCodeSystemVersion.getParentBranchPath() + BranchPathUriUtil.SLASH
					+ newDependantCodeSystemVersion.getVersion() + BranchPathUriUtil.SLASH + codeSystem.getShortName();

			branchMergeService.copyBranchToNewParent(sourceBranchPath, targetBranchPath);

			// Update code system branch path
			codeSystem.setBranchPath(targetBranchPath);
			repository.save(codeSystem);

			if (copyMetadata) {
				Branch sourceBranch = branchService.findBranchOrThrow(sourceBranchPath);
				Branch targetBranch = branchService.findBranchOrThrow(targetBranchPath);
				branchService.updateMetadata(targetBranch.getPath(), sourceBranch.getMetadata());
			}

			logger.info("Migrated code system {} to {}. Run an integrity check next then fix content.", codeSystem.getShortName(), targetBranchPath);
		} catch (ConcurrentModificationException e) {
			throw new ServiceException("Code system migration failed.", e);
		}
	}

	private CodeSystem findOneByBranchPath(String path) {
		List<CodeSystem> results = elasticsearchOperations.queryForList(
				new NativeSearchQueryBuilder().withQuery(termQuery(CodeSystem.Fields.BRANCH_PATH, path)).build(), CodeSystem.class);
		return results.isEmpty() ? null : results.get(0);
	}

	public CodeSystem update(CodeSystem codeSystem, CodeSystemUpdateRequest updateRequest) {
		modelMapper.map(updateRequest, codeSystem);
		validatorService.validate(codeSystem);
		repository.save(codeSystem);
		contentInformationCache.remove(codeSystem.getBranchPath());
		return codeSystem;
	}

	protected void setLatestVersionCanBeFuture(boolean latestVersionCanBeFuture) {
		this.latestVersionCanBeFuture = latestVersionCanBeFuture;
	}
}
