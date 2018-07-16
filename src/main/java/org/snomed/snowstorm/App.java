package org.snomed.snowstorm;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.config.Config;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.rf2.rf2import.ImportService;
import org.snomed.snowstorm.mrcm.MRCMService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.jms.annotation.EnableJms;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;

@EnableSwagger2
@EnableJms
public class App extends Config implements ApplicationRunner {

	public static final String DELETE_INDICES_FLAG = "delete-indices";
	public static final String IMPORT_ARG = "import";
	public static final String IMPORT_FULL_ARG = "import-full";
	public static final String REPLAY_TRACEABILITY_DIRECTORY = "replay-traceability-directory";

	@Autowired
	private ImportService importService;
	
	@Autowired
	private MRCMService mrcmService;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	@Autowired
	private CodeSystemService codeSystemService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public static void main(String[] args) {
		System.setProperty("org.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH", "true"); // Swagger encodes the slash in branch paths
		System.setProperty("tomcat.util.http.parser.HttpParser.requestTargetAllow", "{}|"); // Allow these unencoded characters in URL (used in ECL)
		SpringApplication.run(App.class, args);
	}
	
	@Override
	public void run(ApplicationArguments applicationArguments) throws Exception {
		try {
			boolean deleteIndices = applicationArguments.containsOption(DELETE_INDICES_FLAG);
			if (deleteIndices) logger.warn("Deleting existing Elasticsearch Indices");
			initialiseIndices(deleteIndices);

			codeSystemService.init();
			mrcmService.load();
			referenceSetMemberService.init();

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
			if (applicationArguments.containsOption(REPLAY_TRACEABILITY_DIRECTORY)) {
				String replayDirectory = getOneValue(applicationArguments, REPLAY_TRACEABILITY_DIRECTORY);
				File file = new File(replayDirectory);
				if (!file.isDirectory()) {
					throw new IllegalArgumentException(REPLAY_TRACEABILITY_DIRECTORY + " directory could not be found at " + file.getAbsolutePath());
				}

				getAuthoringMirrorService().replayDirectoryOfFiles(replayDirectory);
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
		String importId = importService.createJob(importType, "MAIN");
		try {
			importService.importArchive(importId, new FileInputStream(releasePath));
		} catch (FileNotFoundException | ReleaseImportException e) {
			logger.error("Import failed.", e);
		}
	}

}
