package org.ihtsdo.elasticsnomed.util;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RF2FullToSnapshot {

	public static void main(String[] args) throws IOException {
		final File dir = new File("/Users/kaikewley/code/tmp/MiniCT_INT_GB_Full_20140131/MiniCT/RF2Release");
		new RF2FullToSnapshot().convert(dir);
	}

	private void convert(File dir) throws IOException {
		List<File> fullFiles = new ArrayList<>();
		findFiles(dir, fullFiles);
		final String basePath = dir.getPath();
		for (File fullFile : fullFiles) {
			Map<String, String> idDateMap = new HashMap<>();
			try (final BufferedReader reader = new BufferedReader(new FileReader(fullFile))) {
				reader.readLine();
				String line, id, date, oldDate;
				while ((line = reader.readLine()) != null) {
					final String[] parts = line.split("\\t");
					id = parts[0];
					date = parts[1];
					oldDate = idDateMap.get(id);
					if (oldDate == null || date.compareTo(oldDate) > 0) {
						idDateMap.put(id, date);
					}
				}
			}

			final String fullFilePath = fullFile.getPath();
			String snapshotFilePath = basePath + fullFilePath.substring(basePath.length()).replace("Full_", "Snapshot_").replace("Full/", "Snapshot/");

			final File snapshotFile = new File(snapshotFilePath);
			snapshotFile.getParentFile().mkdirs();
			try (final BufferedReader reader = new BufferedReader(new FileReader(fullFile));
				 final BufferedWriter writer = new BufferedWriter(new FileWriter(snapshotFile))) {
				String line, id, date;
				writer.write(reader.readLine());
				writer.newLine();
				while ((line = reader.readLine()) != null) {
					final String[] parts = line.split("\\t");
					id = parts[0];
					date = parts[1];
					if (idDateMap.get(id).equals(date)) {
						writer.write(line);
						writer.newLine();
					}
				}
			}
		}
	}

	private void findFiles(File dir, List<File> fullFiles) {
		for (File file : dir.listFiles()) {
			if (file.isDirectory()) {
				findFiles(file, fullFiles);
			} else if (file.isFile() && (file.getName().startsWith("sct2_") || file.getName().startsWith("der2_")) &&
					(file.getName().contains("Full_") || file.getName().contains("Full-"))) {
				fullFiles.add(file);
			}
		}
	}

}
