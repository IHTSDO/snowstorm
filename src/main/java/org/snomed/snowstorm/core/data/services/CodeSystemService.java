package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
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
import org.snomed.snowstorm.core.util.LangUtil;
import org.snomed.snowstorm.rest.pojo.CodeSystemUpdateRequest;
import org.springframework.beans.factory.annotation.Autowired;
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
	private BranchMergeService mergeService;

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

	public synchronized void createCodeSystem(CodeSystem codeSystem) {
		validatorService.validate(codeSystem);
		if (repository.findById(codeSystem.getShortName()).isPresent()) {
			throw new IllegalArgumentException("A code system already exists with this short name.");
		}
		String branchPath = codeSystem.getBranchPath();
		if (findByBranchPath(branchPath).isPresent()) {
			throw new IllegalArgumentException("A code system already exists with this branch path.");
		}
		if (!branchService.exists(branchPath)) {
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
		String effectiveDateString = effectiveDate.toString();
		String version = effectiveDateString.substring(0, 4) + "-" + effectiveDateString.substring(4, 6) + "-" + effectiveDateString.substring(6, 8);
		String branchPath = codeSystem.getBranchPath();
		String releaseBranchPath = branchPath + "/" + version;

		CodeSystemVersion codeSystemVersion = versionRepository.findOneByShortNameAndEffectiveDate(codeSystem.getShortName(), effectiveDate);
		if (codeSystemVersion != null) {
			logger.warn("Aborting Code System Version creation. This version already exists.");
			throw new IllegalStateException("Aborting Code System Version creation. This version already exists.");
		}

		logger.info("Creating Code System version - Code System: {}, Version: {}, Release Branch: {}", codeSystem.getShortName(), version, releaseBranchPath);
		logger.info("Versioning content...");
		releaseService.createVersion(effectiveDate, branchPath);

		logger.info("Creating version branch content...");
		branchService.create(releaseBranchPath);

		logger.info("Persisting Code System Version...");
		versionRepository.save(new CodeSystemVersion(codeSystem.getShortName(), new Date(), branchPath, effectiveDate, version, description));

		logger.info("Versioning complete.");

		return version;
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

			// Lookup latest version
			List<CodeSystemVersion> versions = versionRepository.findByShortNameOrderByEffectiveDateDesc(codeSystem.getShortName(), LARGE_PAGE).getContent();
			if (!versions.isEmpty()) {
				codeSystem.setLatestVersion(versions.get(0));
			}

			// Pull from cache
			Pair<Date, CodeSystem> dateCodeSystemPair = contentInformationCache.get(branchPath);
			if (dateCodeSystemPair != null) {
				if (dateCodeSystemPair.getFirst().equals(latestBranch.getHead())) {
					CodeSystem cachedCodeSystem = dateCodeSystemPair.getSecond();
					codeSystem.setLanguages(cachedCodeSystem.getLanguages());
					codeSystem.setModules(cachedCodeSystem.getModules());
					continue;
				} else {
					// Remove expired cache entry
					contentInformationCache.remove(branchPath);
				}
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

	public List<CodeSystemVersion> findAllVersions(String shortName) {
		return findAllVersions(shortName, true);
	}

	public List<CodeSystemVersion> findAllVersions(String shortName, boolean ascOrder) {
		if (ascOrder) {
			return versionRepository.findByShortNameOrderByEffectiveDate(shortName, LARGE_PAGE).getContent();
		} else {
			return versionRepository.findByShortNameOrderByEffectiveDateDesc(shortName, LARGE_PAGE).getContent();
		}
	}

	public CodeSystemVersion findLatestVersion(String shortName) {
		//return versionRepository.findTopByShortNameOrderByEffectiveDateDesc(shortName);
		List<CodeSystemVersion> versions = findAllVersions(shortName, false);
		if (versions != null && versions.size() > 0) {
			return versions.get(0);
		}
		return null;
	}

	public void deleteAll() {
		repository.deleteAll();
		versionRepository.deleteAll();
	}

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

			mergeService.copyBranchToNewParent(sourceBranchPath, targetBranchPath);

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

	public CodeSystem update(CodeSystem codeSystem, CodeSystemUpdateRequest updateRequest) {
		modelMapper.map(updateRequest, codeSystem);
		validatorService.validate(codeSystem);
		repository.save(codeSystem);
		contentInformationCache.remove(codeSystem.getBranchPath());
		return codeSystem;
	}
}
