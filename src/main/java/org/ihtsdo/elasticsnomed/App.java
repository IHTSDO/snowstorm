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
			// import the international edition from disk at startup
			List<String> values = applicationArguments.getOptionValues(CLEAN_IMPORT_ARG);
			if (values.size() != 1) {
				throw new IllegalArgumentException(CLEAN_IMPORT_ARG + " argument must have exactly one value");
			}
			String releasePath = values.get(0);
			File file = new File(releasePath);
			if (!file.isFile()) {
				throw new IllegalArgumentException(CLEAN_IMPORT_ARG + " file could not be read at " + file.getAbsolutePath());
			}

			deleteAllAndImportInternationalEditionFromDisk(releasePath);
		}
	}

	private void deleteAllAndImportInternationalEditionFromDisk(String releasePath) {
		new Thread(() -> {
			// Wait 10 seconds until everything settled before deleting all components
			try {
				Thread.sleep(1000 * 10);
			} catch (InterruptedException e) {
				throw new RuntimeException("Sleep failed", e);
			}
			// Dropping the indexes would be cleaner but the spring repositories
			// won't reinitialise so deleting documents instead..
			logger.info("Attempting delete all");
			conceptService.deleteAll();
			branchService.deleteAll();
			logger.info("Delete all complete");

			// Import archive
			logger.info("Creating MAIN");
			branchService.create("MAIN");
			String importId = importService.createJob(RF2Type.SNAPSHOT, "MAIN");
			try {
				importService.importArchive(importId, new FileInputStream(releasePath));
			} catch (FileNotFoundException | ReleaseImportException e) {
				logger.error("Import failed.", e);
			}
		}).start();
	}

}
