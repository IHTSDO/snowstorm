package org.ihtsdo.elasticsnomed;

import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.elasticsnomed.core.data.services.ConceptService;
import org.ihtsdo.elasticsnomed.core.data.services.ReferenceSetMemberService;
import org.ihtsdo.elasticsnomed.core.rf2.rf2import.ImportService;
import org.ihtsdo.elasticsnomed.core.rf2.RF2Type;
import org.ihtsdo.elasticsnomed.mrcm.MRCMService;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

@EnableSwagger2
public class App extends Config implements ApplicationRunner {

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

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public static void main(String[] args) {
		System.setProperty("org.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH", "true"); // Swagger encodes the slash in branch paths
		SpringApplication.run(App.class, args);
	}

	@Override
	public void run(ApplicationArguments applicationArguments) throws Exception {
		mrcmService.load();
		referenceSetMemberService.init();

		if (applicationArguments.containsOption("clean-import")) {
			// import the international edition from disk at startup
			deleteAllAndImportInternationalEditionFromDisk();
		}
	}

	@SuppressWarnings("unused")
	private void deleteAllAndImportInternationalEditionFromDisk() {
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
			String releasePath = "release/SnomedCT_InternationalRF2_Production_20170131.zip";
			try {
				importService.importArchive(importId, new FileInputStream(releasePath));
			} catch (FileNotFoundException | ReleaseImportException e) {
				logger.error("Import failed.", e);
			}
		}).start();
	}

}
