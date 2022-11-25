package org.snomed.snowstorm.core.data.services;

import co.elastic.clients.elasticsearch._types.aggregations.AggregationBuilders;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.json.JsonData;
import com.google.common.base.Strings;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.PathUtil;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Metadata;
import org.apache.activemq.command.ActiveMQTopic;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.repositories.CodeSystemRepository;
import org.snomed.snowstorm.core.data.repositories.CodeSystemVersionRepository;
import org.snomed.snowstorm.core.data.services.pojo.CodeSystemDefaultConfiguration;
import org.snomed.snowstorm.core.data.services.pojo.PageWithBucketAggregationsFactory;
import org.snomed.snowstorm.core.pojo.LanguageDialect;
import org.snomed.snowstorm.core.util.AggregationUtils;
import org.snomed.snowstorm.core.util.DateUtil;
import org.snomed.snowstorm.core.util.LangUtil;
import org.snomed.snowstorm.rest.pojo.CodeSystemUpdateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.Aggregation;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.util.Pair;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool;
import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.range;
import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static io.kaicode.elasticvc.helper.QueryHelper.*;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.snomed.snowstorm.config.Config.DEFAULT_LANGUAGE_CODES;
import static org.snomed.snowstorm.core.data.services.BranchMetadataKeys.*;

@Service
public class CodeSystemService {

	public static final String SNOMEDCT = "SNOMEDCT";
	public static final String MAIN = "MAIN";
	private static final Pattern VERSION_BRANCH_NAME_PATTERN = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");


	@Value("${code-systems.version.visible.after.published.date}")
	private Set<String> codeSystemsWithVersionVisibleAfterPublishedDate;

	@Autowired
	private CodeSystemRepository repository;

	@Autowired
	private CodeSystemVersionRepository versionRepository;

	@Autowired
	private CodeSystemDefaultConfigurationService codeSystemDefaultConfigurationService;

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

	@Value("${jms.queue.prefix}")
	private String jmsQueuePrefix;

	@Autowired
	private JmsTemplate jmsTemplate;

	@Value("${codesystem.all.latest-version.allow-future}")
	private boolean latestVersionCanBeFuture;

	@Value("${codesystem.all.latest-version.allow-internal-release}")
	private boolean latestVersionCanBeInternalRelease;

	@Value("${snowstorm.codesystem-version.message.enabled}")
	private boolean jmsMessageEnabled;

	// Cache to prevent expensive aggregations. Entry per branch. Expires if there is a new commit.
	private final ConcurrentHashMap<String, Pair<Date, CodeSystem>> contentInformationCache = new ConcurrentHashMap<>();

