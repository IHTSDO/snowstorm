package org.snomed.snowstorm.syndication.client;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.core.util.StreamUtil;
import org.snomed.snowstorm.syndication.InstallationPackageProgress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.util.Pair;
import org.springframework.http.*;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

import static java.lang.String.format;

@Service
public class SyndicationClient {

	public static final Set<String> acceptablePackageTypes = Set.of("SCT_RF2_SNAPSHOT", "SCT_RF2_FULL", "SCT_RF2_ALL");

	/** Used for download / sizing when the syndication feed does not declare a package link length. */
	public static final long DEFAULT_RF2_PACKAGE_LENGTH_BYTES = 560L * 1024 * 1024;

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
		List<Pair<SyndicationFeedEntry, SyndicationLink>> ordered = collectOrderedPackages(entry, feed, consumedVersionUris);
		return downloadOrderedPackageList(ordered, creds, null);
	}

	public List<Pair<SyndicationFeedEntry, SyndicationLink>> collectOrderedPackages(SyndicationFeedEntry entry, SyndicationFeed feed,
			Set<String> consumedVersionUris) {
		return collectOrderedPackages(entry, feed, consumedVersionUris, true);
	}

	/**
	 * @param resolveDependencies when {@code true}, resolves {@link SyndicationFeedEntry#getPackageDependency()} (edition and derivative
	 *        dependencies). When {@code false}, only the given entry’s own RF2 zip is included — used for refset/derivative add-ons so their
	 *        declared dependencies are not installed separately.
	 */
	public List<Pair<SyndicationFeedEntry, SyndicationLink>> collectOrderedPackages(SyndicationFeedEntry entry, SyndicationFeed feed,
			Set<String> consumedVersionUris, boolean resolveDependencies) {
		if (consumedVersionUris == null) {
			consumedVersionUris = new HashSet<>();
		}
		if (!resolveDependencies) {
			return collectLeafPackageOnly(entry, consumedVersionUris);
		}
		String rootVersionUri = entry.getContentItemVersion();
		logger.info("Collecting RF2 package order from feed for root package {}", rootVersionUri);
		Set<Pair<SyndicationFeedEntry, SyndicationLink>> packageUrls = new LinkedHashSet<>();
		gatherPackageUrls(rootVersionUri, feed.getEntries(), packageUrls, consumedVersionUris, new HashSet<>());
		List<Pair<SyndicationFeedEntry, SyndicationLink>> ordered = new ArrayList<>(packageUrls);
		logger.info("Resolved {} RF2 package(s) in download order for {}", ordered.size(), rootVersionUri);
		if (!ordered.isEmpty()) {
			logger.info("Packages to download:");
			for (Pair<SyndicationFeedEntry, SyndicationLink> packageEntry : ordered) {
				logger.info(" — {} | {}", packageEntry.getFirst().getContentItemVersion(), packageEntry.getFirst().getTitle());
			}
		} else {
			logger.warn("No RF2 packages matched in feed for {} (check edition URI and syndication categories).", rootVersionUri);
		}
		return ordered;
	}

	/**
	 * Single RF2 zip for {@code entry} only (no {@code packageDependency} expansion). Skips if already in {@code consumedVersionUris}.
	 */
	private List<Pair<SyndicationFeedEntry, SyndicationLink>> collectLeafPackageOnly(SyndicationFeedEntry entry, Set<String> consumedVersionUris) {
		String rootVersionUri = entry.getContentItemVersion();
		logger.info("Collecting RF2 package (leaf only, no dependency expansion) for {}", rootVersionUri);
		SyndicationLink zipLink = entry.getZipLink();
		if (zipLink == null || entry.getCategory() == null
				|| !acceptablePackageTypes.contains(entry.getCategory().getTerm())) {
			logger.warn("No acceptable RF2 package link for leaf collect {}", rootVersionUri);
			return Collections.emptyList();
		}
		String versionKey = entry.getContentItemVersion();
		if (consumedVersionUris.contains(versionKey)) {
			return Collections.emptyList();
		}
		consumedVersionUris.add(versionKey);
		return Collections.singletonList(Pair.of(entry, zipLink));
	}

	public List<String> downloadOrderedPackageList(List<Pair<SyndicationFeedEntry, SyndicationLink>> ordered, Pair<String, String> creds,
			List<InstallationPackageProgress> progressSlots) throws IOException, ServiceException {
		List<String> packageFilePaths = new ArrayList<>();
		if (ordered.isEmpty()) {
			logger.error("Cannot download RF2 content: package list is empty (nothing matched in the syndication feed for this install).");
			return packageFilePaths;
		}
		logger.info("Downloading {} RF2 package zip(s) in sequence (authenticated download: {}).", ordered.size(), creds != null);
		if (progressSlots != null && progressSlots.size() != ordered.size()) {
			logger.warn("Package progress slot count {} does not match ordered package count {}", progressSlots.size(), ordered.size());
		}
		try {
			for (int i = 0; i < ordered.size(); i++) {
				InstallationPackageProgress progress = progressSlots != null && i < progressSlots.size() ? progressSlots.get(i) : null;
				packageFilePaths.add(downloadPackage(ordered.get(i), creds, progress).getAbsolutePath());
			}
		} catch (HttpClientErrorException e) {
			throw new ServiceException(format("Failed to download package due to HTTP error: %s", e.getStatusCode()), e);
		}
		return packageFilePaths;
	}

	private File downloadPackage(Pair<SyndicationFeedEntry, SyndicationLink> packageEntry, Pair<String, String> creds,
			InstallationPackageProgress progress) throws ServiceException, IOException {

		SyndicationFeedEntry entry = packageEntry.getFirst();
		SyndicationLink packageLink = packageEntry.getSecond();
		final String contentItemVersion = entry.getContentItemVersion() != null ? entry.getContentItemVersion() : "(unknown)";
		long progressBasisBytes = parseDeclaredPackageBytes(packageLink.getLength());
		logger.info("Starting RF2 package download: {} ({} bytes used as progress denominator) from {}", contentItemVersion, progressBasisBytes, packageLink.getHref());

		if (progress != null) {
			progress.setPhase(InstallationPackageProgress.PHASE_DOWNLOADING);
			progress.setDownloadPercent(0);
		}

		HttpHeaders headers = new HttpHeaders();
		if (creds != null) {
			headers.setBasicAuth(creds.getFirst(), creds.getSecond());
		}
		logger.info("RF2 package {}: sending OPTIONS probe to {}", contentItemVersion, packageLink.getHref());
		restTemplate.exchange(packageLink.getHref(), HttpMethod.OPTIONS, new HttpEntity<Void>(headers), Void.class);
		logger.info("RF2 package {}: OPTIONS OK, starting GET (streaming zip)", contentItemVersion);

		File outputFile = Files.createTempFile(UUID.randomUUID().toString(), ".zip").toFile();
		String progressMessageFormat = "RF2 package download progress for " + contentItemVersion + ": %s%%";
		try {
			restTemplate.execute(packageLink.getHref(), HttpMethod.GET,
					request -> {
						if (creds != null) {
							request.getHeaders().setBasicAuth(creds.getFirst(), creds.getSecond());
						}
					},
					clientHttpResponse -> {
						try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
							int bytesWritten = StreamUtil.copyWithProgress(clientHttpResponse.getBody(), outputStream, progressBasisBytes,
									progressMessageFormat,
									progress != null ? progress::setDownloadPercent : null);
							logger.info("Completed RF2 package download: {} ({} bytes)", contentItemVersion, bytesWritten);
							if (progress != null) {
								progress.setDownloadPercent(100);
								progress.setPhase(InstallationPackageProgress.PHASE_WAITING_IMPORT);
							}
						}
						return outputFile;
					});
		} catch (Exception e) {
			throw new ServiceException("Failed RF2 package download for %s".formatted(contentItemVersion), e);
		}
		return outputFile;
	}

	public static long parseDeclaredPackageBytes(String lengthString) {
		if (lengthString == null || lengthString.isEmpty()) {
			return DEFAULT_RF2_PACKAGE_LENGTH_BYTES;
		}
		return Long.parseLong(lengthString.replace(",", ""));
	}

	public Pair<String, String> getSyndicationCredentials() throws IOException {
		if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
			logger.info("Syndication: using credentials from application configuration (username present, length {}).",
					username.length());
			return Pair.of(username, password);
		}

		logger.warn("Syndication: syndication.username / syndication.password are not set. "
				+ "The installation thread will block on stdin until credentials are entered. "
				+ "For server installs, set both properties. Waiting for interactive input now…");
		Console console = System.console();
		String enteredUsername;
		String enteredPassword;
		if (console != null) {
			enteredUsername = console.readLine("Syndication username:");
			enteredPassword = new String(console.readPassword("Syndication password:"));
		} else {
			BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
			logger.info("Syndication username (stdin):");
			enteredUsername = consoleReader.readLine();
			logger.info("Syndication password (stdin):");
			enteredPassword = consoleReader.readLine();
		}
		if (!StringUtils.hasText(enteredUsername) && !StringUtils.hasText(enteredPassword)) {
			logger.warn("Syndication credentials are blank. If required use the properties: syndication.username and syndication.password.");
			return null;
		}
		return Pair.of(enteredUsername, enteredPassword);
	}

	/**
	 * Resolves {@code loadVersionUri} and its {@link SyndicationDependency} tree into a download/import order.
	 * Dependent packages from the feed are scheduled <strong>before</strong> the package that declares the dependency
	 * so RF2 imports run prerequisite-first.
	 *
	 * @param visitingStack detects circular {@code packageDependency} links in the feed (avoids infinite recursion).
	 */
	private void gatherPackageUrls(String loadVersionUri, List<SyndicationFeedEntry> sortedEntries,
			Set<Pair<SyndicationFeedEntry, SyndicationLink>> downloadList, Set<String> consumedVersionUris,
			Set<String> visitingStack) {
		for (SyndicationFeedEntry entry : sortedEntries) {
			SyndicationLink zipLink = entry.getZipLink();
			if (zipLink != null && entry.getCategory() != null &&
					acceptablePackageTypes.contains(entry.getCategory().getTerm()) &&
					(entry.getContentItemVersion().equals(loadVersionUri) || entry.getContentItemIdentifier().equals(loadVersionUri))) {

				String versionKey = entry.getContentItemVersion();
				if (consumedVersionUris.contains(versionKey)) {
					SyndicationDependency packageDependencySkipped = entry.getPackageDependency();
					if (packageDependencySkipped != null) {
						gatherDependencyUrls(packageDependencySkipped, sortedEntries, downloadList, consumedVersionUris, visitingStack);
					}
					continue;
				}
				if (visitingStack.contains(versionKey)) {
					logger.warn("Circular syndication packageDependency involving {} — skipping repeat expansion.", versionKey);
					continue;
				}
				visitingStack.add(versionKey);
				try {
					SyndicationDependency packageDependency = entry.getPackageDependency();
					if (packageDependency != null) {
						gatherDependencyUrls(packageDependency, sortedEntries, downloadList, consumedVersionUris, visitingStack);
					}
					downloadList.add(Pair.of(entry, zipLink));
					consumedVersionUris.add(versionKey);
				} finally {
					visitingStack.remove(versionKey);
				}
			}
		}
	}

	private void gatherDependencyUrls(SyndicationDependency dependency, List<SyndicationFeedEntry> sortedEntries,
			Set<Pair<SyndicationFeedEntry, SyndicationLink>> downloadList, Set<String> consumedVersionUris,
			Set<String> visitingStack) {
		if (dependency.getEditionDependency() != null) {
			gatherPackageUrls(dependency.getEditionDependency(), sortedEntries, downloadList, consumedVersionUris, visitingStack);
		}
		if (dependency.getDerivativeDependency() != null) {
			for (String dependencyUri : dependency.getDerivativeDependency()) {
				gatherPackageUrls(dependencyUri, sortedEntries, downloadList, consumedVersionUris, visitingStack);
			}
		}
	}
}
