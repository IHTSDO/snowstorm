package org.snomed.snowstorm.util;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReleaseNoteHelper will read a given local Git repository's commits, match the commits against Jira Cloud, then write a summary
 * to a local CSV file. This is useful for generating release notes.
 *
 * To get started, follow the steps below. Note, DO NOT commit your changes.
 * 1) Keep WORKING_DIRECTORY as is for Snowstorm, or change to the local path to another repository.
 * 2) Change START_COMMIT to the commit hash of the first commit to include in the release notes. Typically, this is the commit BEFORE "Start X-SNAPSHOT".
 * 3) Change USERNAME to the email address associated with your Atlassian account.
 * 4) Change TOKEN to your Jira Cloud API token. Tokens can be created here: https://id.atlassian.com/manage-profile/security/api-tokens
 */
public class ReleaseNoteHelper {
	private static final Logger LOGGER = LoggerFactory.getLogger(ReleaseNoteHelper.class);
	private static final String JIRA_API = "https://snomed.atlassian.net/rest/api/3";
	private static final String WORKING_DIRECTORY = ".";
	private static final String START_COMMIT = "";
	private static final String USERNAME = "";
	private static final String TOKEN = "";

	public static void main(String[] args) {
		ReleaseNoteHelper releaseNoteHelper = new ReleaseNoteHelper();
		releaseNoteHelper.start();
	}

	private void start() {
		// Verify starting parameters
		throwIfMissingParameters();

		// Gather commits from Git
		List<Commit> commits = getCommits();

		// Get matching issues from JIRA
		Map<String, Issue> issues = getIssues(commits);

		// Write summary to file
		flushToFile(issues);

		// End program
		System.exit(0);
	}

	private void throwIfMissingParameters() {
		if (JIRA_API == null || JIRA_API.isBlank()) {
			throw new IllegalArgumentException("JIRA_API must be set");
		}

		if (WORKING_DIRECTORY == null || WORKING_DIRECTORY.isBlank()) {
			throw new IllegalArgumentException("WORKING_DIRECTORY must be set");
		}

		if (START_COMMIT == null || START_COMMIT.isBlank()) {
			throw new IllegalArgumentException("START_COMMIT must be set");
		}

		if (USERNAME == null || USERNAME.isBlank()) {
			throw new IllegalArgumentException("USERNAME must be set");
		}

		if (TOKEN == null || TOKEN.isBlank()) {
			throw new IllegalArgumentException("TOKEN must be set");
		}
	}

