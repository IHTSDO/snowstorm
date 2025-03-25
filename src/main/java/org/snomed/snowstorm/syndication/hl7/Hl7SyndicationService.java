package org.snomed.snowstorm.syndication.hl7;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.fhir.services.FHIRLoadPackageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@Service
public class Hl7SyndicationService {

    @Autowired
    private FHIRLoadPackageService loadPackageService;

    @Value("${syndication.working-directory}")
    private String workingDirectory;

    private final Logger logger = LoggerFactory.getLogger(getClass());


    public void importHl7Terminology() throws IOException {
        File file = findHl7TerminologyFile();
        if (file == null) {
            throw new RuntimeException("FHIR package not found!");
        }

        logger.info("Importing HL7 Terminology from: " + file.getName());
        loadPackageService.uploadPackageResources(file, Set.of("*"), file.getName(), false);
    }

    File findHl7TerminologyFile() throws IOException {
        Path dirPath = Paths.get(workingDirectory);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, "hl7.terminology.*.tgz")) {
            for (Path entry : stream) {
                return entry.toFile();
            }
        }
        return null;
    }
}
