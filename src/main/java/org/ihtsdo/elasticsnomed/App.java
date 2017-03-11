package org.ihtsdo.elasticsnomed;

import io.kaicode.elasticvc.api.BranchService;
import org.ihtsdo.elasticsnomed.rf2import.ImportService;
import org.ihtsdo.elasticsnomed.services.ConceptService;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import javax.annotation.PostConstruct;

@EnableSwagger2
public class App extends Config {

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private ImportService importService;

	final Logger logger = LoggerFactory.getLogger(getClass());

	public static void main(String[] args) {
		System.setProperty("org.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH", "true"); // Swagger encodes the slash in branch paths
		SpringApplication.run(App.class, args);
	}

	@PostConstruct
	public void run() throws Exception {
		new Thread(() -> {
			try {
				Thread.sleep(1000 * 10);
				// Uncomment to import the international edition from disk at startup
				logger.info("Attempting delete all");
				conceptService.deleteAll();
				branchService.deleteAll();
				logger.info("Delete all complete");
				logger.info("Creating MAIN");
				branchService.create("MAIN");
				String releasePath = "release/SnomedCT_InternationalRF2_Production_20170131";
				importService.importSnapshot(releasePath, "MAIN");
			} catch (ReleaseImportException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}).start();
	}

}
