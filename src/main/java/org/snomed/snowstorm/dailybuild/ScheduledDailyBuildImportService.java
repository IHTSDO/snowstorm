package org.snomed.snowstorm.dailybuild;


import com.google.common.collect.Iterators;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import io.kaicode.elasticvc.domain.Branch;
import io.kaicode.elasticvc.domain.Entity;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.script.Script;
import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.DomainEntityConfiguration;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.*;
import org.springframework.data.util.CloseableIterator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.*;

@Service
@ConditionalOnProperty(name = "daily-build.delta-import.enabled", havingValue = "true")
public class ScheduledDailyBuildImportService {

	public static final String DAILY_BUILD_DATE_FORMAT = "yyyy-MM-dd-HHmmss";

	@Autowired
	private DailyBuildResourceConfig dailyBuildResourceConfig;

	@Autowired
	private BranchService branchService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchTemplate elasticsearchTemplate;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private ImportService importService;

	@Autowired
	private ResourceLoader resourceLoader;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private  ConceptService conceptService;

	@Autowired
	private ResourcePatternResolver resourcePatternResolver;

	@Autowired
	private DomainEntityConfiguration domainEntityConfiguration;

	private Logger logger = LoggerFactory.getLogger(getClass());

	private  boolean initialised = false;

	private ResourceManager resourceManager;

	@PostConstruct
	public void init() {
		initialised = true;
		resourceManager = new ResourceManager(dailyBuildResourceConfig, resourceLoader);
		logger.info("Daily build import is enabled.");
	}

	@Scheduled(fixedDelay = 60_000)
	public void scheduledDailyBuildDeltaImport() {
		if (!initialised) {
			return;
		}

		List<CodeSystem> codeSystems = codeSystemService.findAll().stream()
				.filter(CodeSystem::isDailyBuildAvailable).collect(Collectors.toList());

		for (CodeSystem codeSystem : codeSystems) {
			performScheduledImport(codeSystem);
		}
	}

	private void performScheduledImport(CodeSystem codeSystem) {
		// using time to look for daily build
		Branch path = branchService.findBranchOrThrow(codeSystem.getBranchPath());
		if (path.isLocked()) {
			return;
		}

		try (InputStream dailyBuildSteam = getLatestDailyBuildIfExists(codeSystem, path.getHeadTimestamp())) {

			if (dailyBuildSteam != null) {
				logger.info("New daily build found for " + codeSystem.getShortName());
				dailyBuildDeltaImport(codeSystem, dailyBuildSteam);
			}
		} catch (IOException e) {
			logger.error("Failed to import daily build for CodeSystem " + codeSystem.getShortName());
		}
	}

	protected void dailyBuildDeltaImport(CodeSystem codeSystem, InputStream dailyBuildSteam) {
		if (dailyBuildSteam == null) {
			return;
		}

		try {
			if (codeSystem.getLatestVersion() == null) {
				codeSystem.setLatestVersion(codeSystemService.findLatestVersion(codeSystem.getShortName()));
			}
			String releaseBranch = codeSystem.getLatestVersion().getBranchPath();
			Branch latestRelease = branchService.findLatest(releaseBranch);
			rollbackCommits(codeSystem.getBranchPath(), latestRelease.getBase());

			// start daily delta import
			logger.info("Start daily build delta import for code system " +  codeSystem.getShortName());
			String importId = importService.createJob(RF2Type.DELTA, codeSystem.getBranchPath(), false);
			importService.importArchive(importId, dailyBuildSteam);
			logger.info("Daily build delta import completed for code system " +  codeSystem.getShortName());

		} catch (Exception e) {
			logger.error("Failed to import daily build delta.", e);
		}
	}

	private List<Branch> rollbackCommits(String path, Date baseTimestamp) {
		List<Branch> rollbackList = new ArrayList<>();
		try {
			branchService.lockBranch(path, ScheduledDailyBuildImportService.class.getSimpleName());
			//find all versions after base timestamp
			Page<Branch> branchPage = branchService.findAllVersionsAfterTimestamp(path, baseTimestamp, Pageable.unpaged());

			logger.info("Total versions {} found to roll back on branch {} after timestamp {} ", branchPage.getTotalElements(), path, baseTimestamp);

			//roll back in reverse order (i.e the most recent first)
			rollbackList = branchPage.getContent();
			Collections.reverse(rollbackList);
			List<Class> domainTypes = getAllDomainTypesToRollback();
			for (Branch branch : rollbackList) {
				logger.info("Start rolling back ended documents with end timestamp=" + branch.getHeadTimestamp() + " on " + branch.getPath());
				Map<Class, Set<String>> endedDocumentsMap = getEndedDocumentsToRollback(branch.getPath(), domainTypes, branch.getHeadTimestamp());
				// update any documents with end timestamp matching
				revertEndedDocumentsWithUpdateQuery(endedDocumentsMap);

				logger.info("Start rolling back commits on {} started at {}", branch.getPath(), branch.getHeadTimestamp());
				// delete documents
				DeleteQuery deleteQuery = new DeleteQuery();
				deleteQuery.setQuery(new BoolQueryBuilder()
						.must(termQuery("path", branch.getPath()))
						.must(termQuery("start", branch.getHeadTimestamp()))
				);

				for (Class domainEntityClass : domainTypes) {
					elasticsearchTemplate.delete(deleteQuery, domainEntityClass);
					elasticsearchTemplate.refresh(domainEntityClass);
				}
				logger.info("Completed rolling back commits on {} started at {}", branch.getPath(), branch.getHeadTimestamp());
			}
		} catch (Exception e) {
			logger.error("Failed to rollback commits on {} started at {}", path, baseTimestamp, e);
		} finally {
			branchService.unlock(path);
		}
		return rollbackList;
	}

