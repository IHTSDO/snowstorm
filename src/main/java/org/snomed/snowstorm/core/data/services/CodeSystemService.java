package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.CodeSystemVersion;
import org.snomed.snowstorm.core.data.repositories.CodeSystemRepository;
import org.snomed.snowstorm.core.data.repositories.CodeSystemVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Service
public class CodeSystemService {

	public static final String SNOMEDCT = "SNOMEDCT";
	public static final String MAIN = "MAIN";

	@Autowired
	private CodeSystemRepository repository;

	@Autowired
	private CodeSystemVersionRepository versionRepository;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ReleaseService releaseService;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public synchronized void init() {
		// Create default code system if it does not yet exist
		if (!repository.findById(SNOMEDCT).isPresent()) {
			createCodeSystem(new CodeSystem(SNOMEDCT, MAIN));
		}
	}

	public synchronized void createCodeSystem(CodeSystem codeSystem) {
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
			return version;
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
		return repository.findAll(PageRequest.of(0, 1000, Sort.by(CodeSystem.Fields.SHORT_NAME))).getContent();
	}

	public CodeSystem find(String codeSystemShortName) {
		return repository.findById(codeSystemShortName).orElse(null);
	}

	public List<CodeSystemVersion> findAllVersions(String shortName) {
		return versionRepository.findByShortNameOrderByEffectiveDate(shortName);
	}

	public void deleteAll() {
		repository.deleteAll();
		versionRepository.deleteAll();
	}
}
