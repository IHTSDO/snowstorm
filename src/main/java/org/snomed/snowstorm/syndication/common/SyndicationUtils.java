package org.snomed.snowstorm.syndication.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class SyndicationUtils {

    private static final Logger logger = LoggerFactory.getLogger(SyndicationUtils.class);

    public static void waitForProcessTermination(Process process, String processName) throws InterruptedException {
        logger.info("Starting process {}", processName);

        Thread stdoutReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info(line);
                }
            } catch (IOException e) {
                logger.error("Error reading stdout", e);
            }
        });

        Thread stderrReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.error(line);
                }
            } catch (IOException e) {
                logger.error("Error reading stderr", e);
            }
        });

        stdoutReader.start();
        stderrReader.start();

        int exitCode = process.waitFor();

        stdoutReader.join();
        stderrReader.join();

        logger.info("Process {} exited with code: {}", processName, exitCode);
    }
}
