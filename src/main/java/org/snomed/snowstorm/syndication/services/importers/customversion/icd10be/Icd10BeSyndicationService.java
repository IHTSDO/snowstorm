package org.snomed.snowstorm.syndication.services.importers.customversion.icd10be;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hl7.fhir.r4.model.CodeSystem;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.syndication.models.domain.SyndicationImportParams;
import org.snomed.snowstorm.syndication.services.importers.SyndicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static java.util.Collections.singletonList;
import static org.snomed.snowstorm.fhir.services.FHIRHelper.DEFAULT_VERSION;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.ICD10_BE_TERMINOLOGY;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.LATEST_VERSION;
import static org.snomed.snowstorm.syndication.constants.SyndicationConstants.LOCAL_VERSION;
import static org.snomed.snowstorm.syndication.utils.FileUtils.findFile;

@Service(ICD10_BE_TERMINOLOGY)
public class Icd10BeSyndicationService extends SyndicationService {

    public static final String FIRST_PROCEDURECODE = "001";
    @Value("${syndication.icd10be.working-directory}")
    private String workingDirectory;

    @Value("${syndication.icd10be.fileNamePattern}")
    private String fileNamePattern;

    @Value("${syndication.icd10be.index_url}")
    private String icd10beIndexUrl;

    @Override
    protected List<File> fetchTerminologyPackages(SyndicationImportParams params) throws IOException, InterruptedException, ServiceException {
        Optional<File> file = LOCAL_VERSION.equals(params.version())
                ? findFile(workingDirectory, fileNamePattern)
                : downloadIcd10Xlsx(params.version());
        return singletonList(file.orElseThrow(() -> new ServiceException("Icd10 BE terminology file not found, cannot be imported")));
    }

    private Optional<File> downloadIcd10Xlsx(String version) throws IOException, InterruptedException, ServiceException {
        String targetVersion = LATEST_VERSION.equals(version) ? getLatestTerminologyVersion(null) : version;
        String url = getDownloadUrl(targetVersion);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            logger.error("Failed to download ICD-10 BE xlsx file: HTTP {}", response.statusCode());
            return Optional.empty();
        }

        File tempFile = Files.createTempFile(Paths.get(workingDirectory), targetVersion + "-temp", "xlsx").toFile();
        try (InputStream in = response.body();
             FileOutputStream out = new FileOutputStream(tempFile)) {
            in.transferTo(out);
        }