	private List<Commit> getCommits() {
		LOGGER.info("Getting commits from Git");

		try {
			File codeDirectory = new File(WORKING_DIRECTORY);
			Process process = Runtime.getRuntime().exec("git log " + START_COMMIT + ".." + "HEAD", new String[]{}, codeDirectory);
			ExecutorService executorService = Executors.newCachedThreadPool();
			executorService.submit(new StreamGobbler(process.getErrorStream(), System.err::println));
			process.waitFor();
			List<Commit> commits = new ArrayList<>();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				Commit commit = new Commit("");
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.startsWith("commit ")) {
						commit = new Commit(line.substring(7));
						commits.add(commit);
					} else if (line.startsWith("Author: ")) {
						commit.setAuthor(line.substring(8));
					} else if (!line.isEmpty() && !line.startsWith("Date: ")) {
						commit.addComment(line.trim());
					}
				}
			}

			return commits;
		} catch (Exception e) {
			LOGGER.error("Failed to get commits", e);
			return Collections.emptyList();
		}
	}

	private Map<String, Issue> getIssues(List<Commit> commits) {
		LOGGER.info("Getting matching issues from JIRA");

		Map<String, Issue> issues = new LinkedHashMap<>();
		for (Commit commit : commits) {
			String issueKey = parseIssueKeyFromCommit(commit.comment);
			if (issueKey == null) {
				issues.put(commit.hash, new Issue(commit.hash, "-", "-", "-").addCommit(commit));
				continue;
			}

			Issue issue = issues.computeIfAbsent(commit.hash, key -> {
				Map<String, Object> issueMap = getFromAPI(issueKey);
				if (issueMap == null || issueMap.isEmpty()) {
					return new Issue("", "Not found", "");
				}

				return convertToIssue(commit, issueKey, issueMap);
			});

			issue.addCommit(commit);
		}

		return issues;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> getFromAPI(String issueKey) {
		try {
			RestTemplate restTemplate = new RestTemplateBuilder().rootUri(JIRA_API).basicAuthentication(USERNAME, TOKEN).build();
			return restTemplate.getForObject("/issue/" + issueKey, Map.class);
		} catch (Exception e) {
			LOGGER.error("Failed to get issues", e);
			return Collections.emptyMap();
		}
	}

	@SuppressWarnings("unchecked")
	private Issue convertToIssue(Commit commit, String issueKey, Map<String, Object> issueMap) {
		Map<String, Object> fieldsMap = (Map<String, Object>) issueMap.get("fields");
		Map<String, Object> statusMap = (Map<String, Object>) fieldsMap.get("status");

		String status = (String) statusMap.get("name");
		String summary = (String) fieldsMap.get("summary");

		return new Issue(commit.hash, issueKey, status, summary);
	}

	private void flushToFile(Map<String, Issue> issues) {
		LOGGER.info("Writing summary to file");

		try (BufferedWriter writer = new BufferedWriter(new FileWriter("commit-change-log.tsv"))) {
			writer.write("Commit hash\tTicket\tTicket Status\tTicket Summary\tCommit comment");
			writer.newLine();
			for (String key : Lists.reverse(new ArrayList<>(issues.keySet()))) {
				Issue issue = issues.get(key);
				StringBuilder comments = new StringBuilder();
				for (Commit commit : issue.getCommits()) {
					comments.append(commit.comment);
				}
				writer.write(String.join("\t",
						issue.getHash(),
						issue.getKey(),
						issue.getStatus(),
						issue.getSummary(),
						comments.toString()
				));
				writer.newLine();
			}

			LOGGER.info("Complete. See 'commit-change-log.tsv'.");
		} catch (Exception e) {
			LOGGER.error("Failed to write to file", e);
		}
	}

	private static String parseIssueKeyFromCommit(String a) {
		Matcher matcher = Pattern.compile("([A-Z]+-[0-9]*)[: ].*").matcher(a);
		if (matcher.matches()) {
			return matcher.group(1);
		}
		return null;
	}

	private static class Issue {
		private final String hash;
		private final String key;
		private String status;
		private final List<Commit> commits = new ArrayList<>();
		private String summary;

		public Issue(String hash, String key, String status) {
			this.hash = hash;
			this.key = key;
			this.status = status;
		}

		public Issue(String hash, String key, String status, String summary) {
			this.hash = hash;
			this.key = key;
			this.status = status;
			this.summary = summary;
		}

		public String getHash() {
			return hash;
		}

		public String getKey() {
			return key;
		}

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		public List<Commit> getCommits() {
			return commits;
		}

		public Issue addCommit(Commit commit) {
			commits.add(commit);
			return this;
		}

		public void setSummary(String summary) {
			this.summary = summary;
		}

		public String getSummary() {
			return summary;
		}
	}

	private static class Commit {

		private final String hash;
		private String author;
		private String comment;

		public Commit(String hash) {
			this.hash = hash;
			comment = "";
		}

		public void setAuthor(String author) {
			this.author = author;
		}

		public void addComment(String comment) {
			this.comment += comment;
		}

		@Override
		public String toString() {
			return hash + '\t' +
					author + '\t' +
					comment;
		}

	}

	private static class StreamGobbler implements Runnable {
		private final InputStream inputStream;
		private final Consumer<String> consumer;

		public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
			this.inputStream = inputStream;
			this.consumer = consumer;
		}

		@Override
		public void run() {
			new BufferedReader(new InputStreamReader(inputStream)).lines()
					.forEach(consumer);
		}
	}
}
