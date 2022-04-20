package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Metadata;
import org.elasticsearch.common.Strings;
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
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.util.DateUtil;
import org.snomed.snowstorm.core.util.LangUtil;
import org.snomed.snowstorm.rest.pojo.CodeSystemUpdateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.Pair;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static java.lang.String.format;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_CODES;
import static org.snomed.snowstorm.core.data.services.BranchMetadataKeys.PREVIOUS_PACKAGE;
import static org.snomed.snowstorm.core.data.services.BranchMetadataKeys.PREVIOUS_RELEASE;
import static org.snomed.snowstorm.core.data.services.BranchMetadataKeys.PREVIOUS_DEPENDENCY_PACKAGE;

@Service
public class CodeSystemService {

	public static final String SNOMEDCT = "SNOMEDCT";
	public static final String MAIN = "MAIN";
	private static final Pattern VERSION_BRANCH_NAME_PATTERN = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

	@Autowired
	private CodeSystemRepository repository;

	@Autowired
	private CodeSystemVersionRepository versionRepository;

	@Autowired
	private CodeSystemConfigurationService codeSystemConfigurationService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private SBranchService sBranchService;

	@Autowired
	private ReleaseService releaseService;

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

	@Value("${codesystem.all.latest-version.allow-internal-release}")
	private boolean latestVersionCanBeInternalRelease;

	// Cache to prevent expensive aggregations. Entry per branch. Expires if there is a new commit.
	private final ConcurrentHashMap<String, Pair<Date, CodeSystem>> contentInformationCache = new ConcurrentHashMap<>();

	private Map<String, Integer> versionCommitEffectiveTimeCache = new HashMap<>();

	private final Logger logger = LoggerFactory.getLogger(getClass());

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

	public void clearCache() {
		contentInformationCache.clear();
	}

	public boolean codeSystemExistsOnBranch(String branchPath) {
		return findOneByBranchPath(branchPath) != null;
	}

	public synchronized CodeSystem createCodeSystem(CodeSystem newCodeSystem) {
		validatorService.validate(newCodeSystem);
		if (repository.findById(newCodeSystem.getShortName()).isPresent()) {
			throw new IllegalArgumentException("A code system already exists with short name " + newCodeSystem.getShortName());
		}
		String branchPath = newCodeSystem.getBranchPath();
		if (findByBranchPath(branchPath).isPresent()) {
			throw new IllegalArgumentException("A code system already exists on branch path " + branchPath);
		}
		String parentPath = PathUtil.getParentPath(newCodeSystem.getBranchPath());
		CodeSystem parentCodeSystem = null;
		if (parentPath != null) {
			Integer dependantVersionEffectiveTime = newCodeSystem.getDependantVersionEffectiveTime();
			parentCodeSystem = findByBranchPath(parentPath).orElse(null);
			if (dependantVersionEffectiveTime != null) {
				// Check dependant version exists on parent path
				if (parentCodeSystem != null) {
					if (findVersion(parentCodeSystem.getShortName(), dependantVersionEffectiveTime) == null) {
						throw new IllegalArgumentException(format("No code system version found matching dependantVersion '%s' on the parent branch path '%s'.",
								dependantVersionEffectiveTime, parentPath));
					}
				} else {
					throw new IllegalArgumentException(format("No code system found on the parent branch path '%s' so dependantVersion property is not required.",
							parentPath));
				}
			} else if (parentCodeSystem != null) {
				// Find latest version on parent path
				CodeSystemVersion latestVersion = findLatestImportedVersion(parentCodeSystem.getShortName());
				if (latestVersion != null) {
					newCodeSystem.setDependantVersionEffectiveTime(latestVersion.getEffectiveDate());
				}
			}
		}
		Integer dependantVersionEffectiveTime = newCodeSystem.getDependantVersionEffectiveTime();
		boolean branchExists = branchService.exists(branchPath);
		if (parentCodeSystem != null && dependantVersionEffectiveTime != null) {
			if (branchExists) {
				throw new IllegalStateException(format("Unable to create code system branch with correct base timepoint because branch '%s' already exists!", branchPath));
			}
			// Create branch with base timepoint matching dependant version branch base timepoint
			String releaseBranchPath = getReleaseBranchPath(parentCodeSystem.getBranchPath(), dependantVersionEffectiveTime);
			Branch dependantVersionBranch = branchService.findLatest(releaseBranchPath);
			if (dependantVersionBranch == null) {
				throw new IllegalStateException(format("Dependant version branch '%s' is missing.", releaseBranchPath));
			}
			branchService.createAtBaseTimepoint(branchPath, dependantVersionBranch.getBase());

		} else if (!branchExists) {
			logger.info("Creating Code System branch '{}'.", branchPath);
			sBranchService.create(branchPath);
		}
		repository.save(newCodeSystem);
		logger.info("Code System '{}' created.", newCodeSystem.getShortName());
		return newCodeSystem;
	}

