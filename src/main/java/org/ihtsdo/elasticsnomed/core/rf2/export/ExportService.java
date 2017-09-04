package org.ihtsdo.elasticsnomed.core.rf2.export;

import com.google.common.io.Files;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.domain.*;
import org.ihtsdo.elasticsnomed.core.data.repositories.ReferenceSetTypeRepository;
import org.ihtsdo.elasticsnomed.core.data.services.QueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Service
public class ExportService {

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private QueryService queryService;

	@Autowired
	private ReferenceSetTypeRepository referenceSetTypeRepository;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public void exportRF2Archive(String branchPath, String filenameEffectiveDate, ExportType exportType, OutputStream outputStream) throws ExportException {
		File exportFile = exportRF2ArchiveFile(branchPath, filenameEffectiveDate, exportType, false);
		try {
			Files.copy(exportFile, outputStream);
		} catch (IOException e) {
			throw new ExportException("Failed to copy RF2 data into output stream.", e);
		} finally {
			exportFile.delete();
		}
	}

	public File exportRF2ArchiveFile(String branchPath, String filenameEffectiveDate, ExportType exportType, boolean forClassification) throws ExportException {
		QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(branchPath);

		try {
			File exportFile = File.createTempFile("export-" + new Date().getTime(), ".zip");
			try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(exportFile))) {
				// Write Concepts
				int conceptLines = exportComponents(Concept.class, "Terminology/", "sct2_Concept_", filenameEffectiveDate, exportType, zipOutputStream, getContentQuery(exportType, branchCriteria), null);
				logger.info("{} concept states exported", conceptLines);

				if (!forClassification) {
					// Write Descriptions
					int descriptionLines = exportComponents(Description.class, "Terminology/", "sct2_Description_", filenameEffectiveDate, exportType, zipOutputStream, getContentQuery(exportType, branchCriteria), null);
					logger.info("{} description states exported", descriptionLines);
				}

				// Write Stated Relationships
				BoolQueryBuilder relationshipQuery = getContentQuery(exportType, branchCriteria);
				relationshipQuery.must(termQuery("characteristicTypeId", Concepts.STATED_RELATIONSHIP));
				int statedRelationshipLines = exportComponents(Relationship.class, "Terminology/", "sct2_StatedRelationship_", filenameEffectiveDate, exportType, zipOutputStream, relationshipQuery, null);
				logger.info("{} stated relationship states exported", statedRelationshipLines);

				// Write Inferred Relationships
				relationshipQuery = getContentQuery(exportType, branchCriteria);
				// Not 'stated' will include inferred and additional
				relationshipQuery.mustNot(termQuery("characteristicTypeId", Concepts.STATED_RELATIONSHIP));
				int inferredRelationshipLines = exportComponents(Relationship.class, "Terminology/", "sct2_Relationship_", filenameEffectiveDate, exportType, zipOutputStream, relationshipQuery, null);
				logger.info("{} inferred and additional relationship states exported", inferredRelationshipLines);

				if (!forClassification) { // TODO: We will need to export the OWL Reference Set soon
					// Write Reference Sets
					List<ReferenceSetType> referenceSetTypes = getReferenceSetTypes(branchCriteria);
					logger.info("{} Reference Set Types found", referenceSetTypes.size());

					for (ReferenceSetType referenceSetType : referenceSetTypes) {
						Set<Long> refsetsOfThisType = queryService.retrieveDescendants(referenceSetType.getConceptId(), branchCriteria, true);
						refsetsOfThisType.add(Long.parseLong(referenceSetType.getConceptId()));
						for (Long refsetToExport : refsetsOfThisType) {
							BoolQueryBuilder memberQuery = getContentQuery(exportType, branchCriteria);
							memberQuery.must(termQuery(ReferenceSetMember.Fields.REFSET_ID, refsetToExport));
							long memberCount = elasticsearchTemplate.count(getNativeSearchQuery(memberQuery), ReferenceSetMember.class);
							if (memberCount > 0) {
								logger.info("Exporting Reference Set {} {} with {} members", refsetToExport, referenceSetType.getExportDir(), memberCount);
								exportComponents(
										ReferenceSetMember.class,
										"Refset/" + referenceSetType.getExportDir() + "/",
										"der2_" + referenceSetType.getFieldTypes() + "Refset_" + refsetToExport,
										filenameEffectiveDate,
										exportType,
										zipOutputStream,
										memberQuery,
										referenceSetType.getFieldNameList());
							}
						}
					}
				}
			}
			return exportFile;
		} catch (IOException e) {
			throw new ExportException("Failed to write RF2 zip file.", e);
		}
	}

	private BoolQueryBuilder getContentQuery(ExportType exportType, QueryBuilder branchCriteria) {
		BoolQueryBuilder contentQuery = boolQuery().must(branchCriteria);
		if (exportType == ExportType.DELTA) {
			contentQuery.mustNot(existsQuery("effectiveTime"));
		}
		return contentQuery;
	}

	private <T> int exportComponents(Class<T> componentClass, String entryDirectory, String entryFilenamePrefix, String filenameEffectiveDate,
									 ExportType exportType, ZipOutputStream zipOutputStream, BoolQueryBuilder contentQuery, List<String> extraFieldNames) {

		String componentFilePath = "SnomedCT_Export/RF2Release/" + entryDirectory + entryFilenamePrefix + String.format("%s_%s.txt", exportType.getName(), filenameEffectiveDate);
		logger.info("Exporting file {}", componentFilePath);
		try {
			// Open zip entry
			zipOutputStream.putNextEntry(new ZipEntry(componentFilePath));

			// Stream components into zip
			try (ExportWriter<T> writer = getExportWriter(componentClass, zipOutputStream, extraFieldNames);
					CloseableIterator<T> componentStream = elasticsearchTemplate.stream(getNativeSearchQuery(contentQuery), componentClass)) {
				writer.writeHeader();
				componentStream.forEachRemaining(writer::write);
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
		BoolQueryBuilder contentQuery = getContentQuery(ExportType.SNAPSHOT, branchCriteria);
		return elasticsearchTemplate.queryForList(getNativeSearchQuery(contentQuery), ReferenceSetType.class);
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
