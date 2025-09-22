package org.snomed.snowstorm.syndication.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class CommandUtils {

    private static final Logger logger = LoggerFactory.getLogger(CommandUtils.class);

    public static void waitForProcessTermination(Process process, String processName) throws InterruptedException {
        logger.info("Starting process {}", processName);

        Thread stdoutReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[STDOUT] {}", line);
                }
            } catch (IOException e) {
                logger.error("Error reading stdout", e);
            }
        });

        Thread stderrReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                terminateProcessAfterFirstStacktrace(process, reader);
            } catch (IOException | InterruptedException e) {
                logger.error("Error reading stderr", e);
            }
        });

        stdoutReader.start();
        stderrReader.start();

        int exitCode = process.waitFor();

        stdoutReader.join();
        stderrReader.join();

        logger.info("Process {} exited with code: {}", processName, exitCode);
        if(exitCode != 0) {
            throw new RuntimeException("Process " + processName + " exited with code " + exitCode);
        }
    }

    public static String getSingleLineCommandResult(String command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command("bash", "-c", command);

        Process process = builder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String version = reader.readLine();

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code " + exitCode);
        }

        return version;
    }

    private static void terminateProcessAfterFirstStacktrace(Process process, BufferedReader reader) throws IOException, InterruptedException {
        String line;
        if ((line = reader.readLine()) != null) {
            logger.error("[STDERR] {}", line);
            Thread.sleep(2000);
            while (reader.ready()) {
                logger.error("[STDERR] {}", reader.readLine());
                Thread.sleep(200);
            }
            process.destroyForcibly();
            logger.error("Process terminated due to error output");
        }
    }
}
