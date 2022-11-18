package org.snomed.snowstorm.util;

import com.google.common.collect.Lists;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hacky utility class to gather Jira tickets and git commit comments into a spreadsheet to help writing change log/release notes.
 */
public class ReleaseNoteHelper {

	private static final Pattern TICKET_PATTERN = Pattern.compile("([A-Z]+-[0-9]*)[: ].*");

	public static void main(String[] args) {
		String startCommit = "-";
		File codeDirectory = new File(".");// This directory
		String endCommit = "HEAD";
		String jiraJSessionId = "-";
		String jiraApi = "https://jira.ihtsdotools.org/rest/api/latest";
		String outputFilename = "commit-change-log.tsv";


		try {
			Process process = Runtime.getRuntime().exec(new String[] {"git log", String.format("%s..%s", startCommit, endCommit)}, new String[]{}, codeDirectory);
			ExecutorService executorService = Executors.newCachedThreadPool();
			executorService.submit(new StreamGobbler(process.getErrorStream(), System.err::println));
			int i = process.waitFor();
			System.out.println(i);

			List<Commit> commits = new ArrayList<>();
			Map<String, Issue> issues = new LinkedHashMap<>();
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

			RestTemplate restTemplate = new RestTemplateBuilder()
					.additionalInterceptors((request, body, execution) -> {
						request.getHeaders().add("Cookie", "JSESSIONID=" + jiraJSessionId);
						return execution.execute(request, body);
					})
					.rootUri(jiraApi)
					.build();

			for (Commit commit : commits) {
				String issueKey = getTicket(commit.comment);
				System.out.println(issueKey);
				try {
					if (issueKey != null) {
						Issue issue = issues.computeIfAbsent(commit.hash, key -> {
							try {
								@SuppressWarnings("unchecked")
								Map<String, Object> issueMap = restTemplate.getForObject("/issue/" + issueKey, Map.class);
								if (issueMap == null) {
									return new Issue("", "Not found", "");
								}
								@SuppressWarnings("unchecked")
								Map<String, Object> fieldsMap = (Map<String, Object>) issueMap.get("fields");
								@SuppressWarnings("unchecked")
								Map<String, Object> statusMap = (Map<String, Object>) fieldsMap.get("status");
								Issue issue1 = new Issue(commit.hash, issueKey, (String) statusMap.get("name"));
								issue1.setSummary((String) fieldsMap.get("summary"));
								issue1.setDescription((String) fieldsMap.get("description"));
								return issue1;
							} catch (HttpClientErrorException e) {
								return new Issue(commit.hash, issueKey, "Lookup failed");
							}
						});
						issue.addCommit(commit);
					} else {
						issues.put(commit.hash, new Issue(commit.hash, "-", "-", "-").addCommit(commit));
					}
				} catch (RestClientException e) {
					System.out.println("Failed to lookup jira issue for key '" + issueKey + "'");
					throw e;
				}
			}
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilename))) {
				writer.write("Commit hash\tTicket\tTicket Status\tTicket Summary\tCommit comment");
				writer.newLine();
				for (String key : Lists.reverse(new ArrayList<>(issues.keySet()))) {
					Issue issue = issues.get(key);
					StringBuilder comments = new StringBuilder();
					for (Commit commit : issue.getCommits()) {
						comments.append(commit.comment).append("|");
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
			}
			System.out.println("done");
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
		System.exit(0);
	}

	private static String getTicket(String a) {
		Matcher matcher = TICKET_PATTERN.matcher(a);
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
		private String description;
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

		public void setDescription(String description) {
			this.description = description;
		}

		public String getDescription() {
			return description;
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
