package org.ihtsdo.elasticsnomed.core.rf2.export;

import org.ihtsdo.elasticsnomed.core.data.domain.Concept;

import java.io.BufferedWriter;
import java.io.IOException;

class ConceptExportWriter extends ExportWriter<Concept> {

	static final String HEADER = "id\teffectiveTime\tactive\tmoduleId\tdefinitionStatusId";

	ConceptExportWriter(BufferedWriter bufferedWriter) {
		super(bufferedWriter);
	}

	void writeHeader() throws IOException {
		bufferedWriter.write(HEADER);
		bufferedWriter.newLine();
	}

	void flush() {
		try {
			for (Concept concept : componentBuffer) {
				bufferedWriter.write(concept.getConceptId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(concept.getEffectiveTime() != null ? concept.getEffectiveTime() : "");
				bufferedWriter.write(TAB);
				bufferedWriter.write(concept.isActive() ? "1" : "0");
				bufferedWriter.write(TAB);
				bufferedWriter.write(concept.getModuleId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(concept.getDefinitionStatusId());
				bufferedWriter.newLine();
			}
			componentBuffer.clear();
		} catch (IOException e) {
			throw new ExportException("Failed to write Concept to RF2 file.", e);
		}
	}

}
