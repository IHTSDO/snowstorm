package org.snomed.snowstorm.syndication.services.importers.fixedversion.icpc2;

import org.hl7.fhir.r4.model.CodeSystem;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.syndication.models.domain.SyndicationImportParams;
import org.snomed.snowstorm.syndication.services.importers.fixedversion.FixedVersionSyndicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.util.Collections.singletonList;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.ICPC2_TERMINOLOGY;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.LOCAL_VERSION;
import static org.snomed.snowstorm.syndication.utils.FileUtils.findFile;

@Service(ICPC2_TERMINOLOGY)
public class Icpc2SyndicationService extends FixedVersionSyndicationService {

    @Value("${syndication.icpc2.zipFileUrl}")
    private String zipFileUrl;

    @Value("${syndication.icpc2.working-directory}")
    private String workingDirectory;

    @Value("${syndication.icpc2.fileName}")
    private String fileName;

    private static final String COMMENT_PREFIX = "--";
    private static final String SEPARATOR_LINE = "-----------------------------------------------------------------------------------------------------------";

    @Override
    protected List<File> fetchTerminologyPackages(SyndicationImportParams params) throws IOException, ServiceException {
        Optional<File> file = LOCAL_VERSION.equals(params.version())
                ? findFile(workingDirectory, codeSystemFilePattern)
                : downloadIcpc2();
        return singletonList(file.orElseThrow(() -> new ServiceException("Icpc2 terminology file not found, cannot be imported")));
    }

    private Optional<File> downloadIcpc2() throws ServiceException {
        try {
            try (ZipInputStream zipInputStream = new ZipInputStream(new URL(zipFileUrl).openStream())) {
                ZipEntry entry;
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    if (fileName.equals(entry.getName())) {

                        File tempFile = Files.createTempFile(Paths.get(workingDirectory), "icpc2-temp", "-codesystem.txt").toFile();

                        try (FileOutputStream out = new FileOutputStream(tempFile)) {
                            byte[] buffer = new byte[4096];
                            int len;
                            while ((len = zipInputStream.read(buffer)) > 0) {
                                out.write(buffer, 0, len);
                            }
                        }

                        return Optional.of(tempFile);
                    }
                }
            }
        } catch (IOException e) {
            throw new ServiceException("Failed to download Icpc2 terminology file", e);
        }
        return Optional.empty();
    }

    @Override
    protected void importTerminology(SyndicationImportParams params, List<File> files) throws IOException, ServiceException {
        CodeSystem codeSystem = new CodeSystem();
        codeSystem.setUrl("http://hl7.org/fhir/sid/icpc-2");
        codeSystem.setName("ICPC2-english-edition");
        codeSystem.setVersion(DEFAULT_VERSION);
        codeSystem.setConcept(readConceptsFromFile(files.get(0)));
        saveCodeSystemAndConcepts(codeSystem);
    }

    private static List<CodeSystem.ConceptDefinitionComponent> readConceptsFromFile(File codeSystem) throws ServiceException {
        List<CodeSystem.ConceptDefinitionComponent> concepts = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(codeSystem), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty() || line.startsWith(COMMENT_PREFIX) || line.equals(SEPARATOR_LINE)) {
                    continue;
                }

                String[] parts = line.split(";", -1);
                if (parts.length < 2) continue;

                CodeSystem.ConceptDefinitionComponent concept = new CodeSystem.ConceptDefinitionComponent();
                concept.setCode(stripQuotes(parts[0]));
                concept.setDisplay(stripQuotes(parts[1]));
                concepts.add(concept);
            }
        } catch (IOException e) {
            throw new ServiceException("Failed to read ICPC-2 concepts from file", e);
        }

        return concepts;
    }

    private static String stripQuotes(String s) {
        if (s == null) return "";
        return s.replaceAll("^\"|\"$", "");
    }

    @Override
    protected String getCodeSystemName() {
        return ICPC2_TERMINOLOGY;
    }
}
