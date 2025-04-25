package org.snomed.snowstorm.syndication.services.importers.customversion.snomed;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.util.StreamUtil;
import org.snomed.snowstorm.syndication.models.integration.SyndicationDependency;
import org.snomed.snowstorm.syndication.models.integration.SyndicationFeed;
import org.snomed.snowstorm.syndication.models.integration.SyndicationFeedEntry;
import org.snomed.snowstorm.syndication.models.integration.SyndicationLink;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.lang.String.format;

@Service
public class SnomedSyndicationClient {

    public static final Set<String> acceptablePackageTypes = Set.of("SCT_RF2_SNAPSHOT", "SCT_RF2_FULL", "SCT_RF2_ALL");

    private final RestTemplate restTemplate;
    private final JAXBContext jaxbContext;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public SnomedSyndicationClient(@Value("${syndication.snomed.url}") String snomedUrl) throws JAXBException {
        restTemplate = new RestTemplateBuilder()
                .rootUri(snomedUrl)
                .messageConverters(new StringHttpMessageConverter())
                .build();
        jaxbContext = JAXBContext.newInstance(SyndicationFeed.class);
    }

    /**
     * @return a list containing first the downloaded edition package file path, then if present the downloaded extension package file path
     */
    public List<File> downloadPackages(String releaseUri, String snomedUsername, String snomedPassword) throws IOException, ServiceException {
        SyndicationFeed feed = getFeed();
        SyndicationFeedEntry entry = findEntryBasedOnUri(feed, releaseUri);
        List<SyndicationFeedEntry> feedEntries = new ArrayList<>(List.of(entry));
        if(isReleaseExtension(entry)) {
            addReleaseEditionLink(entry, feed, feedEntries);
        }
        List<File> packageFilePaths = new ArrayList<>();
        logger.info("{} package(s) will be downloaded", feedEntries.size());
        try {
            for (SyndicationFeedEntry feedEntry : feedEntries) {
                logger.info("Downloading package file {}\n on: {}", feedEntry.getTitle(), feedEntry.getZipLink().getHref());

                File outputFile = Files.createTempFile(UUID.randomUUID().toString(), ".zip").toFile();
                restTemplate.execute(feedEntry.getZipLink().getHref(), HttpMethod.GET,
                        request -> request.getHeaders().setBasicAuth(snomedUsername, snomedPassword),
                        clientHttpResponse -> handleHttpResponse(feedEntry.getZipLink(), clientHttpResponse, outputFile));
                outputFile.deleteOnExit();
                packageFilePaths.add(outputFile);
            }
        } catch (HttpClientErrorException e) {
            throw new ServiceException(format("Failed to download package due to HTTP error: %s", e.getStatusCode()), e);
        }
        return packageFilePaths;
    }

    private void addReleaseEditionLink(SyndicationFeedEntry entry, SyndicationFeed feed, List<SyndicationFeedEntry> feedEntries) throws ServiceException {
            String dependencyVersion = entry.getPackageDependency().getEditionDependency();
            Optional<SyndicationFeedEntry> dependencyEntryOpt = feed.getEntries().stream()
                    .filter(depEntry -> dependencyVersion.equals(depEntry.getContentItemVersion()))
                    .filter(this::validEntry)
                    .findAny();
        feedEntries.add(0, dependencyEntryOpt.orElseThrow(() -> new ServiceException("Could not find package dependency for version " + dependencyVersion)));
    }

    private static boolean isReleaseExtension(SyndicationFeedEntry entry) {
        return Optional.ofNullable(entry.getPackageDependency()).map(SyndicationDependency::getEditionDependency).isPresent();
    }

    private File handleHttpResponse(SyndicationLink packageLink, ClientHttpResponse clientHttpResponse, File outputFile) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            String lengthString = packageLink.getLength();
            int length;
            if (lengthString == null || lengthString.isEmpty()) {
                length = 1024 * 500;
            } else {
                length = Integer.parseInt(lengthString.replace(",", ""));
            }
            try {
                StreamUtil.copyWithProgress(clientHttpResponse.getBody(), outputStream, length, "Download progress: %s%%");
            } catch (Exception e) {
                logger.error("Failed to download file from syndication service.", e);
            }
        }
        return outputFile;
    }

    public SyndicationFeed getFeed() throws IOException {
        logger.info("Loading snow_med syndication feed");
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_ATOM_XML));
        ResponseEntity<String> response = restTemplate.exchange("/feed", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        try {
            String xmlBody = response.getBody();
            assert xmlBody != null;
            xmlBody = xmlBody.replace("xmlns=\"http://www.w3.org/2005/Atom\"", ""); // Strip Atom namespace to simplify unmarshalling
            SyndicationFeed feed = (SyndicationFeed) jaxbContext.createUnmarshaller().unmarshal(new StringReader(xmlBody));
            List<SyndicationFeedEntry> sortedEntries = new ArrayList<>(feed.getEntries());
            sortedEntries.sort(Comparator.comparing(SyndicationFeedEntry::getContentItemVersion, Comparator.reverseOrder()));
            feed.setEntries(sortedEntries);
            return feed;
        } catch (JAXBException e) {
            throw new IOException("Failed to read XML feed.", e);
        }
    }

    private SyndicationFeedEntry findEntryBasedOnUri(SyndicationFeed feed, String loadVersionUri) throws ServiceException {
        for (SyndicationFeedEntry entry : feed.getEntries()) {
            if (loadVersionUri.equals(entry.getContentItemVersion()) && validEntry(entry)) {
                logger.info("Found entry to load {}", entry.getContentItemVersion());
                return entry;
            }
        }
        throw new ServiceException("No matching syndication entry was found for URI" + loadVersionUri);
    }

    private boolean validEntry(SyndicationFeedEntry entry) {
        return entry.getCategory() != null && acceptablePackageTypes.contains(entry.getCategory().getTerm()) && entry.getZipLink() != null;
    }
}
