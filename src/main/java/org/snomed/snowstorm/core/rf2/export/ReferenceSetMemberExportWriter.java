package org.snomed.snowstorm.core.rf2.export;

import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.rf2.RF2Constants;
import org.springframework.util.StringUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;

class ReferenceSetMemberExportWriter extends ExportWriter<ReferenceSetMember> {

	private final List<String> extraFieldNames;

	ReferenceSetMemberExportWriter(BufferedWriter bufferedWriter, List<String> extraFieldNames) {
		super(bufferedWriter);
		this.extraFieldNames = extraFieldNames;
	}

	@Override
	void writeHeader() throws IOException {
		String extraFields = StringUtils.collectionToDelimitedString(extraFieldNames, TAB);
		bufferedWriter.write(RF2Constants.SIMPLE_REFSET_HEADER);
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
				bufferedWriter.write(member.getEffectiveTimeI() == null ? getTransientEffectiveTime() : member.getEffectiveTimeI().toString());
				bufferedWriter.write(TAB);
				bufferedWriter.write(member.isActive() ? "1" : "0");
				bufferedWriter.write(TAB);
				bufferedWriter.write(member.getModuleId() == null ? "" : member.getModuleId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(member.getRefsetId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(member.getReferencedComponentId());

				writeAdditionalFields(member);

				writeNewLine();
			}
			componentBuffer.clear();
		} catch (IOException e) {
			throw new ExportException("Failed to write ReferenceSetMember to RF2 file.", e);
		}
	}

	private void writeAdditionalFields(ReferenceSetMember member) throws IOException {
		for (String extraField : extraFieldNames) {
			bufferedWriter.write(TAB);
			String value = member.getAdditionalField(extraField);

			if (value == null || value.isEmpty()) {
				value = "";

				if (ReferenceSetMember.MDRSFields.SOURCE_EFFECTIVE_TIME.equals(extraField) || ReferenceSetMember.MDRSFields.TARGET_EFFECTIVE_TIME.equals(extraField)) {
					value = getTransientEffectiveTime();
				}
			}

			bufferedWriter.write(value);
		}
	}
}
