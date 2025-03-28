package org.snomed.snowstorm.syndication.hl7;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.fhir.services.FHIRLoadPackageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import static org.snomed.snowstorm.core.util.FileUtils.findFile;

@Service
public class Hl7SyndicationService {

    @Autowired
    private FHIRLoadPackageService loadPackageService;

    @Value("${syndication.hl7.working-directory}")
    private String workingDirectory;

    private final Logger logger = LoggerFactory.getLogger(getClass());


    public void importHl7Terminology() throws IOException {
        File file = findFile(workingDirectory, "hl7.terminology.*.tgz");
        if (file == null) {
            throw new RuntimeException("FHIR package not found!");
        }

        logger.info("Importing HL7 Terminology from: " + file.getName());
        loadPackageService.uploadPackageResources(file, Set.of("*"), file.getName(), false);
    }
}