	public Optional<CodeSystem> findByBranchPath(String branchPath) {
		List<CodeSystem> codeSystems = elasticsearchOperations.search(
				new NativeSearchQueryBuilder()
						.withQuery(boolQuery().must(termsQuery(CodeSystem.Fields.BRANCH_PATH, branchPath)))
						.build(), CodeSystem.class)
				.stream()
				.map(SearchHit::getContent)
				.collect(Collectors.toList());

		return codeSystems.isEmpty() ? Optional.empty() : Optional.of(codeSystems.get(0));
	}

	public CodeSystem findClosestCodeSystemUsingAnyBranch(String branchPath, boolean includeContentInformation) {
		do {
			Optional<CodeSystem> codeSystemOptional = findByBranchPath(branchPath);
			if (codeSystemOptional.isPresent()) {
				CodeSystem codeSystem = codeSystemOptional.get();
				if(includeContentInformation) {
					joinContentInformation(Collections.singletonList(codeSystem));
				}
				return codeSystem;
			}
			branchPath = PathUtil.getParentPath(branchPath);
		} while (branchPath != null);
		return null;
	}

	public String createVersion(CodeSystem codeSystem, Integer effectiveDate, String description) {
		return createVersion(codeSystem, effectiveDate, description, false);
	}

	@PreAuthorize("hasPermission('ADMIN', #codeSystem.branchPath)")
	public synchronized String createVersion(CodeSystem codeSystem, Integer effectiveDate, String description, boolean internalRelease) {

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
		Branch branch = sBranchService.create(releaseBranchPath);

		logger.info("Persisting Code System Version...");
		versionRepository.save(new CodeSystemVersion(codeSystem.getShortName(), branch.getHead(), branchPath, effectiveDate, version, description, internalRelease));

		logger.info("Versioning complete.");

		return version;
	}

	@PreAuthorize("hasPermission('ADMIN', #codeSystemVersion.branchPath)")
	public synchronized CodeSystemVersion updateCodeSystemVersionPackage(CodeSystemVersion codeSystemVersion, String releasePackage) {
		String shortName = codeSystemVersion.getShortName();
		Integer effectiveDate = codeSystemVersion.getEffectiveDate();
		CodeSystemVersion versionToUpdate = findVersion(shortName, effectiveDate);
		if (versionToUpdate == null) {
			throw new IllegalStateException(String.format("No code system version found for %s with effective date %s", shortName, effectiveDate));
		}
		versionToUpdate.setReleasePackage(releasePackage);
		versionRepository.save(versionToUpdate);
		return versionToUpdate;
	}

	private String getHyphenatedVersionString(Integer effectiveDate) {
		String effectiveDateString = effectiveDate.toString();
		return effectiveDateString.substring(0, 4) + "-" + effectiveDateString.substring(4, 6) + "-" + effectiveDateString.substring(6, 8);
	}

	private Integer getVersionEffectiveDateFromBranchName(String branchName) {
		if (branchName != null && VERSION_BRANCH_NAME_PATTERN.matcher(branchName).matches()) {
			final String dateString = branchName.substring(0, 4) + branchName.substring(5, 7) + branchName.substring(8, 10);
			return Integer.parseInt(dateString);
		}
		return null;
	}

	private String getReleaseBranchPath(String branchPath, Integer effectiveDate) {
		return branchPath + "/" + getHyphenatedVersionString(effectiveDate);
	}

	public synchronized void createVersionIfCodeSystemFoundOnPath(String branchPath, Integer releaseDate, boolean internalRelease) {
		List<CodeSystem> codeSystems = elasticsearchOperations.search(new NativeSearchQuery(termQuery(CodeSystem.Fields.BRANCH_PATH, branchPath)), CodeSystem.class)
				.get().map(SearchHit::getContent).collect(Collectors.toList());
		if (!codeSystems.isEmpty()) {
			CodeSystem codeSystem = codeSystems.get(0);
			createVersion(codeSystem, releaseDate, format("%s %s import.", codeSystem.getShortName(), releaseDate), internalRelease);
		}
	}

