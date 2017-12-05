package org.snomed.snowstorm.core.rf2.export;

import org.snomed.snowstorm.core.data.domain.Relationship;

import java.io.BufferedWriter;
import java.io.IOException;

class RelationshipExportWriter extends ExportWriter<Relationship> {

	static final String HEADER = "id\teffectiveTime\tactive\tmoduleId\tsourceId\tdestinationId\trelationshipGroup\ttypeId\tcharacteristicTypeId\tmodifierId";

	RelationshipExportWriter(BufferedWriter bufferedWriter) {
		super(bufferedWriter);
	}

	@Override
	void writeHeader() throws IOException {
		bufferedWriter.write(HEADER);
		bufferedWriter.newLine();
	}

	@Override
	void flush() {
		try {
			for (Relationship relationship : componentBuffer) {
				bufferedWriter.write(relationship.getRelationshipId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(relationship.getEffectiveTime() != null ? relationship.getEffectiveTime() : "");
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
				bufferedWriter.newLine();
			}
			componentBuffer.clear();
		} catch (IOException e) {
			throw new ExportException("Failed to write Relationship to RF2 file.", e);
		}
	}

}
