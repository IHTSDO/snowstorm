package org.snomed.snowstorm.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class FileUtils {

    public static File findFile(String directory, String filePattern) throws IOException {
        return getPath(directory, filePattern).map(Path::toFile).orElse(null);
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
}