	public List<CodeSystem> findAll() {
		List<CodeSystem> codeSystems = repository.findAll(PageRequest.of(0, 10_000, Sort.by(CodeSystem.Fields.SHORT_NAME))).getContent();
		joinContentInformation(codeSystems);
		return codeSystems;
	}

	@Cacheable("code-system-branches")
	public List<String> findAllCodeSystemBranchesUsingCache() {
		return repository.findAll(PageRequest.of(0, 1000, Sort.by(CodeSystem.Fields.SHORT_NAME))).getContent().stream().map(CodeSystem::getBranchPath).sorted().collect(Collectors.toList());
	}

	private void joinContentInformation(List<CodeSystem> codeSystems) {
		for (CodeSystem codeSystem : codeSystems) {
			String branchPath = codeSystem.getBranchPath();

			Branch latestBranch = branchService.findLatest(branchPath);
			if (latestBranch == null) continue;

			// Lookup latest version with an effective date equal or less than today
			codeSystem.setLatestVersion(findLatestVisibleVersion(codeSystem.getShortName()));

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
		codeSystem.setDependantVersionEffectiveTime(cachedCodeSystem.getDependantVersionEffectiveTime());
	}

	private synchronized void doJoinContentInformation(CodeSystem codeSystem, String branchPath, Branch workingBranch) {

		// Pull from cache again in case this just ran in a different thread
		Pair<Date, CodeSystem> dateCodeSystemPair = contentInformationCache.get(branchPath);
		if (dateCodeSystemPair != null) {
			copyDetailsFromCacheEntry(codeSystem, dateCodeSystemPair);
			return;
		}

		// Set dependant version effectiveTime (transient field)
		if (!PathUtil.isRoot(branchPath)) {
			Integer effectiveTime = getVersionEffectiveTime(PathUtil.getParentPath(branchPath), workingBranch.getBase(), codeSystem.getShortName());
			if (effectiveTime == null) {
				logger.warn("Code System {} is not dependant on a specific version of the parent Code System. " +
								"The working branch {} has a base timepoint of {} which does not match the base of any version branches of {}.",
						codeSystem, branchPath, workingBranch.getBase(), PathUtil.getParentPath(branchPath));
			}
			codeSystem.setDependantVersionEffectiveTime(effectiveTime);
		}

		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(workingBranch);

		List<String> acceptableLanguageCodes = new ArrayList<>(DEFAULT_LANGUAGE_CODES);

		// Add list of languages using Description aggregation
		SearchHits<Description> descriptionSearch = elasticsearchOperations.search(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(Description.class))
						.must(termQuery(Description.Fields.ACTIVE, true)))
				.withPageable(PageRequest.of(0, 1))
				.addAggregation(AggregationBuilders.terms("language").field(Description.Fields.LANGUAGE_CODE))
				.build(), Description.class);
		if (descriptionSearch.hasAggregations()) {
			// Collect other languages for concept mini lookup
			List<? extends Terms.Bucket> language = ((ParsedStringTerms) Objects.requireNonNull(descriptionSearch.getAggregations()).get("language")).getBuckets();
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
		SearchHits<ReferenceSetMember> memberPage = elasticsearchOperations.search(new NativeSearchQueryBuilder()
				.withQuery(boolQuery()
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						.must(termQuery(ReferenceSetMember.Fields.ACTIVE, true)))
				.withPageable(PageRequest.of(0, 1))
				.addAggregation(AggregationBuilders.terms("module").field(ReferenceSetMember.Fields.MODULE_ID))
				.build(), ReferenceSetMember.class);
		if (memberPage.hasAggregations()) {
			Map<String, Long> modulesOfActiveMembers = PageWithBucketAggregationsFactory.createPage(memberPage, PageRequest.of(0, 1))
					.getBuckets().get("module");
			List<LanguageDialect> languageDialects = acceptableLanguageCodes.stream().map(LanguageDialect::new).collect(Collectors.toList());
			codeSystem.setModules(conceptService.findConceptMinis(branchCriteria, modulesOfActiveMembers.keySet(), languageDialects).getResultsMap().values());
		}

		// Add to cache
		contentInformationCache.put(branchPath, Pair.of(workingBranch.getHead(), codeSystem));
	}

