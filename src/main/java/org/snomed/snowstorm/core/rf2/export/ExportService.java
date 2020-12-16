package org.snomed.snowstorm.core.rf2.export;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.jobs.ExportConfiguration;
import org.snomed.snowstorm.core.data.repositories.ExportConfigurationRepository;
import org.snomed.snowstorm.core.data.services.BranchMetadataHelper;
import org.snomed.snowstorm.core.data.services.NotFoundException;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.util.DateUtil;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static org.elasticsearch.index.query.QueryBuilders.*;

@Service
public class ExportService {

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private QueryService queryService;

	@Autowired
	private ExportConfigurationRepository exportConfigurationRepository;

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMetadataHelper branchMetadataHelper;

	private final Set<String> refsetTypesRequiredForClassification = Sets.newHashSet(Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN, Concepts.OWL_EXPRESSION_TYPE_REFERENCE_SET);

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
		if (exportConfiguration.getFilenameEffectiveDate() == null) {
			exportConfiguration.setFilenameEffectiveDate(DateUtil.DATE_STAMP_FORMAT.format(new Date()));
		}
		exportConfigurationRepository.save(exportConfiguration);
		return exportConfiguration.getId();
	}

	public ExportConfiguration getExportJobOrThrow(String exportId) {
		Optional<ExportConfiguration> config = exportConfigurationRepository.findById(exportId);
		if (!config.isPresent()) {
			throw new NotFoundException("Export job not found.");
		}
		return config.get();
	}

	public void exportRF2Archive(ExportConfiguration exportConfiguration, OutputStream outputStream) throws ExportException {
		synchronized (this) {
			if (exportConfiguration.getStartDate() != null) {
				throw new IllegalStateException("Export already started.");
			}
			exportConfiguration.setStartDate(new Date());
			exportConfigurationRepository.save(exportConfiguration);
		}

		File exportFile = exportRF2ArchiveFile(exportConfiguration.getBranchPath(), exportConfiguration.getFilenameEffectiveDate(),
				exportConfiguration.getType(), exportConfiguration.isConceptsAndRelationshipsOnly(), exportConfiguration.isUnpromotedChangesOnly(),
				exportConfiguration.getTransientEffectiveTime(), exportConfiguration.getStartEffectiveTime(), exportConfiguration.getModuleIds());
		try (FileInputStream inputStream = new FileInputStream(exportFile)) {
			Streams.copy(inputStream, outputStream, false);
		} catch (IOException e) {
			throw new ExportException("Failed to copy RF2 data into output stream.", e);
		} finally {
			exportFile.delete();
		}
	}

	public File exportRF2ArchiveFile(String branchPath, String filenameEffectiveDate, RF2Type exportType, boolean forClassification) throws ExportException {
		return exportRF2ArchiveFile(branchPath, filenameEffectiveDate, exportType, forClassification, false, null, null, null);
	}

	private File exportRF2ArchiveFile(String branchPath, String filenameEffectiveDate, RF2Type exportType, boolean forClassification,
			boolean unpromotedChangesOnly, String transientEffectiveTime, String startEffectiveTime, Set<String> moduleIds) throws ExportException {
		if (exportType == RF2Type.FULL) {
			throw new IllegalArgumentException("FULL RF2 export is not implemented.");
		}

		logger.info("Starting {} export.", exportType);
		Date startTime = new Date();

		BranchCriteria allContentBranchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		BranchCriteria selectionBranchCriteria = unpromotedChangesOnly ? versionControlHelper.getChangesOnBranchCriteria(branchPath) : allContentBranchCriteria;

		try {
			branchService.lockBranch(branchPath, branchMetadataHelper.getBranchLockMetadata("Exporting RF2 " + exportType.getName()));
			File exportFile = File.createTempFile("export-" + new Date().getTime(), ".zip");
			try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(exportFile))) {
				// Write Concepts
				int conceptLines = exportComponents(Concept.class, "Terminology/", "sct2_Concept_", filenameEffectiveDate, exportType, zipOutputStream,
						getContentQuery(exportType, moduleIds, startEffectiveTime, selectionBranchCriteria.getEntityBranchCriteria(Concept.class)), transientEffectiveTime, null);
				logger.info("{} concept states exported", conceptLines);

				if (!forClassification) {
					// Write Descriptions
					BoolQueryBuilder descriptionBranchCriteria = selectionBranchCriteria.getEntityBranchCriteria(Description.class);
					BoolQueryBuilder descriptionContentQuery = getContentQuery(exportType, moduleIds, startEffectiveTime, descriptionBranchCriteria);
					descriptionContentQuery.mustNot(termQuery(Description.Fields.TYPE_ID, Concepts.TEXT_DEFINITION));
					int descriptionLines = exportComponents(Description.class, "Terminology/", "sct2_Description_", filenameEffectiveDate, exportType, zipOutputStream,
							descriptionContentQuery, transientEffectiveTime,null);
					logger.info("{} description states exported", descriptionLines);

					// Write Text Definitions
					BoolQueryBuilder textDefinitionContentQuery = getContentQuery(exportType, moduleIds, startEffectiveTime, descriptionBranchCriteria);
					textDefinitionContentQuery.must(termQuery(Description.Fields.TYPE_ID, Concepts.TEXT_DEFINITION));
					int textDefinitionLines = exportComponents(Description.class, "Terminology/", "sct2_TextDefinition_", filenameEffectiveDate, exportType, zipOutputStream,
							textDefinitionContentQuery, transientEffectiveTime, null);
					logger.info("{} text defintion states exported", textDefinitionLines);
				}

				// Write Stated Relationships
				BoolQueryBuilder relationshipBranchCritera = selectionBranchCriteria.getEntityBranchCriteria(Relationship.class);
				BoolQueryBuilder relationshipQuery = getContentQuery(exportType, moduleIds, startEffectiveTime, relationshipBranchCritera);
				relationshipQuery.must(termQuery("characteristicTypeId", Concepts.STATED_RELATIONSHIP));
				int statedRelationshipLines = exportComponents(Relationship.class, "Terminology/", "sct2_StatedRelationship_", filenameEffectiveDate, exportType, zipOutputStream,
						relationshipQuery, transientEffectiveTime, null);
				logger.info("{} stated relationship states exported", statedRelationshipLines);

				// Write Inferred Relationships
				relationshipQuery = getContentQuery(exportType, moduleIds, startEffectiveTime, relationshipBranchCritera);
				// Not 'stated' will include inferred and additional
				relationshipQuery.mustNot(termQuery("characteristicTypeId", Concepts.STATED_RELATIONSHIP));
				int inferredRelationshipLines = exportComponents(Relationship.class, "Terminology/", "sct2_Relationship_", filenameEffectiveDate, exportType, zipOutputStream,
						relationshipQuery, transientEffectiveTime, null);
				logger.info("{} inferred and additional relationship states exported", inferredRelationshipLines);

				// Write Reference Sets
				List<ReferenceSetType> referenceSetTypes = getReferenceSetTypes(allContentBranchCriteria.getEntityBranchCriteria(ReferenceSetType.class)).stream()
						.filter(type -> !forClassification || refsetTypesRequiredForClassification.contains(type.getConceptId()))
						.collect(Collectors.toList());

				logger.info("{} Reference Set Types found for this export: {}", referenceSetTypes.size(), referenceSetTypes);

				BoolQueryBuilder memberBranchCriteria = selectionBranchCriteria.getEntityBranchCriteria(ReferenceSetMember.class);
				for (ReferenceSetType referenceSetType : referenceSetTypes) {
					List<Long> refsetsOfThisType = new ArrayList<>(queryService.findDescendantIdsAsUnion(allContentBranchCriteria, true, Collections.singleton(Long.parseLong(referenceSetType.getConceptId()))));
					refsetsOfThisType.add(Long.parseLong(referenceSetType.getConceptId()));
					for (Long refsetToExport : refsetsOfThisType) {
						BoolQueryBuilder memberQuery = getContentQuery(exportType, moduleIds, startEffectiveTime, memberBranchCriteria);
						memberQuery.must(QueryBuilders.termQuery(ReferenceSetMember.Fields.REFSET_ID, refsetToExport));
						long memberCount = elasticsearchTemplate.count(getNativeSearchQuery(memberQuery), ReferenceSetMember.class);
						if (memberCount > 0) {
							logger.info("Exporting Reference Set {} {} with {} members", refsetToExport, referenceSetType.getName(), memberCount);
							String exportDir = referenceSetType.getExportDir();
							String entryDirectory = !exportDir.startsWith("/") ? "Refset/" + exportDir + "/" : exportDir.substring(1) + "/";
							String entryFilenamePrefix = (!entryDirectory.startsWith("Terminology/") ? "der2_" : "sct2_") + referenceSetType.getFieldTypes() + "Refset_" + referenceSetType.getName() + (refsetsOfThisType.size() > 1 ? refsetToExport : "");
							exportComponents(
									ReferenceSetMember.class,
									entryDirectory,
									entryFilenamePrefix,
									filenameEffectiveDate,
									exportType,
									zipOutputStream,
									memberQuery,
									transientEffectiveTime,
									referenceSetType.getFieldNameList());
						}
					}
				}
			}
			logger.info("{} export complete in {} seconds.", exportType, TimerUtil.secondsSince(startTime));
			return exportFile;
		} catch (IOException e) {
			throw new ExportException("Failed to write RF2 zip file.", e);
		} finally {
			branchService.unlock(branchPath);
		}
	}

	public String getFilename(ExportConfiguration exportConfiguration) {
		return String.format("snomed-%s-%s-%s.zip",
				exportConfiguration.getBranchPath().replace("/", "_"),
				exportConfiguration.getFilenameEffectiveDate(),
				exportConfiguration.getType().getName());
	}

	private BoolQueryBuilder getContentQuery(RF2Type exportType, Set<String> moduleIds, String startEffectiveTime, QueryBuilder branchCriteria) {
		BoolQueryBuilder contentQuery = boolQuery().must(branchCriteria);
		if (exportType == RF2Type.DELTA) {
			contentQuery.mustNot(existsQuery(SnomedComponent.Fields.EFFECTIVE_TIME));
		}
		if (!CollectionUtils.isEmpty(moduleIds)) {
			contentQuery.must(termsQuery(SnomedComponent.Fields.MODULE_ID, moduleIds));
		}
		if (startEffectiveTime != null) {
			contentQuery.must(boolQuery()
					.should(boolQuery().mustNot(existsQuery(SnomedComponent.Fields.EFFECTIVE_TIME)))
					.should(rangeQuery(SnomedComponent.Fields.EFFECTIVE_TIME)
							.gte(Integer.parseInt(startEffectiveTime)))
			);
		}
		return contentQuery;
	}

	private <T> int exportComponents(Class<T> componentClass, String entryDirectory, String entryFilenamePrefix, String filenameEffectiveDate,
									 RF2Type exportType, ZipOutputStream zipOutputStream, BoolQueryBuilder contentQuery, String transientEffectiveTime, List<String> extraFieldNames) {

		String componentFilePath = "SnomedCT_Export/RF2Release/" + entryDirectory + entryFilenamePrefix + String.format("%s_INT_%s.txt", exportType.getName(), filenameEffectiveDate);
		logger.info("Exporting file {}", componentFilePath);
		try {
			// Open zip entry
			zipOutputStream.putNextEntry(new ZipEntry(componentFilePath));

			// Stream components into zip
			try (ExportWriter<T> writer = getExportWriter(componentClass, zipOutputStream, extraFieldNames);
					SearchHitsIterator<T> componentStream = elasticsearchTemplate.searchForStream(getNativeSearchQuery(contentQuery), componentClass)) {
				writer.setTransientEffectiveTime(transientEffectiveTime);
				writer.writeHeader();
				componentStream.forEachRemaining(hit -> writer.write(hit.getContent()));
				return writer.getContentLinesWritten();
			} finally {
				// Close zip entry
				zipOutputStream.closeEntry();
			}
		} catch (IOException e) {
			throw new ExportException("Failed to write export zip entry '" + componentFilePath + "'", e);
		}
	}

	private <T> ExportWriter<T> getExportWriter(Class<T> componentClass, OutputStream outputStream, List<String> extraFieldNames) throws IOException {
		if (componentClass.equals(Concept.class)) {
			return (ExportWriter<T>) new ConceptExportWriter(getBufferedWriter(outputStream));
		}
		if (componentClass.equals(Description.class)) {
			return (ExportWriter<T>) new DescriptionExportWriter(getBufferedWriter(outputStream));
		}
		if (componentClass.equals(Relationship.class)) {
			return (ExportWriter<T>) new RelationshipExportWriter(getBufferedWriter(outputStream));
		}
		if (componentClass.equals(ReferenceSetMember.class)) {
			return (ExportWriter<T>) new ReferenceSetMemberExportWriter(getBufferedWriter(outputStream), extraFieldNames);
		}
		throw new UnsupportedOperationException("Not able to export component of type " + componentClass.getCanonicalName());
	}

	private List<ReferenceSetType> getReferenceSetTypes(QueryBuilder branchCriteria) {
		BoolQueryBuilder contentQuery = getContentQuery(RF2Type.SNAPSHOT, null, null, branchCriteria);
		return elasticsearchTemplate.search(new NativeSearchQueryBuilder()
				.withQuery(contentQuery)
				.withSort(SortBuilders.fieldSort(ReferenceSetType.Fields.NAME))
				.withPageable(LARGE_PAGE)
				.build(), ReferenceSetType.class)
				.stream().map(SearchHit::getContent).collect(Collectors.toList());
	}

	private NativeSearchQuery getNativeSearchQuery(BoolQueryBuilder contentQuery) {
		return new NativeSearchQueryBuilder()
				.withQuery(contentQuery)
				.withPageable(LARGE_PAGE)
				.build();
	}

	private BufferedWriter getBufferedWriter(OutputStream outputStream) {
		return new BufferedWriter(new OutputStreamWriter(outputStream));
	}
}
