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
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;

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

	public void init() {
		// Create default code system if it does not yet exist
		if (repository.findOne(SNOMEDCT) == null) {
			repository.save(new CodeSystem(SNOMEDCT, MAIN));
			logger.info("Default Code System '{}' created.", SNOMEDCT);
		}
	}

	public void createVersion(CodeSystem codeSystem, String effectiveDate, String description) {

		if (!effectiveDate.matches("\\d{8}")) {
			throw new IllegalArgumentException("Effective Date must have format yyyymmdd");
		}
		String version = effectiveDate.substring(0, 4) + "-" + effectiveDate.substring(4, 6) + "-" + effectiveDate.substring(6, 8);
		String branchPath = codeSystem.getBranchPath();
		String releaseBranchPath = branchPath + "/" + version;

		CodeSystemVersion codeSystemVersion = versionRepository.findOneByShortNameAndEffectiveDate(codeSystem.getShortName(), effectiveDate);
		if (codeSystemVersion != null) {
			logger.warn("Aborting Code System Version creation. This version already exists.");
			return;
		}

		logger.info("Creating Code System version - Code System: {}, Version: {}, Release Branch: {}", codeSystem.getShortName(), version, releaseBranchPath);
		logger.info("Versioning content...");
		releaseService.createVersion(effectiveDate, branchPath);

		logger.info("Creating version branch content...");
		branchService.create(releaseBranchPath);

		logger.info("Persisting Code System Version...");
		versionRepository.save(new CodeSystemVersion(codeSystem.getShortName(), new Date(), branchPath, effectiveDate, version, description));

		logger.info("Versioning complete.");
	}

	public void createVersionIfCodeSystemFoundOnPath(String branchPath, String releaseDate, String description) {
		List<CodeSystem> codeSystems = elasticsearchOperations.queryForList(new NativeSearchQuery(termQuery(CodeSystem.Fields.BRANCH_PATH, branchPath)), CodeSystem.class);
		if (!codeSystems.isEmpty()) {
			CodeSystem codeSystem = codeSystems.get(0);
			createVersion(codeSystem, releaseDate, description);
		}
	}

	public List<CodeSystem> findAll() {
		return repository.findAll(new PageRequest(0, 1000)).getContent();
	}

	public CodeSystem find(String codeSystemShortName) {
		return repository.findOne(codeSystemShortName);
	}

	public List<CodeSystemVersion> findAllVersions(String shortName) {
		return versionRepository.findByShortName(shortName);
	}

	public void deleteAll() {
		repository.deleteAll();
		versionRepository.deleteAll();
	}
}