	public synchronized Integer getVersionEffectiveTime(String codeSystemBranch, Date timepoint, String forChildCodeSystem) {
		final String key = codeSystemBranch + timepoint.getTime();
		return versionCommitEffectiveTimeCache.computeIfAbsent(key, k -> {
			CodeSystem codeSystem = findByBranchPath(codeSystemBranch).orElse(null);
			if (codeSystem == null) {
				logger.error("Branch should contain a Code System {}", codeSystemBranch);
				return null;
			}

			Map<String, Integer> pathToVersionMap = findAllVersions(codeSystem.getShortName(), true, true).stream()
					.collect(Collectors.toMap(CodeSystemVersion::getBranchPath, CodeSystemVersion::getEffectiveDate));
			if (pathToVersionMap.isEmpty()) {
				logger.error("Code System {} does not have any versions.", codeSystem);
				return null;
			}
			List<Branch> versionBranches = sBranchService.findByPathAndBaseTimepoint(pathToVersionMap.keySet(), timepoint, Sort.by("start").descending());
			if (versionBranches.isEmpty()) {
				return null;
			}
			Branch versionBranch = versionBranches.iterator().next();
			if (versionBranch.getEnd() != null) {
				logger.warn("Code System {} is dependant on a version of the parent Code System {} that no longer exists. " +
								"The branch with base timepoint {} matches the base of an outdated version of branch {}.",
						forChildCodeSystem, codeSystem, timepoint, versionBranch.getPath());
			} else {
				return pathToVersionMap.get(versionBranch.getPath());
			}
			return null;
		});
	}

