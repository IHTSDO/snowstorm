package org.snomed.snowstorm.core.rf2.export;

import org.elasticsearch.common.Strings;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;

class ReferenceSetMemberExportWriter extends ExportWriter<ReferenceSetMember> {

	static final String HEADER = "id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId";

	private final List<String> extraFieldNames;

	ReferenceSetMemberExportWriter(BufferedWriter bufferedWriter, List<String> extraFieldNames) {
		super(bufferedWriter);
		this.extraFieldNames = extraFieldNames;
	}

	@Override
	void writeHeader() throws IOException {
		String extraFields = Strings.collectionToDelimitedString(extraFieldNames, TAB);
		bufferedWriter.write(HEADER);
		if (!extraFields.isEmpty()) {
			bufferedWriter.write(TAB);
			bufferedWriter.write(extraFields);
		}
		writeNewLine();
	}

	@Override
	void flush() {
		try {
			for (ReferenceSetMember member : componentBuffer) {
				bufferedWriter.write(member.getMemberId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(member.getEffectiveTimeI() != null ? member.getEffectiveTimeI().toString() : "");
				bufferedWriter.write(TAB);
				bufferedWriter.write(member.isActive() ? "1" : "0");
				bufferedWriter.write(TAB);
				bufferedWriter.write(member.getModuleId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(member.getRefsetId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(member.getReferencedComponentId());

				for (String extraField : extraFieldNames) {
					bufferedWriter.write(TAB);
					String value = member.getAdditionalField(extraField);
					if (value == null) {
						throw new IllegalStateException(String.format("Additional field '%s' value is null for member %s", extraField, member.getMemberId()));
					}
					bufferedWriter.write(value);
				}
				writeNewLine();
			}
			componentBuffer.clear();
		} catch (IOException e) {
			throw new ExportException("Failed to write ReferenceSetMember to RF2 file.", e);
		}
	}
}
