package org.snomed.snowstorm.syndication.loinc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import static org.snomed.snowstorm.core.util.FileUtils.findFileName;

@Service
public class LoincSyndicationService {

    @Value("${syndication.loinc.working-directory}")
    private String workingDirectory;

    private final Logger logger = LoggerFactory.getLogger(getClass());


    public void importLoincTerminology() throws IOException, InterruptedException {
        String fileName = findFileName(workingDirectory, "Loinc*.zip");
        Process process = new ProcessBuilder(
                "./hapi-fhir-cli", "upload-terminology",
                "-d", fileName,
                "-v", "r4",
                "-t", "http://localhost:8080/fhir",
                "-u", "http://loinc.org")
                .directory(new File(workingDirectory))
                .start();


        // Read the output (optional)
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            logger.info(line);
        }

        // Wait for the process to complete
        int exitCode = process.waitFor();
        System.out.println("Process exited with code: " + exitCode);

    }
}