	private List<Class> getAllDomainTypesToRollback() {
		List<Class> allTypes = new ArrayList<>(domainEntityConfiguration.getAllDomainEntityTypes());
		allTypes.add(Branch.class);
		return allTypes;
	}

	private Map<Class, Set<String>> getEndedDocumentsToRollback(String path, List<Class> allTypes, long timestamp) {
		Map<Class, Set<String>> result = new HashMap<>();
		for (Class domain : allTypes) {
			NativeSearchQuery endedDocumentQuery = new NativeSearchQueryBuilder()
					.withQuery(boolQuery()
							.must(termQuery("end", timestamp))
							.must(termQuery("path", path)))
					.withFields("internalId")
					.withPageable(ConceptService.LARGE_PAGE).build();
			try (final CloseableIterator<Entity> endedDocs = elasticsearchTemplate.stream(endedDocumentQuery, domain)) {
				endedDocs.forEachRemaining(d -> result.computeIfAbsent(d.getClass(), newSet -> new HashSet<>()).add(d.getInternalId()));
			}
		}
		return result;
	}

	private void revertEndedDocumentsWithUpdateQuery(Map<Class, Set<String>> entityMap) {
		for (Class type : entityMap.keySet()) {
			List<UpdateQuery> updateQueries = new ArrayList<>();
			for (String internalId : entityMap.get(type)) {
				UpdateRequest updateRequest = new UpdateRequest();
				updateRequest.script(new Script("ctx._source.remove('end')"));

				updateQueries.add(new UpdateQueryBuilder()
						.withClass(type)
						.withId(internalId)
						.withUpdateRequest(updateRequest)
						.build());
			}
			// add batching for 1000 per batch
			Iterators.partition(updateQueries.iterator(), 1000).forEachRemaining( l -> {
				if (!l.isEmpty()) {
					elasticsearchTemplate.bulkUpdate(l);
				}
			});
			logger.info("Total ended documents rolled back {} for type {} " , updateQueries.size(), type.getSimpleName());
			elasticsearchTemplate.refresh(type);
		}
	}


	protected InputStream getLatestDailyBuildIfExists(CodeSystem codeSystem, long timestamp) {
		String pathUrl = ResourcePathHelper.getFullPath(dailyBuildResourceConfig, codeSystem.getShortName());
		logger.debug("Daily build resources path " + pathUrl);
		Resource codeSystemPath = resourcePatternResolver.getResource(pathUrl);
		InputStream inputStream = null;
		if (codeSystemPath != null) {
			List<String> filenames = new ArrayList<>();
			Resource[] resources = null;
			try {
				resources = resourcePatternResolver.getResources( pathUrl + "/" + "*.zip");
			} catch (IOException e) {
				if (e instanceof FileNotFoundException) {
					logger.info("No daily builds found from " + pathUrl);
				} else {
					logger.error("Failed to fetch resources from " + pathUrl, e);
				}
			}

			if (resources != null) {
				for (Resource resource : resources) {
					if (resource.getFilename().endsWith(".zip")) {
						// check the uploaded time is today and after the timestamp
						if (isAfter(resource.getFilename(), timestamp)) {
							filenames.add(resource.getFilename());
						}
					}
				}
			}

			// get the most recent build for today
			Collections.sort(filenames);
			Collections.reverse(filenames);
			if (!filenames.isEmpty()) {
				String mostRecentBuild = filenames.iterator().next();
				if (filenames.size() > 1) {
					logger.info("Found total {} daily builds " + filenames.size() + " and the most recent one will be loaded " + mostRecentBuild);
				}
				try {
					inputStream = resourceManager.readResourceStream( codeSystem.getShortName() + "/" + mostRecentBuild);
				} catch (IOException e) {
					logger.error("Failed to read resource from " + pathUrl + "/" + mostRecentBuild, e);
				}
			}
		}
		return inputStream;
	}

	private boolean isAfter(String filename, long timestamp) {
		String dateStr = filename.substring(0, filename.lastIndexOf("."));
		SimpleDateFormat formatter = new SimpleDateFormat(DAILY_BUILD_DATE_FORMAT);
		try {
			if (formatter.parse(dateStr).after(new Date(timestamp))) {
				return true;
			}
		} catch (ParseException e) {
			logger.error("File name contains invalid date format expected {} but {}", DAILY_BUILD_DATE_FORMAT, dateStr);
		}
		return false;
	}

	private static class ResourcePathHelper {

		public static String getFullPath(ResourceConfiguration resourceConfiguration, String relativePath) {
			if (resourceConfiguration.isUseCloud()) {
				return "s3://" + resourceConfiguration.getCloud().getBucketName()
						+ "/" + getPathAndRelative(resourceConfiguration.getCloud().getPath(), relativePath);
			} else {
				return getPathAndRelative(resourceConfiguration.getLocal().getPath(), relativePath);
			}
		}
		private static String getPathAndRelative(String path, String relativePath) {
			if (!path.isEmpty() && !path.endsWith("/")) {
				path = path + "/";
			}
			if (relativePath.startsWith("/")) {
				relativePath = relativePath.substring(1);
			}
			return path + relativePath;
		}
	}
}