	private final Map<String, Integer> versionCommitEffectiveTimeCache = new HashMap<>();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public synchronized void init() {
		// Create default code system if it does not yet exist
		if (repository.findById(SNOMEDCT).isEmpty()) {
			createCodeSystem(new CodeSystem(SNOMEDCT, MAIN));
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
		String uriModuleId = newCodeSystem.getUriModuleId();
		if (uriModuleId != null) {
			CodeSystem byModule = findByUriModule(uriModuleId);
			if (byModule != null) {
				throw new IllegalArgumentException(format("A code system already exists with URI module %s : %s ", uriModuleId, byModule.getShortName()));
			}
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

		// Save URI module as default authoring module
		if (uriModuleId != null) {
			branchService.updateMetadata(branchPath, new Metadata().putString(DEFAULT_MODULE_ID, uriModuleId));
		}

		repository.save(newCodeSystem);
		logger.info("Code System '{}' created.", newCodeSystem.getShortName());
		return newCodeSystem;
	}

	public Optional<CodeSystem> findByBranchPath(String branchPath) {
		List<CodeSystem> codeSystems = elasticsearchOperations.search(
						new NativeQueryBuilder()
								.withQuery(bool(b -> b.must(termQuery(CodeSystem.Fields.BRANCH_PATH, branchPath))))
								.build(), CodeSystem.class)
				.stream()
				.map(SearchHit::getContent).toList();

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

		if (jmsMessageEnabled) {
			Map<String, String> payload = new HashMap<>();
			payload.put("codeSystemShortName", codeSystem.getShortName());
			payload.put("codeSystemBranchPath", codeSystem.getBranchPath());
			payload.put("effectiveDate", String.valueOf(effectiveDate));
			payload.put("versioningDate", String.valueOf(new Date().getTime()));
			String topicDestination = jmsQueuePrefix + ".versioning.complete";
			logger.info("Sending JMS Topic - destination {}, payload {}...", topicDestination, payload);
			jmsTemplate.convertAndSend(new ActiveMQTopic(topicDestination), payload);
		}

		return version;
	}

	/**
	 * Create an empty code system version on the root code system.
	 * This can be used as the dependant version when hosting one or many subontologies in Snowstorm.
	 * See https://github.com/IHTSDO/snomed-subontology-extraction
	 */
	public String createEmpty2000Version() {
		// Get the version of MAIN created when Snowstorm first started, before the first import commit.
		Branch branchVersion = branchService.findFirstVersionOrThrow(MAIN);

		int effectiveDate = 20000101;

		CodeSystemVersion codeSystemVersion = versionRepository.findOneByShortNameAndEffectiveDate(SNOMEDCT, effectiveDate);
		if (codeSystemVersion != null) {
			logger.warn("Aborting Code System Version creation. This version already exists.");
			throw new IllegalStateException("Aborting Code System Version creation. This version already exists.");
		}

		// Create version branch
		String releaseBranchPath = getReleaseBranchPath(MAIN, effectiveDate);
		branchService.createAtBaseTimepoint(releaseBranchPath, branchVersion.getBase());

		versionRepository.save(new CodeSystemVersion(SNOMEDCT, new Date(), MAIN, effectiveDate, getHyphenatedVersionString(effectiveDate),
				"Empty version.", true));

		return String.format("Version %s of the root code system created.", effectiveDate);
	}

	@PreAuthorize("hasPermission('ADMIN', #codeSystemVersion.branchPath) || hasPermission('RELEASE_ADMIN', 'global') || hasPermission('RELEASE_MANAGER', 'global')")
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
		List<CodeSystem> codeSystems = elasticsearchOperations.search(new NativeQuery(termQuery(CodeSystem.Fields.BRANCH_PATH, branchPath)), CodeSystem.class)
				.get().map(SearchHit::getContent).toList();
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

	public List<CodeSystem> findAllPostcoordinatedBrief() {
		List<CodeSystem> allCodeSystems = repository.findAll(PageRequest.of(0, 10_000, Sort.by(CodeSystem.Fields.SHORT_NAME))).getContent();
		return allCodeSystems.stream()
				.filter(CodeSystem::isPostcoordinatedNullSafe)
				.peek(codeSystem -> {
					codeSystem.setParentUriModuleId(getParentUriModule(codeSystem, allCodeSystems));
					String branchPath = codeSystem.getBranchPath();
					Branch workingBranch = branchService.findLatest(branchPath);
					doJoinDependentVersionEffectiveTime(codeSystem, branchPath, workingBranch);
				})
				.collect(Collectors.toList());
	}

	private String getParentUriModule(CodeSystem codeSystem, List<CodeSystem> allCodeSystems) {
		String parentPath = PathUtil.getParentPath(codeSystem.getBranchPath());
		Optional<CodeSystem> parent = allCodeSystems.stream().filter(parentCandidate -> parentCandidate.getBranchPath().equals(parentPath)).findFirst();
		return parent.map(CodeSystem::getUriModuleId).orElse(null);
	}

	@Cacheable("code-system-branches")
	public List<String> findAllCodeSystemBranchesUsingCache() {
		return repository.findAll(PageRequest.of(0, 1000, Sort.by(CodeSystem.Fields.SHORT_NAME))).getContent().stream().map(CodeSystem::getBranchPath).sorted().collect(toList());
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

		doJoinDependentVersionEffectiveTime(codeSystem, branchPath, workingBranch);

		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(workingBranch);

		List<String> acceptableLanguageCodes = new ArrayList<>(DEFAULT_LANGUAGE_CODES);

		// Add list of languages using Description aggregation
		SearchHits<Description> descriptionSearch = elasticsearchOperations.search(new NativeQueryBuilder()
				.withQuery(bool(b -> b
						.must(branchCriteria.getEntityBranchCriteria(Description.class))
						.must(termQuery(Description.Fields.ACTIVE, true))))
				.withPageable(PageRequest.of(0, 1))
				.withAggregation("language", AggregationBuilders.terms().field(Description.Fields.LANGUAGE_CODE).size(20).build()._toAggregation())
				.build(), Description.class);
		if (descriptionSearch.hasAggregations()) {
			Aggregation aggregation = AggregationUtils.getAggregations(descriptionSearch.getAggregations()).get("language");
			List<StringTermsBucket> languageBuckets = new ArrayList<>();
			if (aggregation.getAggregate().isSterms()) {
				languageBuckets = new ArrayList<>(aggregation.getAggregate().sterms().buckets().array());

			}
			// Collect other languages for concept mini lookup
			List<String> languageCodesSorted = languageBuckets.stream()
					// sort by number of active descriptions in each language
					.sorted(Comparator.comparing(StringTermsBucket::docCount).reversed())
					.map(b -> b.key().stringValue())
					.collect(toList());

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
		SearchHits<ReferenceSetMember> memberPage = elasticsearchOperations.search(new NativeQueryBuilder()
				.withQuery(bool(b -> b
						.must(branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class))
						.must(termQuery(ReferenceSetMember.Fields.ACTIVE, true))))
				.withPageable(PageRequest.of(0, 1))
				.withAggregation("module", AggregationBuilders.terms(a -> a.field(ReferenceSetMember.Fields.MODULE_ID).size(50)))
				.build(), ReferenceSetMember.class);
		if (memberPage.hasAggregations()) {
			Map<String, Long> modulesOfActiveMembers = PageWithBucketAggregationsFactory.createPage(memberPage, PageRequest.of(0, 1))
					.getBuckets().get("module");
			List<LanguageDialect> languageDialects = acceptableLanguageCodes.stream().map(LanguageDialect::new).collect(toList());
			codeSystem.setModules(conceptService.findConceptMinis(branchCriteria, modulesOfActiveMembers.keySet(), languageDialects).getResultsMap().values());
		}

		// Add to cache
		contentInformationCache.put(branchPath, Pair.of(workingBranch.getHead(), codeSystem));
	}

	// Set dependant version effectiveTime (transient field)
	private void doJoinDependentVersionEffectiveTime(CodeSystem codeSystem, String branchPath, Branch workingBranch) {
		if (!PathUtil.isRoot(branchPath)) {
			Integer effectiveTime = getVersionEffectiveTime(PathUtil.getParentPath(branchPath), workingBranch.getBase(), codeSystem.getShortName());
			if (effectiveTime == null) {
				logger.warn("Code System {} is not dependant on a specific version of the parent Code System. " +
								"The working branch {} has a base timepoint of {} which does not match the base of any version branches of {}.",
						codeSystem, branchPath, workingBranch.getBase(), PathUtil.getParentPath(branchPath));
			}
			codeSystem.setDependantVersionEffectiveTime(effectiveTime);
		}
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
		CodeSystemDefaultConfiguration codeSystemConfiguration = codeSystemDefaultConfigurationService.findByModule(moduleId);
		if (codeSystemConfiguration == null) {
			return null;
		}
		return find(codeSystemConfiguration.shortName());
	}

	public CodeSystem findByUriModule(String moduleId) {
		CodeSystem codeSystem = repository.findByUriModuleId(moduleId);
		return codeSystem != null ? find(codeSystem.getShortName()) : null;
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

	public CodeSystemVersion findVersion(String branchPath) {
		String codeSystemPath = PathUtil.getParentPath(branchPath);
		if (codeSystemPath == null) {
			return null;
		}
		Optional<CodeSystem> codeSystem = findByBranchPath(codeSystemPath);
		if (codeSystem.isEmpty()) {
			return null;
		}
		return findVersion(codeSystem.get().getShortName(), branchPath.substring(codeSystemPath.length() + 1));
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
				.filter(version -> includeFutureVersions || (codeSystemsWithVersionVisibleAfterPublishedDate.contains(shortName) ? version.getEffectiveDate() < todaysEffectiveTime : version.getEffectiveDate() <= todaysEffectiveTime))
				.filter(version -> includeInternalReleases || !version.isInternalRelease())
				.collect(toList());
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
				new NativeQueryBuilder().withQuery(termQuery(CodeSystem.Fields.BRANCH_PATH, path)).build(), CodeSystem.class)
				.get().map(SearchHit::getContent).toList();
		return results.isEmpty() ? null : results.get(0);
	}

	@PreAuthorize("hasPermission('ADMIN', #codeSystem.branchPath)")
	public CodeSystem update(CodeSystem codeSystem, CodeSystemUpdateRequest updateRequest) {
		modelMapper.map(updateRequest, codeSystem);
		validatorService.validate(codeSystem);
		doUpdate(codeSystem);
		return codeSystem;
	}

	private void doUpdate(CodeSystem codeSystem) {
		repository.save(codeSystem);
		contentInformationCache.remove(codeSystem.getBranchPath());
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
		final Map<String, CodeSystemDefaultConfiguration> configurationsMap = codeSystemDefaultConfigurationService.getConfigurations().stream()
				.collect(Collectors.toMap(CodeSystemDefaultConfiguration::shortName, Function.identity()));
		for (CodeSystem codeSystem : findAll()) {
			final CodeSystemDefaultConfiguration configuration = configurationsMap.get(codeSystem.getShortName());
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
			throw new IllegalStateException(String.format("Branch %s is locked already and metadata can not be updated.", branchPath));
		}
		// Find latest version including future / internal releases
		final CodeSystemVersion codeSystemVersion = findLatestImportedVersion(codeSystem.getShortName());
		if (codeSystemVersion == null) {
			throw new IllegalStateException(String.format("No code system version found for %s", codeSystem.getShortName()));
		}
		if (Strings.isNullOrEmpty(codeSystemVersion.getReleasePackage())) {
			throw new IllegalStateException(String.format("No release package found for %s", codeSystemVersion.getBranchPath()));
		}
		Metadata branchMetadata = branch.getMetadata();

		// Previous release = the latest version's effective time
		// Previous package = the latest version's release package
		branchMetadata.putString(PREVIOUS_RELEASE, String.valueOf(codeSystemVersion.getEffectiveDate()));
		branchMetadata.putString(PREVIOUS_PACKAGE, codeSystemVersion.getReleasePackage());

		// Update previous dependency package if dependency is maintained
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
				throw new IllegalStateException("No release package found for " + parentCodeSystemVersion);
			}
			branchMetadata.putString(PREVIOUS_DEPENDENCY_PACKAGE, parentCodeSystemVersion.getReleasePackage());
		}
		branchService.updateMetadata(branchPath, branchMetadata);
	}

