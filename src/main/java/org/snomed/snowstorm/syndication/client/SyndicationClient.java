package org.snomed.snowstorm.syndication.client;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.util.StreamUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.util.Pair;
import org.springframework.http.*;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

import static java.lang.String.format;

@Service
public class SyndicationClient {

	public static final Set<String> acceptablePackageTypes = Set.of("SCT_RF2_SNAPSHOT", "SCT_RF2_FULL", "SCT_RF2_ALL");

	/** Used for download progress when the syndication feed does not declare a package link length. */
	private static final int DEFAULT_RF2_PACKAGE_LENGTH_BYTES = 560 * 1024 * 1024;

	private final RestTemplate restTemplate;
	private final JAXBContext jaxbContext;
	private final String username;
	private final String password;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public SyndicationClient(@Value("${syndication.url}") String url,
			@Value("${syndication.username}") String username,
			@Value("${syndication.password}") String password) throws JAXBException {

		restTemplate = new RestTemplateBuilder()
				.rootUri(url)
				.messageConverters(new StringHttpMessageConverter())
				.build();
		jaxbContext = JAXBContext.newInstance(SyndicationFeed.class);
		this.username = username;
		this.password = password;
	}

	public SyndicationFeed getFeed() throws IOException {
		logger.info("Loading syndication feed");
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_ATOM_XML));
		ResponseEntity<String> response = restTemplate.exchange("/feed", HttpMethod.GET, new HttpEntity<>(headers), String.class);
		try {
			String xmlBody = response.getBody();
			if (xmlBody == null) {
				throw new IOException("Empty response body from syndication feed.");
			}
			// Strip Atom namespace to simplify unmarshalling
			xmlBody = xmlBody.replace("xmlns=\"http://www.w3.org/2005/Atom\"", "");
			SyndicationFeed feed = (SyndicationFeed) jaxbContext.createUnmarshaller().unmarshal(new StringReader(xmlBody));
			List<SyndicationFeedEntry> sortedEntries = new ArrayList<>(feed.getEntries());
			sortedEntries.sort(Comparator.comparing(SyndicationFeedEntry::getContentItemVersion, Comparator.reverseOrder()));
			feed.setEntries(sortedEntries);
			return feed;
		} catch (JAXBException e) {
			throw new IOException("Failed to read XML feed.", e);
		}
	}
	public SyndicationFeedEntry findEntry(String loadVersionUri, SyndicationFeed feed) {
		for (SyndicationFeedEntry entry : feed.getEntries()) {
			SyndicationLink zipLink = entry.getZipLink();
			SyndicationCategory category = entry.getCategory();
			if (category != null) {
				String categoryString = category.getTerm();
				if (zipLink != null &&
						acceptablePackageTypes.contains(categoryString) &&
						(entry.getContentItemVersion().equals(loadVersionUri) || entry.getContentItemIdentifier().equals(loadVersionUri))) {

					logger.info("Found entry to load {}", entry.getContentItemVersion());
					return entry;
				}
			}
		}
		logger.warn("No matching syndication entry was found for URI {}", loadVersionUri);
		return null;
	}

	/**
	 * @param consumedVersionUris content-item version URIs already downloaded in this install; used to avoid duplicate zips when adding derivatives.
	 */
	public List<String> downloadPackages(SyndicationFeedEntry entry, SyndicationFeed feed, Pair<String, String> creds,
			Set<String> consumedVersionUris) throws IOException, ServiceException {
		if (consumedVersionUris == null) {
			consumedVersionUris = new HashSet<>();
		}
		Set<Pair<SyndicationFeedEntry, SyndicationLink>> packageUrls = new LinkedHashSet<>();
		gatherPackageUrls(entry.getContentItemVersion(), feed.getEntries(), packageUrls, consumedVersionUris);
		List<String> packageFilePaths = new ArrayList<>();
		if (packageUrls.isEmpty()) {
			logger.error("Can not load content, no links found within the syndication feed for the requested package.");
			return packageFilePaths;
		}
		logger.info("Matched the following packages:");
		for (Pair<SyndicationFeedEntry, SyndicationLink> packageEntry : packageUrls) {
			logger.info(" {}, {}", packageEntry.getFirst().getTitle(), packageEntry.getFirst().getContentItemVersion());
		}
		try {
			for (Pair<SyndicationFeedEntry, SyndicationLink> packageEntry : packageUrls) {
				packageFilePaths.add(downloadPackage(packageEntry, creds).getAbsolutePath());
			}
		} catch (HttpClientErrorException e) {
			throw new ServiceException(format("Failed to download package due to HTTP error: %s", e.getStatusCode()), e);
		}
		return packageFilePaths;
	}

	private File downloadPackage(Pair<SyndicationFeedEntry, SyndicationLink> packageEntry, Pair<String, String> creds) throws IOException {
		SyndicationFeedEntry entry = packageEntry.getFirst();
		SyndicationLink packageLink = packageEntry.getSecond();
		final String contentItemVersion = entry.getContentItemVersion() != null ? entry.getContentItemVersion() : "(unknown)";
		int progressBasisBytes = parsePackageLength(packageLink.getLength());
		logger.info("Starting RF2 package download: {} ({} bytes used as progress denominator) from {}", contentItemVersion, progressBasisBytes, packageLink.getHref());

		// Test credentials and download link using OPTIONS request
		HttpHeaders headers = new HttpHeaders();
		if (creds != null) {
			headers.setBasicAuth(creds.getFirst(), creds.getSecond());
		}
		restTemplate.exchange(packageLink.getHref(), HttpMethod.OPTIONS, new HttpEntity<Void>(headers), Void.class);

		File outputFile = Files.createTempFile(UUID.randomUUID().toString(), ".zip").toFile();
		String progressMessageFormat = "RF2 package download progress for " + contentItemVersion + ": %s%%";
		restTemplate.execute(packageLink.getHref(), HttpMethod.GET,
				request -> {
					if (creds != null) {
						request.getHeaders().setBasicAuth(creds.getFirst(), creds.getSecond());
					}
				},
				clientHttpResponse -> {
					try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
						try {
							int bytesWritten = StreamUtil.copyWithProgress(clientHttpResponse.getBody(), outputStream, progressBasisBytes, progressMessageFormat);
							logger.info("Completed RF2 package download: {} ({} bytes)", contentItemVersion, bytesWritten);
						} catch (Exception e) {
							logger.error("Failed RF2 package download for {}", contentItemVersion, e);
						}
					}
					return outputFile;
				});
		return outputFile;
	}

	private int parsePackageLength(String lengthString) {
		if (lengthString == null || lengthString.isEmpty()) {
			return DEFAULT_RF2_PACKAGE_LENGTH_BYTES;
		}
		return Integer.parseInt(lengthString.replace(",", ""));
	}

	public Pair<String, String> getSyndicationCredentials() throws IOException {
		if (!Strings.isBlank(username) && !Strings.isBlank(password)) {
			return Pair.of(username, password);
		}

		Console console = System.console();
		String enteredUsername;
		String enteredPassword;
		if (console != null) {
			enteredUsername = console.readLine("Syndication username:");
			enteredPassword = new String(console.readPassword("Syndication password:"));
		} else {
			BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
			logger.info("Syndication username:");
			enteredUsername = consoleReader.readLine();
			logger.info("Syndication password:");
			enteredPassword = consoleReader.readLine();
		}
		if (Strings.isBlank(enteredUsername) && Strings.isBlank(enteredPassword)) {
			logger.warn("Syndication credentials are blank. If required use the properties: syndication.username and syndication.password.");
			return null;
		}
		return Pair.of(enteredUsername, enteredPassword);
	}

	private void gatherPackageUrls(String loadVersionUri, List<SyndicationFeedEntry> sortedEntries,
			Set<Pair<SyndicationFeedEntry, SyndicationLink>> downloadList, Set<String> consumedVersionUris) {
		for (SyndicationFeedEntry entry : sortedEntries) {
			SyndicationLink zipLink = entry.getZipLink();
			if (zipLink != null && entry.getCategory() != null &&
					acceptablePackageTypes.contains(entry.getCategory().getTerm()) &&
					(entry.getContentItemVersion().equals(loadVersionUri) || entry.getContentItemIdentifier().equals(loadVersionUri))) {

				String versionKey = entry.getContentItemVersion();
				if (consumedVersionUris.contains(versionKey)) {
					SyndicationDependency packageDependencySkipped = entry.getPackageDependency();
					if (packageDependencySkipped != null) {
						gatherDependencyUrls(packageDependencySkipped, sortedEntries, downloadList, consumedVersionUris);
					}
					continue;
				}
				downloadList.add(Pair.of(entry, zipLink));
				consumedVersionUris.add(versionKey);

				SyndicationDependency packageDependency = entry.getPackageDependency();
				if (packageDependency != null) {
					gatherDependencyUrls(packageDependency, sortedEntries, downloadList, consumedVersionUris);
				}
			}
		}
	}

	private void gatherDependencyUrls(SyndicationDependency dependency, List<SyndicationFeedEntry> sortedEntries,
			Set<Pair<SyndicationFeedEntry, SyndicationLink>> downloadList, Set<String> consumedVersionUris) {
		if (dependency.getEditionDependency() != null) {
			gatherPackageUrls(dependency.getEditionDependency(), sortedEntries, downloadList, consumedVersionUris);
		}
		if (dependency.getDerivativeDependency() != null) {
			for (String dependencyUri : dependency.getDerivativeDependency()) {
				gatherPackageUrls(dependencyUri, sortedEntries, downloadList, consumedVersionUris);
			}
		}
	}
}
