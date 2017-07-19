package org.ihtsdo.elasticsnomed.core.rf2.export;

import com.google.common.io.Files;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.ihtsdo.elasticsnomed.core.data.domain.Concept;
import org.ihtsdo.elasticsnomed.core.data.domain.Concepts;
import org.ihtsdo.elasticsnomed.core.data.domain.Description;
import org.ihtsdo.elasticsnomed.core.data.domain.Relationship;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.Date;
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

	public void exportRF2Archive(String branchPath, String filenameEffectiveDate, ExportType exportType, OutputStream outputStream) throws ExportException {
		QueryBuilder branchCriteria = versionControlHelper.getBranchCriteria(branchPath);

		File tempExportFile = null;
		try {
			tempExportFile = File.createTempFile("export-" + new Date().getTime(), ".zip");
			try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(tempExportFile))) {
				// Write Concepts
				exportComponents(Concept.class, "Terminology/", "sct2_Concept_", filenameEffectiveDate, exportType, zipOutputStream, getContentQuery(exportType, branchCriteria));

				// Write Descriptions
				exportComponents(Description.class, "Terminology/", "sct2_Description_", filenameEffectiveDate, exportType, zipOutputStream, getContentQuery(exportType, branchCriteria));

				// TODO Write Language Reference Set

				// Write Stated Relationships
				BoolQueryBuilder contentQuery = getContentQuery(exportType, branchCriteria);
				contentQuery.must(termQuery("characteristicTypeId", Concepts.STATED_RELATIONSHIP));
				exportComponents(Relationship.class, "Terminology/", "sct2_StatedRelationship_", filenameEffectiveDate, exportType, zipOutputStream, contentQuery);
			}
			Files.copy(tempExportFile, outputStream);
		} catch (IOException e) {
			throw new ExportException("Failed to write RF2 zip file.", e);
		} finally {
			if (tempExportFile != null) {
				tempExportFile.delete();
			}
		}
	}

	private BoolQueryBuilder getContentQuery(ExportType exportType, QueryBuilder branchCriteria) {
		BoolQueryBuilder contentQuery = boolQuery().must(branchCriteria);
		if (exportType == ExportType.DELTA) {
			contentQuery.mustNot(existsQuery("effectiveTime"));
		}
		return contentQuery;
	}

	private <T> void exportComponents(Class<T> componentClass, String entryDirectory, String entryFilenamePrefix, String filenameEffectiveDate, ExportType exportType, ZipOutputStream zipOutputStream, BoolQueryBuilder contentQuery) {
		String componentFilePath = "SnomedCT_Export/RF2Release/" + entryFilenamePrefix + String.format("%s_%s.txt", exportType.getName(), filenameEffectiveDate);
		try {
			// Open zip entry
			zipOutputStream.putNextEntry(new ZipEntry(componentFilePath));

			NativeSearchQuery branchQuery = new NativeSearchQueryBuilder()
					.withQuery(contentQuery)
					.withPageable(LARGE_PAGE).build();

			// Stream components into zip
			try (ExportWriter<T> writer = getExportWriter(componentClass, zipOutputStream);
					CloseableIterator<T> componentStream = elasticsearchTemplate.stream(branchQuery, componentClass)) {
				writer.writeHeader();
				componentStream.forEachRemaining(writer::write);
			}

			// Close zip entry
			zipOutputStream.closeEntry();
		} catch (IOException e) {
			throw new ExportException("Failed to write export zip entry '" + componentFilePath + "'", e);
		}
	}

	private <T> ExportWriter<T> getExportWriter(Class<T> componentClass, OutputStream outputStream) throws IOException {
		if (componentClass.equals(Concept.class)) {
			return (ExportWriter<T>) new ConceptExportWriter(getBufferedWriter(outputStream));
		}
		if (componentClass.equals(Description.class)) {
			return (ExportWriter<T>) new DescriptionExportWriter(getBufferedWriter(outputStream));
		}
		if (componentClass.equals(Relationship.class)) {
			return (ExportWriter<T>) new RelationshipExportWriter(getBufferedWriter(outputStream));
		}
		throw new UnsupportedOperationException("Not able to export component of type " + componentClass.getCanonicalName());
	}

	private BufferedWriter getBufferedWriter(OutputStream outputStream) {
		return new BufferedWriter(new OutputStreamWriter(outputStream));
	}

}
