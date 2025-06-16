package org.snomed.snowstorm.syndication.services.importers.customversion.icd10;

import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.syndication.models.domain.SyndicationImportParams;
import org.snomed.snowstorm.syndication.services.importers.SyndicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.singletonList;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.ICD10_TERMINOLOGY;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.LATEST_VERSION;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.LOCAL_VERSION;
import static org.snomed.snowstorm.syndication.utils.CommandUtils.waitForProcessTermination;
import static org.snomed.snowstorm.syndication.utils.FileUtils.findFile;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service(ICD10_TERMINOLOGY)
public class Icd10SyndicationService extends SyndicationService {

    @Value("${syndication.icd10.working-directory}")
    private String workingDirectory;

    @Value("${syndication.icd10.fileNamePattern}")
    private String fileNamePattern;

    private static final String ICD10_INDEX_URL = "https://icdcdn.who.int/icd10/index.html";

    @Override
    protected List<File> fetchTerminologyPackages(SyndicationImportParams params) throws IOException, InterruptedException, ServiceException {
        Optional<File> file = LOCAL_VERSION.equals(params.version())
                ? findFile(workingDirectory, fileNamePattern)
                : downloadIcd10Zip(params.version());
        return singletonList(file.orElseThrow(() -> new ServiceException("Icd10 terminology file not found, cannot be imported")));
    }

    @Override
    protected void importTerminology(SyndicationImportParams params, List<File> files) throws IOException, InterruptedException {
        String fileName = files.get(0).getName();
        Process process = new ProcessBuilder(
                "../../hapi/hapi-fhir-cli", "upload-terminology",
                "-d", fileName,
                "-v", "r4",
                "-t", "http://localhost:8080/fhir",
                "-u", "http://hl7.org/fhir/sid/icd-10")
                .directory(new File(workingDirectory))
                .start();

        waitForProcessTermination(process, "Import ICD-10 terminology");
    }

    private Optional<File> downloadIcd10Zip(String version) throws IOException, InterruptedException {
        version = LATEST_VERSION.equals(version) ? getLatestTerminologyVersion(null) : version;
        String url = "https://icdcdn.who.int/icd10/claml/icd10" + version + "en.xml.zip";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            logger.error("Failed to download ICD-10 ZIP file: HTTP {}", response.statusCode());
            return Optional.empty();
        }

        File tempFile = Files.createTempFile(Paths.get(workingDirectory), version + "-temp", ".xml.zip").toFile();
        try (InputStream in = response.body();
             FileOutputStream out = new FileOutputStream(tempFile)) {
            in.transferTo(out);
        }

        logger.info("Downloaded icd10 to: " + tempFile.getAbsolutePath());
        return Optional.of(tempFile);
    }

    @Override
    protected String getTerminologyVersion(String releaseFileName) {
        return releaseFileName.split("-temp")[0];
    }

    @Override
    protected String getLatestTerminologyVersion(String params) throws IOException {
        Document doc = Jsoup.connect(ICD10_INDEX_URL).get();

        Elements links = doc.select("a[href]");
        TreeSet<String> versions = new TreeSet<>();

        for (Element link : links) {
            String href = link.attr("href").trim();
            if (href.matches("claml/icd10\\d{4}en.xml.zip")) {
                String version = extractIcd10Version(href);
                if(version != null) {
                    versions.add(version);
                }
            }
        }

        if (versions.isEmpty()) {
            throw new IOException("No ICD-10 versions found on the page.");
        }

        return versions.last(); // Latest version lexicographically
    }

    private static String extractIcd10Version(String input) {
        Matcher matcher = Pattern.compile("icd10(\\d{4})en").matcher(input);
        return matcher.find() ? matcher.group(1) : null;
    }
}
