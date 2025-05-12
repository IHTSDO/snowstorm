package org.snomed.snowstorm.core.rf2.export;

import org.snomed.snowstorm.core.data.domain.Identifier;
import org.snomed.snowstorm.core.rf2.RF2Constants;

import java.io.BufferedWriter;
import java.io.IOException;

class IdentifierExportWriter extends ExportWriter<Identifier> {

	IdentifierExportWriter(BufferedWriter bufferedWriter) {
		super(bufferedWriter);
	}

	void writeHeader() throws IOException {
		bufferedWriter.write(RF2Constants.IDENTIFIER_HEADER);
		writeNewLine();
	}

	void flush() {
		try {
			for (Identifier identifier : componentBuffer) {
				bufferedWriter.write(identifier.getAlternateIdentifier());
				bufferedWriter.write(TAB);
				bufferedWriter.write(identifier.getEffectiveTimeI() != null ? identifier.getEffectiveTimeI().toString() : getTransientEffectiveTime());
				bufferedWriter.write(TAB);
				bufferedWriter.write(identifier.isActive() ? "1" : "0");
				bufferedWriter.write(TAB);
				bufferedWriter.write(identifier.getModuleId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(identifier.getIdentifierSchemaId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(identifier.getReferencedComponentId());
				writeNewLine();
			}
			componentBuffer.clear();
		} catch (IOException e) {
			throw new ExportException("Failed to write Identifier to RF2 file.", e);
		}
	}

}
