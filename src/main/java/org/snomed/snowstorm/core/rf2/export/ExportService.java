package org.snomed.snowstorm.core.rf2.export;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.jobs.ExportConfiguration;
import org.snomed.snowstorm.core.data.domain.jobs.ExportStatus;
import org.snomed.snowstorm.core.data.repositories.ExportConfigurationRepository;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.util.DateUtil;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool;
import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static io.kaicode.elasticvc.helper.QueryHelper.*;
import static java.lang.String.format;
import static org.snomed.snowstorm.core.data.domain.Concepts.LANG_REFSET;

@Service
public class ExportService {

	private final VersionControlHelper versionControlHelper;
	private final ElasticsearchOperations elasticsearchOperations;
	private final QueryService queryService;
	private final ExportConfigurationRepository exportConfigurationRepository;
	private final BranchService branchService;
	private final BranchMetadataHelper branchMetadataHelper;
	private final CodeSystemService codeSystemService;
	private final ExecutorService executorService;
	private final SBranchService sBranchService;


	public ExportService(VersionControlHelper versionControlHelper, ElasticsearchOperations elasticsearchOperations, QueryService queryService,
						 ExportConfigurationRepository exportConfigurationRepository, BranchService branchService,
						 BranchMetadataHelper branchMetadataHelper, CodeSystemService codeSystemService, ExecutorService executorService, SBranchService sBranchService) {
		this.versionControlHelper = versionControlHelper;
		this.elasticsearchOperations = elasticsearchOperations;
		this.queryService = queryService;
		this.exportConfigurationRepository = exportConfigurationRepository;
		this.branchService = branchService;
		this.branchMetadataHelper = branchMetadataHelper;
		this.codeSystemService = codeSystemService;
		this.executorService = executorService;
		this.sBranchService = sBranchService;
	}

	private static final String INTERNATIONAL_RF2_NAME = "INT";

	private final Set<String> refsetTypesRequiredForClassification = Sets.newHashSet(Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN, Concepts.OWL_EXPRESSION_TYPE_REFERENCE_SET, Concepts.MODULE_DEPENDENCY_REFERENCE_SET);

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public String createJob(ExportConfiguration exportConfiguration) {
		if (exportConfiguration.getType() == RF2Type.FULL) {
			throw new IllegalArgumentException("FULL RF2 export is not implemented.");
		}
		if (exportConfiguration.getStartEffectiveTime() != null && exportConfiguration.getType() != RF2Type.SNAPSHOT) {
			throw new IllegalArgumentException("The startEffectiveTime parameter can only be used with the SNAPSHOT export type.");
		}
		branchService.findBranchOrThrow(exportConfiguration.getBranchPath());
		exportConfiguration.setId(UUID.randomUUID().toString());
		exportConfiguration.setStatus(ExportStatus.PENDING);
		if (exportConfiguration.getFilenameEffectiveDate() == null) {
			exportConfiguration.setFilenameEffectiveDate(DateUtil.DATE_STAMP_FORMAT.format(new Date()));
		}
		exportConfigurationRepository.save(exportConfiguration);
		return exportConfiguration.getId();
	}

	public ExportConfiguration getExportJobOrThrow(String exportId) {
		Optional<ExportConfiguration> config = exportConfigurationRepository.findById(exportId);
		if (config.isEmpty()) {
			throw new NotFoundException("Export job not found.");
		}
		return config.get();
	}

