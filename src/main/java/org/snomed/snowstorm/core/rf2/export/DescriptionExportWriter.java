package org.snomed.snowstorm.core.rf2.export;

import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.rf2.RF2Constants;

import java.io.BufferedWriter;
import java.io.IOException;

class DescriptionExportWriter extends ExportWriter<Description> {

	DescriptionExportWriter(BufferedWriter bufferedWriter) {
		super(bufferedWriter);
	}

	void writeHeader() throws IOException {
		bufferedWriter.write(RF2Constants.DESCRIPTION_HEADER);
		writeNewLine();
	}

	void flush() {
		try {
			for (Description description : componentBuffer) {
				bufferedWriter.write(description.getDescriptionId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(description.getEffectiveTimeI() != null ? description.getEffectiveTimeI().toString() : getTransientEffectiveTime());
				bufferedWriter.write(TAB);
				bufferedWriter.write(description.isActive() ? "1" : "0");
				bufferedWriter.write(TAB);
				bufferedWriter.write(description.getModuleId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(description.getConceptId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(description.getLanguageCode());
				bufferedWriter.write(TAB);
				bufferedWriter.write(description.getTypeId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(description.getTerm());
				bufferedWriter.write(TAB);
				bufferedWriter.write(description.getCaseSignificanceId());
				writeNewLine();
			}
			componentBuffer.clear();
		} catch (IOException e) {
			throw new ExportException("Failed to write Description to RF2 file.", e);
		}
	}

}
