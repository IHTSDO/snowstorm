package org.snomed.snowstorm.syndication.loinc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Optional;

import static org.snomed.snowstorm.core.util.FileUtils.findFileName;

@Service
public class LoincSyndicationService {

    @Value("${syndication.loinc.working-directory}")
    private String workingDirectory;

    @Value("${syndication.loinc.fileNamePattern}")
    private String fileNamePattern;

    private final Logger logger = LoggerFactory.getLogger(getClass());


    public void importLoincTerminology() throws IOException, InterruptedException, ServiceException {
        Optional<String> fileName = findFileName(workingDirectory, fileNamePattern);
        if(fileName.isEmpty()) {
            fileName = downloadLoincZip();
        }
        importLoincZip(fileName);
    }

    private Optional<String> downloadLoincZip() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("node", "./download_loinc.mjs")
                .directory(new File(workingDirectory))
                .start();

        waitForProcessTermination(process, "Download LOINC process");
        return findFileName(workingDirectory, fileNamePattern);
    }

    private void importLoincZip(Optional<String> fileName) throws IOException, InterruptedException, ServiceException {
        if(fileName.isEmpty()) {
            throw new ServiceException("Unable to fetch the LOINC zip file");
        }
        Process process = new ProcessBuilder(
                "./hapi-fhir-cli", "upload-terminology",
                "-d", fileName.get(),
                "-v", "r4",
                "-t", "http://localhost:8080/fhir",
                "-u", "http://loinc.org")
                .directory(new File(workingDirectory))
                .start();

        waitForProcessTermination(process, "Import LOINC process");
    }

    private void waitForProcessTermination(Process process, String processName) throws IOException, InterruptedException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            logger.info(line);
        }
        System.out.println(processName + " exited with code: " +  + process.waitFor());
    }
}