	public void exportRF2Archive(ExportConfiguration exportConfiguration, OutputStream outputStream) throws ExportException {
		synchronized (this) {
			if (exportConfiguration.getStartDate() != null) {
				throw new IllegalStateException("Export already started.");
			}

			if (branchService.findBranchOrThrow(exportConfiguration.getBranchPath()).isLocked()) {
				throw new IllegalStateException(format("Branch %s is already locked", exportConfiguration.getBranchPath()));
			}

			exportConfiguration.setStartDate(new Date());
			exportConfiguration.setStatus(ExportStatus.RUNNING);
			exportConfigurationRepository.save(exportConfiguration);
		}

		File exportFile;
		try {
			exportFile = exportRF2ArchiveFile(exportConfiguration.getBranchPath(), exportConfiguration.getFilenameEffectiveDate(),
					exportConfiguration.getType(), exportConfiguration.isConceptsAndRelationshipsOnly(), exportConfiguration.isLanguageOnly(), exportConfiguration.isUnpromotedChangesOnly(),
					exportConfiguration.getTransientEffectiveTime(), exportConfiguration.getStartEffectiveTime(), exportConfiguration.getModuleIds(),
					exportConfiguration.isLegacyZipNaming(), exportConfiguration.getRefsetIds(), exportConfiguration.getId());
		} catch (ExportException | IllegalStateException e) {
			exportConfiguration.setStatus(ExportStatus.FAILED);
			exportConfigurationRepository.save(exportConfiguration);
			throw e;
		}

		logger.debug("Transmitting {} export file {}", exportConfiguration.getId(), exportFile);
		try (FileInputStream inputStream = new FileInputStream(exportFile)) {
			long fileSize = Files.size(exportFile.toPath());
			long bytesTransferred = Streams.copy(inputStream, outputStream, false);
			exportConfiguration.setStatus(ExportStatus.COMPLETED);
			exportConfigurationRepository.save(exportConfiguration);
			logger.debug("Transmitted {}bytes (file size = {}bytes) for export {}", bytesTransferred, fileSize, exportConfiguration.getId());
		} catch (IOException e) {
			exportConfiguration.setStatus(ExportStatus.FAILED);
			exportConfigurationRepository.save(exportConfiguration);
			throw new ExportException("Failed to copy RF2 data into output stream.", e);
		} finally {
			try {
				Files.delete(exportFile.toPath());
				logger.debug("Deleted {} export file {}", exportConfiguration.getId(), exportFile);
			} catch (IOException e) {
				logger.warn("Failed to delete temporary file {}", exportFile.getAbsolutePath(), e);
			}
		}
	}

	public File exportRF2ArchiveFile(String branchPath, String filenameEffectiveDate, RF2Type exportType, boolean forClassification) throws ExportException {
		return exportRF2ArchiveFile(branchPath, filenameEffectiveDate, exportType, forClassification, false, false, null, null, new HashSet<>(), true, new HashSet<>(), null);
	}

	public void exportRF2ArchiveAsync(ExportConfiguration exportConfiguration) {
		executorService.execute(() -> {
			synchronized (this) {
				if (exportConfiguration.getStartDate() != null) {
					throw new IllegalStateException("Export already started.");
				}

				exportConfiguration.setStartDate(new Date());
				exportConfiguration.setStatus(ExportStatus.RUNNING);
				exportConfigurationRepository.save(exportConfiguration);
			}

			File file = null;
			try {
				file = exportRF2ArchiveFile(exportConfiguration.getBranchPath(), exportConfiguration.getFilenameEffectiveDate(),
						exportConfiguration.getType(), exportConfiguration.isConceptsAndRelationshipsOnly(), exportConfiguration.isLanguageOnly(),
						exportConfiguration.isUnpromotedChangesOnly(),
						exportConfiguration.getTransientEffectiveTime(), exportConfiguration.getStartEffectiveTime(), exportConfiguration.getModuleIds(),
						exportConfiguration.isLegacyZipNaming(), exportConfiguration.getRefsetIds(), exportConfiguration.getId());

				exportConfiguration.setExportFilePath(file.getAbsolutePath());
				exportConfiguration.setStatus(ExportStatus.COMPLETED);
			} catch (ExportException | SecurityException | IllegalStateException e) {
				exportConfiguration.setExportFilePath(null);
				exportConfiguration.setStatus(ExportStatus.FAILED);
			} finally {
				exportConfigurationRepository.save(exportConfiguration);
				if (file != null) {
					file.deleteOnExit();
				}
			}
		});
	}

