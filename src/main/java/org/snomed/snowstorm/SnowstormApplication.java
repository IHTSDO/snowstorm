package org.snomed.snowstorm;

import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.services.*;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.snomed.snowstorm.core.util.CollectionUtils;
import org.snomed.snowstorm.syndication.services.StartupSyndicationService;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

@EnableJms
@EnableScheduling
@EnableCaching
public class SnowstormApplication extends Config implements ApplicationRunner {

	private static final String DELETE_INDICES_FLAG = "delete-indices";
	private static final String IMPORT_ARG = "import";
	private static final String IMPORT_FULL_ARG = "import-full";
	private static final String SYNDICATION = "syndication";
	private static final String EXIT = "exit";

	@Autowired
	private ImportService importService;
	
	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private CodeSystemVersionService codeSystemVersionService;

	@Autowired
	private StartupSyndicationService startupSyndicationService;

	private static final Logger logger = LoggerFactory.getLogger(SnowstormApplication.class);

	public static void main(String[] args) {
		try {
			SpringApplication.run(SnowstormApplication.class, args);
		} catch (BeanCreationException e) {
			if (e.getCause() instanceof StartupException startupException) {
				logger.error("Error creating Snowstorm Spring context:", e);
				System.out.println();
				System.out.println();
				logger.error("Snowstorm failed to start. Cause: {}", startupException.getMessage());
			}
		}
	}

	@Bean
	public TomcatConnectorCustomizer connectorCustomizer() {
		// Swagger encodes the slash in branch paths
		return connector -> connector.setEncodedSolidusHandling(EncodedSolidusHandling.DECODE.getValue());
	}

	@Override
	public void run(ApplicationArguments applicationArguments) throws InterruptedException, ServiceException, IOException, ReleaseImportException {
		try {
			boolean deleteIndices = applicationArguments.containsOption(DELETE_INDICES_FLAG);
			if (deleteIndices) {
				logger.warn("Deleting existing Elasticsearch Indices");
				initialiseIndices(true);
			}

			updateIndexMaxTermsSetting(QueryConcept.class);
			updateIndexMaxTermsSettingForAllSnomedComponents();
			updateIndexMappingFieldsLimitSetting();

			codeSystemService.init();
			referenceSetMemberService.init();

			logger.info("--- Snowstorm startup complete ---");

			logger.info("Warming CodeSystem aggregation cache...");
			List<CodeSystem> codeSystems = codeSystemService.findAll();

			logger.info("Warming CodeSystemVersion dependency cache...");
			codeSystemVersionService.initDependantVersionCache(codeSystems);

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
			else if (applicationArguments.containsOption(SYNDICATION)) {
				startupSyndicationService.handleStartupSyndication(applicationArguments);
			}
			if (applicationArguments.containsOption(EXIT)) {
				logger.info("Exiting application.");
				((ConfigurableApplicationContext)applicationContext).close();
				Thread.sleep(5_000);// Allow graceful shutdown
				System.exit(0);
			}
		} catch (Exception e) {
			// Logging and rethrowing because Spring does not seem to log this
			logger.error(e.getMessage(), e);
			throw e;
		}
	}

	public static String getOneValueOrDefault(ApplicationArguments applicationArguments, String argName, String defaultValue) {
        return CollectionUtils.orEmpty(applicationArguments.getOptionValues(argName)).isEmpty() ? defaultValue : getOneValue(applicationArguments, argName);
    }

	private static String getOneValue(ApplicationArguments applicationArguments, String argName) {
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