	/**
	 * Return versioned Branches for the given CodeSystem, where each Branch was versioned within the given time range.
	 *
	 * @param codeSystem The CodeSystem to use for finding versioned Branches.
	 * @param lowerBound The start of the time range to find versioned Branches.
	 * @param upperBound The end of the time range to find versioned Branches.
	 * @return Versioned Branches for the given CodeSystem, where each Branch was versioned within the given time range.
	 */
	public Set<Branch> findVersionsByCodeSystemAndBaseTimepointRange(CodeSystem codeSystem, long lowerBound, long upperBound) {
		if (codeSystem == null) {
			throw new IllegalArgumentException("CodeSystem cannot be null.");
		}

		if (lowerBound == 0L || upperBound == 0L || upperBound < lowerBound) {
			throw new IllegalArgumentException(String.format("Invalid time range. lowerBound: %d, upperBound: %d", lowerBound, upperBound));
		}

		SearchHits<CodeSystemVersion> queryCodeSystemVersions = elasticsearchOperations.search(
				new NativeQueryBuilder()
						.withQuery(bool(b -> b.must(termQuery(CodeSystemVersion.Fields.SHORT_NAME, codeSystem.getShortName()))))
						.build(), CodeSystemVersion.class
		);

		if (queryCodeSystemVersions.isEmpty()) {
			return Collections.emptySet();
		}

		List<CodeSystemVersion> codeSystemVersions = queryCodeSystemVersions.getSearchHits().stream().map(SearchHit::getContent).toList();
		Set<String> branchPaths = codeSystemVersions.stream().map(CodeSystemVersion::getBranchPath).collect(Collectors.toSet());
		SearchHits<Branch> queryBranches = elasticsearchOperations.search(
				new NativeQueryBuilder()
						.withQuery(bool(b -> b
										.must(range(r -> r.field("base").gt(JsonData.of(lowerBound)).lte(JsonData.of(upperBound))).range()._toQuery())
										.mustNot(existsQuery(Branch.Fields.END)))
						).withFilter(termsQuery(Branch.Fields.PATH, branchPaths))
						.build(), Branch.class
		);

		return queryBranches.getSearchHits().stream().map(SearchHit::getContent).collect(Collectors.toSet());
	}
}
