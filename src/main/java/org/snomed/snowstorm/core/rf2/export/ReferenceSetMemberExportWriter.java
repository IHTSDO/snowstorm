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
		bufferedWriter.write(HEADER + Strings.collectionToDelimitedString(extraFieldNames, TAB, TAB, ""));
		bufferedWriter.newLine();
	}

	@Override
	void flush() {
		try {
			for (ReferenceSetMember member : componentBuffer) {
				bufferedWriter.write(member.getMemberId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(member.getEffectiveTime() != null ? member.getEffectiveTime() : "");
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
				bufferedWriter.newLine();
			}
			componentBuffer.clear();
		} catch (IOException e) {
			throw new ExportException("Failed to write ReferenceSetMember to RF2 file.", e);
		}
	}
}
