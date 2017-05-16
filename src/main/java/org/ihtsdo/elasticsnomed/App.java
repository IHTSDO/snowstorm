package org.ihtsdo.elasticsnomed;

import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.elasticsnomed.core.data.services.ConceptService;
import org.ihtsdo.elasticsnomed.core.rf2import.ImportService;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@EnableSwagger2
public class App extends Config implements ApplicationRunner {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ImportService importService;

	final Logger logger = LoggerFactory.getLogger(getClass());

	public static void main(String[] args) {
		System.setProperty("org.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH", "true"); // Swagger encodes the slash in branch paths
		SpringApplication.run(App.class, args);
	}

	@Override
	public void run(ApplicationArguments applicationArguments) throws Exception {
		if (applicationArguments.containsOption("clean-import")) {
			// import the international edition from disk at startup
			deleteAllAndImportInternationalEditionFromDisk();
		}
	}

	@SuppressWarnings("unused")
	private void deleteAllAndImportInternationalEditionFromDisk() {
		new Thread(() -> {
			try {
				Thread.sleep(1000 * 10);
				logger.info("Attempting delete all");
				conceptService.deleteAll();
				branchService.deleteAll();
				logger.info("Delete all complete");
				logger.info("Creating MAIN");
				branchService.create("MAIN");
				String releasePath = "release/SnomedCT_InternationalRF2_Production_20170131";
				importService.importSnapshot(releasePath, "MAIN");
			} catch (ReleaseImportException | InterruptedException e) {
				logger.error("Import failed", e);
			}
		}).start();
	}

}
