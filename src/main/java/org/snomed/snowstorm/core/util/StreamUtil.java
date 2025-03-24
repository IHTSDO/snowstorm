package org.snomed.snowstorm.core.util;

import org.snomed.snowstorm.core.data.domain.ConceptMini;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.BinaryOperator;

public class StreamUtil {
	public static final BinaryOperator<ConceptMini> MERGE_FUNCTION = (u, v) -> {
		throw new IllegalStateException(String.format("Duplicate key %s", u.getConceptId()));
	};

	public static void copyWithProgress(InputStream inputStream, OutputStream outputStream, int totalStreamLength, String messageFormat) throws IOException {
		int byteCount = 0;
		int bytesRead;
		int percentageLogged = -1;
		if(totalStreamLength <= 0) {
			throw new IllegalArgumentException("Invalid length: " + totalStreamLength);
		}
		for (byte[] buffer = new byte[4096]; (bytesRead = inputStream.read(buffer)) != -1; byteCount += bytesRead) {
			outputStream.write(buffer, 0, bytesRead);
			float percentageFloat = ((float) byteCount / (float) totalStreamLength) * 100;
			int percentage = (int) Math.floor(percentageFloat);
			if (percentage % 10 == 0 && percentage > percentageLogged) {
				System.out.printf(messageFormat + "%n", percentage);
				percentageLogged = percentage;
			}
		}
		outputStream.flush();
	}
}
