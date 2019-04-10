package org.snomed.snowstorm.core.rf2.export;

import org.snomed.snowstorm.core.data.domain.Concept;

import java.io.BufferedWriter;
import java.io.IOException;

class ConceptExportWriter extends ExportWriter<Concept> {

	static final String HEADER = "id\teffectiveTime\tactive\tmoduleId\tdefinitionStatusId";

	ConceptExportWriter(BufferedWriter bufferedWriter) {
		super(bufferedWriter);
	}

	void writeHeader() throws IOException {
		bufferedWriter.write(HEADER);
		writeNewLine();
	}

	void flush() {
		try {
			for (Concept concept : componentBuffer) {
				bufferedWriter.write(concept.getConceptId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(concept.getEffectiveTimeI() != null ? concept.getEffectiveTimeI().toString() : "");
				bufferedWriter.write(TAB);
				bufferedWriter.write(concept.isActive() ? "1" : "0");
				bufferedWriter.write(TAB);
				bufferedWriter.write(concept.getModuleId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(concept.getDefinitionStatusId());
				writeNewLine();
			}
			componentBuffer.clear();
		} catch (IOException e) {
			throw new ExportException("Failed to write Concept to RF2 file.", e);
		}
	}

}