	@Scheduled(fixedDelayString = "${export.async.cleanup-interval-ms:600000}")
	public void cleanUpExpiredArchives() {
		try {
			long now = System.currentTimeMillis();
			Date yesterday = new Date(now - TimeUnit.DAYS.toMillis(1));
			Date sixDaysAgo = new Date(now - TimeUnit.DAYS.toMillis(6));
			PageRequest page = PageRequest.of(0, 1_000, Sort.by(Sort.Direction.DESC, "startDate"));
			for (ExportConfiguration config : exportConfigurationRepository.findByStartDateBetweenAndStatusIn(sixDaysAgo, yesterday, List.of(ExportStatus.COMPLETED, ExportStatus.RUNNING), page)) {
				if (deleteArchiveFile(config.getExportFilePath())) {
					logger.info("Deleted expired export archive {} for branch {} started {}", config.getId(), config.getBranchPath(), config.getStartDate());
				}
				config.setStatus(ExportStatus.EXPIRED);
				exportConfigurationRepository.save(config);
			}
		} catch (Exception e) {
			logger.error("Failed to clean up expired export archives.", e);
		}
	}

	private boolean deleteArchiveFile(String path) {
		if (path != null) {
			try {
				return Files.deleteIfExists(Path.of(path));
			} catch (IOException e) {
				logger.warn("Failed to delete expired export archive {}", path, e);
			}
		}
		return false;
	}

	public void copyRF2Archive(ExportConfiguration exportConfiguration, OutputStream outputStream) {
		File archive = new File(exportConfiguration.getExportFilePath());
		if (archive.isFile()) {
			try (FileInputStream inputStream = new FileInputStream(archive)) {
				long fileSize = Files.size(archive.toPath());
				long bytesTransferred = Streams.copy(inputStream, outputStream, false);
				logger.info("Transmitted " + bytesTransferred + "bytes (file size = " + fileSize + "bytes) for export " + exportConfiguration.getId());
			} catch (IOException e) {
				throw new ExportException("Failed to copy RF2 data into output stream.", e);
			} finally {
				try {
					Files.delete(archive.toPath());
					logger.info("Deleted {} export file.", exportConfiguration.getId());
					exportConfiguration.setStatus(ExportStatus.DOWNLOADED);
					exportConfigurationRepository.save(exportConfiguration);
				} catch (IOException e) {
					logger.error("Failed to delete {} export file.", exportConfiguration.getId(), e);
				}
			}
		} else {
			exportConfiguration.setStatus(ExportStatus.FAILED);
			exportConfigurationRepository.save(exportConfiguration);
		}
	}

	private File exportRF2ArchiveFile(String branchPath, String filenameEffectiveDate, RF2Type exportType, boolean forClassification,
									  boolean languageOnly, boolean unpromotedChangesOnly, String transientEffectiveTime, String startEffectiveTime, Set<String> moduleIds,
									  boolean legacyZipNaming, Set<String> refsetIds, String exportId) throws ExportException {

		if (exportType == RF2Type.FULL) {
			throw new IllegalArgumentException("FULL RF2 export is not implemented.");
		}

		String exportStr = exportId == null ? "" : (" - " + exportId);
		logger.info("Starting {} export of {}{}", exportType, branchPath, exportStr);
		Date startTime = new Date();

		BranchCriteria allContentBranchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		BranchCriteria selectionBranchCriteria = unpromotedChangesOnly ? versionControlHelper.getChangesOnBranchCriteria(branchPath) : allContentBranchCriteria;

		String entryDirectoryPrefix = legacyZipNaming ? "SnomedCT_Export/RF2Release/" : format("SnomedCT_Export/%s/", exportType.getName());
		final RF2FileNaming fileNaming = getRF2FileNaming(branchPath, legacyZipNaming, forClassification);

		branchService.lockBranch(branchPath, branchMetadataHelper.getBranchLockMetadata("Exporting RF2 " + exportType.getName()));
		try {
			File exportFile = File.createTempFile("export-" + new Date().getTime(), ".zip");
			try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(exportFile))) {
				RF2ExportContext export = new RF2ExportContext(filenameEffectiveDate, exportType, transientEffectiveTime, startEffectiveTime, moduleIds,
						entryDirectoryPrefix, zipOutputStream, fileNaming);

				boolean refsetOnlyExport = refsetIds != null && !refsetIds.isEmpty();

				if (!refsetOnlyExport) {
					exportTerminologyComponents(selectionBranchCriteria, forClassification, languageOnly, export);
				}

				// Write Reference Sets
				exportRefsetMembers(branchPath, filenameEffectiveDate, exportType, forClassification, languageOnly, transientEffectiveTime, startEffectiveTime,
						moduleIds, refsetIds, allContentBranchCriteria, selectionBranchCriteria, refsetOnlyExport, entryDirectoryPrefix, zipOutputStream, fileNaming);
			}

