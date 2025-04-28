package org.snomed.snowstorm.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.snomed.snowstorm.syndication.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FileUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void testFindFile_Success() throws IOException {
        Path hl7File = tempDir.resolve("hl7.terminology.r4-6.2.0.tgz");
        Files.createFile(hl7File);

        Optional<File> foundFile = FileUtils.findFile(tempDir.toString(), "hl7.terminology.*.tgz");
        assertTrue(foundFile.isPresent());
        assertEquals("hl7.terminology.r4-6.2.0.tgz", foundFile.get().getName());
    }

    @Test
    void testFindFile_NoMatch() throws IOException {
        Optional<File> foundFile = FileUtils.findFile(tempDir.toString(), "hl7.terminology.*.tgz");
        assertTrue(foundFile.isEmpty());
    }

    @Test
    void testFindFileName_Success() throws IOException {
        Path hl7File = tempDir.resolve("hl7.terminology.r4-6.2.0.tgz");
        Files.createFile(hl7File);

        Optional<String> foundFileName = FileUtils.findFileName(tempDir.toString(), "hl7.terminology.*.tgz");
        assertTrue(foundFileName.isPresent());
        assertEquals("hl7.terminology.r4-6.2.0.tgz", foundFileName.get());
    }

    @Test
    void testFindFileName_NoMatch() throws IOException {
        Optional<String> foundFileName = FileUtils.findFileName(tempDir.toString(), "hl7.terminology.*.tgz");
        assertFalse(foundFileName.isPresent());
    }
}