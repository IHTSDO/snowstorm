package org.snomed.snowstorm.util;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class to filter an extracted RF2 archive by the ids in another extracted RF2 archive.
 */
public class RF2Filter {

	public static void main(String[] args) throws IOException {
		final Path filter = Path.of("jan-2021-release-fix");
		final Path source = Path.of("snomed-MAIN_DHMJUL21_DHMJUL21-918-20210413-Delta");
		final Path output = Path.of("snomed-MAIN_DHMJUL21_DHMJUL21-918-20210413-Delta-filtered");
		filterRF2(filter, source, output);
	}

	/**
	 * Utility method to filter an extracted RF2 archive by the ids in another extracted RF2 archive.
	 * The ids within the 'filter' directory will be used to filter the content of the RF2 files in the 'source' directory
	 * while copying the files, and directory structure, to the 'output' directory.
	 * @param filter Directory containing an extracted RF2 archive to gather the filter ids from.
	 * @param source Directory containing an extracted RF2 archive to be filtered using the ids in the filter directory.
	 * @param output The path where the filtered RF2 files will be created.
	 * @throws IOException if any type of IO error occurs.
	 */
	private static void filterRF2(Path filter, Path source, Path output) throws IOException {

		Set<Long> sctids = new LongOpenHashSet();
		Set<String> uuids = new HashSet<>();

		for (File file : Files.walk(filter).map(Path::toFile).filter(file -> file.getName().endsWith(".txt")).collect(Collectors.toList())) {
			final String name = file.getName();
			boolean refset = name.contains("Refset");
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
				System.out.printf("Reading %s\n", name);
				reader.readLine();
				String line, id;
				while ((line = reader.readLine()) != null) {
					id = line.split("\t", 2)[0];
					if (refset) {
						uuids.add(id);
					} else {
						try {
							sctids.add(Long.parseLong(id));
						} catch (NumberFormatException e) {
							System.out.println("id " + id);
							throw e;
						}
					}
				}
			}
		}
		System.out.println();
		System.out.printf("Gathered %s SCTIDs and %s UUIDS\n", sctids.size(), uuids.size());
		System.out.println();

		for (File sourceFile : Files.walk(source).map(Path::toFile).filter(file -> file.getName().endsWith(".txt")).collect(Collectors.toList())) {
			final String name = sourceFile.getName();
			boolean refset = name.contains("Refset");
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(sourceFile)))) {
				String relativePath = sourceFile.getAbsolutePath().substring(source.toFile().getAbsolutePath().length());
				System.out.printf("Filtering %s\n", relativePath);
				final File outputFile = new File(output.toFile(), relativePath);
				if (!outputFile.getParentFile().mkdirs()) {
					System.out.println("Failed to make directories");
				}
				try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
					System.out.printf("Output %s\n", outputFile.getAbsolutePath());
					writer.write(reader.readLine());
					writer.newLine();
					String line, id;
					while ((line = reader.readLine()) != null) {
						id = line.split("\t", 2)[0];
						if ((refset && uuids.contains(id)) || !refset && sctids.contains(Long.parseLong(id))) {
							writer.write(line);
							writer.newLine();
						}
					}
				}
			}
		}

	}

}