			logger.info("{} export of {}{} complete in {} seconds.", exportType, branchPath, exportStr, TimerUtil.secondsSince(startTime));
			return exportFile;
		} catch (IOException e) {
			throw new ExportException("Failed to write RF2 zip file.", e);
		} finally {
			branchService.unlock(branchPath);
		}
	}

	/**
	 * The file naming for an export. The country/namespace element is the code system short code under the new zip naming,
	 * otherwise {@code INT}. Only exports for classification carry a package type tag.
	 */
	private RF2FileNaming getRF2FileNaming(String branchPath, boolean legacyZipNaming, boolean forClassification) {
		if (legacyZipNaming && !forClassification) {
			return new RF2FileNaming(INTERNATIONAL_RF2_NAME, null);
		}
		CodeSystem codeSystem = codeSystemService.findClosestCodeSystemUsingAnyBranch(branchPath, false);
		String countryNamespace = INTERNATIONAL_RF2_NAME;
		if (!legacyZipNaming && codeSystem != null && codeSystem.getShortCode() != null) {
			countryNamespace = codeSystem.getShortCode();
		}
		return new RF2FileNaming(countryNamespace, forClassification ? getClassificationPackageTypeTag(codeSystem) : null);
	}

	private void exportTerminologyComponents(BranchCriteria selectionBranchCriteria, boolean forClassification, boolean languageOnly, RF2ExportContext export) {
		// Write Concepts
		int conceptLines = exportTerminologyFile(Concept.class, "sct2_Concept_",
				getContentQuery(export, selectionBranchCriteria.getEntityBranchCriteria(Concept.class)), export);
		logger.info("{} concept states exported", conceptLines);

		if (!forClassification) {
			exportDescriptions(selectionBranchCriteria, export);
		}

		if (!languageOnly) {
			exportRelationshipsAllTypes(selectionBranchCriteria, export);
			// Write Identifiers
			exportIdentifiers(selectionBranchCriteria, export);
		}
	}

	private void exportDescriptions(BranchCriteria selectionBranchCriteria, RF2ExportContext export) {
		// Write Descriptions
		Query descriptionBranchCriteria = selectionBranchCriteria.getEntityBranchCriteria(Description.class);
		BoolQuery.Builder descriptionContentQuery = getContentQueryBuilder(export, descriptionBranchCriteria);
		descriptionContentQuery.mustNot(termQuery(Description.Fields.TYPE_ID, Concepts.TEXT_DEFINITION));
		int descriptionLines = exportTerminologyFile(Description.class, "sct2_Description_", descriptionContentQuery.build()._toQuery(), export);
		logger.info("{} description states exported", descriptionLines);

		// Write Text Definitions
		BoolQuery.Builder textDefinitionContentQuery = getContentQueryBuilder(export, descriptionBranchCriteria);
		textDefinitionContentQuery.must(termQuery(Description.Fields.TYPE_ID, Concepts.TEXT_DEFINITION));
		int textDefinitionLines = exportTerminologyFile(Description.class, "sct2_TextDefinition_", textDefinitionContentQuery.build()._toQuery(), export);
		logger.info("{} text defintion states exported", textDefinitionLines);
	}

	private void exportRelationshipsAllTypes(BranchCriteria selectionBranchCriteria, RF2ExportContext export) {
		// Write Stated Relationships
		Query relationshipBranchCritera = selectionBranchCriteria.getEntityBranchCriteria(Relationship.class);
		BoolQuery.Builder relationshipQuery = getContentQueryBuilder(export, relationshipBranchCritera);
		relationshipQuery.must(termQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, Concepts.STATED_RELATIONSHIP));
		int statedRelationshipLines = exportTerminologyFile(Relationship.class, "sct2_StatedRelationship_", relationshipQuery.build()._toQuery(), export);
		logger.info("{} stated relationship states exported", statedRelationshipLines);

		// Write Inferred non-concrete Relationships
		relationshipQuery = getContentQueryBuilder(export, relationshipBranchCritera);
		// Not 'stated' will include inferred and additional
		relationshipQuery.mustNot(termQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, Concepts.STATED_RELATIONSHIP));
		relationshipQuery.must(existsQuery(Relationship.Fields.DESTINATION_ID));
		int inferredRelationshipLines = exportTerminologyFile(Relationship.class, "sct2_Relationship_", relationshipQuery.build()._toQuery(), export);
		logger.info("{} inferred (non-concrete) and additional relationship states exported", inferredRelationshipLines);

		// Write Concrete Inferred Relationships
		relationshipQuery = getContentQueryBuilder(export, relationshipBranchCritera);
		relationshipQuery.mustNot(termQuery(Relationship.Fields.CHARACTERISTIC_TYPE_ID, Concepts.STATED_RELATIONSHIP));
		relationshipQuery.must(existsQuery(Relationship.Fields.VALUE));
		int inferredConcreteRelationshipLines = exportTerminologyFile(Relationship.class, "sct2_RelationshipConcreteValues_", relationshipQuery.build()._toQuery(), export);
		logger.info("{} concrete inferred relationship states exported", inferredConcreteRelationshipLines);
	}

	private void exportIdentifiers(BranchCriteria selectionBranchCriteria, RF2ExportContext export) {
		int identifierLines = exportTerminologyFile(Identifier.class, "sct2_Identifier_",
				getContentQuery(export, selectionBranchCriteria.getEntityBranchCriteria(Identifier.class)), export);
		logger.info("{} identifier states exported", identifierLines);
	}

	private <T> int exportTerminologyFile(Class<T> componentClass, String entryFilenamePrefix, Query contentQuery, RF2ExportContext export) {
		return exportComponents(componentClass, export.entryDirectoryPrefix(), "Terminology/", entryFilenamePrefix, export.filenameEffectiveDate(), export.exportType(),
				export.zipOutputStream(), contentQuery, export.transientEffectiveTime(), null, export.fileNaming(), null);
	}

	private BoolQuery.Builder getContentQueryBuilder(RF2ExportContext export, Query branchCriteria) {
		return getContentQuery(export.exportType(), export.moduleIds(), export.startEffectiveTime(), branchCriteria);
	}

	private Query getContentQuery(RF2ExportContext export, Query branchCriteria) {
		return getContentQueryBuilder(export, branchCriteria).build()._toQuery();
	}

	/**
	 * The settings shared by every file written in one export.
	 */
	private record RF2ExportContext(String filenameEffectiveDate, RF2Type exportType, String transientEffectiveTime, String startEffectiveTime,
									Set<String> moduleIds, String entryDirectoryPrefix, ZipOutputStream zipOutputStream, RF2FileNaming fileNaming) {
	}

	private void exportRefsetMembers(String branchPath, String filenameEffectiveDate, RF2Type exportType, boolean forClassification, boolean languageOnly, String transientEffectiveTime,
									 String startEffectiveTime, Set<String> moduleIds, Set<String> refsetIds, BranchCriteria allContentBranchCriteria, BranchCriteria selectionBranchCriteria, boolean refsetOnlyExport, String entryDirectoryPrefix, ZipOutputStream zipOutputStream, RF2FileNaming fileNaming) {

		List<ReferenceSetType> referenceSetTypes = getReferenceSetTypes(allContentBranchCriteria.getEntityBranchCriteria(ReferenceSetType.class)).stream()
				.filter(type -> !forClassification || refsetTypesRequiredForClassification.contains(type.getConceptId()))
				.filter(type -> !languageOnly || LANG_REFSET.equals(type.getConceptId()))
				.toList();

		logger.info("{} Reference Set Types found for this export: {}", referenceSetTypes.size(), referenceSetTypes);

		Query memberBranchCriteria = selectionBranchCriteria.getEntityBranchCriteria(ReferenceSetMember.class);
		for (ReferenceSetType referenceSetType : referenceSetTypes) {
			List<Long> refsetsOfThisType = new ArrayList<>(queryService.findDescendantIdsAsUnion(allContentBranchCriteria, true, Collections.singleton(Long.parseLong(referenceSetType.getConceptId()))));
			refsetsOfThisType.add(Long.parseLong(referenceSetType.getConceptId()));
			for (Long refsetToExport : refsetsOfThisType) {
				exportRefset(branchPath, filenameEffectiveDate, exportType, transientEffectiveTime, startEffectiveTime, moduleIds, refsetIds, refsetOnlyExport, entryDirectoryPrefix, zipOutputStream, fileNaming, referenceSetType, refsetToExport, memberBranchCriteria, refsetsOfThisType);
			}
		}
	}

	private void exportRefset(String branchPath, String filenameEffectiveDate, RF2Type exportType, String transientEffectiveTime,
							  String startEffectiveTime, Set<String> moduleIds, Set<String> refsetIds, boolean refsetOnlyExport, String entryDirectoryPrefix,
							  ZipOutputStream zipOutputStream, RF2FileNaming fileNaming, ReferenceSetType referenceSetType, Long refsetToExport, Query memberBranchCriteria,
							  List<Long> refsetsOfThisType) {

		if (!refsetOnlyExport || refsetIds.contains(refsetToExport.toString())) {
			BoolQuery.Builder memberQueryBuilder = getContentQuery(exportType, getModuleIdsForExport(moduleIds, refsetToExport, branchPath), startEffectiveTime, memberBranchCriteria);
			memberQueryBuilder.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, refsetToExport));
			Query memberQuery = memberQueryBuilder.build()._toQuery();
			long memberCount = elasticsearchOperations.count(getNativeSearchQuery(memberQuery), ReferenceSetMember.class);
			if (memberCount > 0) {
				logger.info("Exporting Reference Set {} {} with {} members", refsetToExport, referenceSetType.getName(), memberCount);
				String exportDir = referenceSetType.getExportDir();
				String entryDirectory = !exportDir.startsWith("/") ? "Refset/" + exportDir + "/" : exportDir.substring(1) + "/";
				String entryFilenamePrefix = (!entryDirectory.startsWith("Terminology/") ? "der2_" : "sct2_") + referenceSetType.getFieldTypes() + "Refset_" + referenceSetType.getName() + (refsetsOfThisType.size() > 1 ? refsetToExport : "");
				exportComponents(
						ReferenceSetMember.class,
						entryDirectoryPrefix, entryDirectory,
						entryFilenamePrefix,
						filenameEffectiveDate,
						exportType,
						zipOutputStream,
						memberQuery,
						transientEffectiveTime,
						referenceSetType.getFieldNameList(),
						fileNaming,
						null);
			}
		}
	}

	/**
	 * The package type tag for the RF2 file names in an export for classification, chosen by the code system's naming
	 * convention: none under the legacy convention, otherwise the code system's package type, for example {@code Edition}.
	 *
	 * @param codeSystem the code system being classified, or null if the branch has none
	 * @return the tag, or null when the file names carry no tag
	 */
	static String getClassificationPackageTypeTag(CodeSystem codeSystem) {
		if (codeSystem == null || codeSystem.isLegacyNamingConvention() || codeSystem.getPackageType() == null) {
			return null;
		}
		return codeSystem.getPackageType().getTag();
	}

	/**
	 * How the RF2 file names in one export are built.
	 *
	 * @param countryNamespace the country/namespace element, for example {@code INT}
	 * @param packageTypeTag   the package type tag added under the new naming convention, for example {@code Edition};
	 *                         null for none. See {@link #withPackageTypeTag(String, String)} for where it goes.
	 */
	record RF2FileNaming(String countryNamespace, String packageTypeTag) {

		String fileName(String entryFilenamePrefix, RF2Type exportType, String filenameEffectiveDate) {
			return withPackageTypeTag(entryFilenamePrefix, packageTypeTag) + format("%s_%s_%s.txt", exportType.getName(), countryNamespace, filenameEffectiveDate);
		}

		/**
		 * Adds the tag to the file name prefix. Core component files take it at the end of the content type, the second
		 * element: {@code sct2_Concept_} becomes {@code sct2_ConceptEdition_}. Refset files keep their content type and take
		 * it at the end of the prefix, just before the release type: {@code der2_cRefset_Language} becomes
		 * {@code der2_cRefset_LanguageEdition}, giving {@code der2_cRefset_LanguageEditionSnapshot-en_INT_20270701.txt}.
		 */
		static String withPackageTypeTag(String entryFilenamePrefix, String packageTypeTag) {
			if (packageTypeTag == null) {
				return entryFilenamePrefix;
			}
			if (entryFilenamePrefix.contains("Refset_")) {
				return entryFilenamePrefix + packageTypeTag;
			}
			int contentTypeEnd = entryFilenamePrefix.indexOf('_', entryFilenamePrefix.indexOf('_') + 1);
			if (contentTypeEnd < 0) {
				return entryFilenamePrefix;
			}
			return entryFilenamePrefix.substring(0, contentTypeEnd) + packageTypeTag + entryFilenamePrefix.substring(contentTypeEnd);
		}
	}

	public String getFilename(ExportConfiguration exportConfiguration) {
		return format("snomed-%s-%s-%s.zip",
				exportConfiguration.getBranchPath().replace("/", "_"),
				exportConfiguration.getFilenameEffectiveDate(),
				exportConfiguration.getType().getName());
	}

	private BoolQuery.Builder getContentQuery(RF2Type exportType, Set<String> moduleIds, String startEffectiveTime, Query branchCriteria) {
		BoolQuery.Builder contentQuery = bool().must(branchCriteria);
		if (exportType == RF2Type.DELTA) {
			contentQuery.mustNot(existsQuery(SnomedComponent.Fields.EFFECTIVE_TIME));
		}
		if (!CollectionUtils.isEmpty(moduleIds)) {
			contentQuery.must(termsQuery(SnomedComponent.Fields.MODULE_ID, moduleIds));
		}
		if (startEffectiveTime != null) {
			contentQuery.must(bool(b -> b
					.should(bool(bq -> bq.mustNot(existsQuery(SnomedComponent.Fields.EFFECTIVE_TIME))))
					.should(RangeQuery.of(r -> r.number(nrq -> nrq.field(SnomedComponent.Fields.EFFECTIVE_TIME)
							.gte((double) Integer.parseInt(startEffectiveTime))))._toQuery())));
		}
		return contentQuery;
	}

	private <T> int exportComponents(Class<T> componentClass, String entryDirectoryPrefix, String entryDirectory, String entryFilenamePrefix, String filenameEffectiveDate,
									 RF2Type exportType, ZipOutputStream zipOutputStream, Query contentQuery, String transientEffectiveTime, List<String> extraFieldNames, RF2FileNaming fileNaming, ExportFilter<T> exportFilter) {

		String componentFilePath = entryDirectoryPrefix + entryDirectory + fileNaming.fileName(entryFilenamePrefix, exportType, filenameEffectiveDate);
		logger.info("Exporting file {}", componentFilePath);
		logger.info("Export filter is " + (exportFilter == null ? "null" : "present"));
		try {
			// Open zip entry
			zipOutputStream.putNextEntry(new ZipEntry(componentFilePath));

			// Stream components into zip
			try (ExportWriter<T> writer = getExportWriter(componentClass, zipOutputStream, extraFieldNames, entryFilenamePrefix.contains("Concrete"));
				 SearchHitsIterator<T> componentStream = elasticsearchOperations.searchForStream(getNativeSearchQuery(contentQuery), componentClass)) {
				writer.setTransientEffectiveTime(transientEffectiveTime);
				writer.writeHeader();
				componentStream.forEachRemaining(hit -> doFilteredWrite(exportFilter, writer, hit.getContent()));
				return writer.getContentLinesWritten();
			} finally {
				// Close zip entry
				zipOutputStream.closeEntry();
			}
		} catch (IOException e) {
			throw new ExportException("Failed to write export zip entry '" + componentFilePath + "'", e);
		}
	}

	private <T> void doFilteredWrite(ExportFilter<T> exportFilter, ExportWriter<T> writer, T item) {
		if (exportFilter == null || exportFilter.isValid(item)) {
			writer.write(item);
		}
	}

	@SuppressWarnings("unchecked")
	private <T> ExportWriter<T> getExportWriter(Class<T> componentClass, OutputStream outputStream, List<String> extraFieldNames, boolean concrete) {
		if (componentClass.equals(Concept.class)) {
			return (ExportWriter<T>) new ConceptExportWriter(getBufferedWriter(outputStream));
		}
		if (componentClass.equals(Description.class)) {
			return (ExportWriter<T>) new DescriptionExportWriter(getBufferedWriter(outputStream));
		}
		if (componentClass.equals(Relationship.class)) {
			return (ExportWriter<T>) (concrete ? new ConcreteRelationshipExportWriter(getBufferedWriter(outputStream)) : new RelationshipExportWriter(getBufferedWriter(outputStream)));
		}
		if (componentClass.equals(ReferenceSetMember.class)) {
			return (ExportWriter<T>) new ReferenceSetMemberExportWriter(getBufferedWriter(outputStream), extraFieldNames);
		}
		if (componentClass.equals(Identifier.class)) {
			return (ExportWriter<T>) new IdentifierExportWriter(getBufferedWriter(outputStream));
		}
		throw new UnsupportedOperationException("Not able to export component of type " + componentClass.getCanonicalName());
	}

	private List<ReferenceSetType> getReferenceSetTypes(Query branchCriteria) {
		BoolQuery.Builder contentQuery = getContentQuery(RF2Type.SNAPSHOT, null, null, branchCriteria);
		return elasticsearchOperations.search(new NativeQueryBuilder()
						.withQuery(contentQuery.build()._toQuery())
						.withSort(SortOptions.of(s -> s.field(f -> f.field(ReferenceSetType.Fields.NAME))))
						.withPageable(LARGE_PAGE)
						.build(), ReferenceSetType.class)
				.stream().map(SearchHit::getContent).collect(Collectors.toList());
	}

	private NativeQuery getNativeSearchQuery(Query contentQuery) {
		return new NativeQueryBuilder()
				.withQuery(contentQuery)
				.withPageable(LARGE_PAGE)
				.build();
	}

	private BufferedWriter getBufferedWriter(OutputStream outputStream) {
		return new BufferedWriter(new OutputStreamWriter(outputStream));
	}

	private Set<String> getModuleIdsForExport(Set<String> moduleIds, Long refsetToExport, String branchPath) {
		if (Concepts.MODULE_DEPENDENCY_REFERENCE_SET.equals(String.valueOf(refsetToExport))) {
			Set<String> branchModules = sBranchService.getModules(branchPath);
			if (!branchModules.isEmpty()) {
				Set<String> expanded = new HashSet<>(moduleIds);
				expanded.addAll(branchModules);
				return expanded;
			}
		}

		return moduleIds;
	}
}