package org.snomed.snowstorm.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamUtils {

	private static final Logger logger = LoggerFactory.getLogger(StreamUtils.class);

	public static void copy(InputStream inputStream, OutputStream outputStream, boolean closeInputStream, boolean closeOutputStream) throws IOException {
		byte[] bytes = new byte[1024];
		try {
			int read;
			while ((read = inputStream.read(bytes)) > 0) {
				outputStream.write(bytes, 0, read);
			}
			outputStream.flush();
		} finally {
			if (closeInputStream) {
				try {
					inputStream.close();
				} catch (IOException e) {
					logger.error("Failed to close inputStream.", e);
				}
			}
			if (closeOutputStream) {
				try {
					outputStream.close();
				} catch (IOException e) {
					logger.error("Failed to close outputStream.", e);
				}
			}
		}
	}

}
