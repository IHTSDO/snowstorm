package org.snomed.snowstorm.syndication.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FileUtils {

    private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);

    public static Optional<File> findFile(String directory, String filePattern) throws IOException {
        return getPath(directory, filePattern).map(Path::toFile);
    }

    public static List<File> findFiles(String directory, String filePattern) throws IOException {
        Path dirPath = Paths.get(directory);
        List<File> result = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, filePattern)) {
            for (Path entry : stream) {
                result.add(entry.toFile());
            }
        }

        return result;
    }

    public static Optional<String> findFileName(String directory, String filePattern) throws IOException {
        return getPath(directory, filePattern).map(Path::getFileName).map(Path::toString);
    }

    private static Optional<Path> getPath(String directory, String filePattern) throws IOException {
        Path dirPath = Paths.get(directory);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, filePattern)) {
            for (Path entry : stream) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public static void removeFiles(List<File> files) {
        for (File file : files) {
            if (!file.delete()) {
                logger.warn("Failed to delete file {}", file.getAbsolutePath());
            }
        }
    }
}