	public CodeSystem findOrThrow(String codeSystemShortName) {
		CodeSystem codeSystem = find(codeSystemShortName);
		if (codeSystem == null) {
			throw new NotFoundException(String.format("Code System with short name '%s' does not exist.", codeSystemShortName));
		}
		return codeSystem;
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

	public CodeSystemVersion findVersion(String shortName, String versionBranchName) {
		if (CodeSystemService.VERSION_BRANCH_NAME_PATTERN.matcher(versionBranchName).matches()) {
			final Integer date = getVersionEffectiveDateFromBranchName(versionBranchName);
			if (date != null) {
				return findVersion(shortName, date);
			}
		}
		return null;
	}

	public List<CodeSystemVersion> findAllVersions(String shortName, boolean includeFutureVersions, boolean includeInternalReleases) {
		return findAllVersions(shortName, true, includeFutureVersions, includeInternalReleases);
	}

	private List<CodeSystemVersion> findAllVersions(String shortName, boolean ascOrder, boolean includeFutureVersions, boolean includeInternalReleases) {
		List<CodeSystemVersion> content;
		if (ascOrder) {
			content = versionRepository.findByShortNameOrderByEffectiveDate(shortName, LARGE_PAGE).getContent();
		} else {
			content = versionRepository.findByShortNameOrderByEffectiveDateDesc(shortName, LARGE_PAGE).getContent();
		}
		int todaysEffectiveTime = DateUtil.getTodaysEffectiveTime();
		return content.stream()
				.filter(version -> includeFutureVersions || version.getEffectiveDate() < todaysEffectiveTime)
				.filter(version -> includeInternalReleases || !version.isInternalRelease())
				.collect(Collectors.toList());
	}

	public CodeSystemVersion findLatestImportedVersion(String shortName) {
		List<CodeSystemVersion> versions = findAllVersions(shortName, false, true, true);
		if (versions != null && !versions.isEmpty()) {
			return versions.get(0);
		}
		return null;
	}

	public CodeSystemVersion findLatestVisibleVersion(String shortName) {
		List<CodeSystemVersion> versions = findAllVersions(shortName, false, latestVersionCanBeFuture, latestVersionCanBeInternalRelease);
		if (versions != null && !versions.isEmpty()) {
			return versions.get(0);
		}
		return null;
	}

	public void deleteAll() {
		repository.deleteAll();
		versionRepository.deleteAll();
	}

	CodeSystem findOneByBranchPath(String path) {
		List<CodeSystem> results = elasticsearchOperations.search(
				new NativeSearchQueryBuilder().withQuery(termQuery(CodeSystem.Fields.BRANCH_PATH, path)).build(), CodeSystem.class)
				.get().map(SearchHit::getContent).collect(Collectors.toList());
		return results.isEmpty() ? null : results.get(0);
	}

	@PreAuthorize("hasPermission('ADMIN', #codeSystem.branchPath)")
	public CodeSystem update(CodeSystem codeSystem, CodeSystemUpdateRequest updateRequest) {
		modelMapper.map(updateRequest, codeSystem);
		validatorService.validate(codeSystem);
		repository.save(codeSystem);
		contentInformationCache.remove(codeSystem.getBranchPath());
		return codeSystem;
	}

	@PreAuthorize("hasPermission('ADMIN', #codeSystem.branchPath)")
	public void deleteVersion(CodeSystem codeSystem, CodeSystemVersion version) {
		findOrThrow(codeSystem.getShortName());
		if (!version.getShortName().equals(codeSystem.getShortName())) {
			throw new IllegalArgumentException("The given code system and version do not match.");
		}
		versionRepository.delete(version);
	}

	@PreAuthorize("hasPermission('ADMIN', #codeSystem.branchPath)")
	public void deleteCodeSystemAndVersions(CodeSystem codeSystem) {
		if (codeSystem.getBranchPath().equals("MAIN")) {
			throw new IllegalArgumentException("The root code system can not be deleted. " +
					"If you need to start again delete all indices and restart Snowstorm.");
		}
		logger.info("Deleting Code System '{}'.", codeSystem.getShortName());
		List<CodeSystemVersion> allVersions = findAllVersions(codeSystem.getShortName(), true, false);
		versionRepository.deleteAll(allVersions);
		repository.delete(codeSystem);
		logger.info("Deleted Code System '{}' and versions.", codeSystem.getShortName());
	}

	protected void setLatestVersionCanBeFuture(boolean latestVersionCanBeFuture) {
		this.latestVersionCanBeFuture = latestVersionCanBeFuture;
	}

	public void updateDetailsFromConfig() {
		logger.info("Updating the details of all code systems using values from configuration.");
		final Map<String, CodeSystemConfiguration> configurationsMap = codeSystemConfigurationService.getConfigurations().stream()
				.collect(Collectors.toMap(CodeSystemConfiguration::getShortName, Function.identity()));
		for (CodeSystem codeSystem : findAll()) {
			final CodeSystemConfiguration configuration = configurationsMap.get(codeSystem.getShortName());
			if (configuration != null) {
				logger.info("Updating code system {}", codeSystem.getShortName());
				update(codeSystem, new CodeSystemUpdateRequest(codeSystem).populate(configuration));
			}
		}
	}

	@PreAuthorize("hasPermission('ADMIN', #codeSystem.branchPath)")
	public void updateCodeSystemBranchMetadata(CodeSystem codeSystem) {
		String branchPath = codeSystem.getBranchPath();
		Branch branch = branchService.findBranchOrThrow(branchPath);
		if (branch.isLocked()) {
			throw new IllegalStateException("Branch metadata can not be updated when branch is locked.");
		}
		// Find latest version including future / internal releases
		final CodeSystemVersion codeSystemVersion = findLatestImportedVersion(codeSystem.getShortName());
		if (codeSystemVersion == null) {
			throw new IllegalStateException("Latest code system version not found.");
		}
		if (Strings.isNullOrEmpty(codeSystemVersion.getReleasePackage())) {
			throw new IllegalStateException("Previous package not found.");
		}
		Metadata branchMetadata = branch.getMetadata();
		branchMetadata.putString(PREVIOUS_RELEASE, String.valueOf(codeSystemVersion.getEffectiveDate()));
		branchMetadata.putString(PREVIOUS_PACKAGE, codeSystemVersion.getReleasePackage());

		if (codeSystem.getDependantVersionEffectiveTime() != null) {
			final Optional<CodeSystem> parentCodeSystem = findByBranchPath(PathUtil.getParentPath(branchPath));
			if (parentCodeSystem.isEmpty()) {
				throw new IllegalStateException("Dependant version set but parent code system not found.");
			}
			final CodeSystemVersion parentCodeSystemVersion = findVersion(parentCodeSystem.get().getShortName(), codeSystem.getDependantVersionEffectiveTime());
			if (parentCodeSystemVersion == null) {
				throw new IllegalStateException("Dependant version " + codeSystem.getDependantVersionEffectiveTime() + " not found.");
			}
			if (Strings.isNullOrEmpty(parentCodeSystemVersion.getReleasePackage())) {
				throw new IllegalStateException("Previous dependency package not found.");
			}
			branchMetadata.putString(PREVIOUS_DEPENDENCY_PACKAGE, parentCodeSystemVersion.getReleasePackage());
		}
		branchService.updateMetadata(branchPath, branchMetadata);
	}
}