        logger.info("Downloaded icd10 BE to: {}", tempFile.getAbsolutePath());
        return Optional.of(tempFile);
    }

    private String getDownloadUrl(String version) throws IOException, ServiceException {
        Document doc = Jsoup.connect(icd10beIndexUrl).get();

        Elements links = doc.select("a[href]");
        String versionRegex = ".+fy" + version + ".+xlsx$";

        for (Element link : links) {
            String href = link.attr("href").trim();
            if (href.matches(versionRegex)) {
                return href;
            }
        }
        throw new ServiceException("ICD10-BE xlsx file URL version " + version + " not found");
    }

    @Override
    protected void importTerminology(SyndicationImportParams params, List<File> files) throws IOException, InterruptedException, ServiceException {
        var concepts = readConceptsFromFile(files.get(0));
        var cmConcepts = concepts.get(0);
        CodeSystem cmCodeSystem = new CodeSystem();
        cmCodeSystem.setUrl("http://hl7.org/fhir/sid/icd-10-cm");
        cmCodeSystem.setName("International Classification of Diseases, 10th Revision, Clinical Modification (ICD-10-CM)");
        cmCodeSystem.setVersion(DEFAULT_VERSION);
        cmCodeSystem.setConcept(cmConcepts);
        saveCodeSystemAndConcepts(cmCodeSystem);

        var procedureCodeConcepts = concepts.get(1);
        CodeSystem procedureCodeSystem = new CodeSystem();
        procedureCodeSystem.setUrl("http://www.cms.gov/Medicare/Coding/ICD10");
        procedureCodeSystem.setName("ICD-10 Procedure Codes");
        procedureCodeSystem.setVersion(DEFAULT_VERSION);
        procedureCodeSystem.setConcept(procedureCodeConcepts);
        saveCodeSystemAndConcepts(procedureCodeSystem);
    }

    private List<List<CodeSystem.ConceptDefinitionComponent>> readConceptsFromFile(File file) throws ServiceException {
        Map<String, CodeSystem.ConceptDefinitionComponent> cmConcepts = new HashMap<>();
        Map<String, CodeSystem.ConceptDefinitionComponent> procedureCodeConcepts = new HashMap<>();

        boolean isClinicalModification = true;

        IOUtils.setByteArrayMaxOverride(200 * 1024 * 1024); // increased from 100 to 200 MB

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(1);
            Iterator<Row> rowIterator = sheet.iterator();

            int codeIdx = -1, frIdx = -1, nlIdx = -1, enIdx = -1;

            if (rowIterator.hasNext()) {
                Row header = rowIterator.next();
                for (Cell cell : header) {
                    String value = cell.getStringCellValue().trim();
                    switch (value) {
                        case "ICDCODE" -> codeIdx = cell.getColumnIndex();
                        case "ICDTXTFR" -> frIdx = cell.getColumnIndex();
                        case "ICDTXTNL" -> nlIdx = cell.getColumnIndex();
                        case "ICDTXTEN" -> enIdx = cell.getColumnIndex();
                    }
                }
            }

            if (codeIdx == -1 || frIdx == -1 || nlIdx == -1 || enIdx == -1) {
                throw new IllegalStateException("One or more required columns not found.");
            }

            logger.info("Importing ICD10 BE codes from {}", file.getAbsolutePath());

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                String code = getString(row.getCell(codeIdx));
                String fr = getString(row.getCell(frIdx));
                String nl = getString(row.getCell(nlIdx));
                String en = getString(row.getCell(enIdx));

                if (code == null || code.isBlank()) continue;

                if(FIRST_PROCEDURECODE.equals(code)) {
                    isClinicalModification = false;
                }

                if(isClinicalModification) {
                    if(code.length() > 3 && code.charAt(3) != '.') {
                        code = code.substring(0, 3) + "." + code.substring(3);
                    }
                    cmConcepts.put(code, makeConcept(code, en, fr, nl));
                } else {
                    procedureCodeConcepts.put(code, makeConcept(code, en, fr, nl));
                }
            }

        } catch (IOException e) {
            throw new ServiceException("Failed to read Excel file", e);
        }

        return List.of(cmConcepts.values().stream().toList(), procedureCodeConcepts.values().stream().toList());
    }

    private CodeSystem.ConceptDefinitionComponent makeConcept(String code, String en, String fr, String nl) {
        CodeSystem.ConceptDefinitionComponent concept = new CodeSystem.ConceptDefinitionComponent();
        concept.setCode(code);
        concept.setDisplay(en);

        List<CodeSystem.ConceptDefinitionDesignationComponent> designations = new ArrayList<>();

        if (fr != null && !fr.isBlank()) {
            designations.add(makeDesignation("fr", fr));
        }
        if (nl != null && !nl.isBlank()) {
            designations.add(makeDesignation("nl", nl));
        }
        if (en != null && !en.isBlank()) {
            designations.add(makeDesignation("en", en));
        }

        concept.setDesignation(designations);
        return concept;
    }

    private String getString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue());
            default -> null;
        };
    }

    private CodeSystem.ConceptDefinitionDesignationComponent makeDesignation(String lang, String value) {
        return new CodeSystem.ConceptDefinitionDesignationComponent()
                .setLanguage(lang)
                .setValue(value);
    }

    @Override
    protected String getTerminologyVersion(String releaseFileName) {
        return releaseFileName.split("-temp")[0];
    }

    @Override
    protected String getLatestTerminologyVersion(String params) throws IOException {
        Document doc = Jsoup.connect(icd10beIndexUrl).get();

        Elements links = doc.select("a[href]");
        TreeSet<String> versions = new TreeSet<>();

        for (Element link : links) {
            String href = link.attr("href").trim();
            if (href.matches(".+fy\\d{4}.+xlsx$")) {
                String version = extractVersion(href);
                if(version != null) {
                    versions.add(version);
                }
            }
        }

        if (versions.isEmpty()) {
            throw new IOException("No ICD-10 BE versions found on the page.");
        }

        return versions.last(); // Latest version lexicographically
    }

    private static String extractVersion(String input) {
        Matcher matcher = Pattern.compile(".+fy(\\d{4}).+xlsx$").matcher(input);
        return matcher.find() ? matcher.group(1) : null;
    }
}
