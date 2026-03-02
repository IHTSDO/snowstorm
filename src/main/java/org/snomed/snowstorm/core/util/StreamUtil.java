package org.snomed.snowstorm.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.ConceptMini;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.BinaryOperator;

public class StreamUtil {

	private static final Logger logger = LoggerFactory.getLogger(StreamUtil.class);

	private StreamUtil() {
	}

	public static final BinaryOperator<ConceptMini> MERGE_FUNCTION = (u, v) -> {
		throw new IllegalStateException(String.format("Duplicate key %s", u.getConceptId()));
	};

	public static int copyWithProgress(InputStream inputStream, OutputStream outputStream, int totalStreamLength, String messageFormat) throws IOException {
		int byteCount = 0;
		int bytesRead;
		int percentageLogged = -1;
		for (byte[] buffer = new byte[4096]; (bytesRead = inputStream.read(buffer)) != -1; byteCount += bytesRead) {
			outputStream.write(buffer, 0, bytesRead);

			float percentageFloat = ((float) byteCount / (float) totalStreamLength) * 100;
			int percentage = (int) Math.floor(percentageFloat);
			if (percentage % 10 == 0 && percentage > percentageLogged) {
				if (logger.isInfoEnabled()) {
					logger.info(String.format(messageFormat, percentage));
				}
				percentageLogged = percentage;
			}
		}

		outputStream.flush();
		return byteCount;
	}
}
