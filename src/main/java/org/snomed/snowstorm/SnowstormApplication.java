package org.snomed.snowstorm;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.data.services.StartupException;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.scheduling.annotation.EnableScheduling;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

@EnableSwagger2
@EnableJms
@EnableScheduling
@EnableCaching
public class SnowstormApplication extends Config implements ApplicationRunner {

	private static final String DELETE_INDICES_FLAG = "delete-indices";
	private static final String IMPORT_ARG = "import";
	private static final String IMPORT_FULL_ARG = "import-full";
	private static final String EXIT = "exit";

	@Autowired
	private ImportService importService;
	
	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private ApplicationContext applicationContext;

	private static final Logger logger = LoggerFactory.getLogger(SnowstormApplication.class);

	public static void main(String[] args) {
		System.setProperty("org.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH", "true"); // Swagger encodes the slash in branch paths
		System.setProperty("tomcat.util.http.parser.HttpParser.requestTargetAllow", "{}|"); // Allow these unencoded characters in URL (used in ECL)
		try {
			SpringApplication.run(SnowstormApplication.class, args);
		} catch (BeanCreationException e) {
			if (e.getCause() instanceof StartupException) {
				StartupException startupException = (StartupException) e.getCause();
				logger.error("Error creating Snowstorm Spring context:", e);
				System.out.println();
				System.out.println();
				logger.error("Snowstorm failed to start. Cause: {}", startupException.getMessage());
			}
		}
	}

	@Override
	public void run(ApplicationArguments applicationArguments) {
		try {
			boolean deleteIndices = applicationArguments.containsOption(DELETE_INDICES_FLAG);
			if (deleteIndices) logger.warn("Deleting existing Elasticsearch Indices");
			initialiseIndices(deleteIndices);

			updateIndexMaxTermsSettingForQueryConcept();

			codeSystemService.init();
			referenceSetMemberService.init();

			logger.info("--- Snowstorm startup complete ---");

			logger.info("Warming CodeSystem aggregation cache...");
			codeSystemService.findAll();
			logger.info("Caches are hot.");

			if (applicationArguments.containsOption(IMPORT_ARG)) {
				// Import a single release or 'Snapshot' from an Edition RF2 zip file from disk at startup
				String releasePath = getOneValue(applicationArguments, IMPORT_ARG);
				fileExistsForArgument(releasePath, IMPORT_ARG);

				importEditionRF2FromDisk(releasePath, RF2Type.SNAPSHOT);
			} else if (applicationArguments.containsOption(IMPORT_FULL_ARG)) {
				// Import many releases or 'Full' from an Edition RF2 zip file from disk at startup
				String releasePath = getOneValue(applicationArguments, IMPORT_FULL_ARG);
				fileExistsForArgument(releasePath, IMPORT_FULL_ARG);

				importEditionRF2FromDisk(releasePath, RF2Type.FULL);
			}
			if (applicationArguments.containsOption(EXIT)) {
				logger.info("Exiting application.");
				((ConfigurableApplicationContext)applicationContext).close();
			}
		} catch (Exception e) {
			// Logging and rethrowing because Spring does not seem to log this
			logger.error(e.getMessage(), e);
			throw e;
		}
	}

	private String getOneValue(ApplicationArguments applicationArguments, String argName) {
		List<String> values = applicationArguments.getOptionValues(argName);
		if (values.size() != 1) {
			throw new IllegalArgumentException(argName + " argument must have exactly one value");
		}
		return values.get(0);
	}

	private void fileExistsForArgument(String filePath, String argName) {
		File file = new File(filePath);
		if (!file.isFile()) {
			throw new IllegalArgumentException(argName + " file could not be read at " + file.getAbsolutePath());
		}
	}

	private void importEditionRF2FromDisk(String releasePath, RF2Type importType) {
		// Import archive
		String importId = importService.createJob(importType, "MAIN", true, false);
		try (FileInputStream releaseFileStream = new FileInputStream(releasePath)) {
			importService.importArchive(importId, releaseFileStream);
		} catch (IOException | ReleaseImportException e) {
			logger.error("Import failed.", e);
		}
	}

}
