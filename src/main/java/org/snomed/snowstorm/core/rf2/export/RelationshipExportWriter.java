package org.snomed.snowstorm.core.rf2.export;

import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.rf2.RF2Constants;

import java.io.BufferedWriter;
import java.io.IOException;

class RelationshipExportWriter extends ExportWriter<Relationship> {

	RelationshipExportWriter(BufferedWriter bufferedWriter) {
		super(bufferedWriter);
	}

	@Override
	void writeHeader() throws IOException {
		bufferedWriter.write(RF2Constants.RELATIONSHIP_HEADER);
		writeNewLine();
	}

	@Override
	void flush() {
		try {
			for (Relationship relationship : componentBuffer) {
				bufferedWriter.write(relationship.getRelationshipId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(relationship.getEffectiveTimeI() != null ? relationship.getEffectiveTimeI().toString() : getTransientEffectiveTime());
				bufferedWriter.write(TAB);
				bufferedWriter.write(relationship.isActive() ? "1" : "0");
				bufferedWriter.write(TAB);
				bufferedWriter.write(relationship.getModuleId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(relationship.getSourceId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(relationship.getDestinationId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(relationship.getRelationshipGroup() + "");
				bufferedWriter.write(TAB);
				bufferedWriter.write(relationship.getTypeId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(relationship.getCharacteristicTypeId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(relationship.getModifierId());
				writeNewLine();
			}
			componentBuffer.clear();
		} catch (IOException e) {
			throw new ExportException("Failed to write Relationship to RF2 file.", e);
		}
	}

}
