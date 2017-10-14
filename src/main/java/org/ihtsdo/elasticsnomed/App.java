package org.ihtsdo.elasticsnomed;

import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.elasticsnomed.config.Config;
import org.ihtsdo.elasticsnomed.core.data.services.CodeSystemService;
import org.ihtsdo.elasticsnomed.core.data.services.ConceptService;
import org.ihtsdo.elasticsnomed.core.data.services.ReferenceSetMemberService;
import org.ihtsdo.elasticsnomed.core.rf2.RF2Type;
import org.ihtsdo.elasticsnomed.core.rf2.rf2import.ImportService;
import org.ihtsdo.elasticsnomed.mrcm.MRCMService;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	public static final String CLEAN_IMPORT_ARG = "clean-import";
	public static final String CLEAN_IMPORT_FULL_ARG = "clean-import-full";
	public static final String REPLAY_TRACEABILITY_DIRECTORY = "replay-traceability-directory";

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

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
		SpringApplication.run(App.class, args);
	}
	
	@Override
	public void run(ApplicationArguments applicationArguments) throws Exception {
		codeSystemService.init();
		mrcmService.load();
		referenceSetMemberService.init();

		if (applicationArguments.containsOption(CLEAN_IMPORT_ARG)) {
			// Import a single release or 'Snapshot' from an Edition RF2 zip file from disk at startup
			String releasePath = getOneValue(applicationArguments, CLEAN_IMPORT_ARG);
			fileExistsForArgument(releasePath, CLEAN_IMPORT_ARG);

			deleteAllAndImportEditionRF2FromDisk(releasePath, RF2Type.SNAPSHOT);
		} else if (applicationArguments.containsOption(CLEAN_IMPORT_FULL_ARG)) {
			// Import many releases or 'Full' from an Edition RF2 zip file from disk at startup
			String releasePath = getOneValue(applicationArguments, CLEAN_IMPORT_FULL_ARG);
			fileExistsForArgument(releasePath, CLEAN_IMPORT_FULL_ARG);

			deleteAllAndImportEditionRF2FromDisk(releasePath, RF2Type.FULL);
		}
		if (applicationArguments.containsOption(REPLAY_TRACEABILITY_DIRECTORY)) {
			String replayDirectory = getOneValue(applicationArguments, REPLAY_TRACEABILITY_DIRECTORY);
			File file = new File(replayDirectory);
			if (!file.isDirectory()) {
				throw new IllegalArgumentException(REPLAY_TRACEABILITY_DIRECTORY + " directory could not be found at " + file.getAbsolutePath());
			}

			getAuthoringMirrorService().replayDirectoryOfFiles(replayDirectory);
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

	private void deleteAllAndImportEditionRF2FromDisk(String releasePath, RF2Type importType) {
		// Wait 10 seconds until everything settled before deleting all components
		try {
			Thread.sleep(1000 * 10);
		} catch (InterruptedException e) {
			throw new RuntimeException("Sleep failed", e);
		}
		// Dropping the indexes would be cleaner but the spring repositories
		// won't reinitialise so deleting documents instead..
		logger.info("Attempting delete all snomed content");
		conceptService.deleteAll();
		branchService.deleteAll();
		logger.info("Delete all complete");

		// Import archive
		logger.info("Creating MAIN");
		branchService.create("MAIN");
		String importId = importService.createJob(importType, "MAIN");
		try {
			importService.importArchive(importId, new FileInputStream(releasePath));
		} catch (FileNotFoundException | ReleaseImportException e) {
			logger.error("Import failed.", e);
		}
	}

}
